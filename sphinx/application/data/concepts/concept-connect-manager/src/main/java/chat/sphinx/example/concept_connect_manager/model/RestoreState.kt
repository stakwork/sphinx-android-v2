package chat.sphinx.example.concept_connect_manager.model

sealed class RestoreState {
    object RestoringContacts : RestoreState()

    object RestoringMessages : RestoreState()

    object RestoreFinished : RestoreState()

    data class FetchingMessagesPerContact(val publicKey: String) : RestoreState()
}