package chat.sphinx.feature_relay

import chat.sphinx.concept_crypto_rsa.RSA
import chat.sphinx.concept_network_tor.TorManager
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.kotlin_response.Response
import chat.sphinx.wrapper_relay.*
import chat.sphinx.wrapper_rsa.RsaPublicKey
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_encryption_key.EncryptionKeyHandler
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import io.matthewnelson.crypto_common.annotations.UnencryptedDataAccess
import io.matthewnelson.crypto_common.clazzes.EncryptedString
import io.matthewnelson.crypto_common.clazzes.Password
import io.matthewnelson.crypto_common.clazzes.UnencryptedString
import io.matthewnelson.crypto_common.exceptions.DecryptionException
import io.matthewnelson.crypto_common.exceptions.EncryptionException
import io.matthewnelson.crypto_common.extensions.toHex
import io.matthewnelson.feature_authentication_core.AuthenticationCoreManager
import io.matthewnelson.k_openssl.KOpenSSL
import io.matthewnelson.k_openssl.algos.AES256CBC_PBKDF2_HMAC_SHA256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class RelayDataHandlerImpl(
    private val authenticationStorage: AuthenticationStorage,
    private val authenticationCoreManager: AuthenticationCoreManager,
    private val dispatchers: CoroutineDispatchers,
    private val encryptionKeyHandler: EncryptionKeyHandler,
    private val torManager: TorManager,
    private val rsa: RSA,
) : RelayDataHandler(), CoroutineDispatchers by dispatchers {

    companion object {
        @Volatile
        private var tokenCache: AuthorizationToken? = null


        const val RELAY_AUTHORIZATION_KEY = "RELAY_JWT_KEY"
    }

    private val kOpenSSL: KOpenSSL by lazy {
        AES256CBC_PBKDF2_HMAC_SHA256()
    }

    @OptIn(UnencryptedDataAccess::class, RawPasswordAccess::class)
    @Throws(EncryptionException::class, IllegalArgumentException::class)
    private suspend fun encryptData(
        privateKey: Password,
        data: UnencryptedString
    ): EncryptedString {
        if (privateKey.value.isEmpty()) {
            throw IllegalArgumentException("Private Key cannot be empty")
        }
        if (data.value.isEmpty()) {
            throw IllegalArgumentException("Data cannot be empty")
        }

        return try {
            kOpenSSL.encrypt(
                privateKey,
                encryptionKeyHandler.getTestStringEncryptHashIterations(privateKey),
                data,
                default
            )
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data", e)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    @OptIn(UnencryptedDataAccess::class, RawPasswordAccess::class)
    @Throws(DecryptionException::class, IllegalArgumentException::class)
    private suspend fun decryptData(
        privateKey: Password,
        data: EncryptedString
    ): UnencryptedString {
        if (privateKey.value.isEmpty()) {
            throw IllegalArgumentException("Private Key cannot be empty")
        }
        if (data.value.isEmpty()) {
            throw IllegalArgumentException("Data cannot be empty")
        }

        return try {
            kOpenSSL.decrypt(
                privateKey,
                encryptionKeyHandler.getTestStringEncryptHashIterations(privateKey),
                data,
                default
            )
        } catch (e: Exception) {
            throw DecryptionException("Failed to decrypt data", e)
        }
    }

    private fun signHMacSha256(
        key: RelayHMacKey,
        text: String
    ) : String {
        val sha256HMac = Mac.getInstance("HMacSHA256")

        val secretKey = SecretKeySpec(
            key.value.toByteArray(), "HMacSHA256"
        )
        sha256HMac.init(secretKey)

        return sha256HMac.doFinal(
            text.toByteArray()
        ).toHex()
    }

    private val lock = Mutex()

    override suspend fun persistAuthorizationToken(token: AuthorizationToken?): Boolean {
        return authenticationCoreManager.getEncryptionKey()?.privateKey?.let { privateKey ->
            persistJavaWebTokenImpl(token, privateKey)
        } ?: false
    }

    /**
     * If sending `null` argument for [token], an empty [Password] is safe to send as this
     * will only clear the token from storage and not encrypt anything.
     * */
    suspend fun persistJavaWebTokenImpl(token: AuthorizationToken?, privateKey: Password): Boolean {
        lock.withLock {
            if (token == null) {
                authenticationStorage.putString(RELAY_AUTHORIZATION_KEY, null)
                tokenCache = null
                return true
            } else {
                val encryptedJWT = try {
                    encryptData(privateKey, UnencryptedString(token.value))
                } catch (e: Exception) {
                    return false
                }

                authenticationStorage.putString(RELAY_AUTHORIZATION_KEY, encryptedJWT.value)
                tokenCache = token
                return true
            }
        }
    }

    @OptIn(UnencryptedDataAccess::class)
    override suspend fun retrieveAuthorizationToken(): AuthorizationToken? {
        return authenticationCoreManager.getEncryptionKey()?.privateKey?.let { privateKey ->
            lock.withLock {
                tokenCache ?: authenticationStorage.getString(RELAY_AUTHORIZATION_KEY, null)
                    ?.let { encryptedJwtString ->
                        try {
                            decryptData(privateKey, EncryptedString(encryptedJwtString))
                                .value
                                .let { decryptedJwtString ->
                                    val token = AuthorizationToken(decryptedJwtString)
                                    tokenCache = token
                                    token
                                }
                        } catch (e: Exception) {
                            null
                        }
                    }
            }
        }
    }

}
