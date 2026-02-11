package chat.sphinx.wrapper_common.datasync

@JvmInline
value class DataSyncIdentifier(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "DataSyncIdentifier cannot be empty"
        }
    }
}
