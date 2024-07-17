package chat.sphinx.example.concept_connect_manager.model

sealed class RestoreState {
    object RestoringContacts : RestoreState()
    data class RestoringMessages(val lastIndexFetch: Long, val totalIndex: Long) : RestoreState()
    object RestoreFinished : RestoreState()
}