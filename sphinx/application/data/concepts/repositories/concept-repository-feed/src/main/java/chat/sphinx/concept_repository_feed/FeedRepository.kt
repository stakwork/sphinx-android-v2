package chat.sphinx.concept_repository_feed

import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_chat.ChatHost
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.feed.*
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.message.MessageUUID
import chat.sphinx.wrapper_feed.*
import chat.sphinx.wrapper_podcast.Podcast
import chat.sphinx.wrapper_podcast.FeedSearchResultRow
import chat.sphinx.wrapper_podcast.PodcastEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface FeedRepository {

    val createProjectTimestamps: MutableStateFlow<MutableMap<String, Long>> // Key: episodeId, Value: timestampMillis

    fun getPodcastByChatId(chatId: ChatId): Flow<Podcast?>
    fun getPodcastById(feedId: FeedId): Flow<Podcast?>
    suspend fun checkIfEpisodeNodeExists(
        podcastEpisode: PodcastEpisode?,
        podcastTitle: FeedTitle?,
        videoId: FeedId? = null,
        workflowId: Int?,
        token: String?
    )
    suspend fun getEpisodeNodeDetails(
        podcastEpisode: PodcastEpisode?,
        podcastTitle: FeedTitle?,
        referenceId: FeedReferenceId,
        workflowId: Int?,
        token: String?
    )
    suspend fun getChaptersData(
        podcastEpisode: PodcastEpisode?,
        podcastTitle: FeedTitle?,
        referenceId: FeedReferenceId,
        id: FeedId,
        workflowId: Int?,
        token: String?
    )
    fun searchFeedsBy(
        searchTerm: String,
        feedType: FeedType?,
    ): Flow<List<FeedSearchResultRow>>

    suspend fun updateFeedContent(
        chatId: ChatId,
        host: ChatHost,
        feedUrl: FeedUrl,
        searchResultDescription: FeedDescription? = null,
        searchResultImageUrl: PhotoUrl? = null,
        chatUUID: ChatUUID?,
        subscribed: Subscribed,
        currentItemId: FeedId? = null,
        delay: Long = 500L
    ): Response<FeedId, ResponseError>

    fun getFeedByChatId(chatId: ChatId): Flow<Feed?>
    fun getFeedById(feedId: FeedId): Flow<Feed?>
    fun getFeedItemById(feedItemId: FeedId): Flow<FeedItem?>
    fun getRecommendationFeedItemById(feedItemId: FeedId): Flow<FeedItem?>

    fun getAllDownloadedFeedItems(): Flow<List<FeedItem>>

    fun getDownloadedFeedItemsByFeedId(feedId: FeedId): Flow<List<FeedItem>>

    fun getFeedForLink(link: FeedItemLink): Flow<Feed?>

    fun updatePlayedMark(feedItemId: FeedId, played: Boolean)

    fun updateLastPlayed(feedId: FeedId)

    fun getPlayedMark(feedItemId: FeedId): Flow<Boolean?>

    fun setRecommendationsToggle(enabled: Boolean)

    val recommendationsToggleStateFlow: MutableStateFlow<Boolean>
    val recommendationsPodcast: MutableStateFlow<Podcast?>

    suspend fun toggleFeedSubscribeState(feedId: FeedId, currentSubscribeState: Subscribed)

    suspend fun updateChatContentSeenAt(chatId: ChatId)

    fun streamFeedPayments(
        chatId: ChatId,
        feedId: String,
        feedItemId: String,
        currentTime: Long,
        amount: Sat?,
        playerSpeed: FeedPlayerSpeed?,
        destinations: List<FeedDestination>,
        clipMessageUUID: MessageUUID? = null,
        routerUrl: String?,
        routerPubKey: String?
    )

    fun updateContentFeedStatus(
        feedId: FeedId,
        itemId: FeedId
    )

    fun updateContentFeedStatus(
        feedId: FeedId,
        feedUrl: FeedUrl,
        subscriptionStatus: Subscribed,
        chatId: ChatId?,
        itemId: FeedId?,
        satsPerMinute: Sat?,
        playerSpeed: FeedPlayerSpeed?,
        shouldSync: Boolean = false
    )

    fun updateContentEpisodeStatus(
        feedId: FeedId,
        itemId: FeedId,
        duration: FeedItemDuration,
        currentTime: FeedItemDuration,
        played: Boolean = false,
        shouldSync: Boolean = false
    )

    fun restoreContentFeedStatuses(
        playingPodcastId: String? = null,
        playingEpisodeId: String? = null,
        durationRetrieverHandler: ((url: String) -> Long)? = null
    )

    fun restoreContentFeedStatusByFeedId(
        feedId: FeedId,
        playingPodcastId: String? = null,
        playingEpisodeId: String? = null
    )

    fun saveContentFeedStatuses()
}