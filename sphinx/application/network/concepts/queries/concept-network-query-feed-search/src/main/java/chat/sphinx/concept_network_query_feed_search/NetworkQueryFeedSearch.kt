package chat.sphinx.concept_network_query_feed_search

import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeResponseDto
import chat.sphinx.concept_network_query_feed_search.model.FeedSearchResultDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_common.feed.FeedType
import chat.sphinx.wrapper_feed.FeedReferenceId
import chat.sphinx.wrapper_feed.FeedTitle
import chat.sphinx.wrapper_podcast.ChapterResponseDto
import chat.sphinx.wrapper_podcast.PodcastEpisode
import kotlinx.coroutines.flow.Flow

abstract class NetworkQueryFeedSearch {

    ///////////
    /// GET ///
    ///////////
    abstract fun searchFeeds(
        searchTerm: String,
        feedType: FeedType,
    ): Flow<LoadResponse<List<FeedSearchResultDto>, ResponseError>>

    abstract fun checkIfEpisodeNodeExists(episode: PodcastEpisode, feedTitle: FeedTitle): Flow<LoadResponse<EpisodeNodeResponseDto, ResponseError>>

    abstract fun getChaptersData(referenceId: FeedReferenceId): Flow<LoadResponse<ChapterResponseDto, ResponseError>>

}