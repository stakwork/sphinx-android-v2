package chat.sphinx.wrapper_common.lsp

@Suppress("NOTHING_TO_INLINE")
inline fun String.toLspPaymentRequest(): LspPaymentRequest? =
    try {
        LspPaymentRequest(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class LspPaymentRequest(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "LspPaymentRequest cannot be empty"
        }
    }
}
