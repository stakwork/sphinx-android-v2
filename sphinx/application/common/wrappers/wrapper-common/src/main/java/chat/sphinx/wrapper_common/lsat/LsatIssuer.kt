package chat.sphinx.wrapper_common.lsat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLsatIssuer(): LsatIssuer? =
    try {
        LsatIssuer(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LsatIssuer(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspIssuer cannot be empty"
        }
    }
}