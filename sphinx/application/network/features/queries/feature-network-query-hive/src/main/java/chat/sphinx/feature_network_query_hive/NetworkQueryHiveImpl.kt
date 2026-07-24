package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.feature_network_query_hive.model.HiveAuthenticationRequestDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(private val networkCall: NetworkCall) : NetworkQueryHive() {

    companion object {
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_HIVE_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    override fun postHiveAuthentication(token: String, pubkey: String, timestamp: String) =
        networkCall.post(
            url = ENDPOINT_HIVE_AUTH,
            responseJsonClass = HiveAuthenticationTokenDto::class.java,
            requestBodyJsonClass = HiveAuthenticationRequestDto::class.java,
            requestBody = HiveAuthenticationRequestDto(token, pubkey, timestamp),
            headers = mapOf("Cache-Control" to "no-store, no-cache"),
        )
}
