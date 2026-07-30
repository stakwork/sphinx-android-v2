package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspacesListDto(
    @Json(name = "workspaces") val workspaces: List<WorkspaceDto> = emptyList()
)
