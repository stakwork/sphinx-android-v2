package chat.sphinx.wrapper_common.lsat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLsatIdentifier(): LsatIdentifier? =
    try {
        LsatIdentifier(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LsatIdentifier(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspIdentifier cannot be empty"
        }
    }
}
