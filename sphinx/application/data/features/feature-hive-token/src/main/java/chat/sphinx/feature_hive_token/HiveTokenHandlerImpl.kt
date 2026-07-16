package chat.sphinx.feature_hive_token

import chat.sphinx.concept_hive_token.HiveTokenHandler
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_encryption_key.EncryptionKeyHandler
import io.matthewnelson.feature_authentication_core.AuthenticationCoreManager
import io.matthewnelson.k_openssl.KOpenSSL
import io.matthewnelson.k_openssl.algos.AES256CBC_PBKDF2_HMAC_SHA256
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import io.matthewnelson.crypto_common.annotations.UnencryptedDataAccess
import io.matthewnelson.crypto_common.clazzes.EncryptedString
import io.matthewnelson.crypto_common.clazzes.Password
import io.matthewnelson.crypto_common.clazzes.UnencryptedString
import io.matthewnelson.crypto_common.exceptions.DecryptionException
import io.matthewnelson.crypto_common.exceptions.EncryptionException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stores/retrieves the Hive auth token in encrypted form, keyed by [HIVE_AUTH_TOKEN_KEY].
 *
 * No in-memory cache is maintained intentionally — a single non-expiring token does not
 * need one, and a cache would risk a plaintext credential surviving logout/lock/account-switch.
 *
 * All methods require the user to be logged in (encryption key present); otherwise `false`/`null`
 * is returned immediately with no write to storage.
 */
class HiveTokenHandlerImpl(
    private val authenticationStorage: AuthenticationStorage,
    private val authenticationCoreManager: AuthenticationCoreManager,
    private val dispatchers: CoroutineDispatchers,
    private val encryptionKeyHandler: EncryptionKeyHandler,
) : HiveTokenHandler(), CoroutineDispatchers by dispatchers {

    companion object {
        const val HIVE_AUTH_TOKEN_KEY = "HIVE_AUTH_TOKEN_KEY"
    }

    private val kOpenSSL: KOpenSSL by lazy {
        AES256CBC_PBKDF2_HMAC_SHA256()
    }

    @OptIn(UnencryptedDataAccess::class, RawPasswordAccess::class)
    @Throws(EncryptionException::class, IllegalArgumentException::class)
    private suspend fun encryptData(
        privateKey: Password,
        data: UnencryptedString,
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
        data: EncryptedString,
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

    private val lock = Mutex()

    /**
     * Encrypts [token] and writes it to [AuthenticationStorage] under [HIVE_AUTH_TOKEN_KEY].
     * Overwrites any existing value. Returns `false` if the encryption key is absent (user not
     * logged in) — no partial write occurs in that case.
     */
    override suspend fun persistHiveToken(token: String): Boolean {
        return authenticationCoreManager.getEncryptionKey()?.privateKey?.let { privateKey ->
            lock.withLock {
                val encryptedToken = try {
                    encryptData(privateKey, UnencryptedString(token))
                } catch (e: Exception) {
                    return@withLock false
                }
                authenticationStorage.putString(HIVE_AUTH_TOKEN_KEY, encryptedToken.value)
                true
            }
        } ?: false
    }

    /**
     * Reads and decrypts the stored Hive token. Returns `null` if the encryption key is absent,
     * no token has been stored, or decryption fails for any reason.
     */
    @OptIn(UnencryptedDataAccess::class)
    override suspend fun retrieveHiveToken(): String? {
        return authenticationCoreManager.getEncryptionKey()?.privateKey?.let { privateKey ->
            lock.withLock {
                authenticationStorage.getString(HIVE_AUTH_TOKEN_KEY, null)
                    ?.let { encryptedTokenString ->
                        try {
                            decryptData(privateKey, EncryptedString(encryptedTokenString)).value
                        } catch (e: Exception) {
                            null
                        }
                    }
            }
        }
    }

    /**
     * Clears the stored Hive token from [AuthenticationStorage]. Always returns `true`.
     */
    override suspend fun clearHiveToken(): Boolean {
        lock.withLock {
            authenticationStorage.putString(HIVE_AUTH_TOKEN_KEY, null)
        }
        return true
    }
}
