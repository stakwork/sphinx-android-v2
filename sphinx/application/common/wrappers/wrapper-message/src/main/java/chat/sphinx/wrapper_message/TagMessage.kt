package chat.sphinx.wrapper_message

@Suppress("NOTHING_TO_INLINE")
inline fun String.toTagMessage(): TagMessage? =
    try {
        TagMessage(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class TagMessage(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "TagMessage cannot be empty"
        }
    }
}