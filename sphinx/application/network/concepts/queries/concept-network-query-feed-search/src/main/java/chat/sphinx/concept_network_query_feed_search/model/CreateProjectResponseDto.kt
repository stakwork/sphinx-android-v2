package chat.sphinx.concept_network_query_feed_search.model

data class CreateProjectResponseDto(
    val success: Boolean?,
    val data: ProjectData?
)

data class ProjectData(
    val project_id: Long?
)
