package chat.sphinx.chat_common.ui.viewstate.menu

sealed class MoreMenuOptionsViewState {
    object ShareTribeLinkAvailable: MoreMenuOptionsViewState()
    object ShareTribeLinkDisable: MoreMenuOptionsViewState()
}
