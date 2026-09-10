package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.HiveTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveTaskActionsTest {

    private fun task(
        status: String? = "TODO",
        workflowStatus: String? = null,
        prUrl: String? = null,
        prStatus: String? = null,
    ) = HiveTask(
        id = "task-1",
        title = "Task",
        status = status,
        workflowStatus = workflowStatus,
        prUrl = prUrl,
        prStatus = prStatus,
    )

    @Test
    fun `archived segment exposes only Unarchive regardless of status`() {
        val statuses = listOf(null, "TODO", "IN_PROGRESS", "DONE")
        statuses.forEach { status ->
            assertEquals(
                setOf(HiveTaskAction.UNARCHIVE),
                HiveTaskActions.available(task(status = status), archivedSegment = true)
            )
        }
    }

    @Test
    fun `active TODO exposes Start Complete Archive Duplicate and EditFlags`() {
        assertEquals(
            setOf(
                HiveTaskAction.ARCHIVE,
                HiveTaskAction.DUPLICATE,
                HiveTaskAction.START,
                HiveTaskAction.MARK_COMPLETE,
                HiveTaskAction.EDIT_FLAGS,
            ),
            HiveTaskActions.available(task(status = "TODO"), archivedSegment = false)
        )
    }

    @Test
    fun `active IN_PROGRESS exposes Mark Complete Archive and Duplicate`() {
        assertEquals(
            setOf(
                HiveTaskAction.ARCHIVE,
                HiveTaskAction.DUPLICATE,
                HiveTaskAction.MARK_COMPLETE,
            ),
            HiveTaskActions.available(task(status = "IN_PROGRESS"), archivedSegment = false)
        )
    }

    @Test
    fun `active DONE exposes only Archive and Duplicate`() {
        assertEquals(
            setOf(HiveTaskAction.ARCHIVE, HiveTaskAction.DUPLICATE),
            HiveTaskActions.available(task(status = "DONE"), archivedSegment = false)
        )
    }

    @Test
    fun `null status on active has no Start MarkComplete or EditFlags`() {
        val available = HiveTaskActions.available(task(status = null), archivedSegment = false)
        assertEquals(setOf(HiveTaskAction.ARCHIVE, HiveTaskAction.DUPLICATE), available)
        assertFalse(HiveTaskAction.START in available)
        assertFalse(HiveTaskAction.MARK_COMPLETE in available)
        assertFalse(HiveTaskAction.EDIT_FLAGS in available)
    }

    @Test
    fun `HALTED with blank prUrl allows Retry`() {
        val available = HiveTaskActions.available(
            task(status = "IN_PROGRESS", workflowStatus = "HALTED", prUrl = null, prStatus = null),
            archivedSegment = false,
        )
        assertTrue(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `HALTED with empty prUrl allows Retry`() {
        val available = HiveTaskActions.available(
            task(status = "TODO", workflowStatus = "HALTED", prUrl = "  ", prStatus = null),
            archivedSegment = false,
        )
        assertTrue(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `HALTED with open PR does not allow Retry`() {
        val available = HiveTaskActions.available(
            task(
                status = "IN_PROGRESS",
                workflowStatus = "HALTED",
                prUrl = "https://github.com/org/repo/pull/1",
                prStatus = "OPEN",
            ),
            archivedSegment = false,
        )
        assertFalse(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `HALTED with prStatus OPEN even if prUrl blank does not allow Retry`() {
        val available = HiveTaskActions.available(
            task(status = "IN_PROGRESS", workflowStatus = "HALTED", prUrl = null, prStatus = "OPEN"),
            archivedSegment = false,
        )
        assertFalse(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `HALTED with merged PR does not allow Retry`() {
        val available = HiveTaskActions.available(
            task(status = "IN_PROGRESS", workflowStatus = "HALTED", prUrl = null, prStatus = "MERGED"),
            archivedSegment = false,
        )
        assertFalse(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `HALTED with DONE PR does not allow Retry`() {
        val available = HiveTaskActions.available(
            task(status = "IN_PROGRESS", workflowStatus = "HALTED", prUrl = null, prStatus = "DONE"),
            archivedSegment = false,
        )
        assertFalse(HiveTaskAction.RETRY in available)
    }

    @Test
    fun `FAILED pill is independent of retry eligibility`() {
        val failed = task(status = "IN_PROGRESS", workflowStatus = "FAILED")
        assertTrue(HiveTaskActions.isFailed(failed))
        assertFalse(
            HiveTaskAction.RETRY in HiveTaskActions.available(failed, archivedSegment = false)
        )

        val haltedFailed = task(status = "IN_PROGRESS", workflowStatus = "FAILED", prUrl = null)
        assertTrue(HiveTaskActions.isFailed(haltedFailed))

        val haltedRetry = task(status = "IN_PROGRESS", workflowStatus = "HALTED")
        assertFalse(HiveTaskActions.isFailed(haltedRetry))
        assertTrue(
            HiveTaskAction.RETRY in HiveTaskActions.available(haltedRetry, archivedSegment = false)
        )
    }

    @Test
    fun `archived HALTED task still only exposes Unarchive`() {
        assertEquals(
            setOf(HiveTaskAction.UNARCHIVE),
            HiveTaskActions.available(
                task(status = "TODO", workflowStatus = "HALTED"),
                archivedSegment = true,
            )
        )
    }
}
