package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspPreImage(): LspPreImage? =
    try {
        LspPreImage(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspPreImage(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspPreImage cannot be empty"
        }
    }
}