package chat.sphinx.wrapper_common.message

@Suppress("NOTHING_TO_INLINE")
inline fun String.toRemoteTimezoneIdentifier(): RemoteTimezoneIdentifier? =
    try {
        RemoteTimezoneIdentifier(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class RemoteTimezoneIdentifier(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "RemoteTimezoneIdentifier cannot be empty"
        }
    }
}