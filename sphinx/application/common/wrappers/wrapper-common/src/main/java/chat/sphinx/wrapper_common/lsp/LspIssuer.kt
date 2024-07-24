package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspIssuer(): LspIssuer? =
    try {
        LspIssuer(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspIssuer (val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspIssuer cannot be empty"
        }
    }
}