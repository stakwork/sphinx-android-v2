package chat.sphinx.concept_repository_data_sync

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_common.datasync.DataSyncIdentifier
import chat.sphinx.wrapper_common.datasync.DataSyncKey
import chat.sphinx.wrapper_common.datasync.DataSyncValue
import kotlinx.coroutines.flow.Flow


interface DataSyncRepository {

    val getAllDataSync: Flow<List<DataSync>>

    fun getDataSyncByKeyAndIdentifier(
        key: DataSyncKey,
        identifier: DataSyncIdentifier
    ): Flow<DataSync?>

    suspend fun upsertDataSync(
        key: DataSyncKey,
        identifier: DataSyncIdentifier,
        date: DateTime,
        value: DataSyncValue
    )

    suspend fun deleteDataSync(
        key: DataSyncKey,
        identifier: DataSyncIdentifier
    )

    suspend fun deleteAllDataSync()
}