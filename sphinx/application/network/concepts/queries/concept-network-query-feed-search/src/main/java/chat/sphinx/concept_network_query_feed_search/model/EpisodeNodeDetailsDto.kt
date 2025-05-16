package chat.sphinx.concept_network_query_feed_search.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EpisodeNodeDetailsDto(
    val name: String?,
    val node_type: String?,
    val properties: EpisodeNodeProperties?,
    val ref_id: String?
)

@JsonClass(generateAdapter = true)
data class EpisodeNodeProperties(
    val date: Long?,
    val episode_title: String?,
    val image_url: String?,
    val media_url: String?,
    val project_id: String?,
    val source_link: String?,
    val status: String?
)
