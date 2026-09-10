package chat.sphinx.dashboard.ui

import chat.sphinx.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceDetailFragmentsAdapterTest {

    @Test
    fun `features tab receives a workspaceId constructor argument`() {
        assertEquals("arg_workspace_id", WorkspaceFeaturesFragment.ARG_WORKSPACE_ID)
    }

    @Test
    fun `tasks tab receives a workspaceId constructor argument`() {
        assertEquals("arg_workspace_id", WorkspaceTasksFragment.ARG_WORKSPACE_ID)
    }

    @Test
    fun `position 0 is WorkspaceFeaturesFragment and Tasks is WorkspaceTasksFragment`() {
        assertEquals(
            WorkspaceFeaturesFragment::class.java,
            WorkspaceDetailFragmentsAdapter.fragmentClassForPosition(
                WorkspaceDetailFragmentsAdapter.FEATURES_TAB_POSITION
            )
        )
        assertEquals(
            WorkspaceTasksFragment::class.java,
            WorkspaceDetailFragmentsAdapter.fragmentClassForPosition(
                WorkspaceDetailFragmentsAdapter.TASKS_TAB_POSITION
            )
        )
        assertEquals(
            WorkspaceDetailStubFragment::class.java,
            WorkspaceDetailFragmentsAdapter.fragmentClassForPosition(
                WorkspaceDetailFragmentsAdapter.GRAPH_CHAT_TAB_POSITION
            )
        )
        assertEquals(
            WorkspaceDetailStubFragment::class.java,
            WorkspaceDetailFragmentsAdapter.fragmentClassForPosition(
                WorkspaceDetailFragmentsAdapter.PODS_TAB_POSITION
            )
        )
    }

    @Test
    fun `tab titles map Features Tasks Graph Chat Pods with Features as default`() {
        assertEquals(4, WorkspaceDetailFragmentsAdapter.TAB_TITLES.size)
        assertEquals(0, WorkspaceDetailFragmentsAdapter.FEATURES_TAB_POSITION)
        assertEquals(1, WorkspaceDetailFragmentsAdapter.TASKS_TAB_POSITION)
        assertEquals(2, WorkspaceDetailFragmentsAdapter.GRAPH_CHAT_TAB_POSITION)
        assertEquals(3, WorkspaceDetailFragmentsAdapter.PODS_TAB_POSITION)

        assertEquals(
            R.string.workspace_detail_features_tab_name,
            WorkspaceDetailFragmentsAdapter.TAB_TITLES[WorkspaceDetailFragmentsAdapter.FEATURES_TAB_POSITION]
        )
        assertEquals(
            R.string.workspace_detail_tasks_tab_name,
            WorkspaceDetailFragmentsAdapter.TAB_TITLES[WorkspaceDetailFragmentsAdapter.TASKS_TAB_POSITION]
        )
        assertEquals(
            R.string.workspace_detail_graph_chat_tab_name,
            WorkspaceDetailFragmentsAdapter.TAB_TITLES[WorkspaceDetailFragmentsAdapter.GRAPH_CHAT_TAB_POSITION]
        )
        assertEquals(
            R.string.workspace_detail_pods_tab_name,
            WorkspaceDetailFragmentsAdapter.TAB_TITLES[WorkspaceDetailFragmentsAdapter.PODS_TAB_POSITION]
        )
    }
}
