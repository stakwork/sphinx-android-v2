package chat.sphinx.concept_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

abstract class NetworkQueryHive {
    abstract fun postHiveAuthentication(
        token: String,
        pubkey: String,
        timestamp: String,
    ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>>
}
