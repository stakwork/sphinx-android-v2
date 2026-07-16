package chat.sphinx.feature_hive_token

import io.matthewnelson.k_openssl.isSalted
import io.matthewnelson.test_feature_authentication_core.AuthenticationCoreDefaultsTestHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

class HiveTokenHandlerImplUnitTest : AuthenticationCoreDefaultsTestHelper() {

    companion object {
        private const val SAMPLE_TOKEN = "hive-test-token-abc123"
        private const val SAMPLE_TOKEN_2 = "hive-test-token-xyz789"
    }

    private val hiveTokenHandler: HiveTokenHandlerImpl by lazy {
        HiveTokenHandlerImpl(
            testStorage,
            testCoreManager,
            dispatchers,
            testHandler,
        )
    }

    // -----------------------------------------------------------------------
    // Login-guard: nothing works without an active session
    // -----------------------------------------------------------------------

    @Test
    fun `login is required for persistHiveToken to write anything`() =
        runTest(testDispatcher) {
            val result = hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN)
            Assert.assertFalse(result)
            // Nothing should have been written
            val stored = testStorage.getString(HiveTokenHandlerImpl.HIVE_AUTH_TOKEN_KEY, null)
            Assert.assertNull(stored)
        }

    @Test
    fun `login is required for retrieveHiveToken to return anything`() =
        runTest(testDispatcher) {
            val result = hiveTokenHandler.retrieveHiveToken()
            Assert.assertNull(result)
        }

    // -----------------------------------------------------------------------
    // Encrypt-store-retrieve round-trip
    // -----------------------------------------------------------------------

    @Test
    fun `persisted token is encrypted in storage`() =
        runTest(testDispatcher) {
            login()

            val persisted = hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN)
            Assert.assertTrue(persisted)

            val raw = testStorage.getString(HiveTokenHandlerImpl.HIVE_AUTH_TOKEN_KEY, null)
            Assert.assertNotNull("No value written to storage", raw)
            Assert.assertTrue("Stored value should be salted/encrypted", raw!!.isSalted)
        }

    @Test
    fun `encrypt-store-retrieve round-trip returns original token`() =
        runTest(testDispatcher) {
            login()

            hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN)
            val retrieved = hiveTokenHandler.retrieveHiveToken()
            Assert.assertEquals(SAMPLE_TOKEN, retrieved)
        }

    @Test
    fun `overwriting token stores the new value`() =
        runTest(testDispatcher) {
            login()

            hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN)
            hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN_2)

            val retrieved = hiveTokenHandler.retrieveHiveToken()
            Assert.assertEquals(SAMPLE_TOKEN_2, retrieved)
        }

    // -----------------------------------------------------------------------
    // clearHiveToken
    // -----------------------------------------------------------------------

    @Test
    fun `clearHiveToken followed by retrieveHiveToken returns null`() =
        runTest(testDispatcher) {
            login()

            hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN)
            Assert.assertEquals(SAMPLE_TOKEN, hiveTokenHandler.retrieveHiveToken())

            hiveTokenHandler.clearHiveToken()

            val notInStorage = "NOT_IN_STORAGE"
            val raw = testStorage.getString(HiveTokenHandlerImpl.HIVE_AUTH_TOKEN_KEY, notInStorage)
            // putString(key, null) means the default is returned
            Assert.assertEquals(
                "Token should have been cleared from storage",
                notInStorage,
                raw
            )

            val retrieved = hiveTokenHandler.retrieveHiveToken()
            Assert.assertNull(retrieved)
        }

    // -----------------------------------------------------------------------
    // Corrupted data
    // -----------------------------------------------------------------------

    @Test
    fun `corrupted stored value causes retrieveHiveToken to return null without throwing`() =
        runTest(testDispatcher) {
            login()

            // Write garbage directly into storage (not encrypted)
            testStorage.putString(HiveTokenHandlerImpl.HIVE_AUTH_TOKEN_KEY, "not-valid-encrypted-data")

            val retrieved = hiveTokenHandler.retrieveHiveToken()
            Assert.assertNull("Corrupted value should yield null, not an exception", retrieved)
        }

    // -----------------------------------------------------------------------
    // Concurrent writes do not corrupt the stored value
    // -----------------------------------------------------------------------

    @Test
    fun `concurrent persist calls leave exactly one complete token`() =
        runTest(testDispatcher) {
            login()

            // Two coroutines racing to persist different tokens
            val results = awaitAll(
                async { hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN) },
                async { hiveTokenHandler.persistHiveToken(SAMPLE_TOKEN_2) },
            )

            // Both should have returned true
            Assert.assertTrue(results.all { it })

            // The final stored value must be one of the two complete tokens (no mix/corruption)
            val retrieved = hiveTokenHandler.retrieveHiveToken()
            Assert.assertTrue(
                "Final token must be one of the two persisted values, got: $retrieved",
                retrieved == SAMPLE_TOKEN || retrieved == SAMPLE_TOKEN_2
            )
        }
}
