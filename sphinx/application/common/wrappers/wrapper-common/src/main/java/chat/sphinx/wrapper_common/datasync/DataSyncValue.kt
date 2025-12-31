package chat.sphinx.wrapper_common.datasync


@JvmInline
value class DataSyncValue(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "DataSyncValue cannot be empty"
        }
    }
}