package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveAuthRequestDto(
    val token: String,
    val pubkey: String,
    val timestamp: String,
)
