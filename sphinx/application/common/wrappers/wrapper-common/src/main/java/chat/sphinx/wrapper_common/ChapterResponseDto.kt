package chat.sphinx.wrapper_common

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ChapterResponseDto(
    val edges: List<EdgeDto>,
    val nodes: List<NodeDto>,
    val status: String
)

@JsonClass(generateAdapter = true)
data class EdgeDto(
    val edge_type: String,
    val ref_id: String,
    val source: String,
    val target: String,
    val weight: Int,
    val properties: Map<String, String>?
)

@JsonClass(generateAdapter = true)
data class NodeDto(
    val ref_id: String,
    val node_type: String,
    val date_added_to_graph: Double,
    val properties: ChapterProperties?
)

@JsonClass(generateAdapter = true)
data class ChapterProperties(
    val is_ad: String?, // will be parsed as "True"/"False"
    val name: String?,
    val source_link: String?,
    val timestamp: String?,
    val episode_title: String?, // only exists for Episode node
    val image_url: String?,
    val media_url: String?,
    val status: String?,
    val date: Long?
) {
    val isAdBoolean: Boolean
        get() = is_ad?.equals("True", ignoreCase = true) == true

}