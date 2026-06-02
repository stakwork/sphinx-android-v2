package chat.sphinx.wrapper_common.datasync

import chat.sphinx.wrapper_common.DateTime

data class DataSync(
    val sync_key: DataSyncKey,
    val identifier: DataSyncIdentifier,
    val date: DateTime,
    val sync_value: DataSyncValue
)