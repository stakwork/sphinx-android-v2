package chat.sphinx.wrapper_common.lsat

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLsatMetaData(): LsatMetaData? =
    try {
        LsatMetaData(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LsatMetaData(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspMetaData cannot be empty"
        }
    }
}