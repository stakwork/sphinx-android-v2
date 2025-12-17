package chat.sphinx.feature_network_query_verify_external

import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_network_query_verify_external.model.*
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryAuthorizeExternalImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryAuthorizeExternal() {

    companion object {
        private const val ENDPOINT_VERIFY_EXTERNAL = "/verify_external"
        private const val ENDPOINT_SIGN_BASE_64 = "/signer/%s"
        private const val ENDPOINT_AUTHORIZE_EXTERNAL = "https://%s/verify/%s?token=%s"
        private const val ENDPOINT_GET_PERSON_INFO = "https://%s/person/%s"
        private const val ENDPOINT_CREATE_PROFILE = "https://%s/person?token=%s"
        private const val ENDPOINT_REDEEM_SATS = "https://%s"

        private const val ENDPOINT_SERVER_URL = "https://%s"
        private const val ENDPOINT_ASK_AUTHENTICATION = "$ENDPOINT_SERVER_URL/ask"
    }

    override fun authorizeExternal(
        host: String,
        challenge: String,
        token: String,
        info: VerifyExternalInfoDto,
    ): Flow<LoadResponse<Any, ResponseError>> =
        networkRelayCall.post(
            url = String.format(
                ENDPOINT_AUTHORIZE_EXTERNAL,
                host,
                challenge,
                token
            ),
            responseJsonClass = Any::class.java,
            requestBodyJsonClass = VerifyExternalInfoDto::class.java,
            requestBody = info,
        )

    override fun requestNewChallenge(host: String): Flow<LoadResponse<ChallengeExternalDto, ResponseError>> =
        networkRelayCall.get(
            url = String.format(ENDPOINT_ASK_AUTHENTICATION, host),
            responseJsonClass = ChallengeExternalDto::class.java,
        )

    override fun redeemSats(
        host: String,
        info: RedeemSatsDto,
    ): Flow<LoadResponse<Any, ResponseError>> =
        networkRelayCall.post(
            url = String.format(
                ENDPOINT_REDEEM_SATS,
                host
            ),
            responseJsonClass = Any::class.java,
            requestBodyJsonClass = RedeemSatsDto::class.java,
            requestBody = info,
        )

    override fun getPersonInfo(
        host: String,
        publicKey: String
    ): Flow<LoadResponse<PersonInfoDto, ResponseError>> =
        networkRelayCall.get(
            url = String.format(
                ENDPOINT_GET_PERSON_INFO,
                host,
                publicKey
            ),
            responseJsonClass = PersonInfoDto::class.java,
        )

    override fun createPeopleProfile(
        host: String,
        person: PersonInfoDto,
        token: String
    ): Flow<LoadResponse<Any, ResponseError>> =
        networkRelayCall.post(
            url = String.format(
                ENDPOINT_CREATE_PROFILE,
                host,
                token
            ),
            responseJsonClass = Any::class.java,
            requestBodyJsonClass = PersonInfoDto::class.java,
            requestBody = person,
            accept400AsSuccess = true
        )
}
