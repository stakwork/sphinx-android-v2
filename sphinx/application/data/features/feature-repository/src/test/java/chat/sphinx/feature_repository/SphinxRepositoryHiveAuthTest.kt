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
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SphinxRepository.authenticateWithHive] and [SphinxRepository.retrieveHiveToken].
 *
 * Uses simple in-memory fakes — no mocking framework needed.
 */
class SphinxRepositoryHiveAuthTest {

    // ── In-memory fakes ──────────────────────────────────────────────────────

    private val storage = FakeAuthenticationStorage()

    private var signedTimestamp: String? = null
    private var ownerPubKey: String? = null

    private var networkResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        Response.Error(ResponseError("default"))

    private val fakeNetworkQueryHive = object : NetworkQueryHive() {
        override fun authenticateWithHive(
            token: String,
            pubkey: String,
            timestamp: String,
        ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> = flow {
            emit(networkResponse)
        }
    }

    private lateinit var subject: TestableSphinxRepository

    @Before
    fun setUp() {
        signedTimestamp = "signed_ts"
        ownerPubKey = "03abc123"
        networkResponse = Response.Error(ResponseError("default"))
        storage.clear()
        subject = TestableSphinxRepository(
            storage = storage,
            getSignedTs = { signedTimestamp },
            getPubKey = { ownerPubKey },
            networkQueryHive = fakeNetworkQueryHive,
        )
    }

    // ── retrieveHiveToken ────────────────────────────────────────────────────

    @Test
    fun `retrieveHiveToken returns null when key absent`() = runBlocking {
        assertNull(subject.retrieveHiveToken())
    }

    @Test
    fun `retrieveHiveToken returns stored value`() = runBlocking {
        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, "myJwt")
        assertEquals("myJwt", subject.retrieveHiveToken())
    }

    // ── authenticateWithHive ─────────────────────────────────────────────────

    @Test
    fun `returns true immediately when token already cached`() = runBlocking {
        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, "cached")
        // If this were to call getSignedTimeStamps it would fail — set to null to catch that
        signedTimestamp = null
        assertTrue(subject.authenticateWithHive())
        // Network was never consulted
        assertFalse(storage.wasCalledFor("putString_after_auth"))
    }

    @Test
    fun `returns false when getSignedTimeStamps returns null`() = runBlocking {
        signedTimestamp = null
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertNull(storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns false when pubkey is null`() = runBlocking {
        ownerPubKey = null
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertNull(storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns true and persists token on success with valid token`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = "real_jwt"))
        val result = subject.authenticateWithHive()
        assertTrue(result)
        assertEquals("real_jwt", storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns false and does NOT persist when dto token is null`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = null))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertNull(storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns false and does NOT persist when dto token is blank`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = "  "))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertNull(storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns false on network error and storage untouched`() = runBlocking {
        networkResponse = Response.Error(ResponseError("network failure"))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertNull(storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }
}

// ── Test doubles ─────────────────────────────────────────────────────────────

class FakeAuthenticationStorage : AuthenticationStorage {
    private val map = mutableMapOf<String, String>()
    private val sideEffects = mutableSetOf<String>()

    fun clear() { map.clear(); sideEffects.clear() }
    fun wasCalledFor(tag: String) = sideEffects.contains(tag)

    override suspend fun getString(key: String, defaultValue: String?): String? = map[key] ?: defaultValue

    override suspend fun putString(key: String, value: String?) {
        if (value != null) map[key] = value else map.remove(key)
    }

    override suspend fun removeString(key: String) { map.remove(key) }
}

/**
 * Minimal testable stub — only the fields needed by [authenticateWithHive] / [retrieveHiveToken].
 */
class TestableSphinxRepository(
    private val storage: FakeAuthenticationStorage,
    private val getSignedTs: () -> String?,
    private val getPubKey: () -> String?,
    private val networkQueryHive: NetworkQueryHive,
) {
    suspend fun retrieveHiveToken(): String? =
        storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null)

    suspend fun authenticateWithHive(): Boolean {
        retrieveHiveToken()?.let { return true }

        val signedToken = getSignedTs() ?: return false
        val pubkey = getPubKey() ?: return false
        val timestamp = System.currentTimeMillis().toString()

        var success = false
        networkQueryHive.authenticateWithHive(signedToken, pubkey, timestamp)
            .collect { response ->
                when (response) {
                    is Response.Success -> {
                        val jwt = response.value.token
                            ?.takeIf { it.isNotBlank() }
                            ?: return@collect
                        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, jwt)
                        success = true
                    }
                    else -> { /* success remains false */ }
                }
            }
        return success
    }
}
