package chat.sphinx.dashboard.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.ui.feed.FeedFragment
import chat.sphinx.wrapper_chat.ChatType

/**
 * A [FragmentPagerAdapter] that returns a fragment corresponding to
 * one of the sections/tabs/pages.
 */
class DashboardFragmentsAdapter(
    private val fragment: Fragment
) : FragmentStateAdapter(fragment) {

    companion object {
        const val TRIBES_TAB_POSITION  = 0   // was 2
        const val FRIENDS_TAB_POSITION = 1   // unchanged
        const val FEED_TAB_POSITION    = 2   // was 0
        const val FIRST_INIT           = 3

        const val TRIBES_ITEM_ID  = 100L
        const val FRIENDS_ITEM_ID = 101L
        const val FEED_ITEM_ID    = 102L

        val TAB_TITLES = arrayOf(
            R.string.dashboard_tribes_tab_name,   // position 0
            R.string.dashboard_friends_tab_name,  // position 1
            R.string.dashboard_feed_tab_name,     // position 2
        )

        fun itemIdForPosition(position: Int): Long = when (position) {
            TRIBES_TAB_POSITION  -> TRIBES_ITEM_ID
            FRIENDS_TAB_POSITION -> FRIENDS_ITEM_ID
            FEED_TAB_POSITION    -> FEED_ITEM_ID
            else                 -> position.toLong()
        }

        fun containsItemId(itemId: Long): Boolean = itemId in TRIBES_ITEM_ID..FEED_ITEM_ID
    }

    override fun getItemId(position: Int): Long = itemIdForPosition(position)

    override fun containsItem(itemId: Long): Boolean = containsItemId(itemId)

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            FEED_TAB_POSITION -> {
                FeedFragment.newInstance()
            }
            FRIENDS_TAB_POSITION -> {
                ChatListFragment.newInstance(
                    chatListType = ChatType.Conversation
                )
            }
            TRIBES_TAB_POSITION -> {
                ChatListFragment.newInstance(
                    chatListType = ChatType.Tribe
                )
            }
            else ->  {
                ChatListFragment.newInstance()
            }
        }
    }

    fun getPageTitle(position: Int): CharSequence? {
        return fragment.resources.getString(TAB_TITLES[position])
    }

    override fun getItemCount(): Int {
        return TAB_TITLES.size
    }
}
