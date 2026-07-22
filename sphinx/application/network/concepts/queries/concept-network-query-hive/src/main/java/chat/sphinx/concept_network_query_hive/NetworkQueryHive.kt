package chat.sphinx.concept_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

abstract class NetworkQueryHive {

    abstract fun authenticateWithHive(
        signedToken: String,
        pubkey: String,
        timestamp: String,
    ): Flow<LoadResponse<HiveAuthResponseDto, ResponseError>>
}
