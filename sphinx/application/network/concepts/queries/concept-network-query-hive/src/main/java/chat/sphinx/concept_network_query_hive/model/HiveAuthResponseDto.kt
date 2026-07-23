package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json

@JsonClass(generateAdapter = true)
data class HiveAuthResponseDto(
    @Json(name = "token") val authToken: String
)
