package chat.sphinx.chat_common.ui.viewstate.search

import chat.sphinx.chat_common.ui.viewstate.messageholder.MessageHolderViewState
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.PodcastClip
import io.matthewnelson.concept_views.viewstate.ViewState

sealed class MessagesSearchViewState: ViewState<MessagesSearchViewState>() {

    object Idle: MessagesSearchViewState()

    object Clear: MessagesSearchViewState()

    object Cancel: MessagesSearchViewState()

    class Searching(
        val loading: Boolean,
        val term: String?,
        val clearButtonVisible: Boolean,
        val results: List<Pair<Long, String>>,
        val index: Int,
        val navigatingForward: Boolean
    ): MessagesSearchViewState()
}