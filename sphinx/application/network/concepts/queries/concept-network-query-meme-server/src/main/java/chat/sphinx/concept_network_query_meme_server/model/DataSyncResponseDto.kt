package chat.sphinx.concept_network_query_meme_server.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DataSyncResponseDto(
    val items: List<DataSyncItemDto>
) {
    fun toJson(): String {
        return """{"items": ${items.joinToString(",", "[", "]") { it.toJson() }}}"""
    }
}

@JsonClass(generateAdapter = true)
data class DataSyncItemDto(
    val key: String,
    val identifier: String,
    val date: String,
    val value: Any
) {

    fun toJson(): String {
        return """
            {
              "key":"$key",
              "identifier":"$identifier",
              "date":"$date",
              "value":${valueToJson(value)}
            }
        """.trimIndent().replace("\n", "")
    }

    private fun valueToJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (k, v) ->
                "\"${k.toString()}\":${valueToJson(v)}"
            }
            is List<*> -> value.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ","
            ) { item ->
                valueToJson(item)
            }
            else -> "\"${escape(value.toString())}\""
        }
    }

    private fun escape(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
