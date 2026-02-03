package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class FeedItemStatus(
    @Json(name = "duration") val duration: Int,
    @Json(name = "current_time") val currentTime: Int
) {
    val progressPercentage: Double
        get() = if (duration > 0) (currentTime.toDouble() / duration) * 100 else 0.0

    val remainingTime: Int
        get() = maxOf(0, duration - currentTime)

    val isCompleted: Boolean
        get() = currentTime >= duration

    @Throws(AssertionError::class)
    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(FeedItemStatus::class.java)
        return adapter.toJson(this)
    }

    companion object {
        fun String.toFeedItemStatusNull(moshi: Moshi): FeedItemStatus? {
            return try {
                this.toFeedItemStatus(moshi)
            } catch (e: Exception) {
                null
            }
        }

        @Throws(JsonDataException::class)
        fun String.toFeedItemStatus(moshi: Moshi): FeedItemStatus {
            val adapter = moshi.adapter(FeedItemStatus::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for FeedItemStatus")
        }
    }
}