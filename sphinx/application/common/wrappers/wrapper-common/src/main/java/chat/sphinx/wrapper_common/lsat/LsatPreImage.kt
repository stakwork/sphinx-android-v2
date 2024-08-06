package chat.sphinx.wrapper_common.lsat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLsatPreImage(): LsatPreImage? =
    try {
        LsatPreImage(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LsatPreImage(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspPreImage cannot be empty"
        }
    }
}