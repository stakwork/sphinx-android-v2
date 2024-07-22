package chat.sphinx.feature_network_query_chat

import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import chat.sphinx.concept_network_query_chat.model.*
import chat.sphinx.concept_network_query_chat.model.feed.FeedDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_chat.ChatHost
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import kotlinx.coroutines.flow.Flow

class NetworkQueryChatImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryChat() {

    companion object {
        private const val GET_TRIBE_INFO_URL_TEST = "http://%s/tribes/%s"
        private const val GET_TRIBE_INFO_URL_PRODUCTION = "https://%s/tribes/%s"
        private const val GET_FEED_CONTENT_URL = "https://%s/feed?url=%s&fulltext=true"
        const val TEST_V2_TRIBES_SERVER = "75.101.247.127:8801"
    }

    override fun getTribeInfo(
        host: ChatHost,
        tribePubKey: LightningNodePubKey,
        isProductionEnvironment: Boolean
    ): Flow<LoadResponse<NewTribeDto, ResponseError>> =
        networkRelayCall.get(
            url = String.format(
                if (isProductionEnvironment) GET_TRIBE_INFO_URL_PRODUCTION else GET_TRIBE_INFO_URL_TEST,
                if (isProductionEnvironment) host.value else TEST_V2_TRIBES_SERVER,
                tribePubKey.value
            ),
            responseJsonClass = NewTribeDto::class.java,
        )

    override fun getFeedContent(
        host: ChatHost,
        feedUrl: FeedUrl,
        chatUUID: ChatUUID?,
    ): Flow<LoadResponse<FeedDto, ResponseError>> =
        networkRelayCall.get(
            url = if (chatUUID != null) {
                "${String.format(GET_FEED_CONTENT_URL, host.value, feedUrl.value)}&uuid=${chatUUID.value}"
            } else {
                String.format(GET_FEED_CONTENT_URL, host.value, feedUrl.value)
            },
            responseJsonClass = FeedDto::class.java,
        )
}
