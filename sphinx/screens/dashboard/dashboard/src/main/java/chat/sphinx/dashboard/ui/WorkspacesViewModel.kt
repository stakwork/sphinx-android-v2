package chat.sphinx.dashboard.ui

import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.kotlin_response.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed class WorkspacesViewState : ViewState<WorkspacesViewState>() {
    object Idle : WorkspacesViewState()
}

@HiltViewModel
internal class WorkspacesViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
) : SideEffectViewModel<
        android.content.Context,
        ChatListSideEffect,
        WorkspacesViewState
        >(dispatchers, WorkspacesViewState.Idle) {

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Distinguishes "fetch failed" from "fetch succeeded with zero workspaces".
    // The RecyclerView otherwise has no way to tell the two apart when workspaces is empty.
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private var fetchJob: Job? = null

    fun loadWorkspaces() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _loading.value = true
            _error.value = false
            try {
                when (val response = repositoryDashboard.fetchWorkspaces()) {
                    is Response.Success -> {
                        _workspaces.value = response.value
                    }
                    is Response.Error -> {
                        _error.value = true
                    }
                }
            } catch (e: Exception) {
                // Guard here so _loading is always reset even on unexpected throws,
                // and surface it as a failure rather than silently doing nothing.
                _error.value = true
            } finally {
                _loading.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = false
    }
}
