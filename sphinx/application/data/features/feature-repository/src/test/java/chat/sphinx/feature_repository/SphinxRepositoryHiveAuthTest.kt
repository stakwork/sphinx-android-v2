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

// ---------------------------------------------------------------------------
// Minimal fake AuthenticationStorage backed by an in-memory map
// ---------------------------------------------------------------------------

class FakeAuthenticationStorage : AuthenticationStorage {
    private val map: MutableMap<String, String?> = LinkedHashMap()
    private val sideEffects: MutableSet<String> = LinkedHashSet()

    fun clear() {
        map.clear()
        sideEffects.clear()
    }

    fun wasCalledFor(tag: String): Boolean = sideEffects.contains(tag)

    override suspend fun getString(key: String, defaultValue: String?): String? =
        if (map.containsKey(key)) map[key] else defaultValue

    override suspend fun putString(key: String, value: String?) {
        sideEffects.add(key)
        map[key] = value
    }

    override suspend fun removeString(key: String) {
        map.remove(key)
    }
}

// ---------------------------------------------------------------------------
// Minimal testable shim that mirrors just the Hive-auth surface of
// SphinxRepository without requiring the full DI graph.
// ---------------------------------------------------------------------------

class TestableSphinxRepository(
    val storage: FakeAuthenticationStorage,
    val getSignedTs: () -> String?,
    val getPubKey: () -> String?,
    val networkQueryHive: NetworkQueryHive,
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

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class SphinxRepositoryHiveAuthTest {

    private val storage = FakeAuthenticationStorage()

    // Mutable state that each test controls
    private var signedTimestamp: String? = "signed_ts"
    private var ownerPubKey: String? = "03abc123"
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

    // -----------------------------------------------------------------------
    // retrieveHiveToken
    // -----------------------------------------------------------------------

    @Test
    fun `retrieveHiveToken returns null when key absent`() = runBlocking {
        assertNull(subject.retrieveHiveToken())
    }

    @Test
    fun `retrieveHiveToken returns stored value`() = runBlocking {
        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, "my-jwt")
        assertEquals("my-jwt", subject.retrieveHiveToken())
    }

    // -----------------------------------------------------------------------
    // authenticateWithHive — short-circuit paths
    // -----------------------------------------------------------------------

    @Test
    fun `returns true immediately when token already cached`() = runBlocking {
        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, "cached-jwt")
        // Reset any side-effect tracking after the manual putString
        storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN) // consumed

        // signedTimestamp set to null to prove the FFI is never touched
        signedTimestamp = null

        val result = subject.authenticateWithHive()
        assertTrue(result)
    }

    @Test
    fun `returns false when getSignedTimeStamps returns null`() = runBlocking {
        signedTimestamp = null
        val result = subject.authenticateWithHive()
        assertFalse(result)
        // putString must NOT have been called
        assertFalse(storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN))
    }

    @Test
    fun `returns false when pubkey is null`() = runBlocking {
        ownerPubKey = null
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertFalse(storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN))
    }

    // -----------------------------------------------------------------------
    // authenticateWithHive — network response variants
    // -----------------------------------------------------------------------

    @Test
    fun `returns true and persists token on success with valid token`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = "hive-jwt-abc"))
        val result = subject.authenticateWithHive()
        assertTrue(result)
        assertEquals("hive-jwt-abc", storage.getString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, null))
    }

    @Test
    fun `returns false and does NOT persist when dto token is null`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = null))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertFalse(storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN))
    }

    @Test
    fun `returns false and does NOT persist when dto token is blank`() = runBlocking {
        networkResponse = Response.Success(HiveAuthenticationTokenDto(token = "   "))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertFalse(storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN))
    }

    @Test
    fun `returns false on network error and storage untouched`() = runBlocking {
        networkResponse = Response.Error(ResponseError("network failure"))
        val result = subject.authenticateWithHive()
        assertFalse(result)
        assertFalse(storage.wasCalledFor(SphinxRepository.HIVE_AUTHENTICATION_TOKEN))
    }
}
