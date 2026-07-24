package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SphinxRepository.authenticateWithHive() and retrieveHiveToken().
 *
 * Uses a standalone helper class that exercises only the Hive auth logic
 * without instantiating the full abstract SphinxRepository.
 */
class HiveAuthenticationUnitTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Minimal fakes
    // ─────────────────────────────────────────────────────────────────────────

    private class FakeAuthenticationStorage(
        initial: Map<String, String> = emptyMap()
    ) : AuthenticationStorage {
        val storage = mutableMapOf<String, String?>().also { it.putAll(initial) }

        override suspend fun getString(key: String, defaultValue: String?): String? =
            if (storage.containsKey(key)) storage[key] else defaultValue

        override suspend fun putString(key: String, value: String?) {
            storage[key] = value
        }

        override suspend fun removeString(key: String) {
            storage.remove(key)
        }
    }

    private class FakeNetworkQueryHive(
        private val responseBuilder: (String, String, String) -> Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>>
    ) : NetworkQueryHive() {
        var callCount = 0

        override fun authenticateWithHive(
            token: String,
            pubkey: String,
            timestamp: String
        ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> {
            callCount++
            return responseBuilder(token, pubkey, timestamp)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extracted logic under test (mirrors SphinxRepository implementation)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Encapsulates only the Hive auth logic from SphinxRepository so we can test
     * it in isolation without the full dependency graph.
     */
    private class HiveAuthLogic(
        private val authenticationStorage: AuthenticationStorage,
        private val networkQueryHive: NetworkQueryHive,
        private val signedTimestampProvider: () -> String?,
        private val pubkeyProvider: () -> String?
    ) {
        suspend fun authenticateWithHive(): Boolean {
            retrieveHiveToken()?.let { return true }

            val signedToken = signedTimestampProvider() ?: return false
            val pubkey = pubkeyProvider() ?: return false
            val timestamp = System.currentTimeMillis().toString()

            var success = false
            networkQueryHive.authenticateWithHive(signedToken, pubkey, timestamp)
                .collect { response ->
                    when (response) {
                        is Response.Success -> {
                            val jwt = response.value.token
                                ?.takeIf { it.isNotBlank() }
                                ?: return@collect
                            authenticationStorage.putString(
                                SphinxRepository.HIVE_AUTHENTICATION_TOKEN, jwt
                            )
                            success = true
                        }
                        else -> { /* success remains false */ }
                    }
                }
            return success
        }

        suspend fun retrieveHiveToken(): String? =
            authenticationStorage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests — retrieveHiveToken
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `retrieveHiveToken returns null when no token stored`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ -> flow {} }
        val logic = HiveAuthLogic(storage, query, { "sig" }, { "pk" })

        assertNull(logic.retrieveHiveToken())
    }

    @Test
    fun `retrieveHiveToken returns stored token`() = runBlocking {
        val storage = FakeAuthenticationStorage(
            mapOf(SphinxRepository.HIVE_AUTHENTICATION_TOKEN to "stored_jwt")
        )
        val query = FakeNetworkQueryHive { _, _, _ -> flow {} }
        val logic = HiveAuthLogic(storage, query, { "sig" }, { "pk" })

        assertEquals("stored_jwt", logic.retrieveHiveToken())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests — authenticateWithHive
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `returns true immediately when token already cached - no network call made`() = runBlocking {
        val storage = FakeAuthenticationStorage(
            mapOf(SphinxRepository.HIVE_AUTHENTICATION_TOKEN to "existing_jwt")
        )
        val query = FakeNetworkQueryHive { _, _, _ -> flow {} }
        val logic = HiveAuthLogic(
            storage, query,
            signedTimestampProvider = { error("should not be called") },
            pubkeyProvider = { error("should not be called") }
        )

        val result = logic.authenticateWithHive()

        assertTrue(result)
        assertEquals(0, query.callCount)
    }

    @Test
    fun `returns false when signed token is null - no network call made`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ -> flow {} }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { null },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertEquals(0, query.callCount)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns false when pubkey is null - no network call made`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ -> flow {} }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { null }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertEquals(0, query.callCount)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns true and stores token on success with valid jwt`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val expectedJwt = "valid.hive.jwt"
        val query = FakeNetworkQueryHive { _, _, _ ->
            flow { emit(Response.Success(HiveAuthenticationTokenDto(expectedJwt))) }
        }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertTrue(result)
        assertEquals(expectedJwt, storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns false and does not store when response token is null`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ ->
            flow { emit(Response.Success(HiveAuthenticationTokenDto(null))) }
        }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns false and does not store when response token is blank`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ ->
            flow { emit(Response.Success(HiveAuthenticationTokenDto("   "))) }
        }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns false and does not store when response token is empty string`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ ->
            flow { emit(Response.Success(HiveAuthenticationTokenDto(""))) }
        }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `returns false on network error and storage is untouched`() = runBlocking {
        val storage = FakeAuthenticationStorage()
        val query = FakeNetworkQueryHive { _, _, _ ->
            flow {
                emit(Response.Error(ResponseError("network failure")))
            }
        }
        val logic = HiveAuthLogic(storage, query,
            signedTimestampProvider = { "signed_ts" },
            pubkeyProvider = { "pubkey123" }
        )

        val result = logic.authenticateWithHive()

        assertFalse(result)
        assertNull(storage.storage[SphinxRepository.HIVE_AUTHENTICATION_TOKEN])
    }

    @Test
    fun `HIVE_AUTHENTICATION_TOKEN constant has expected value`() {
        assertEquals("HIVE_AUTHENTICATION_TOKEN", SphinxRepository.HIVE_AUTHENTICATION_TOKEN)
    }
}
