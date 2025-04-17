package chat.sphinx.feature_network_query_podcast_search

import chat.sphinx.concept_network_query_feed_search.NetworkQueryFeedSearch
import chat.sphinx.concept_network_query_feed_search.model.CreateProjectResponseDto
import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeDetailsDto
import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeResponseDto
import chat.sphinx.concept_network_query_feed_search.model.FeedSearchResultDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.feed.FeedType
import chat.sphinx.wrapper_common.feed.isPodcast
import chat.sphinx.wrapper_common.time
import chat.sphinx.wrapper_feed.FeedReferenceId
import chat.sphinx.wrapper_feed.FeedTitle
import chat.sphinx.wrapper_podcast.ChapterResponseDto
import chat.sphinx.wrapper_podcast.PodcastEpisode
import kotlinx.coroutines.flow.Flow

class NetworkQueryFeedSearchImpl(
    private val networkRelayCall: NetworkRelayCall,
): NetworkQueryFeedSearch() {

    companion object {
        private const val TRIBES_DEFAULT_SERVER_URL = "https://people.sphinx.chat"
        private const val GRAPH_MINDSET_BASE_URL = "https://graphmindset.sphinx.chat"
        private const val GRAPH_MINDSET_ADD_NODE_URL = "https://graphmindset.sphinx.chat/api/add_node?sig=&msg="
        private const val ENDPOINT_GET_CHAPTERS = "$GRAPH_MINDSET_BASE_URL/api/graph/subgraph?start_node=%s&include_properties=true&depth=1&node_type=%%5B%%27Chapter%%27%%5D"
        private const val ENDPOINT_STAKWORK_PROJECT = "https://api.stakwork.com/api/v1/projects"

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
            mediaType = "application/json",
            accept400AsSuccess = true
        )
    }

    override fun createStakworkProject(
        podcastEpisode: PodcastEpisode,
        feedTitle: FeedTitle,
        workflowId: Int,
        token: String,
        referenceId: FeedReferenceId
    ): Flow<LoadResponse<CreateProjectResponseDto, ResponseError>> {

        val mediaUrl = podcastEpisode.enclosureUrl.value
        val requestBody = mapOf(
            "name" to mediaUrl,
            "workflow_id" to workflowId,
            "workflow_params" to mapOf(
                "set_var" to mapOf(
                    "attributes" to mapOf(
                        "vars" to mapOf(
                            "media_url" to mediaUrl,
                            "ref_id" to referenceId.value,
                            "episode_publish_date" to (podcastEpisode.date?.time?.div(1000) ?: 0),
                            "episode_title" to podcastEpisode.title.value,
                            "episode_thumbnail_url" to (podcastEpisode.image?.value ?: ""),
                            "show_title" to (feedTitle.value)
                        )
                    )
                )
            )
        )

        return networkRelayCall.post(
            url = ENDPOINT_STAKWORK_PROJECT,
            responseJsonClass = CreateProjectResponseDto::class.java,
            requestBodyJsonClass = Map::class.java,
            requestBody = requestBody,
            mediaType = "application/json",
            headers = mapOf("Authorization" to "Bearer ${token}")
        )
    }

    override fun getEpisodeNodeDetails(referenceId: FeedReferenceId): Flow<LoadResponse<EpisodeNodeDetailsDto, ResponseError>> {
        val url = "$GRAPH_MINDSET_BASE_URL/api/node/${referenceId.value}"

        return networkRelayCall.get(
            url = url,
            responseJsonClass = EpisodeNodeDetailsDto::class.java
        )
    }

    override fun getChaptersData(referenceId: FeedReferenceId): Flow<LoadResponse<ChapterResponseDto, ResponseError>> {
        return networkRelayCall.get(
            url = String.format(ENDPOINT_GET_CHAPTERS, referenceId.value),
            responseJsonClass = ChapterResponseDto::class.java
        )
    }

}
