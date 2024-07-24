package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toMacaroon(): Macaroon? =
    try {
        Macaroon(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class Macaroon(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "Macaroon cannot be empty"
        }
    }
}
