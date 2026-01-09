package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class FeedStatus(
    val chatPubkey: String,
    val feedUrl: String,
    val feedId: String,
    val subscribed: Boolean,
    val satsPerMinute: Int,
    val playerSpeed: Double,
    val itemId: String
) {
    @Throws(AssertionError::class)
    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(FeedStatus::class.java)
        return adapter.toJson(this)
    }

    companion object {
        fun String.toFeedStatusNull(moshi: Moshi): FeedStatus? {
            return try {
                this.toFeedStatus(moshi)
            } catch (e: Exception) {
                null
            }
        }

        @Throws(JsonDataException::class)
        fun String.toFeedStatus(moshi: Moshi): FeedStatus {
            val adapter = moshi.adapter(FeedStatus::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for FeedStatus")
        }
    }
}
