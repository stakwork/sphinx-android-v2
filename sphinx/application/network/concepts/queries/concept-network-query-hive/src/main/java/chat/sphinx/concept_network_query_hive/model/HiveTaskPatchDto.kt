package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveTaskPatchDto(
    @Json(name = "startWorkflow") val startWorkflow: Boolean? = null,
    @Json(name = "retryWorkflow") val retryWorkflow: Boolean? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "archived") val archived: Boolean? = null,
    @Json(name = "autoMerge") val autoMerge: Boolean? = null,
    @Json(name = "runBuild") val runBuild: Boolean? = null,
    @Json(name = "runTestSuite") val runTestSuite: Boolean? = null,
)
