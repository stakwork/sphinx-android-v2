package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveTaskDuplicateDto(
    @Json(name = "title") val title: String,
    @Json(name = "priority") val priority: String,
    @Json(name = "status") val status: String = "TODO",
    @Json(name = "autoMerge") val autoMerge: Boolean = false,
    @Json(name = "description") val description: String? = null,
    @Json(name = "phaseId") val phaseId: String? = null,
    @Json(name = "repositoryId") val repositoryId: String? = null,
    @Json(name = "dependsOnTaskIds") val dependsOnTaskIds: List<String>? = null,
    @Json(name = "workflowId") val workflowId: String? = null,
    @Json(name = "workflowName") val workflowName: String? = null,
    @Json(name = "workflowRefId") val workflowRefId: String? = null,
    @Json(name = "workflowTaskType") val workflowTaskType: String? = null,
    @Json(name = "workflowVersionId") val workflowVersionId: String? = null,
)
