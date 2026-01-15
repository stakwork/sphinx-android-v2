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
    val value: String
) {
    fun toJson(): String {
        val escapedValue = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """{"key":"$key","identifier":"$identifier","date":"$date","value":"$escapedValue"}"""
    }
}