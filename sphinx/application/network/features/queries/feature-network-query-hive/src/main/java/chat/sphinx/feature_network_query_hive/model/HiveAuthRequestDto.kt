package chat.sphinx.feature_network_query_hive.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json

@JsonClass(generateAdapter = true)
data class HiveAuthRequestDto(
    @Json(name = "token") val signedToken: String,
    @Json(name = "pubkey") val pubkey: String,
    @Json(name = "timestamp") val timestamp: String
)
