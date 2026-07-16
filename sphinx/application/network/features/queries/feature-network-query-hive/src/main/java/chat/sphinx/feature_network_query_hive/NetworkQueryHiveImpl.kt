package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    private val networkRelayCall: NetworkRelayCall,
) : NetworkQueryHive() {

    companion object {
        private const val BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_AUTH = "$BASE_URL/auth/sphinx/token"
    }

    override fun authenticate(
        requestBody: HiveAuthRequestDto,
    ): Flow<LoadResponse<HiveAuthResponseDto, ResponseError>> =
        networkRelayCall.post(
            url = ENDPOINT_AUTH,
            responseJsonClass = HiveAuthResponseDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = requestBody,
        )
}
