package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class FeedStatus(
    @Json(name = "chat_pubkey") val chatPubkey: String,
    @Json(name = "feed_url") val feedUrl: String,
    @Json(name = "feed_id") val feedId: String,
    @Json(name = "subscribed") val subscribed: Boolean,
    @Json(name = "sats_per_minute") val satsPerMinute: Int,
    @Json(name = "player_speed") val playerSpeed: Double,
    @Json(name = "item_id") val itemId: String
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