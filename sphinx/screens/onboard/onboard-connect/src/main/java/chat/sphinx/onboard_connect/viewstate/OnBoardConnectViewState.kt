package chat.sphinx.onboard_connect.viewstate

import io.matthewnelson.concept_views.viewstate.ViewState

internal sealed class OnBoardConnectViewState: ViewState<OnBoardConnectViewState>() {
    object Idle: OnBoardConnectViewState()
    data class NewUser(val code: String?) : OnBoardConnectViewState()
    object ExistingUser: OnBoardConnectViewState()
}