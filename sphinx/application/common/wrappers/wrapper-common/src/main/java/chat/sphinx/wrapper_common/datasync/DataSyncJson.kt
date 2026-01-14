package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

sealed class DataSyncJson {
    data class StringValue(val value: String) : DataSyncJson()
    data class ObjectValue(val value: Map<String, Any>) : DataSyncJson()

    fun asString(): String? = (this as? StringValue)?.value
    fun asInt(): Int? = asString()?.toIntOrNull()
    fun asBool(): Boolean? = asString()?.let {
        when (it.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }
    }
    fun asTimezone(): TimezoneSetting? = (this as? ObjectValue)?.toTimezoneSetting()
    fun asFeedStatus(): FeedStatus? = (this as? ObjectValue)?.toFeedStatus()
    fun asFeedItemStatus(): FeedItemStatus? = (this as? ObjectValue)?.toFeedItemStatus()

    private fun ObjectValue.toTimezoneSetting(): TimezoneSetting? {
        val enabled = value["timezoneEnabled"]?.toString() ?: return null
        val identifier = value["timezoneIdentifier"]?.toString() ?: return null
        return TimezoneSetting(
            timezoneEnabled = enabled.lowercase() == "true",
            timezoneIdentifier = identifier
        )
    }

    private fun ObjectValue.toFeedStatus(): FeedStatus? {
        return FeedStatus(
            chatPubkey = value["chat_pubkey"]?.toString() ?: return null,
            feedUrl = value["feed_url"]?.toString() ?: return null,
            feedId = value["feed_id"]?.toString() ?: return null,
            subscribed = value["subscribed"]?.toString()?.lowercase() == "true",
            satsPerMinute = value["sats_per_minute"]?.toString()?.toIntOrNull() ?: 0,
            playerSpeed = value["player_speed"]?.toString()?.toDoubleOrNull() ?: 1.0,
            itemId = value["item_id"]?.toString() ?: ""
        )
    }

    private fun ObjectValue.toFeedItemStatus(): FeedItemStatus? {
        return FeedItemStatus(
            duration = value["duration"]?.toString()?.toIntOrNull() ?: return null,
            currentTime = value["current_time"]?.toString()?.toIntOrNull() ?: return null
        )
    }

    companion object {
        fun fromString(string: String, forKey: String): DataSyncJson? {
            return when (forKey) {
                "tip_amount", "private_photo" -> StringValue(string)
                "timezone", "feed_status", "feed_item_status" -> {
                    try {
                        val map = parseJsonObject(string)
                        ObjectValue(map)
                    } catch (e: Exception) {
                        null
                    }
                }
                else -> StringValue(string)
            }
        }

        private fun parseJsonObject(json: String): Map<String, Any> {
            val map = mutableMapOf<String, Any>()
            val cleaned = json.trim().removePrefix("{").removeSuffix("}")

            cleaned.split(",").forEach { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().removeSurrounding("\"")
                    val valueStr = parts[1].trim().removeSurrounding("\"")

                    // Try to parse as number or boolean, otherwise keep as string
                    val value: Any = valueStr.toIntOrNull()
                        ?: valueStr.toDoubleOrNull()
                        ?: when(valueStr.lowercase()) {
                            "true" -> true
                            "false" -> false
                            else -> valueStr
                        }
                    map[key] = value
                }
            }

            return map
        }
    }
}
