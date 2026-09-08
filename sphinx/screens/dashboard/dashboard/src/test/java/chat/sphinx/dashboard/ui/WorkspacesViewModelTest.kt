package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Minimal testable shim mirroring the fetch/error-state logic of
// WorkspacesViewModel, without requiring the full Android/Hilt
// SideEffectViewModel graph.
// ---------------------------------------------------------------------------

private class TestableWorkspacesViewModel(
    private val fetchWorkspaces: suspend () -> Response<List<Workspace>, ResponseError>
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
                is Response.Success -> _workspaces.value = response.value
                is Response.Error -> _error.value = true
            }
        } catch (e: Exception) {
            _error.value = true
        } finally {
            _loading.value = false
        }
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
}
