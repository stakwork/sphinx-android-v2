package chat.sphinx.concept_network_query_feed_search.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EpisodeNodeResponseDto(
    val data: EpisodeNodeDataDto?,
    val success: Boolean? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val node_key: String? = null,
    val status: String? = null,
    val status_messages: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeNodeDataDto(
    val project_id: Long? = null,
    val node_key: String? = null,
    val ref_id: String
)
