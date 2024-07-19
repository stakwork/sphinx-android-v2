package chat.sphinx.example.concept_connect_manager.model

data class RestoreProgress(
    var progressPercentage: Int = 0,
    var contactsRestoredAmount: Int = 0,
    var totalContactsKey: Int = 0,
    var totalMessages: Int = 0,
    var restoredMessagesAmount: Int = 0,
    val fixedContactPercentage: Int = 10,
    val fixedMessagesPercentage: Int = 90
)