package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

sealed class JsonValue {
    data class StringValue(val value: String) : JsonValue()
    data class ObjectValue(val value: Map<String, String>) : JsonValue()

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
        val enabled = value["timezoneEnabled"] ?: return null
        val identifier = value["timezoneIdentifier"] ?: return null
        return TimezoneSetting(
            timezoneEnabled = enabled.lowercase() == "true",
            timezoneIdentifier = identifier
        )
    }

    private fun ObjectValue.toFeedStatus(): FeedStatus? {
        return FeedStatus(
            chatPubkey = value["chat_pubkey"] ?: return null,
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

    companion object {
        fun fromString(string: String, forKey: String): JsonValue? {
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

        private fun parseJsonObject(json: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            val cleaned = json.trim().removePrefix("{").removeSuffix("}")

            cleaned.split(",").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val key = parts[0].trim().removeSurrounding("\"")
                    val value = parts[1].trim().removeSurrounding("\"")
                    map[key] = value
                }
            }

            return map
        }
    }
}

class JsonValueAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): JsonValue {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> {
                JsonValue.StringValue(reader.nextString())
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                val map = mutableMapOf<String, String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    val value = reader.nextString()
                    map[name] = value
                }
                reader.endObject()
                JsonValue.ObjectValue(map)
            }
            else -> throw JsonDataException("Unexpected JSON token: ${reader.peek()}")
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: JsonValue?) {
        when (value) {
            is JsonValue.StringValue -> writer.value(value.value)
            is JsonValue.ObjectValue -> {
                writer.beginObject()
                value.value.forEach { (k, v) ->
                    writer.name(k).value(v)
                }
                writer.endObject()
            }
            null -> writer.nullValue()
        }
    }
}
