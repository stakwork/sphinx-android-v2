package chat.sphinx.dashboard.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import chat.sphinx.dashboard.R

class WorkspaceDetailFragmentsAdapter(
    private val fragment: Fragment,
    val workspaceId: String,
    // Passed only to the Pods tab; Features/Tasks continue to use workspaceId.
    val workspaceSlug: String? = null,
) : FragmentStateAdapter(fragment) {

    companion object {
        const val FEATURES_TAB_POSITION = 0
        const val TASKS_TAB_POSITION = 1
        const val GRAPH_CHAT_TAB_POSITION = 2
        const val PODS_TAB_POSITION = 3

        val TAB_TITLES = arrayOf(
            R.string.workspace_detail_features_tab_name,
            R.string.workspace_detail_tasks_tab_name,
            R.string.workspace_detail_graph_chat_tab_name,
            R.string.workspace_detail_pods_tab_name,
        )

        fun fragmentClassForPosition(position: Int): Class<out Fragment> {
            return when (position) {
                FEATURES_TAB_POSITION -> WorkspaceFeaturesFragment::class.java
                TASKS_TAB_POSITION -> WorkspaceTasksFragment::class.java
                PODS_TAB_POSITION -> WorkspacePodsFragment::class.java
                else -> WorkspaceDetailStubFragment::class.java
            }
        }
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            FEATURES_TAB_POSITION -> WorkspaceFeaturesFragment.newInstance(workspaceId)
            TASKS_TAB_POSITION -> WorkspaceTasksFragment.newInstance(workspaceId)
            PODS_TAB_POSITION -> WorkspacePodsFragment.newInstance(workspaceId, workspaceSlug)
            else -> {
                val titleRes = TAB_TITLES.getOrElse(position) { TAB_TITLES[FEATURES_TAB_POSITION] }
                WorkspaceDetailStubFragment.newInstance(fragment.getString(titleRes))
            }
        }
    }

    fun getPageTitle(position: Int): CharSequence {
        return fragment.getString(TAB_TITLES[position])
    }

    override fun getItemCount(): Int {
        return TAB_TITLES.size
    }
}
