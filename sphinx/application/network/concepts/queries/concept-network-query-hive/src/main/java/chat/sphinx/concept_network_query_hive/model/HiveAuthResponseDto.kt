package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveAuthResponseDto(
    @field:com.squareup.moshi.Json(name = "token") val authToken: String
)
