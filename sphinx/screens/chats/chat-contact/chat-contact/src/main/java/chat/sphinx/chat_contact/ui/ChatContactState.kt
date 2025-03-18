package chat.sphinx.chat_contact.ui

import chat.sphinx.chat_contact.ui.ChatContactState.ChatContactData
import chat.sphinx.wrapper_chat.Chat
import io.matthewnelson.concept_views.viewstate.ViewState

sealed class ChatContactState : ViewState<ChatContactState>() {
    object Idle : ChatContactState()

    data class ChatContactData(
        val chat: Chat?
    ) : ChatContactState()
}

fun Chat.toChatContactData(): ChatContactData = ChatContactData(this)
