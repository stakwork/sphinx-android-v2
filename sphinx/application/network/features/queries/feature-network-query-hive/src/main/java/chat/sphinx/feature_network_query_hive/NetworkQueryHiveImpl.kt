package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    private val networkRelayCall: NetworkRelayCall,
) : NetworkQueryHive() {

    companion object {
        private const val ENDPOINT_HIVE_AUTH = "https://hive.sphinx.chat/api/auth/sphinx/token"
    }

    override fun getHiveToken(body: HiveAuthRequestDto): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> =
        networkRelayCall.post(
            url = ENDPOINT_HIVE_AUTH,
            responseJsonClass = HiveAuthTokenDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = body,
        )
}
