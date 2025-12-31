package chat.sphinx.wrapper_common.datasync

@JvmInline
value class DataSyncKey(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "DataSyncKey cannot be empty"
        }
    }
}