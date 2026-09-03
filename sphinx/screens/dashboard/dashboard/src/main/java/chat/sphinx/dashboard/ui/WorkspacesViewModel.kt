package chat.sphinx.dashboard.ui

import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
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

    private var fetchJob: Job? = null

    fun loadWorkspaces() {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _loading.value = true
            try {
                _workspaces.value = repositoryDashboard.fetchWorkspaces()
            } catch (e: Exception) {
                // fetchWorkspaces() is expected to return emptyList() on failure,
                // but guard here so _loading is always reset even on unexpected throws
            } finally {
                _loading.value = false
            }
        }
    }
}
