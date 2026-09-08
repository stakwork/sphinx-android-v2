package chat.sphinx.concept_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
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
}
