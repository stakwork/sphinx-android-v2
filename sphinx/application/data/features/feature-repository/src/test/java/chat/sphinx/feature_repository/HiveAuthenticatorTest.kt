package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.feature_repository.util.HiveAuthenticator
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.LogType
import chat.sphinx.logger.SphinxLogger
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiveAuthenticatorTest {

    // ─── Fakes ───────────────────────────────────────────────────────────────

    private class FakeAuthenticationStorage : AuthenticationStorage {
        val storage = mutableMapOf<String, String?>()
        var putStringCallCount = 0

        override suspend fun getString(key: String, defaultValue: String?): String? =
            storage[key] ?: defaultValue

        override suspend fun putString(key: String, value: String?) {
            putStringCallCount++
            storage[key] = value
        }

        override suspend fun removeString(key: String) {
            storage.remove(key)
        }
    }

    private class FakeNetworkQueryHive(
        private val response: Flow<LoadResponse<HiveAuthTokenDto, ResponseError>>
    ) : NetworkQueryHive() {
        var callCount = 0

        override fun getHiveAuthToken(
            token: String,
            pubkey: String,
            timestamp: String
        ): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> {
            callCount++
            return response
        }
    }

    private class NoOpLogger : SphinxLogger() {
        override fun log(tag: String, message: String, type: LogType, throwable: Throwable?) {}
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun successFlow(token: String = "test-token"): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> =
        flow { emit(Response.Success(HiveAuthTokenDto(token))) }

    private fun errorFlow(): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> =
        flow { emit(Response.Error(ResponseError("error"))) }

    private fun buildAuthenticator(
        networkQueryHive: NetworkQueryHive,
        storage: AuthenticationStorage,
        signedToken: () -> String? = { "signed-token" },
        pubKey: () -> String? = { "03" + "a".repeat(64) },
    ): HiveAuthenticator = HiveAuthenticator(
        networkQueryHive = networkQueryHive,
        authenticationStorage = storage,
        getSignedTimestamp = signedToken,
        getNodePubKey = pubKey,
        log = NoOpLogger(),
    )

    // ─── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `null signed token aborts before any network call and never writes storage`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(successFlow())
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
                signedToken = { null },
            )

            auth.authenticate()

            assertEquals("no network call should be made", 0, fakeNetwork.callCount)
            assertEquals("putString must not be called", 0, fakeStorage.putStringCallCount)
        }

    @Test
    fun `null pubkey aborts before any network call and never writes storage`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(successFlow())
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
                pubKey = { null },
            )

            auth.authenticate()

            assertEquals("no network call should be made", 0, fakeNetwork.callCount)
            assertEquals("putString must not be called", 0, fakeStorage.putStringCallCount)
        }

    @Test
    fun `blank pubkey aborts before any network call and never writes storage`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(successFlow())
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
                pubKey = { "   " },
            )

            auth.authenticate()

            assertEquals("no network call should be made", 0, fakeNetwork.callCount)
            assertEquals("putString must not be called", 0, fakeStorage.putStringCallCount)
        }

    @Test
    fun `valid token and pubkey with Success response stores token exactly once`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(successFlow("hive-token-xyz"))
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
            )

            auth.authenticate()

            assertEquals("putString called exactly once", 1, fakeStorage.putStringCallCount)
            assertEquals(
                "stored token matches response",
                "hive-token-xyz",
                fakeStorage.storage[HiveAuthenticator.HIVE_TOKEN_KEY]
            )
        }

    @Test
    fun `Response Error never writes to storage`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(errorFlow())
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
            )

            auth.authenticate()

            assertEquals("network call should be made", 1, fakeNetwork.callCount)
            assertEquals("putString must not be called on error", 0, fakeStorage.putStringCallCount)
            assertNull(
                "no token stored on error",
                fakeStorage.storage[HiveAuthenticator.HIVE_TOKEN_KEY]
            )
        }

    @Test
    fun `second call within 60 seconds short-circuits without network call or storage write`() =
        runBlockingTest {
            val fakeNetwork = FakeNetworkQueryHive(successFlow())
            val fakeStorage = FakeAuthenticationStorage()

            val auth = buildAuthenticator(
                networkQueryHive = fakeNetwork,
                storage = fakeStorage,
            )

            // First call succeeds and sets lastAuthMs
            auth.authenticate()
            assertEquals("first call should hit network", 1, fakeNetwork.callCount)
            assertEquals("first call should write storage", 1, fakeStorage.putStringCallCount)

            // Second call immediately (within 60s window) should short-circuit
            auth.authenticate()
            assertEquals("second call must NOT hit network", 1, fakeNetwork.callCount)
            assertEquals("second call must NOT write storage", 1, fakeStorage.putStringCallCount)
        }
}
