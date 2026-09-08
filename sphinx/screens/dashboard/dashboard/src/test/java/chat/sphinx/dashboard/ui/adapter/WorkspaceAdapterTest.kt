package chat.sphinx.dashboard.ui.adapter

import chat.sphinx.concept_repository_dashboard.model.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Mirrors [WorkspaceAdapter.WorkspaceViewHolder.bind]'s click wiring
 * (`binding.root.setOnClickListener { onWorkspaceClicked(workspace) }`)
 * without inflating Android views.
 */
private class TestableWorkspaceRow(
    private val onWorkspaceClicked: (Workspace) -> Unit,
) {
    fun bind(workspace: Workspace): () -> Unit = {
        onWorkspaceClicked(workspace)
    }
}

class WorkspaceAdapterTest {

    private fun makeWorkspace(
        id: String = "ws-1",
        name: String = "Acme Workspace",
    ) = Workspace(
        id = id,
        name = name,
        logoUrl = null,
        userRole = "admin",
        memberCount = 3,
        slug = "acme",
    )

    @Test
    fun `clicking a bound row invokes onWorkspaceClicked with that workspace`() {
        var clicked: Workspace? = null
        val row = TestableWorkspaceRow { clicked = it }
        val workspace = makeWorkspace(id = "ws-42", name = "Hive")

        val onClick = row.bind(workspace)
        onClick()

        assertNotNull(clicked)
        assertEquals("ws-42", clicked?.id)
        assertEquals("Hive", clicked?.name)
    }
}
