package chat.sphinx.feature_network_query_hive.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveAuthenticationRequestDto(
    val token: String,
    val pubkey: String,
    val timestamp: String,
)
