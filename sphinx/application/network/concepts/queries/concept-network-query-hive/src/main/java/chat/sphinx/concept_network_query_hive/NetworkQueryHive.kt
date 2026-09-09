package chat.sphinx.concept_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
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
}
