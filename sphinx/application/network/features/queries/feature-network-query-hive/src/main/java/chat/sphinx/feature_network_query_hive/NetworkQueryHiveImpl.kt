package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDependsOnPatchDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPatchDto
import chat.sphinx.concept_network_query_hive.model.HiveTasksListDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder

class NetworkQueryHiveImpl(
    private val networkCall: NetworkCall
) : NetworkQueryHive() {

    companion object {
        const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        const val ENDPOINT_AUTH = "/auth/sphinx/token"
        const val ENDPOINT_WORKSPACES = "/workspaces"
        const val ENDPOINT_FEATURES = "/features"
        const val ENDPOINT_TASKS = "/tasks"
        const val ENDPOINT_TICKETS = "/tickets"
        const val FEATURES_PAGE_LIMIT = 20
        const val TASKS_PAGE_LIMIT = 20

        /**
         * Percent-encode [segment] as a URL path segment.
         *
         * [URLEncoder] emits `+` for spaces (form encoding), which is invalid in a
         * path — rewrite those to `%20`.
         */
        fun encodePathSegment(segment: String): String =
            URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

        /**
         * Percent-encode [value] as a URL query parameter (form encoding).
         * Spaces become `+`, which is correct for query values.
         */
        fun encodeQueryValue(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    override fun authenticateWithHive(
        token: String,
        pubkey: String,
        timestamp: Long
    ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> =
        networkCall.post(
            url = HIVE_BASE_URL + ENDPOINT_AUTH,
            responseJsonClass = HiveAuthenticationTokenDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = HiveAuthRequestDto(token, pubkey, timestamp)
        )

    override fun getWorkspaces(
        authToken: String
    ): Flow<LoadResponse<WorkspacesListDto, ResponseError>> =
        networkCall.get(
            url = HIVE_BASE_URL + ENDPOINT_WORKSPACES,
            responseJsonClass = WorkspacesListDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken")
        )

    override fun getWorkspaceImage(
        slug: String,
        authToken: String
    ): Flow<LoadResponse<WorkspaceImageDto, ResponseError>> =
        networkCall.get(
            url = HIVE_BASE_URL + "/workspaces/" + encodePathSegment(slug) + "/image",
            responseJsonClass = WorkspaceImageDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken")
        )

    override fun getFeatures(
        workspaceId: String,
        page: Int,
        authToken: String
    ): Flow<LoadResponse<HiveFeaturesListDto, ResponseError>> {
        val encodedWorkspaceId = encodeQueryValue(workspaceId)
        val encodedPage = encodeQueryValue(page.toString())
        return networkCall.get(
            url = "$HIVE_BASE_URL$ENDPOINT_FEATURES?workspaceId=$encodedWorkspaceId&limit=$FEATURES_PAGE_LIMIT&page=$encodedPage",
            responseJsonClass = HiveFeaturesListDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken"),
            requireSuccessful = true
        )
    }

    override fun updateFeature(
        featureId: String,
        patch: HiveFeaturePatchDto,
        authToken: String
    ): Flow<LoadResponse<HiveFeatureUpdateDto, ResponseError>> =
        networkCall.patch(
            url = "$HIVE_BASE_URL$ENDPOINT_FEATURES/${encodePathSegment(featureId)}",
            responseJsonClass = HiveFeatureUpdateDto::class.java,
            requestBodyJsonClass = HiveFeaturePatchDto::class.java,
            requestBody = patch,
            mediaType = "application/json",
            headers = mapOf(
                "Authorization" to "Bearer $authToken",
                "Content-Type" to "application/json"
            ),
            requireSuccessful = true
        )

    override fun deleteFeature(
        featureId: String,
        authToken: String
    ): Flow<LoadResponse<HiveDeleteResponseDto, ResponseError>> =
        networkCall.delete<HiveDeleteResponseDto, Any>(
            url = "$HIVE_BASE_URL$ENDPOINT_FEATURES/${encodePathSegment(featureId)}",
            responseJsonClass = HiveDeleteResponseDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken"),
            requireSuccessful = true
        )

    override fun getTasks(
        workspaceId: String,
        page: Int,
        includeArchived: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTasksListDto, ResponseError>> {
        val encodedWorkspaceId = encodeQueryValue(workspaceId)
        val encodedPage = encodeQueryValue(page.toString())
        val archivedQuery = if (includeArchived) "&includeArchived=true" else ""
        return networkCall.get(
            url = "$HIVE_BASE_URL$ENDPOINT_TASKS?workspaceId=$encodedWorkspaceId&limit=$TASKS_PAGE_LIMIT&page=$encodedPage$archivedQuery",
            responseJsonClass = HiveTasksListDto::class.java,
            headers = mapOf("Authorization" to "Bearer $authToken"),
            requireSuccessful = true
        )
    }

    override fun startTask(
        taskId: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        patchTask(taskId, HiveTaskPatchDto(startWorkflow = true), authToken)

    override fun retryTask(
        taskId: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        patchTask(taskId, HiveTaskPatchDto(retryWorkflow = true), authToken)

    override fun updateTaskStatus(
        taskId: String,
        status: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        patchTask(taskId, HiveTaskPatchDto(status = status), authToken)

    override fun setTaskArchived(
        taskId: String,
        archived: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        patchTask(taskId, HiveTaskPatchDto(archived = archived), authToken)

    override fun updateTaskFlags(
        taskId: String,
        autoMerge: Boolean,
        runBuild: Boolean,
        runTestSuite: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        patchTask(
            taskId,
            HiveTaskPatchDto(
                autoMerge = autoMerge,
                runBuild = runBuild,
                runTestSuite = runTestSuite,
            ),
            authToken
        )

    override fun duplicateTask(
        featureId: String,
        body: HiveTaskDuplicateDto,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        networkCall.post(
            url = "$HIVE_BASE_URL$ENDPOINT_FEATURES/${encodePathSegment(featureId)}/tickets",
            responseJsonClass = HiveTaskMutationDto::class.java,
            requestBodyJsonClass = HiveTaskDuplicateDto::class.java,
            requestBody = body,
            mediaType = "application/json",
            headers = mapOf(
                "Authorization" to "Bearer $authToken",
                "Content-Type" to "application/json"
            ),
            requireSuccessful = true
        )

    override fun updateTaskDependsOn(
        taskId: String,
        dependsOnTaskIds: List<String>,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        networkCall.patch(
            url = "$HIVE_BASE_URL$ENDPOINT_TICKETS/${encodePathSegment(taskId)}",
            responseJsonClass = HiveTaskMutationDto::class.java,
            requestBodyJsonClass = HiveTaskDependsOnPatchDto::class.java,
            requestBody = HiveTaskDependsOnPatchDto(dependsOnTaskIds),
            mediaType = "application/json",
            headers = mapOf(
                "Authorization" to "Bearer $authToken",
                "Content-Type" to "application/json"
            ),
            requireSuccessful = true
        )

    private fun patchTask(
        taskId: String,
        patch: HiveTaskPatchDto,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        networkCall.patch(
            url = "$HIVE_BASE_URL$ENDPOINT_TASKS/${encodePathSegment(taskId)}",
            responseJsonClass = HiveTaskMutationDto::class.java,
            requestBodyJsonClass = HiveTaskPatchDto::class.java,
            requestBody = patch,
            mediaType = "application/json",
            headers = mapOf(
                "Authorization" to "Bearer $authToken",
                "Content-Type" to "application/json"
            ),
            requireSuccessful = true
        )
}
