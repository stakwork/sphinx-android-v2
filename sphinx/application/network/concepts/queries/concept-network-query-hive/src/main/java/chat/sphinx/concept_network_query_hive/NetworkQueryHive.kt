package chat.sphinx.concept_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto
import chat.sphinx.concept_network_query_hive.model.HiveTasksListDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

abstract class NetworkQueryHive {

    abstract fun authenticateWithHive(
        token: String,
        pubkey: String,
        timestamp: Long
    ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>>

    abstract fun getWorkspaces(
        authToken: String
    ): Flow<LoadResponse<WorkspacesListDto, ResponseError>>

    abstract fun getWorkspaceImage(
        slug: String,
        authToken: String
    ): Flow<LoadResponse<WorkspaceImageDto, ResponseError>>

    abstract fun getFeatures(
        workspaceId: String,
        page: Int,
        authToken: String
    ): Flow<LoadResponse<HiveFeaturesListDto, ResponseError>>

    abstract fun updateFeature(
        featureId: String,
        patch: HiveFeaturePatchDto,
        authToken: String
    ): Flow<LoadResponse<HiveFeatureUpdateDto, ResponseError>>

    abstract fun deleteFeature(
        featureId: String,
        authToken: String
    ): Flow<LoadResponse<HiveDeleteResponseDto, ResponseError>>

    abstract fun getTasks(
        workspaceId: String,
        page: Int,
        includeArchived: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTasksListDto, ResponseError>>

    abstract fun startTask(
        taskId: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun retryTask(
        taskId: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun updateTaskStatus(
        taskId: String,
        status: String,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun setTaskArchived(
        taskId: String,
        archived: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun updateTaskFlags(
        taskId: String,
        autoMerge: Boolean,
        runBuild: Boolean,
        runTestSuite: Boolean,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun duplicateTask(
        featureId: String,
        body: HiveTaskDuplicateDto,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>

    abstract fun updateTaskDependsOn(
        taskId: String,
        dependsOnTaskIds: List<String>,
        authToken: String
    ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>>
}
