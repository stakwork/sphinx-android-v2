package chat.sphinx.dashboard.ui

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.feed.FeedId
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import io.matthewnelson.android_feature_navigation.requests.PopBackStack
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_navigation.BaseNavigationDriver
import io.matthewnelson.concept_navigation.NavigationRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceDetailViewModelTest {

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
    fun `popBackStack delegates to dashboardNavigator`() = runTest {
        val driver = FakeNavDriver()
        val viewModel = WorkspaceDetailViewModel(
            dispatchers = dispatchers,
            handler = SavedStateHandle(),
            dashboardNavigator = FakeDashboardNavigator(driver),
        )

        viewModel.popBackStack()

        assertEquals(1, driver.requests.size)
        assertTrue(driver.requests.first() is PopBackStack)
    }

    private class FakeNavDriver : BaseNavigationDriver<NavController>() {
        val requests = mutableListOf<NavigationRequest<NavController>>()
        override suspend fun submitNavigationRequest(request: NavigationRequest<NavController>) {
            requests.add(request)
        }
    }

    private class FakeDashboardNavigator(
        navigationDriver: BaseNavigationDriver<NavController>,
    ) : DashboardNavigator(navigationDriver) {
        override suspend fun toChatContact(chatId: ChatId?, contactId: ContactId) = Unit
        override suspend fun toChatGroup(chatId: ChatId) = Unit
        override suspend fun toChatTribe(chatId: ChatId) = Unit
        override suspend fun toWorkspaceDetail(workspaceId: String, workspaceName: String) = Unit
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
}
