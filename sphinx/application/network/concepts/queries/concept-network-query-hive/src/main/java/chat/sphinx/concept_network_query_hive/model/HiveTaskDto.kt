package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveTaskDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "priority") val priority: String? = null,
    @Json(name = "workflowStatus") val workflowStatus: String? = null,
    @Json(name = "archived") val archived: Boolean? = null,
    @Json(name = "sourceType") val sourceType: String? = null,
    @Json(name = "mode") val mode: String? = null,
    @Json(name = "podId") val podId: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "chatMessageCount") val chatMessageCount: Int? = null,
    @Json(name = "stakworkProjectId") val stakworkProjectId: String? = null,
    @Json(name = "deploymentStatus") val deploymentStatus: String? = null,
    @Json(name = "deployedToProductionAt") val deployedToProductionAt: String? = null,
    @Json(name = "systemAssigneeType") val systemAssigneeType: String? = null,
    @Json(name = "autoMerge") val autoMerge: Boolean? = null,
    @Json(name = "runBuild") val runBuild: Boolean? = null,
    @Json(name = "runTestSuite") val runTestSuite: Boolean? = null,
    @Json(name = "dependsOnTaskIds") val dependsOnTaskIds: List<String>? = null,
    @Json(name = "workflowId") val workflowId: String? = null,
    @Json(name = "workflowName") val workflowName: String? = null,
    @Json(name = "workflowRefId") val workflowRefId: String? = null,
    @Json(name = "workflowTaskType") val workflowTaskType: String? = null,
    @Json(name = "workflowVersionId") val workflowVersionId: String? = null,
    @Json(name = "featureId") val featureId: String? = null,
    @Json(name = "workspaceId") val workspaceId: String? = null,
    @Json(name = "phaseId") val phaseId: String? = null,
    @Json(name = "prUrl") val prUrl: String? = null,
    @Json(name = "prStatus") val prStatus: String? = null,
    @Json(name = "feature") val feature: HiveTaskFeatureDto? = null,
    @Json(name = "assignee") val assignee: HiveTaskPersonDto? = null,
    @Json(name = "repository") val repository: HiveTaskRepositoryDto? = null,
    @Json(name = "createdBy") val createdBy: HiveTaskPersonDto? = null,
    @Json(name = "prArtifact") val prArtifact: HiveTaskPrArtifactDto? = null,
    @Json(name = "phase") val phase: HiveTaskPhaseDto? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskFeatureDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskPersonDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "image") val image: String? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskRepositoryDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "repositoryUrl") val repositoryUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskPrArtifactDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "content") val content: HiveTaskPrArtifactContentDto? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "number") val number: Int? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskPrArtifactContentDto(
    @Json(name = "url") val url: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "number") val number: Int? = null,
)

@JsonClass(generateAdapter = true)
data class HiveTaskPhaseDto(
    @Json(name = "id") val id: String? = null,
)
