package chat.sphinx.contact.ui

import chat.sphinx.wrapper_chat.Chat
import io.matthewnelson.concept_views.viewstate.ViewState

sealed class ContactViewState: ViewState<ContactViewState>() {
    object Idle: ContactViewState()
    object Saving: ContactViewState()
    object Saved: ContactViewState()
    object Error: ContactViewState()

    data class ShareTimezone(
        val chat: Chat
    ): ContactViewState()
}
