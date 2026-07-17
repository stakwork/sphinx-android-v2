package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_call.NetworkCall
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

// Intentionally uses NetworkCall (not NetworkRelayCall): Hive is an external API
// and must never receive relay Authorization headers.
class NetworkQueryHiveImpl(
    private val networkCall: NetworkCall
) : NetworkQueryHive() {

    companion object {
        private const val HIVE_BASE_URL = "https://hive.sphinx.chat/api"
        private const val ENDPOINT_HIVE_AUTH = "$HIVE_BASE_URL/auth/sphinx/token"
    }

    override fun authenticateWithHive(
        request: HiveAuthRequestDto
    ): Flow<LoadResponse<HiveAuthResponseDto, ResponseError>> =
        networkCall.post(
            url = ENDPOINT_HIVE_AUTH,
            responseJsonClass = HiveAuthResponseDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = request,
        )
}
