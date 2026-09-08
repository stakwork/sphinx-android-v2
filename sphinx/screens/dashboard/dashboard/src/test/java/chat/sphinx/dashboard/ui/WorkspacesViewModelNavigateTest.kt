package chat.sphinx.dashboard.ui

import androidx.navigation.NavController
import chat.sphinx.concept_paging.PageSourceWrapper
import chat.sphinx.concept_repository_dashboard.DashboardItem
import chat.sphinx.concept_repository_dashboard.model.Workspace
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.dashboard.InviteId
import chat.sphinx.wrapper_common.feed.FeedId
import chat.sphinx.wrapper_common.feed.FeedType
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.message.MessageId
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_feed.Feed
import chat.sphinx.wrapper_invite.Invite
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_podcast.FeedRecommendation
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_navigation.BaseNavigationDriver
import io.matthewnelson.concept_navigation.NavigationRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspacesViewModelNavigateTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `navigateToWorkspaceDetail calls dashboardNavigator with id and name`() = runTest {
        val navigator = FakeDashboardNavigator()
        val viewModel = WorkspacesViewModel(
            dispatchers = dispatchers,
            repositoryDashboard = UnusedRepositoryDashboard(),
            dashboardNavigator = navigator,
        )

        viewModel.navigateToWorkspaceDetail("ws-7", "Hive Workspace")

        assertEquals("ws-7", navigator.lastWorkspaceId)
        assertEquals("Hive Workspace", navigator.lastWorkspaceName)
        assertEquals(1, navigator.toWorkspaceDetailCount)
    }

    private class FakeNavDriver : BaseNavigationDriver<NavController>() {
        override suspend fun submitNavigationRequest(request: NavigationRequest<NavController>) {}
    }

    private class FakeDashboardNavigator : DashboardNavigator(FakeNavDriver()) {
        var lastWorkspaceId: String? = null
        var lastWorkspaceName: String? = null
        var toWorkspaceDetailCount: Int = 0

        override suspend fun toChatContact(chatId: ChatId?, contactId: ContactId) = Unit
        override suspend fun toChatGroup(chatId: ChatId) = Unit
        override suspend fun toChatTribe(chatId: ChatId) = Unit
        override suspend fun toWorkspaceDetail(workspaceId: String, workspaceName: String) {
            lastWorkspaceId = workspaceId
            lastWorkspaceName = workspaceName
            toWorkspaceDetailCount++
        }
        override suspend fun toJoinTribeDetail(tribeLink: TribeJoinLink) = Unit
        override suspend fun toQRCodeDetail(qrText: String, viewTitle: String, description: String?) = Unit
        override suspend fun toAddContactDetail(
            pubKey: LightningNodePubKey,
            routeHint: LightningRouteHint?,
        ) = Unit
        override suspend fun toVideoWatchScreen(chatId: ChatId, feedId: FeedId, feedUrl: FeedUrl) = Unit
        override suspend fun toWebViewDetail(
            chatId: ChatId?,
            title: String,
            url: FeedUrl,
            feedId: FeedId?,
            feedItemId: FeedId?,
        ) = Unit
        override suspend fun toNewsletterDetail(chatId: ChatId, feedUrl: FeedUrl) = Unit
        override suspend fun toPodcastPlayerScreen(
            chatId: ChatId,
            feedId: FeedId,
            feedUrl: FeedUrl,
            fromDownloadedSection: Boolean,
        ) = Unit
        override suspend fun toCommonPlayerScreen(podcastId: FeedId, episodeId: FeedId) = Unit
    }

    // Unused by navigateToWorkspaceDetail; only present so the real VM can be constructed.
    private class UnusedRepositoryDashboard : RepositoryDashboardAndroid<Any> {
        override suspend fun getAccountBalance(): StateFlow<NodeBalance?> = MutableStateFlow(null)
        override val getAllChats: Flow<List<Chat>> = emptyFlow()
        override val getAllContactChats: Flow<List<Chat>> = emptyFlow()
        override val getAllTribeChats: Flow<List<Chat>> = emptyFlow()
        override fun getConversationByContactId(contactId: ContactId): Flow<Chat?> = emptyFlow()
        override fun getUnseenMessagesByChatId(chatId: ChatId): Flow<Long?> = emptyFlow()
        override fun getUnseenMentionsByChatId(chatId: ChatId): Flow<Long?> = emptyFlow()
        override fun getUnseenMessagesByChatIdCache(chatId: ChatId): Flow<Long?> = emptyFlow()
        override fun getUnseenMentionsByChatIdCache(chatId: ChatId): Flow<Long?> = emptyFlow()
        override fun getUnseenActiveConversationMessagesCount(): Flow<Long?> = emptyFlow()
        override fun getUnseenTribeMessagesCount(): Flow<Long?> = emptyFlow()
        override fun getUnseenReceivedMessages(): Flow<List<Message>?> = emptyFlow()
        override fun getUnseenReceivedMentions(): Flow<List<Message>?> = emptyFlow()
        override val accountOwner: StateFlow<Contact?> = MutableStateFlow(null)
        override val getAllNotBlockedContacts: Flow<List<Contact>> = emptyFlow()
        override val getAllInvites: Flow<List<Invite>> = emptyFlow()
        override fun getContactById(contactId: ContactId): Flow<Contact?> = emptyFlow()
        override var updatedContactIds: MutableList<ContactId> = mutableListOf()
        override fun getMessageById(messageId: MessageId): Flow<Message?> = emptyFlow()
        override fun getInviteById(inviteId: InviteId): Flow<Invite?> = emptyFlow()
        override suspend fun payForInvite(invite: Invite) = Unit
        override suspend fun deleteInviteAndContact(inviteString: String) = Unit
        override fun getAllFeedsOfType(feedType: FeedType): Flow<List<Feed>> = emptyFlow()
        override fun getAllSubscribedFeedsOfType(feedType: FeedType): Flow<List<Feed>> = emptyFlow()
        override fun getAllFeeds(): Flow<List<Feed>> = emptyFlow()
        override fun getAllSubscribedFeeds(): Flow<List<Feed>> = emptyFlow()
        override fun getRecommendedFeeds(): Flow<List<FeedRecommendation>> = emptyFlow()
        override suspend fun authorizeStakwork(
            host: String,
            id: String,
            challenge: String,
        ): Response<String, ResponseError> = Response.Error(ResponseError("unused"))
        override suspend fun redeemSats(
            host: String,
            token: String,
        ): Response<Boolean, ResponseError> = Response.Error(ResponseError("unused"))
        override suspend fun savePeopleProfile(
            body: String,
        ): Response<Boolean, ResponseError> = Response.Error(ResponseError("unused"))
        override suspend fun deletePeopleProfile(
            body: String,
        ): Response<Boolean, ResponseError> = Response.Error(ResponseError("unused"))
        override val networkRefreshBalance: MutableStateFlow<Long?> = MutableStateFlow(null)
        override suspend fun didCancelRestore() = Unit
        override suspend fun clearDatabase() = Unit
        override suspend fun fetchWorkspaces(): Response<List<Workspace>, ResponseError> =
            Response.Success(emptyList())
        override suspend fun fetchWorkspaceImageUrl(slug: String): Response<String, ResponseError> =
            Response.Error(ResponseError("unused"))
        override suspend fun getDashboardItemPagingSource(): PageSourceWrapper<Long, DashboardItem, Any> {
            error("unused")
        }
    }
}
