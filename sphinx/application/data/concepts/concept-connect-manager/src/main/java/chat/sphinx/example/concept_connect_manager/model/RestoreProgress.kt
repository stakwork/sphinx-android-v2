package chat.sphinx.example.concept_connect_manager.model

data class RestoreProgress(
    var progressPercentage: Int = 0,

    var contactsRestoredAmount: Int = 0,
    var totalContactsKey: Int = 0,

    var messagesHighestIndex: Int = 0,
    var currentChatRestoreIndex: Int = 0,
    var totalChatsToRestore: Int = 0,
    var chatPublicKeys: List<String> = emptyList(),

    val fixedContactPercentage: Int = 20,
    val fixedMessagesPercentage: Int = 80
)