package chat.sphinx.wrapper_common.lsat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLsatPaths(): LsatPaths? =
    try {
        LsatPaths(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LsatPaths(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspPaths cannot be empty"
        }
    }
}