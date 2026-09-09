package chat.sphinx.dashboard.ui

import androidx.navigation.NavController
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.feed.FeedId
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import io.matthewnelson.concept_navigation.BaseNavigationDriver
import io.matthewnelson.concept_navigation.NavigationRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceFeaturesViewModelNavigateTest {

    @Test
    fun `toFeaturePlan is invoked with feature id and title`() = runTest {
        val navigator = FakeDashboardNavigator()

        navigator.toFeaturePlan("feat-42", "Build Features tab")

        assertEquals("feat-42", navigator.lastFeatureId)
        assertEquals("Build Features tab", navigator.lastFeatureTitle)
        assertEquals(1, navigator.toFeaturePlanCount)
    }

    private class FakeNavDriver : BaseNavigationDriver<NavController>() {
        override suspend fun submitNavigationRequest(request: NavigationRequest<NavController>) {}
    }

    private class FakeDashboardNavigator : DashboardNavigator(FakeNavDriver()) {
        var lastFeatureId: String? = null
        var lastFeatureTitle: String? = null
        var toFeaturePlanCount: Int = 0

        override suspend fun toChatContact(chatId: ChatId?, contactId: ContactId) = Unit
        override suspend fun toChatGroup(chatId: ChatId) = Unit
        override suspend fun toChatTribe(chatId: ChatId) = Unit
        override suspend fun toWorkspaceDetail(workspaceId: String, workspaceName: String) = Unit
        override suspend fun toFeaturePlan(featureId: String, featureTitle: String) {
            lastFeatureId = featureId
            lastFeatureTitle = featureTitle
            toFeaturePlanCount++
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
}
