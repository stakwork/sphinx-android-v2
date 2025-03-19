package chat.sphinx.chat_common.ui.viewstate.header

import chat.sphinx.wrapper_common.message.RemoteTimezoneIdentifier
import io.matthewnelson.concept_views.viewstate.ViewState

sealed class ChatHeaderViewState: ViewState<ChatHeaderViewState>() {

    object Idle: ChatHeaderViewState()

    data class Initialized(
        val chatHeaderName: String,
        val showLock: Boolean,
        val isMuted: Boolean,
        val isChatAvailable: Boolean
    ): ChatHeaderViewState()
}
