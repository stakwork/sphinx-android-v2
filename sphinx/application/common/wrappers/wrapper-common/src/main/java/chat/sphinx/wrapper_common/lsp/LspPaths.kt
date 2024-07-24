package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspPaths(): LspPaths? =
    try {
        LspPaths(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspPaths(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspPaths cannot be empty"
        }
    }
}