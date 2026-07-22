package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.feature_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryHiveImpl(
    private val networkRelayCall: NetworkRelayCall,
) : NetworkQueryHive() {

    companion object {
        const val HIVE_AUTH_URL = "https://hive.sphinx.chat/api/auth/sphinx/token"
    }

    override fun authenticateWithHive(
        signedToken: String,
        pubkey: String,
        timestamp: String
    ): Flow<LoadResponse<HiveAuthResponseDto, ResponseError>> =
        networkRelayCall.post(
            url = HIVE_AUTH_URL,
            responseJsonClass = HiveAuthResponseDto::class.java,
            requestBodyJsonClass = HiveAuthRequestDto::class.java,
            requestBody = HiveAuthRequestDto(
                signedToken = signedToken,
                pubkey = pubkey,
                timestamp = timestamp
            ),
        )
}
