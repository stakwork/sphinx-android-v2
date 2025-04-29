package chat.sphinx.concept_network_query_feed_search.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateProjectResponseDto(
    val success: Boolean?,
    val data: ProjectData?,
    val error: ErrorResponse?
)

@JsonClass(generateAdapter = true)
data class ProjectData(
    val project_id: Long?
)
@JsonClass(generateAdapter = true)
data class ErrorResponse(
    val message: String?
)
