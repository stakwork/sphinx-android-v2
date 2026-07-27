package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    private val networkCall: NetworkCall
) : NetworkQueryHive() {

    companion object {
        const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        const val ENDPOINT_AUTH = "/auth/sphinx/token"
    }

    override fun authenticateWithHive(
        token: String,
        pubkey: String,
        timestamp: String
    ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> =
        networkCall.post(
            url = HIVE_BASE_URL + ENDPOINT_AUTH,
            responseJsonClass = HiveAuthenticationTokenDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = HiveAuthRequestDto(token, pubkey, timestamp)
        )
}
