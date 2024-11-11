package chat.sphinx.wrapper_chat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toSecondBrainUrl(): SecondBrainUrl? =
    try {
        SecondBrainUrl(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class SecondBrainUrl(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "SecondBrainUrl cannot be empty"
        }
    }
}
