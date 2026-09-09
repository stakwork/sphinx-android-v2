package chat.sphinx.dashboard.ui.adapter

import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Mirrors [HiveFeatureAdapter.HiveFeatureViewHolder.bind] click/edit/delete
 * wiring without inflating Android views.
 */
private class TestableHiveFeatureRow(
    private val onFeatureClicked: (HiveFeature) -> Unit,
    private val onEditClicked: (HiveFeature) -> Unit,
    private val onDeleteClicked: (HiveFeature) -> Unit,
) {
    var title: String? = null
    var status: String? = null
    var priority: String? = null

    fun bind(feature: HiveFeature): BoundActions {
        title = feature.title
        status = feature.status
        priority = feature.priority
        return BoundActions(
            onClick = { onFeatureClicked(feature) },
            onEdit = { onEditClicked(feature) },
            onDelete = { onDeleteClicked(feature) },
        )
    }

    class BoundActions(
        val onClick: () -> Unit,
        val onEdit: () -> Unit,
        val onDelete: () -> Unit,
    )
}

class HiveFeatureAdapterTest {

    private fun makeFeature(
        id: String = "feat-1",
        title: String = "Build Features tab",
        status: String? = "IN_PROGRESS",
        priority: String? = "HIGH",
    ) = HiveFeature(
        id = id,
        title = title,
        status = status,
        priority = priority,
    )

    @Test
    fun `binding exposes title status and priority`() {
        val row = TestableHiveFeatureRow({}, {}, {})
        val feature = makeFeature(title = "Plan chat", status = "PLANNED", priority = "LOW")

        row.bind(feature)

        assertEquals("Plan chat", row.title)
        assertEquals("PLANNED", row.status)
        assertEquals("LOW", row.priority)
    }

    @Test
    fun `clicking a bound row invokes onFeatureClicked with that feature`() {
        var clicked: HiveFeature? = null
        val row = TestableHiveFeatureRow(onFeatureClicked = { clicked = it }, {}, {})
        val feature = makeFeature(id = "feat-42", title = "Hive")

        row.bind(feature).onClick()

        assertNotNull(clicked)
        assertEquals("feat-42", clicked?.id)
        assertEquals("Hive", clicked?.title)
    }

    @Test
    fun `edit action invokes onEditClicked with that feature`() {
        var edited: HiveFeature? = null
        val row = TestableHiveFeatureRow({}, onEditClicked = { edited = it }, {})
        val feature = makeFeature(id = "feat-7")

        row.bind(feature).onEdit()

        assertEquals("feat-7", edited?.id)
    }

    @Test
    fun `delete action invokes onDeleteClicked with that feature`() {
        var deleted: HiveFeature? = null
        val row = TestableHiveFeatureRow({}, {}, onDeleteClicked = { deleted = it })
        val feature = makeFeature(id = "feat-9")

        row.bind(feature).onDelete()

        assertEquals("feat-9", deleted?.id)
    }
}
