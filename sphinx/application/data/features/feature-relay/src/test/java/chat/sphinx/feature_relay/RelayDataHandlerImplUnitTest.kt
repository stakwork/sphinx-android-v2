package chat.sphinx.feature_relay

import chat.sphinx.concept_crypto_rsa.RSA
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.feature_crypto_rsa.RSAAlgorithm
import chat.sphinx.feature_crypto_rsa.RSAImpl
import chat.sphinx.wrapper_relay.AuthorizationToken
import io.matthewnelson.k_openssl.isSalted
import io.matthewnelson.test_feature_authentication_core.AuthenticationCoreDefaultsTestHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class RelayDataHandlerImplUnitTest: AuthenticationCoreDefaultsTestHelper() {

    companion object {
        private const val RAW_URL = "https://some-endpoint.chat:3001"
        private const val RAW_JWT = "gsaAiFtGG/RfsaO"
    }

    private val testRSA: RSA by lazy {
        RSAImpl(RSAAlgorithm.RSA)
    }

    private val relayHandler: RelayDataHandler by lazy {
        RelayDataHandlerImpl(
            testStorage,
            testCoreManager,
            dispatchers,
            testHandler,
            testRSA
        )
    }

    @Test
    fun `login is required for anything to work`() =
        runTest(testDispatcher) {
            Assert.assertFalse(relayHandler.persistAuthorizationToken(AuthorizationToken(RAW_JWT)))
            Assert.assertNull(relayHandler.retrieveAuthorizationToken())
        }

    @Test
    fun `persisted data is encrypted`() =
        runTest(testDispatcher) {
            login()

            Assert.assertTrue(relayHandler.persistAuthorizationToken(AuthorizationToken(RAW_JWT)))
            testStorage.getString(RelayDataHandlerImpl.RELAY_AUTHORIZATION_KEY, null)?.let { encryptedJwt ->
                Assert.assertTrue(encryptedJwt.isSalted)
            } ?: Assert.fail("Failed to persist relay jwt to storage")
        }

    @Test
    fun `clearing JavaWebToken updates storage properly`() =
        runTest(testDispatcher) {
            login()

            relayHandler.persistAuthorizationToken(AuthorizationToken(RAW_JWT))
            testStorage.getString(RelayDataHandlerImpl.RELAY_AUTHORIZATION_KEY, null)?.let { encryptedJwt ->
                Assert.assertTrue(encryptedJwt.isSalted)
            } ?: Assert.fail("Failed to persist relay jwt to storage")

            relayHandler.persistAuthorizationToken(null)
            val notInStorage = "NOT_IN_STORAGE"
            testStorage.getString(RelayDataHandlerImpl.RELAY_AUTHORIZATION_KEY, notInStorage).let { jwt ->
                // default value is returned if persisted value is null
                if (jwt != notInStorage) {
                    Assert.fail("Java Web Token was not cleared from storage")
                }
            }
        }

    // ---- Hive token tests ----

    @Test
    fun `login is required for hive token operations`() =
        runTest(testDispatcher) {
            Assert.assertFalse(relayHandler.persistHiveToken("some.valid.jwt"))
            Assert.assertNull(relayHandler.retrieveHiveToken())
        }

    @Test
    fun `persisted hive token is encrypted`() =
        runTest(testDispatcher) {
            login()

            Assert.assertTrue(relayHandler.persistHiveToken(RAW_JWT))
            testStorage.getString(RelayDataHandlerImpl.HIVE_TOKEN_KEY, null)?.let { encryptedToken ->
                Assert.assertTrue(encryptedToken.isSalted)
            } ?: Assert.fail("Failed to persist Hive token to storage")
        }

    @Test
    fun `hive token encrypt decrypt round trip`() =
        runTest(testDispatcher) {
            login()

            Assert.assertTrue(relayHandler.persistHiveToken(RAW_JWT))
            val retrieved = relayHandler.retrieveHiveToken()
            Assert.assertEquals(RAW_JWT, retrieved)
        }

    @Test
    fun `empty string hive token is rejected`() =
        runTest(testDispatcher) {
            login()

            val result = relayHandler.persistHiveToken("")
            Assert.assertFalse("persistHiveToken(\"\") should return false", result)

            // Storage must remain untouched
            val notInStorage = "NOT_IN_STORAGE"
            val stored = testStorage.getString(RelayDataHandlerImpl.HIVE_TOKEN_KEY, notInStorage)
            Assert.assertEquals("Storage should be untouched after empty-string rejection", notInStorage, stored)
        }

    @Test
    fun `null hive token clears storage`() =
        runTest(testDispatcher) {
            login()

            relayHandler.persistHiveToken(RAW_JWT)
            testStorage.getString(RelayDataHandlerImpl.HIVE_TOKEN_KEY, null)?.let { encryptedToken ->
                Assert.assertTrue(encryptedToken.isSalted)
            } ?: Assert.fail("Failed to persist Hive token to storage")

            Assert.assertTrue(relayHandler.persistHiveToken(null))
            val notInStorage = "NOT_IN_STORAGE"
            testStorage.getString(RelayDataHandlerImpl.HIVE_TOKEN_KEY, notInStorage).let { stored ->
                if (stored != notInStorage) {
                    Assert.fail("Hive token was not cleared from storage")
                }
            }
            Assert.assertNull(relayHandler.retrieveHiveToken())
        }
}
