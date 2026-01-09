package chat.sphinx.concept_data_sync.model

sealed class DataSyncState {
    object Idle : DataSyncState()
    object Syncing : DataSyncState()
    data class Synced(val timestamp: Long) : DataSyncState()
    data class Error(val error: DataSyncError) : DataSyncState()
}
sealed class DataSyncError {
    object NetworkError : DataSyncError()
    object EncryptionError : DataSyncError()
    object ParseError : DataSyncError()
    data class UnknownError(val message: String) : DataSyncError()
}