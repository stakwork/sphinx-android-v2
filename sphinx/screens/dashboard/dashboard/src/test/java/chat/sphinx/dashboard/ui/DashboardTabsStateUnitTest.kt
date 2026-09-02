package chat.sphinx.dashboard.ui

import chat.sphinx.dashboard.ui.viewstates.DashboardTabsViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DashboardViewModel] requires a large Dagger-provided dependency graph
 * (repositories, navigators, network queries, etc.) that cannot be
 * instantiated in a plain JVM unit test. To exercise the tab-state logic
 * described by this ticket without standing up that graph, this class
 * mirrors the exact branch logic of [DashboardViewModel.updateTabsState]
 * and [DashboardViewModel.getCurrentPagePosition] against the real
 * [DashboardTabsViewState] and [DashboardFragmentsAdapter] tab-position
 * constants.
 *
 * If either of the mirrored methods on [DashboardViewModel] is changed,
 * this shim (and these tests) should be updated to match.
 */
private class TestableDashboardTabsState {

    var currentState: DashboardTabsViewState = DashboardTabsViewState.Idle
        private set

    /** Mirrors [DashboardViewModel.updateTabsState]. */
    fun updateTabsState(
        feedActive: Boolean? = null,
        friendsActive: Boolean? = null,
        tribesActive: Boolean? = null,
        friendsBadgeVisible: Boolean? = null,
        tribesBadgeVisible: Boolean? = null,
    ) {
        val current = currentState

        currentState = if (current is DashboardTabsViewState.TabsState) {
            DashboardTabsViewState.TabsState(
                feedActive = feedActive ?: current.feedActive,
                friendsActive = friendsActive ?: current.friendsActive,
                tribesActive = tribesActive ?: current.tribesActive,
                friendsBadgeVisible = friendsBadgeVisible ?: current.friendsBadgeVisible,
                tribesBadgeVisible = tribesBadgeVisible ?: current.tribesBadgeVisible,
            )
        } else {
            DashboardTabsViewState.TabsState(
                feedActive = feedActive ?: false,
                friendsActive = friendsActive ?: false,
                tribesActive = tribesActive ?: true,
                friendsBadgeVisible = friendsBadgeVisible ?: false,
                tribesBadgeVisible = tribesBadgeVisible ?: false,
            )
        }
    }

    /** Mirrors [DashboardViewModel.getCurrentPagePosition]. */
    fun getCurrentPagePosition(): Int {
        val current = currentState

        if (current is DashboardTabsViewState.TabsState) {
            return when {
                current.feedActive -> {
                    DashboardFragmentsAdapter.FEED_TAB_POSITION
                }
                current.friendsActive -> {
                    DashboardFragmentsAdapter.FRIENDS_TAB_POSITION
                }
                current.tribesActive -> {
                    DashboardFragmentsAdapter.TRIBES_TAB_POSITION
                }
                else -> DashboardFragmentsAdapter.TRIBES_TAB_POSITION
            }
        }

        return DashboardFragmentsAdapter.FIRST_INIT
    }
}

class DashboardTabsStateUnitTest {

    @Test
    fun `tab position constants are ordered Tribes then Feed then Friends`() {
        assertEquals(0, DashboardFragmentsAdapter.TRIBES_TAB_POSITION)
        assertEquals(1, DashboardFragmentsAdapter.FEED_TAB_POSITION)
        assertEquals(2, DashboardFragmentsAdapter.FRIENDS_TAB_POSITION)
    }

    @Test
    fun `updateTabsState else-branch defaults tribesActive true and friendsActive false when no prior state exists`() {
        val tabsState = TestableDashboardTabsState()

        // No prior state exists yet (currentState is Idle), and no explicit
        // tribesActive flag is passed in - this reproduces the badge-update
        // coroutines fired from DashboardViewModel's init block landing in
        // the `else` branch before setupViewPager() has run.
        tabsState.updateTabsState(tribesActive = null)

        val resultState = tabsState.currentState
        check(resultState is DashboardTabsViewState.TabsState)

        assertTrue(resultState.tribesActive)
        assertFalse(resultState.friendsActive)
        assertFalse(resultState.feedActive)
    }

    @Test
    fun `getCurrentPagePosition fallback returns TRIBES_TAB_POSITION when no active flag is set`() {
        val tabsState = TestableDashboardTabsState()

        // A TabsState with no active flag explicitly set true should fall
        // through to the `else` branch of getCurrentPagePosition().
        tabsState.updateTabsState(
            feedActive = false,
            friendsActive = false,
            tribesActive = false,
        )

        assertEquals(
            DashboardFragmentsAdapter.TRIBES_TAB_POSITION,
            tabsState.getCurrentPagePosition()
        )
    }

    @Test
    fun `getCurrentPagePosition returns FIRST_INIT when state is Idle`() {
        val tabsState = TestableDashboardTabsState()

        assertEquals(DashboardFragmentsAdapter.FIRST_INIT, tabsState.getCurrentPagePosition())
    }
}
