package chat.sphinx.wrapper_chat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toTimezoneIdentifier(): TimezoneIdentifier? =
    try {
        TimezoneIdentifier(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class TimezoneIdentifier(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "TimezoneIdentifier cannot be empty"
        }
    }
}