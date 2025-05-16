package chat.sphinx.wrapper_feed

@Suppress("NOTHING_TO_INLINE")
inline fun String.toFeedReferenceId(): FeedReferenceId? =
    try {
        FeedReferenceId(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class FeedReferenceId(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "ReferenceId cannot be empty"
        }
    }
}