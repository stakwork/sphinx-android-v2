package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspMetaData(): LspMetaData? =
    try {
        LspMetaData(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspMetaData (val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspMetaData cannot be empty"
        }
    }
}