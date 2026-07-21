package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveAuthRequestDto(
    @Json(name = "token") val token: String,
    @Json(name = "pubkey") val pubkey: String,
    @Json(name = "timestamp") val timestamp: String,
)
