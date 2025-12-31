package chat.sphinx.feature_coredb.adapters.data_sync

import com.squareup.sqldelight.ColumnAdapter
import chat.sphinx.wrapper_common.datasync.DataSyncIdentifier
import chat.sphinx.wrapper_common.datasync.DataSyncKey
import chat.sphinx.wrapper_common.datasync.DataSyncValue

internal class DataSyncKeyAdapter : ColumnAdapter<DataSyncKey, String> {
    override fun decode(databaseValue: String): DataSyncKey {
        return DataSyncKey(value = databaseValue)
    }

    override fun encode(value: DataSyncKey): String {
        return value.value
    }
}

internal class DataSyncIdentifierAdapter : ColumnAdapter<DataSyncIdentifier, String> {
    override fun decode(databaseValue: String): DataSyncIdentifier {
        return DataSyncIdentifier(value = databaseValue)
    }

    override fun encode(value: DataSyncIdentifier): String {
        return value.value
    }
}

internal class DataSyncValueAdapter : ColumnAdapter<DataSyncValue, String> {
    override fun decode(databaseValue: String): DataSyncValue {
        return DataSyncValue(value = databaseValue)
    }

    override fun encode(value: DataSyncValue): String {
        return value.value
    }
}
