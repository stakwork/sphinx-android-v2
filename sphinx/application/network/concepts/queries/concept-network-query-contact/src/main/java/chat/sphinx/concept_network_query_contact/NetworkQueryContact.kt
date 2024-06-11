package chat.sphinx.concept_network_query_contact

import chat.sphinx.concept_network_query_contact.model.*
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_relay.RelayUrl
import kotlinx.coroutines.flow.Flow

abstract class NetworkQueryContact {

    ///////////
    /// GET ///
    ///////////

    abstract fun hasAdmin(
        url: RelayUrl
    ): Flow<LoadResponse<Any, ResponseError>>

    abstract fun getAccountConfig(): Flow<LoadResponse<AccountConfigV2, ResponseError>>
}
