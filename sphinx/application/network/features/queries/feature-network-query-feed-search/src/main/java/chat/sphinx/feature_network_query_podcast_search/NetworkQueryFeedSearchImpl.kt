package chat.sphinx.feature_network_query_podcast_search

import chat.sphinx.concept_network_query_feed_search.NetworkQueryFeedSearch
import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeResponseDto
import chat.sphinx.concept_network_query_feed_search.model.FeedSearchResultDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.feed.FeedType
import chat.sphinx.wrapper_common.feed.isPodcast
import chat.sphinx.wrapper_feed.FeedTitle
import chat.sphinx.wrapper_podcast.PodcastEpisode
import kotlinx.coroutines.flow.Flow

class NetworkQueryFeedSearchImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryFeedSearch() {

    companion object {
        private const val TRIBES_DEFAULT_SERVER_URL = "https://people.sphinx.chat"
        private const val GRAPH_MINDSET_BASE_URL = "https://graphmindset.sphinx.chat"
        private const val GRAPH_MINDSET_ADD_NODE_URL = "https://graphmindset.sphinx.chat/api/add_node?sig=&msg="

        private const val ENDPOINT_PODCAST_SEARCH = "$TRIBES_DEFAULT_SERVER_URL/search_podcasts?q=%s"
        private const val ENDPOINT_YOUTUBE_SEARCH = "$TRIBES_DEFAULT_SERVER_URL/search_youtube?q=%s"
        private const val ENDPOINT_FEED_RECOMMENDATIONS = "/feeds"

    }

    override fun searchFeeds(
        searchTerm: String,
        feedType: FeedType,
    ): Flow<LoadResponse<List<FeedSearchResultDto>, ResponseError>> =
        networkRelayCall.getList(   
            url = String.format(
                if (feedType.isPodcast())
                    ENDPOINT_PODCAST_SEARCH
                else
                    ENDPOINT_YOUTUBE_SEARCH, searchTerm
            ),
            responseJsonClass = FeedSearchResultDto::class.java,
        )

    override fun checkIfEpisodeNodeExists(
        episode: PodcastEpisode,
        feedTitle: FeedTitle
    ): Flow<LoadResponse<EpisodeNodeResponseDto, ResponseError>> {

        val nodeData = mapOf(
            "source_link" to episode.enclosureUrl.value,
            "date" to episode.date?.value?.time?.div(1000),
            "episode_title" to episode.title.value,
            "image_url" to episode.image?.value,
            "show_title" to feedTitle.value
        ).filterValues { it != null }

        val requestBody = mapOf(
            "node_type" to "Episode",
            "node_data" to nodeData
        )

        return networkRelayCall.post(
            url = GRAPH_MINDSET_ADD_NODE_URL,
            responseJsonClass = EpisodeNodeResponseDto::class.java,
            requestBodyJsonClass = Map::class.java,
            requestBody = requestBody,
            mediaType = "application/json"
        )
    }
}
