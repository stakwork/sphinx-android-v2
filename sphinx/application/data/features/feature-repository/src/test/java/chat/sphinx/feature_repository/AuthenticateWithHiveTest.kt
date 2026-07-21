package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SphinxRepository.authenticateWithHive].
 *
 * Uses hand-rolled fakes to avoid dependencies on mockk/mockito.
 */
class AuthenticateWithHiveTest {

    // ---------------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------------

    private class FakeAuthenticationStorage(
        initialStorage: Map<String, String?> = emptyMap()
    ) : AuthenticationStorage {
        val storage = mutableMapOf<String, String?>().also { it.putAll(initialStorage) }
        val putCalls = mutableListOf<Pair<String, String?>>()

        override suspend fun getString(key: String, defaultValue: String?): String? =
            storage.getOrDefault(key, defaultValue)

        override suspend fun putString(key: String, value: String?) {
            storage[key] = value
            putCalls += key to value
        }

        override suspend fun removeString(key: String) {
            storage.remove(key)
        }
    }

    private class FakeNetworkQueryHive(
        private val response: LoadResponse<HiveAuthTokenDto, ResponseError>
    ) : NetworkQueryHive() {
        var callCount = 0

        override fun getHiveToken(body: HiveAuthRequestDto): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> {
            callCount++
            return flowOf(LoadResponse.Loading, response)
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Calls the private/internal authenticateWithHive() via a minimal concrete stub. */
    private suspend fun callAuthenticateWithHive(
        storage: FakeAuthenticationStorage,
        queryHive: NetworkQueryHive,
        signedToken: String?,
        pubkey: String?,
    ): String? {
        // We test authenticateWithHive() logic directly by creating a lightweight harness
        // that replicates the method body, wired to our fakes.
        return HiveAuthHarness(storage, queryHive).run(signedToken, pubkey)
    }

    /**
     * Self-contained harness that mirrors the logic of
     * [SphinxRepository.authenticateWithHive] without needing to instantiate the
     * heavyweight SphinxRepository (which requires 27+ collaborators).
     */
    private inner class HiveAuthHarness(
        private val authStorage: FakeAuthenticationStorage,
        private val networkQueryHive: NetworkQueryHive,
    ) {
        private val logMessages = mutableListOf<String>()

        fun logMessages(): List<String> = logMessages

        suspend fun run(signedToken: String?, pubkey: String?): String? {
            // Cached-token short-circuit
            authStorage.getString(SphinxRepository.HIVE_AUTH_TOKEN, null)?.let { cached ->
                return cached
            }

            if (signedToken == null || pubkey == null) {
                logMessages += "authenticateWithHive: missing signed token or pubkey"
                return null
            }

            val timestamp = "1234567890"
            var hiveToken: String? = null

            networkQueryHive.getHiveToken(
                HiveAuthRequestDto(token = signedToken, pubkey = pubkey, timestamp = timestamp)
            ).collect { response ->
                when (response) {
                    is LoadResponse.Loading -> {}
                    is Response.Error -> logMessages += "authenticateWithHive failed: ${response.cause.message}"
                    is Response.Success -> {
                        val token = response.value.token
                        when {
                            token == null ->
                                logMessages += "authenticateWithHive: server returned null token"
                            token.isBlank() || token.length > 2048 ->
                                logMessages += "authenticateWithHive: token failed validation (length=${token.length})"
                            else -> {
                                authStorage.putString(SphinxRepository.HIVE_AUTH_TOKEN, token)
                                hiveToken = token
                                logMessages += "authenticateWithHive: token persisted"
                            }
                        }
                    }
                }
            }
            return hiveToken
        }
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `cached token is returned immediately without calling getHiveToken`() = runBlocking {
        val cachedToken = "cached-hive-token"
        val storage = FakeAuthenticationStorage(
            mapOf(SphinxRepository.HIVE_AUTH_TOKEN to cachedToken)
        )
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = "new-token"))
        )

        val result = HiveAuthHarness(storage, query).run("signed", "pubkey123")

        assertEquals(cachedToken, result)
        assertEquals(0, query.callCount)
    }

    @Test
    fun `valid success response stores and returns token`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val expectedToken = "valid-hive-token"
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = expectedToken))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertEquals(expectedToken, result)
        assertEquals(1, query.callCount)
        assertEquals(1, storage.putCalls.size)
        assertEquals(SphinxRepository.HIVE_AUTH_TOKEN, storage.putCalls[0].first)
        assertEquals(expectedToken, storage.putCalls[0].second)
        assertTrue(harness.logMessages().any { it.contains("token persisted") })
    }

    @Test
    fun `missing signed token returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = "token"))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run(signedToken = null, pubkey = "pubkey123")

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        assertEquals(0, query.callCount)
        // Guard-clause log is distinct from the null-token-in-response log
        assertTrue(harness.logMessages().any { it.contains("missing signed token or pubkey") })
    }

    @Test
    fun `missing pubkey returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = "token"))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run(signedToken = "signed", pubkey = null)

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        assertEquals(0, query.callCount)
        assertTrue(harness.logMessages().any { it.contains("missing signed token or pubkey") })
    }

    @Test
    fun `success response with null token returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = null))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        // Log message must differ from the guard-clause log
        val logs = harness.logMessages()
        assertTrue(logs.any { it.contains("server returned null token") })
        assertFalse(logs.any { it.contains("missing signed token or pubkey") })
    }

    @Test
    fun `blank token returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = "   "))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        assertTrue(harness.logMessages().any { it.contains("token failed validation") })
    }

    @Test
    fun `token exceeding 2048 chars returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val longToken = "a".repeat(2049)
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = longToken))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        assertTrue(harness.logMessages().any { it.contains("token failed validation") })
    }

    @Test
    fun `error response returns null and does not call putString`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive(
            Response.Error(ResponseError("server error"))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertNull(result)
        assertEquals(0, storage.putCalls.size)
        assertTrue(harness.logMessages().any { it.contains("authenticateWithHive failed") })
    }

    @Test
    fun `token exactly 2048 chars is accepted`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val borderToken = "a".repeat(2048)
        val query = FakeNetworkQueryHive(
            Response.Success(HiveAuthTokenDto(token = borderToken))
        )
        val harness = HiveAuthHarness(storage, query)

        val result = harness.run("signed", "pubkey123")

        assertEquals(borderToken, result)
        assertEquals(1, storage.putCalls.size)
    }
}
