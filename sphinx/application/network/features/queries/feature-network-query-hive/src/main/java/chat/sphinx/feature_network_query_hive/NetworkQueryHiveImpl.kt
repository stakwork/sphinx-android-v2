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
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_HIVE_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    override fun getHiveAuthToken(
        token: String,
        pubkey: String,
        timestamp: String
    ): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> =
        networkRelayCall.post(
            url = ENDPOINT_HIVE_AUTH,
            responseJsonClass = HiveAuthTokenDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = HiveAuthRequestDto(token = token, pubkey = pubkey, timestamp = timestamp)
        )
}
