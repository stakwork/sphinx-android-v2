package chat.sphinx.feature_data_sync.adapter

import chat.sphinx.wrapper_common.datasync.SettingItemRaw
import com.squareup.moshi.*

class SettingItemRawAdapter {

    @FromJson
    fun fromJson(reader: JsonReader): SettingItemRaw {
        var key = ""
        var identifier = ""
        var date = ""
        var value: Any = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "key" -> key = reader.nextString()
                "identifier" -> identifier = reader.nextString()
                "date" -> date = reader.nextString()
                "value" -> {
                    value = when (reader.peek()) {
                        JsonReader.Token.STRING -> {
                            // Simple string value like "12"
                            reader.nextString()
                        }
                        JsonReader.Token.BEGIN_OBJECT -> {
                            // Object with string key-value pairs
                            readStringMap(reader)
                        }
                        else -> {
                            reader.skipValue()
                            ""
                        }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return SettingItemRaw(
            key = key,
            identifier = identifier,
            date = date,
            value = value
        )
    }

    private fun readStringMap(reader: JsonReader): Map<String, String> {
        val map = mutableMapOf<String, String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            val value = when (reader.peek()) {
                JsonReader.Token.STRING -> reader.nextString()
                JsonReader.Token.NULL -> {
                    reader.nextNull<Any>()
                    ""
                }
                else -> {
                    reader.skipValue()
                    ""
                }
            }
            map[key] = value
        }
        reader.endObject()
        return map
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: SettingItemRaw) {
        writer.beginObject()
        writer.name("key").value(value.key)
        writer.name("identifier").value(value.identifier)
        writer.name("date").value(value.date)
        writer.name("value")

        when (val v = value.value) {
            is String -> writer.value(v)
            is Map<*, *> -> {
                writer.beginObject()
                @Suppress("UNCHECKED_CAST")
                (v as Map<String, String>).forEach { (k, mapValue) ->
                    writer.name(k).value(mapValue)
                }
                writer.endObject()
            }
            else -> writer.value(v.toString())
        }

        writer.endObject()
    }
}