package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    dispatchers: CoroutineDispatchers,
    private val networkRelayCall: NetworkRelayCall,
) : NetworkQueryHive(), CoroutineDispatchers by dispatchers {

    companion object {
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    override fun authenticate(
        token: String,
        pubkey: String,
        timestamp: String,
    ): Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> {
        require(ENDPOINT_AUTH.startsWith("https://")) { "Hive URL must use HTTPS" }
        return networkRelayCall.post(
            url = ENDPOINT_AUTH,
            responseJsonClass = HiveAuthTokenDto::class.java,
            requestBodyJsonClass = Map::class.java,
            requestBody = mapOf("token" to token, "pubkey" to pubkey, "timestamp" to timestamp),
        )
    }
}
