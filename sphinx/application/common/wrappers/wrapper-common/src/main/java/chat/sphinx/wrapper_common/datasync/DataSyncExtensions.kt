package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonDataException

// Convert raw response to processed items
fun ItemsResponseRaw.toSettingItems(): List<SettingItem> {
    return items.map { raw ->
        SettingItem(
            key = raw.key,
            identifier = raw.identifier,
            date = raw.date,
            value = DataSyncJson.fromAny(raw.value)
        )
    }
}

// Convert processed items back to raw (for uploading)
fun List<SettingItem>.toItemsResponseRaw(): ItemsResponseRaw {
    return ItemsResponseRaw(
        items = this.map { item ->
            SettingItemRaw(
                key = item.key,
                identifier = item.identifier,
                date = item.date,
                value = when (val v = item.value) {
                    is DataSyncJson.StringValue -> v.value
                    is DataSyncJson.ObjectValue -> v.value
                }
            )
        }
    )
}

// Parse JSON string to raw response
@Throws(JsonDataException::class)
fun String.toItemsResponseRaw(moshi: Moshi): ItemsResponseRaw {
    val adapter = moshi.adapter(ItemsResponseRaw::class.java)
    return adapter.fromJson(this)
        ?: throw IllegalArgumentException("Invalid JSON for ItemsResponse")
}

fun String.toItemsResponseRawNull(moshi: Moshi): ItemsResponseRaw? {
    return try {
        this.toItemsResponseRaw(moshi)
    } catch (e: Exception) {
        null
    }
}

// Serialize raw response to JSON
fun ItemsResponseRaw.toJson(moshi: Moshi): String {
    val adapter = moshi.adapter(ItemsResponseRaw::class.java)
    return adapter.toJson(this)
}

// Serialize processed items to JSON (via raw)
fun List<SettingItem>.toJson(moshi: Moshi): String {
    return this.toItemsResponseRaw().toJson(moshi)
}

/**
 * Parse a simple JSON object string into a Map<String, String>.
 * Handles basic key-value pairs without nested objects.
 * Example: {"key1":"value1","key2":"value2"} -> mapOf("key1" to "value1", "key2" to "value2")
 */
fun parseSimpleJsonObject(json: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val cleaned = json.trim().removePrefix("{").removeSuffix("}")

    if (cleaned.isEmpty()) return map

    cleaned.split(",").forEach { pair ->
        val parts = pair.split(":", limit = 2)
        if (parts.size == 2) {
            val key = parts[0].trim().removeSurrounding("\"")
            val value = parts[1].trim().removeSurrounding("\"")
            map[key] = value
        }
    }

    return map
}