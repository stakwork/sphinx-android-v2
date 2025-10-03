package chat.sphinx.chat_common.ui.viewstate.messageholder

import chat.sphinx.chat_common.ui.viewstate.search.MessagesSearchViewState
import io.matthewnelson.concept_views.viewstate.ViewState

sealed class MessagesLoadingViewState: ViewState<MessagesLoadingViewState>() {
    object Idle : MessagesLoadingViewState()
    object Loading : MessagesLoadingViewState()
}
