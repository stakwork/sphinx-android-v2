package chat.sphinx.feature_network_query_discover_tribes

import chat.sphinx.concept_network_query_chat.model.NewTribeDto
import chat.sphinx.concept_network_query_discover_tribes.NetworkQueryDiscoverTribes
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow

class NetworkQueryDiscoverTribesImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryDiscoverTribes() {

    companion object {
        private const val ENDPOINT_OFFSET_TRIBES = "/tribes?limit=%s&page=%s&sortBy=member_count"
        private const val ENDPOINT_TRIBES = "/tribes"
        private const val ENDPOINT_SEARCH_TRIBES = "&search=%s"
        private const val ENDPOINT_TAGS_TRIBES = "&tags=%s"
        private const val TEST_TRIBE_SERVER_PORT = 8801
    }

    override fun getAllDiscoverTribes(
        page: Int,
        itemsPerPage: Int,
        searchTerm: String?,
        tags: String?,
        tribeServer: String?
    ): Flow<LoadResponse<List<NewTribeDto>, ResponseError>> {
        val protocol = if (tribeServer?.contains(TEST_TRIBE_SERVER_PORT.toString()) == true) "http://" else "https://"
        var url = protocol + tribeServer + ENDPOINT_TRIBES

        searchTerm?.let {
            if (it.isNotEmpty()){
                url += String.format(ENDPOINT_SEARCH_TRIBES, it)
            }
        }

        tags?.let {
            if (it.isNotEmpty()) {
                url += String.format(ENDPOINT_TAGS_TRIBES, it)
            }
        }

        return networkRelayCall.getList(
            url = url,
            responseJsonClass = NewTribeDto::class.java
        )
    }}