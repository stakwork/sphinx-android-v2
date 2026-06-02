package chat.sphinx.feature_repository.mappers.data_sync

import chat.sphinx.conceptcoredb.DataSyncDbo
import chat.sphinx.feature_repository.mappers.ClassMapper
import chat.sphinx.wrapper_common.datasync.DataSync
import io.matthewnelson.concept_coroutines.CoroutineDispatchers

internal class DataSyncDboPresenterMapper(
    dispatchers: CoroutineDispatchers,
) : ClassMapper<DataSyncDbo, DataSync>(dispatchers) {

    override suspend fun mapFrom(value: DataSyncDbo): DataSync {
        return DataSync(
            sync_key = value.sync_key,
            identifier = value.identifier,
            date = value.date,
            sync_value = value.sync_value
        )
    }

    override suspend fun mapTo(value: DataSync): DataSyncDbo {
        return DataSyncDbo(
            sync_key = value.sync_key,
            identifier = value.identifier,
            date = value.date,
            sync_value = value.sync_value
        )
    }
}