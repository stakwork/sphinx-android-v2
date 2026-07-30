package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.feature_repository.mappers.hive.toDomain
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fake storage reused from SphinxRepositoryHiveAuthTest (same package).
// We define a standalone version here to keep tests self-contained.
// ---------------------------------------------------------------------------

private class FakeStorage2 : io.matthewnelson.concept_authentication.data.AuthenticationStorage {
    val map: MutableMap<String, String?> = LinkedHashMap()
    var removeCallCount = 0

    fun clear() { map.clear(); removeCallCount = 0 }

    override suspend fun getString(key: String, defaultValue: String?) =
        if (map.containsKey(key)) map[key] else defaultValue

    override suspend fun putString(key: String, value: String?) { map[key] = value }

    override suspend fun removeString(key: String) {
        removeCallCount++
        map.remove(key)
    }
}

// ---------------------------------------------------------------------------
// Testable shim – exposes the fetchWorkspaces() surface without full DI.
// ---------------------------------------------------------------------------

private class TestableRepo(
    val storage: FakeStorage2,
    val getSignedTs: () -> String? = { "signed_ts" },
    val getPubKey: () -> String? = { "03abc" },
    val hive: NetworkQueryHive,
) {
    val HIVE_AUTHENTICATION_TOKEN = SphinxRepository.HIVE_AUTHENTICATION_TOKEN
    val HIVE_TOKEN_DELIMITER = SphinxRepository.HIVE_TOKEN_DELIMITER
    val HIVE_TOKEN_TTL_MS = SphinxRepository.HIVE_TOKEN_TTL_MS
    val HIVE_TOKEN_EXPIRY_MARGIN_MS = SphinxRepository.HIVE_TOKEN_EXPIRY_MARGIN_MS

    suspend fun retrieveHiveToken(): String? {
        val raw = storage.getString(HIVE_AUTHENTICATION_TOKEN, null) ?: return null
        val parts = raw.split(HIVE_TOKEN_DELIMITER)
        if (parts.size < 2) return raw
        val jwt = parts[0]
        val expiresAt = parts[1].toLongOrNull() ?: return jwt
        val now = System.currentTimeMillis()
        return if (now >= expiresAt - HIVE_TOKEN_EXPIRY_MARGIN_MS) null else jwt
    }

    suspend fun clearHiveToken() { storage.removeString(HIVE_AUTHENTICATION_TOKEN) }

    suspend fun authenticateWithHive(): Boolean {
        retrieveHiveToken()?.let { return true }
        val signedToken = getSignedTs() ?: return false
        val pubkey = getPubKey() ?: return false
        val timestamp = System.currentTimeMillis().toString()
        var success = false
        hive.authenticateWithHive(signedToken, pubkey, timestamp).collect { response ->
            when (response) {
                is Response.Success -> {
                    val jwt = response.value.token?.takeIf { it.isNotBlank() } ?: return@collect
                    val expiresAt = System.currentTimeMillis() + HIVE_TOKEN_TTL_MS
                    storage.putString(HIVE_AUTHENTICATION_TOKEN, "$jwt$HIVE_TOKEN_DELIMITER$expiresAt")
                    success = true
                }
                else -> {}
            }
        }
        return success
    }

    private val hiveAuthMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun fetchWorkspaces(): List<Workspace> {
        return try {
            val token = retrieveHiveToken()
            if (token != null) {
                var result: List<Workspace>? = null
                var failed = false
                hive.getWorkspaces(token).collect { response ->
                    when (response) {
                        is Response.Success -> result = response.value.workspaces.map { it.toDomain() }
                        is Response.Error -> failed = true
                        else -> {}
                    }
                }
                if (!failed && result != null) return result!!
                hiveAuthMutex.withLock {
                    clearHiveToken()
                    authenticateWithHive()
                }
                val newToken = retrieveHiveToken() ?: return emptyList()
                var retryResult: List<Workspace> = emptyList()
                hive.getWorkspaces(newToken).collect { retryResponse ->
                    if (retryResponse is Response.Success) {
                        retryResult = retryResponse.value.workspaces.map { it.toDomain() }
                    }
                }
                retryResult
            } else {
                hiveAuthMutex.withLock { authenticateWithHive() }
                val newToken = retrieveHiveToken() ?: return emptyList()
                var result: List<Workspace> = emptyList()
                hive.getWorkspaces(newToken).collect { response ->
                    if (response is Response.Success) {
                        result = response.value.workspaces.map { it.toDomain() }
                    }
                }
                result
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ---------------------------------------------------------------------------
// Helper builders
// ---------------------------------------------------------------------------

private fun makeWorkspaceDto(id: String = "ws-1", name: String = "My Workspace") =
    WorkspaceDto(id = id, name = name, userRole = "admin", memberCount = 5, logoUrl = null)

private fun successWorkspaces(vararg dtos: WorkspaceDto): LoadResponse<WorkspacesListDto, ResponseError> =
    Response.Success(WorkspacesListDto(workspaces = dtos.toList()))

private fun errorResponse(): LoadResponse<WorkspacesListDto, ResponseError> =
    Response.Error(ResponseError("network error"))

private fun authSuccess(token: String): LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
    Response.Success(HiveAuthenticationTokenDto(token = token))

private fun authError(): LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
    Response.Error(ResponseError("auth error"))

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class SphinxRepositoryFetchWorkspacesTest {

    private val storage = FakeStorage2()

    // Mutable state each test controls
    private var authResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        authSuccess("new-jwt")
    private var workspacesResponses: MutableList<LoadResponse<WorkspacesListDto, ResponseError>> =
        mutableListOf()
    private var workspacesCallCount = 0
    private var authCallCount = 0

    private val fakeHive = object : NetworkQueryHive() {
        override fun authenticateWithHive(
            token: String,
            pubkey: String,
            timestamp: String,
        ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> = flow {
            authCallCount++
            emit(authResponse)
        }

        override fun getWorkspaces(
            authToken: String,
        ): Flow<LoadResponse<WorkspacesListDto, ResponseError>> = flow {
            val response = if (workspacesCallCount < workspacesResponses.size) {
                workspacesResponses[workspacesCallCount]
            } else {
                workspacesResponses.lastOrNull() ?: errorResponse()
            }
            workspacesCallCount++
            emit(response)
        }
    }

    @Before
    fun setUp() {
        storage.clear()
        authResponse = authSuccess("new-jwt")
        workspacesResponses = mutableListOf()
        workspacesCallCount = 0
        authCallCount = 0
    }

    private fun makeRepo() = TestableRepo(storage = storage, hive = fakeHive)

    // Helper: store a valid (non-expired) token
    private suspend fun storeValidToken(repo: TestableRepo, jwt: String = "valid-jwt") {
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes from now
        storage.putString(
            SphinxRepository.HIVE_AUTHENTICATION_TOKEN,
            "$jwt${SphinxRepository.HIVE_TOKEN_DELIMITER}$expiresAt"
        )
    }

    // Helper: store an expired token
    private suspend fun storeExpiredToken(repo: TestableRepo, jwt: String = "expired-jwt") {
        val expiresAt = System.currentTimeMillis() - 1000L // already expired
        storage.putString(
            SphinxRepository.HIVE_AUTHENTICATION_TOKEN,
            "$jwt${SphinxRepository.HIVE_TOKEN_DELIMITER}$expiresAt"
        )
    }

    // -----------------------------------------------------------------------
    // Test 1: Cached valid token — getWorkspaces called directly; auth never invoked
    // -----------------------------------------------------------------------

    @Test
    fun `cached valid token uses it directly without re-authenticating`() = runBlocking {
        val repo = makeRepo()
        storeValidToken(repo, "cached-jwt")
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto()))

        val result = repo.fetchWorkspaces()

        assertEquals(1, workspacesCallCount)
        assertEquals(0, authCallCount)
        assertEquals(1, result.size)
        assertEquals("ws-1", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 2: No stored token — authenticates first, then fetches
    // -----------------------------------------------------------------------

    @Test
    fun `no stored token triggers authenticateWithHive then getWorkspaces`() = runBlocking {
        val repo = makeRepo()
        // No token stored
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto("ws-2", "Workspace 2")))

        val result = repo.fetchWorkspaces()

        assertEquals(1, authCallCount)
        assertEquals(1, workspacesCallCount)
        assertEquals(1, result.size)
        assertEquals("ws-2", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 3: Expired token — retrieveHiveToken() returns null → re-auth path
    // -----------------------------------------------------------------------

    @Test
    fun `expired token triggers proactive re-auth without waiting for server 401`() = runBlocking {
        val repo = makeRepo()
        storeExpiredToken(repo, "expired-jwt")
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto("ws-3")))

        val result = repo.fetchWorkspaces()

        // retrieveHiveToken() returns null for expired token → enters no-token branch
        assertEquals(1, authCallCount)
        assertEquals(1, workspacesCallCount)
        assertEquals("ws-3", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 4: Network error → clearHiveToken + re-auth + single retry
    // -----------------------------------------------------------------------

    @Test
    fun `network error triggers clearHiveToken then single retry with new token`() = runBlocking {
        val repo = makeRepo()
        storeValidToken(repo, "stale-jwt")
        // First call fails, retry succeeds
        workspacesResponses.add(errorResponse())
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto("ws-retry")))

        val result = repo.fetchWorkspaces()

        // getWorkspaces called exactly twice
        assertEquals(2, workspacesCallCount)
        // clearHiveToken called once during retry
        assertEquals(1, storage.removeCallCount)
        // authenticateWithHive called once during retry
        assertEquals(1, authCallCount)
        assertEquals("ws-retry", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 5: Re-auth failure on retry path → returns emptyList()
    // -----------------------------------------------------------------------

    @Test
    fun `re-auth failure after network error returns emptyList`() = runBlocking {
        val repo = makeRepo()
        storeValidToken(repo, "stale-jwt")
        authResponse = authError()
        workspacesResponses.add(errorResponse())

        val result = repo.fetchWorkspaces()

        assertTrue(result.isEmpty())
        assertEquals(1, storage.removeCallCount) // clearHiveToken was called
        assertEquals(1, authCallCount)
        // getWorkspaces only called once (retry skipped because newToken is null)
        assertEquals(1, workspacesCallCount)
    }

    // -----------------------------------------------------------------------
    // Test 6a: WorkspaceDto deserialization — all 12 fields
    // -----------------------------------------------------------------------

    @Test
    fun `WorkspaceDto with all fields maps to domain correctly`() {
        val dto = WorkspaceDto(
            id = "abc",
            name = "Full Workspace",
            slug = "full-workspace",
            description = "A full workspace",
            userRole = "owner",
            memberCount = 42,
            ownerId = "owner-123",
            logoUrl = "https://example.com/logo.png",
            logoKey = "logo-key",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-06-01T00:00:00Z",
            lastAccessedAt = "2024-07-01T00:00:00Z",
        )
        val domain = dto.toDomain()
        assertEquals("abc", domain.id)
        assertEquals("Full Workspace", domain.name)
        assertEquals("owner", domain.userRole)
        assertEquals(42, domain.memberCount)
        assertEquals("https://example.com/logo.png", domain.logoUrl)
    }

    // -----------------------------------------------------------------------
    // Test 6b: WorkspaceDto deserialization — only id + name (all optionals null-safe)
    // -----------------------------------------------------------------------

    @Test
    fun `WorkspaceDto with only id and name maps to domain with null optionals`() {
        val dto = WorkspaceDto(id = "min-id", name = "Minimal")
        val domain = dto.toDomain()
        assertEquals("min-id", domain.id)
        assertEquals("Minimal", domain.name)
        assertEquals(null, domain.logoUrl)
        assertEquals(null, domain.userRole)
        assertEquals(0, domain.memberCount) // default
    }

    // -----------------------------------------------------------------------
    // Test 7: Token freshness — within 60-second margin → treated as null (expired)
    // -----------------------------------------------------------------------

    @Test
    fun `token within expiry margin is treated as expired and triggers re-auth`() = runBlocking {
        val repo = makeRepo()
        // Store a token that expires in 30 seconds (within the 60s margin)
        val expiresAt = System.currentTimeMillis() + 30_000L
        storage.putString(
            SphinxRepository.HIVE_AUTHENTICATION_TOKEN,
            "margin-jwt${SphinxRepository.HIVE_TOKEN_DELIMITER}$expiresAt"
        )
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto("ws-fresh")))

        val result = repo.fetchWorkspaces()

        // Token was treated as expired → re-auth triggered
        assertEquals(1, authCallCount)
        assertEquals("ws-fresh", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 8: retrieveHiveToken — legacy token (no delimiter) returned as-is
    // -----------------------------------------------------------------------

    @Test
    fun `legacy token without delimiter is returned as-is`() = runBlocking {
        val repo = makeRepo()
        // Store a raw token with no expiry delimiter (backward-compat)
        storage.putString(SphinxRepository.HIVE_AUTHENTICATION_TOKEN, "legacy-jwt")
        workspacesResponses.add(successWorkspaces(makeWorkspaceDto("ws-legacy")))

        val result = repo.fetchWorkspaces()

        // Should use the token directly without re-authing
        assertEquals(0, authCallCount)
        assertEquals("ws-legacy", result[0].id)
    }

    // -----------------------------------------------------------------------
    // Test 9: Valid list of multiple workspaces mapped correctly
    // -----------------------------------------------------------------------

    @Test
    fun `multiple workspaces are all mapped to domain`() = runBlocking {
        val repo = makeRepo()
        storeValidToken(repo)
        workspacesResponses.add(
            successWorkspaces(
                makeWorkspaceDto("ws-a", "Alpha"),
                makeWorkspaceDto("ws-b", "Beta"),
                makeWorkspaceDto("ws-c", "Gamma"),
            )
        )

        val result = repo.fetchWorkspaces()

        assertEquals(3, result.size)
        assertEquals("ws-a", result[0].id)
        assertEquals("ws-b", result[1].id)
        assertEquals("ws-c", result[2].id)
    }
}
