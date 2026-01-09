package chat.sphinx.concept_network_query_meme_server.model

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class DataSyncResponseDto(
    val items: List<DataSyncItemDto>
) {
    fun toJson(): String {
        // Convert to JSON string matching expected format
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
        val valueStr = when (value) {
            is String -> "\"$value\""
            is Map<*, *> -> {
                // Convert map to JSON object string
                value.entries.joinToString(",", "{", "}") {
                    "\"${it.key}\": ${if (it.value is String) "\"${it.value}\"" else it.value}"
                }
            }
            else -> value.toString()
        }
        return """{"key":"$key","identifier":"$identifier","date":"$date","value":$valueStr}"""
    }
}