package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson


sealed class DataSyncJson {
    data class StringValue(val value: String) : DataSyncJson()
    data class ObjectValue(val value: Map<String, String>) : DataSyncJson()

    // Convenience getters
    fun asString(): String? = (this as? StringValue)?.value

    fun asInt(): Int? = asString()?.toIntOrNull()

    fun asBool(): Boolean? = asString()?.let {
        when (it.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }

    fun asMap(): Map<String, String>? = (this as? ObjectValue)?.value

    fun asTimezone(): TimezoneSetting? = (this as? ObjectValue)?.toTimezoneSetting()

    fun asFeedStatus(): FeedStatus? = (this as? ObjectValue)?.toFeedStatus()

    fun asFeedItemStatus(): FeedItemStatus? = (this as? ObjectValue)?.toFeedItemStatus()

    // Type-specific converters
    private fun ObjectValue.toTimezoneSetting(): TimezoneSetting? {
        val enabled = value["timezoneEnabled"] ?: return null
        val identifier = value["timezoneIdentifier"] ?: return null
        return TimezoneSetting(
            timezoneEnabled = enabled.lowercase() == "true",
            timezoneIdentifier = identifier
        )
    }

    private fun ObjectValue.toFeedStatus(): FeedStatus? {
        return FeedStatus(
            chatPubkey = value["chat_pubkey"] ?: "",
            feedUrl = value["feed_url"] ?: return null,
            feedId = value["feed_id"] ?: return null,
            subscribed = value["subscribed"]?.lowercase() == "true",
            satsPerMinute = value["sats_per_minute"]?.toIntOrNull() ?: 0,
            playerSpeed = value["player_speed"]?.toDoubleOrNull() ?: 1.0,
            itemId = value["item_id"] ?: ""
        )
    }

    private fun ObjectValue.toFeedItemStatus(): FeedItemStatus? {
        return FeedItemStatus(
            duration = value["duration"]?.toIntOrNull() ?: return null,
            currentTime = value["current_time"]?.toIntOrNull() ?: return null
        )
    }

    // Convert raw value to DataSyncJson
    companion object {
        fun fromAny(value: Any): DataSyncJson {
            return when (value) {
                is String -> StringValue(value)
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    ObjectValue(value as Map<String, String>)
                }
                else -> StringValue(value.toString())
            }
        }
    }
}
