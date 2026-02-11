package chat.sphinx.concept_repository_data_sync

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_common.datasync.DataSyncIdentifier
import chat.sphinx.wrapper_common.datasync.DataSyncKey
import chat.sphinx.wrapper_common.datasync.DataSyncValue
import chat.sphinx.wrapper_contact.Contact
import kotlinx.coroutines.flow.Flow


interface DataSyncRepository {

    val getAllDataSync: Flow<List<DataSync>>

    fun getDataSyncByKeyAndIdentifier(
        key: DataSyncKey,
        identifier: DataSyncIdentifier
    ): Flow<DataSync?>

    abstract fun setOwner(owner: Contact?)

    suspend fun upsertDataSync(
        key: DataSyncKey,
        identifier: DataSyncIdentifier,
        date: DateTime,
        value: DataSyncValue
    )

    fun startDataSyncObservation()

    fun syncWithServer()

    suspend fun deleteDataSync(
        key: DataSyncKey,
        identifier: DataSyncIdentifier
    )

    suspend fun deleteAllDataSync()
}