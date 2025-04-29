package chat.sphinx.wrapper_feed

@Suppress("NOTHING_TO_INLINE")
inline fun String.toFeedChapterData(): FeedChaptersData? =
    try {
        FeedChaptersData(this)
    } catch (e: IllegalArgumentException) {
        null
    }

@JvmInline
value class FeedChaptersData(val value: String) {
    init {
        require(value.isNotEmpty()) {
            "ChaptersData cannot be empty"
        }
    }
}
