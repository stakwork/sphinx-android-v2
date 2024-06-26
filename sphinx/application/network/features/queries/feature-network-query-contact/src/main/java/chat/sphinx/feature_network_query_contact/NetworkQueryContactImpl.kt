package chat.sphinx.feature_network_query_contact

import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_network_query_contact.model.*
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.feature_network_query_contact.model.*
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_relay.RelayUrl
import kotlinx.coroutines.flow.Flow

class NetworkQueryContactImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryContact() {

    companion object {
        private const val ENDPOINT_HAS_ADMIN = "/has_admin"
        private const val ENDPOINT_PRODUCTION_CONFIG = "https://config.config.sphinx.chat/api/config/bitcoin"
        private const val ENDPOINT_TEST_CONFIG = "https://config.config.sphinx.chat/api/config/regtest"
    }

    ///////////
    /// GET ///
    ///////////

    override fun hasAdmin(
        url: RelayUrl
    ): Flow<LoadResponse<Any, ResponseError>> =
        networkRelayCall.get(
            url = "${url.value}$ENDPOINT_HAS_ADMIN",
            responseJsonClass = HasAdminRelayResponse::class.java,
        )

    override fun getAccountConfig(isProductionEnvironment: Boolean): Flow<LoadResponse<AccountConfigV2, ResponseError>> =
        networkRelayCall.get(
            if (isProductionEnvironment) ENDPOINT_PRODUCTION_CONFIG else ENDPOINT_TEST_CONFIG,
            responseJsonClass = AccountConfigV2::class.java
        )
}
