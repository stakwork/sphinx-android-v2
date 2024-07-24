package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspIdentifier(): LspIdentifier? =
    try {
        LspIdentifier(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspIdentifier(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspIdentifier cannot be empty"
        }
    }
}
