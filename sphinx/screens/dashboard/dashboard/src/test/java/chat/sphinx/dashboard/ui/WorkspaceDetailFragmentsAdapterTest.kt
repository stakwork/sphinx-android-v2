package chat.sphinx.dashboard.ui

import chat.sphinx.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceDetailFragmentsAdapterTest {

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
