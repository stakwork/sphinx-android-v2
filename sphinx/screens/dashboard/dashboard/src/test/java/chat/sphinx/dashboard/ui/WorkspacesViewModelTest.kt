package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Minimal testable shim mirroring the fetch/error-state logic of
// WorkspacesViewModel, without requiring the full Android/Hilt
// SideEffectViewModel graph.
// ---------------------------------------------------------------------------

private class TestableWorkspacesViewModel(
    private val fetchWorkspaces: suspend () -> Response<List<Workspace>, ResponseError>,
    private val fetchWorkspaceImageUrl: suspend (String) -> Response<String, ResponseError> =
        { Response.Error(ResponseError("not stubbed")) },
) {
    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Distinguishes "fetch failed" from "fetch succeeded with zero workspaces".
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    suspend fun loadWorkspaces() {
        _loading.value = true
        _error.value = false
        try {
            when (val response = fetchWorkspaces()) {
                is Response.Success -> {
                    val list = response.value
                    // Emit immediately so names/role/members show while logos resolve.
                    _workspaces.value = list
                    _loading.value = false
                    resolveWorkspaceLogos(list)
                }
                is Response.Error -> {
                    _error.value = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _error.value = true
        } finally {
            _loading.value = false
        }
    }

    private suspend fun resolveWorkspaceLogos(workspaces: List<Workspace>) {
        if (workspaces.none { !it.slug.isNullOrBlank() }) {
            return
        }

        val resolved = coroutineScope {
            workspaces.map { workspace ->
                async {
                    val slug = workspace.slug
                    if (slug.isNullOrBlank()) {
                        workspace
                    } else {
                        when (val result = fetchWorkspaceImageUrl(slug)) {
                            is Response.Success -> workspace.copy(logoUrl = result.value)
                            is Response.Error -> workspace
                        }
                    }
                }
            }.awaitAll()
        }

        _workspaces.value = resolved
    }

    fun dismissError() {
        _error.value = false
    }
}

class WorkspacesViewModelTest {

    private fun makeWorkspace(id: String = "ws-1", slug: String? = null) = Workspace(
        id = id,
        name = "Workspace $id",
        logoUrl = null,
        userRole = "admin",
        memberCount = 3,
        slug = slug,
    )

    @Before
    fun setUp() {
        // no shared mutable state between tests beyond what's created per-test
    }

    @Test
    fun `loadWorkspaces populates workspaces and clears error on success`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = { Response.Success(listOf(makeWorkspace("ws-1"))) }
        )

        subject.loadWorkspaces()

        assertFalse(subject.error.value)
        assertFalse(subject.loading.value)
        assertEquals(1, subject.workspaces.value.size)
        assertEquals("ws-1", subject.workspaces.value[0].id)
    }

    @Test
    fun `loadWorkspaces with a real empty success list does NOT set error`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = { Response.Success(emptyList()) }
        )

        subject.loadWorkspaces()

        // Zero workspaces via a genuine success must be distinguishable from a failure —
        // error must remain false so the UI doesn't show a spurious retry alert.
        assertFalse(subject.error.value)
        assertTrue(subject.workspaces.value.isEmpty())
    }

    @Test
    fun `loadWorkspaces sets error true on auth or network failure and leaves workspaces untouched`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = { Response.Error(ResponseError("network error")) }
        )

        subject.loadWorkspaces()

        assertTrue(subject.error.value)
        assertFalse(subject.loading.value)
        assertTrue(subject.workspaces.value.isEmpty())
    }

    @Test
    fun `loadWorkspaces sets error true when fetchWorkspaces throws unexpectedly`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = { throw RuntimeException("boom") }
        )

        subject.loadWorkspaces()

        assertTrue(subject.error.value)
        assertFalse(subject.loading.value)
    }

    @Test
    fun `dismissError resets the error flag without touching workspaces`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = { Response.Error(ResponseError("network error")) }
        )

        subject.loadWorkspaces()
        assertTrue(subject.error.value)

        subject.dismissError()
        assertFalse(subject.error.value)
    }

    @Test
    fun `retrying after failure via loadWorkspaces again can recover into success`() = runBlocking {
        var attempt = 0
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = {
                attempt++
                if (attempt == 1) {
                    Response.Error(ResponseError("network error"))
                } else {
                    Response.Success(listOf(makeWorkspace("ws-recovered")))
                }
            }
        )

        subject.loadWorkspaces()
        assertTrue(subject.error.value)

        // Simulates the user tapping "Retry" in the alert dialog
        subject.loadWorkspaces()
        assertFalse(subject.error.value)
        assertEquals("ws-recovered", subject.workspaces.value[0].id)
    }

    @Test
    fun `successful per-slug logo resolution populates logoUrl`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = {
                Response.Success(
                    listOf(
                        makeWorkspace("ws-1", slug = "acme"),
                        makeWorkspace("ws-2", slug = "beta"),
                    )
                )
            },
            fetchWorkspaceImageUrl = { slug -> Response.Success("https://presigned/$slug") },
        )

        subject.loadWorkspaces()

        assertFalse(subject.error.value)
        assertEquals(2, subject.workspaces.value.size)
        assertEquals("https://presigned/acme", subject.workspaces.value[0].logoUrl)
        assertEquals("https://presigned/beta", subject.workspaces.value[1].logoUrl)
        assertEquals("Workspace ws-1", subject.workspaces.value[0].name)
        assertEquals("admin", subject.workspaces.value[0].userRole)
        assertEquals(3, subject.workspaces.value[0].memberCount)
        assertEquals("Workspace ws-2", subject.workspaces.value[1].name)
        assertEquals("admin", subject.workspaces.value[1].userRole)
        assertEquals(3, subject.workspaces.value[1].memberCount)
    }

    @Test
    fun `logo fetch error leaves that logoUrl null while others still resolve`() = runBlocking {
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = {
                Response.Success(
                    listOf(
                        makeWorkspace("ws-1", slug = "acme"),
                        makeWorkspace("ws-2", slug = "no-logo"),
                    )
                )
            },
            fetchWorkspaceImageUrl = { slug ->
                if (slug == "no-logo") {
                    Response.Error(ResponseError("404"))
                } else {
                    Response.Success("https://presigned/$slug")
                }
            },
        )

        subject.loadWorkspaces()

        assertFalse(subject.error.value)
        assertEquals("https://presigned/acme", subject.workspaces.value[0].logoUrl)
        assertNull(subject.workspaces.value[1].logoUrl)
        assertEquals("Workspace ws-2", subject.workspaces.value[1].name)
        assertEquals("admin", subject.workspaces.value[1].userRole)
        assertEquals(3, subject.workspaces.value[1].memberCount)
    }

    @Test
    fun `null or blank slug never triggers fetchWorkspaceImageUrl and keeps logoUrl null`() = runBlocking {
        val fetchedSlugs = mutableListOf<String>()
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = {
                Response.Success(
                    listOf(
                        makeWorkspace("ws-1", slug = null),
                        makeWorkspace("ws-2", slug = "  "),
                        makeWorkspace("ws-3", slug = "acme"),
                    )
                )
            },
            fetchWorkspaceImageUrl = { slug ->
                fetchedSlugs.add(slug)
                Response.Success("https://presigned/$slug")
            },
        )

        subject.loadWorkspaces()

        assertEquals(listOf("acme"), fetchedSlugs)
        assertNull(subject.workspaces.value[0].logoUrl)
        assertNull(subject.workspaces.value[1].logoUrl)
        assertEquals("https://presigned/acme", subject.workspaces.value[2].logoUrl)
        assertEquals("Workspace ws-1", subject.workspaces.value[0].name)
        assertEquals("admin", subject.workspaces.value[0].userRole)
        assertEquals(3, subject.workspaces.value[0].memberCount)
    }

    @Test
    fun `workspace list is emitted before logo resolution completes`() = runBlocking {
        val logosStarted = CompletableDeferred<Unit>()
        val allowLogos = CompletableDeferred<Unit>()
        val subject = TestableWorkspacesViewModel(
            fetchWorkspaces = {
                Response.Success(listOf(makeWorkspace("ws-1", slug = "acme")))
            },
            fetchWorkspaceImageUrl = { slug ->
                logosStarted.complete(Unit)
                allowLogos.await()
                Response.Success("https://presigned/$slug")
            },
        )

        val job = launch { subject.loadWorkspaces() }

        logosStarted.await()
        assertEquals(1, subject.workspaces.value.size)
        assertEquals("ws-1", subject.workspaces.value[0].id)
        assertEquals("Workspace ws-1", subject.workspaces.value[0].name)
        assertEquals("admin", subject.workspaces.value[0].userRole)
        assertEquals(3, subject.workspaces.value[0].memberCount)
        assertNull(subject.workspaces.value[0].logoUrl)
        assertFalse(subject.loading.value)
        assertFalse(subject.error.value)

        allowLogos.complete(Unit)
        job.join()

        assertEquals("https://presigned/acme", subject.workspaces.value[0].logoUrl)
        assertFalse(subject.loading.value)
    }
}
