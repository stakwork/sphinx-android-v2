package chat.sphinx.dashboard.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import chat.sphinx.dashboard.navigation.DashboardNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewState
import javax.inject.Inject

internal sealed class WorkspaceDetailViewState : ViewState<WorkspaceDetailViewState>() {
    object Idle : WorkspaceDetailViewState()
}

@HiltViewModel
internal class WorkspaceDetailViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    handler: SavedStateHandle,
    private val dashboardNavigator: DashboardNavigator,
) : SideEffectViewModel<
        Context,
        ChatListSideEffect,
        WorkspaceDetailViewState
        >(dispatchers, WorkspaceDetailViewState.Idle) {

    private val args: WorkspaceDetailFragmentArgs =
        WorkspaceDetailFragmentArgs.fromSavedStateHandle(handler)

    val workspaceId: String
        get() = args.argWorkspaceId

    val workspaceName: String
        get() = args.argWorkspaceName

    val workspaceSlug: String?
        get() = args.argWorkspaceSlug

    suspend fun popBackStack() {
        dashboardNavigator.popBackStack()
    }
}
