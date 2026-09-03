package chat.sphinx.dashboard.ui

import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.FEED_ITEM_ID
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.FEED_TAB_POSITION
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.FRIENDS_ITEM_ID
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.FRIENDS_TAB_POSITION
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.TAB_TITLES
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.TRIBES_ITEM_ID
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.TRIBES_TAB_POSITION
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.containsItemId
import chat.sphinx.dashboard.ui.DashboardFragmentsAdapter.Companion.itemIdForPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DashboardFragmentsAdapterTest {

    @Test
    fun `tab strip order is Tribes then Friends then Feed`() {
        assertEquals(0, TRIBES_TAB_POSITION)
        assertEquals(1, FRIENDS_TAB_POSITION)
        assertEquals(2, FEED_TAB_POSITION)

        assertEquals(R.string.dashboard_tribes_tab_name, TAB_TITLES[TRIBES_TAB_POSITION])
        assertEquals(R.string.dashboard_friends_tab_name, TAB_TITLES[FRIENDS_TAB_POSITION])
        assertEquals(R.string.dashboard_feed_tab_name, TAB_TITLES[FEED_TAB_POSITION])
    }

    @Test
    fun `getItemId returns distinct stable IDs for each position`() {
        val tribesId = itemIdForPosition(TRIBES_TAB_POSITION)
        val friendsId = itemIdForPosition(FRIENDS_TAB_POSITION)
        val feedId = itemIdForPosition(FEED_TAB_POSITION)

        assertEquals(TRIBES_ITEM_ID, tribesId)
        assertEquals(FRIENDS_ITEM_ID, friendsId)
        assertEquals(FEED_ITEM_ID, feedId)

        assertNotEquals(tribesId, friendsId)
        assertNotEquals(tribesId, feedId)
        assertNotEquals(friendsId, feedId)

        assertEquals(3, setOf(tribesId, friendsId, feedId).size)
    }

    @Test
    fun `containsItem accepts only the stable dashboard tab IDs`() {
        assertTrue(containsItemId(TRIBES_ITEM_ID))
        assertTrue(containsItemId(FRIENDS_ITEM_ID))
        assertTrue(containsItemId(FEED_ITEM_ID))

        assertFalse(containsItemId(0L))
        assertFalse(containsItemId(99L))
        assertFalse(containsItemId(103L))
    }
}
