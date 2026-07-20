package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    private val networkRelayCall: NetworkRelayCall,
) : NetworkQueryHive() {

    companion object {
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    override fun authenticateWithHive(
        token: String,
        pubkey: String,
        timestamp: String
    ): Flow<LoadResponse<HiveTokenDto, ResponseError>> {
        // Security note: The shared OkHttpClient uses HttpLoggingInterceptor.Level.BODY in debug
        // builds. Both the seed-derived signed token (request body) and the returned Hive token
        // (response body) will appear in plaintext logcat in every debug build. No body-field
        // redaction exists in this codebase yet. Follow-up task: add body-field redaction for
        // fields named "token" in NetworkClientImpl, or reduce logging to HEADERS for requests
        // to hive.sphinx.chat.
        return networkRelayCall.post(
            url = ENDPOINT_AUTH,
            responseJsonClass = HiveTokenDto::class.java,
            requestBodyJsonClass = Map::class.java,
            requestBody = mapOf(
                "token" to token,
                "pubkey" to pubkey,
                "timestamp" to timestamp
            ),
        )
    }
}
