package chat.sphinx.dashboard.ui.adapter

import chat.sphinx.concept_repository_dashboard.model.HiveTask
import chat.sphinx.dashboard.ui.HiveTaskAction
import chat.sphinx.dashboard.ui.HiveTaskActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors [HiveTaskAdapter.HiveTaskViewHolder.bind] action wiring without inflating Android views.
 */
private class TestableHiveTaskRow(
    private val onStartClicked: (HiveTask) -> Unit = {},
    private val onRetryClicked: (HiveTask) -> Unit = {},
    private val onMarkCompleteClicked: (HiveTask) -> Unit = {},
    private val onArchiveClicked: (HiveTask) -> Unit = {},
    private val onUnarchiveClicked: (HiveTask) -> Unit = {},
    private val onDuplicateClicked: (HiveTask) -> Unit = {},
    private val onFlagsChanged: (HiveTask, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
) {
    var title: String? = null
    var status: String? = null
    var priority: String? = null
    var failedVisible: Boolean = false
    var menuItems: Set<HiveTaskAction> = emptySet()
    var autoMergeChecked: Boolean = false
    var runBuildChecked: Boolean = false
    var runTestSuiteChecked: Boolean = false
    var flagsEnabled: Boolean = false

    fun bind(task: HiveTask, archivedSegment: Boolean): BoundActions {
        title = task.title
        status = task.status
        priority = task.priority
        failedVisible = HiveTaskActions.isFailed(task)
        menuItems = HiveTaskActions.available(task, archivedSegment)
        flagsEnabled = HiveTaskAction.EDIT_FLAGS in menuItems
        autoMergeChecked = task.autoMerge == true
        runBuildChecked = task.runBuild == true
        runTestSuiteChecked = task.runTestSuite == true
        return BoundActions(
            onStart = { onStartClicked(task) },
            onRetry = { onRetryClicked(task) },
            onMarkComplete = { onMarkCompleteClicked(task) },
            onArchive = { onArchiveClicked(task) },
            onUnarchive = { onUnarchiveClicked(task) },
            onDuplicate = { onDuplicateClicked(task) },
            onAutoMerge = {
                onFlagsChanged(
                    task,
                    !(task.autoMerge == true),
                    task.runBuild == true,
                    task.runTestSuite == true,
                )
            },
        )
    }

    class BoundActions(
        val onStart: () -> Unit,
        val onRetry: () -> Unit,
        val onMarkComplete: () -> Unit,
        val onArchive: () -> Unit,
        val onUnarchive: () -> Unit,
        val onDuplicate: () -> Unit,
        val onAutoMerge: () -> Unit,
    )
}

class HiveTaskAdapterTest {

    private fun makeTask(
        id: String = "task-1",
        title: String = "Ship it",
        status: String? = "TODO",
        priority: String? = "HIGH",
        workflowStatus: String? = null,
        prUrl: String? = null,
        prStatus: String? = null,
        featureId: String? = "feat-1",
        autoMerge: Boolean? = false,
        runBuild: Boolean? = true,
        runTestSuite: Boolean? = false,
    ) = HiveTask(
        id = id,
        title = title,
        status = status,
        priority = priority,
        workflowStatus = workflowStatus,
        prUrl = prUrl,
        prStatus = prStatus,
        featureId = featureId,
        autoMerge = autoMerge,
        runBuild = runBuild,
        runTestSuite = runTestSuite,
    )

    @Test
    fun `binding exposes title status priority and failed pill`() {
        val row = TestableHiveTaskRow()
        val task = makeTask(
            title = "Retry workflow",
            status = "IN_PROGRESS",
            priority = "LOW",
            workflowStatus = "FAILED",
        )

        row.bind(task, archivedSegment = false)

        assertEquals("Retry workflow", row.title)
        assertEquals("IN_PROGRESS", row.status)
        assertEquals("LOW", row.priority)
        assertTrue(row.failedVisible)
    }

    @Test
    fun `active TODO menu matches HiveTaskActions and flags are enabled`() {
        val row = TestableHiveTaskRow()
        val task = makeTask(status = "TODO", autoMerge = true, runBuild = false, runTestSuite = true)

        row.bind(task, archivedSegment = false)

        assertEquals(
            HiveTaskActions.available(task, archivedSegment = false),
            row.menuItems,
        )
        assertTrue(row.flagsEnabled)
        assertTrue(row.autoMergeChecked)
        assertFalse(row.runBuildChecked)
        assertTrue(row.runTestSuiteChecked)
    }

    @Test
    fun `archived menu is Unarchive only`() {
        val row = TestableHiveTaskRow()
        val task = makeTask(status = "TODO")

        row.bind(task, archivedSegment = true)

        assertEquals(setOf(HiveTaskAction.UNARCHIVE), row.menuItems)
        assertFalse(row.flagsEnabled)
    }

    @Test
    fun `start action invokes onStartClicked with that task`() {
        var started: HiveTask? = null
        val row = TestableHiveTaskRow(onStartClicked = { started = it })
        val task = makeTask(id = "task-9")

        row.bind(task, archivedSegment = false).onStart()

        assertEquals("task-9", started?.id)
    }

    @Test
    fun `retry action invokes onRetryClicked with that task`() {
        var retried: HiveTask? = null
        val row = TestableHiveTaskRow(onRetryClicked = { retried = it })
        val task = makeTask(id = "task-8", workflowStatus = "HALTED")

        row.bind(task, archivedSegment = false).onRetry()

        assertEquals("task-8", retried?.id)
    }

    @Test
    fun `mark complete invokes onMarkCompleteClicked with that task`() {
        var completed: HiveTask? = null
        val row = TestableHiveTaskRow(onMarkCompleteClicked = { completed = it })
        val task = makeTask(id = "task-7", status = "IN_PROGRESS")

        row.bind(task, archivedSegment = false).onMarkComplete()

        assertEquals("task-7", completed?.id)
    }

    @Test
    fun `archive and duplicate callbacks fire with that task`() {
        var archived: HiveTask? = null
        var duplicated: HiveTask? = null
        val row = TestableHiveTaskRow(
            onArchiveClicked = { archived = it },
            onDuplicateClicked = { duplicated = it },
        )
        val task = makeTask(id = "task-6")

        val actions = row.bind(task, archivedSegment = false)
        actions.onArchive()
        actions.onDuplicate()

        assertEquals("task-6", archived?.id)
        assertEquals("task-6", duplicated?.id)
    }

    @Test
    fun `unarchive callback fires with that task`() {
        var unarchived: HiveTask? = null
        val row = TestableHiveTaskRow(onUnarchiveClicked = { unarchived = it })
        val task = makeTask(id = "task-5")

        row.bind(task, archivedSegment = true).onUnarchive()

        assertEquals("task-5", unarchived?.id)
    }

    @Test
    fun `toggling autoMerge inverts only that flag`() {
        var received: Triple<Boolean, Boolean, Boolean>? = null
        val row = TestableHiveTaskRow(
            onFlagsChanged = { _, autoMerge, runBuild, runTestSuite ->
                received = Triple(autoMerge, runBuild, runTestSuite)
            }
        )
        val task = makeTask(autoMerge = false, runBuild = true, runTestSuite = false)

        row.bind(task, archivedSegment = false).onAutoMerge()

        assertEquals(Triple(true, true, false), received)
    }
}
