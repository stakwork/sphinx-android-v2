package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.HiveTask

enum class HiveTaskAction {
    START,
    RETRY,
    MARK_COMPLETE,
    ARCHIVE,
    UNARCHIVE,
    DUPLICATE,
    EDIT_FLAGS,
}

object HiveTaskActions {

    private val retryBlockedPrStatuses = setOf("OPEN", "MERGED", "DONE")

    fun available(task: HiveTask, archivedSegment: Boolean): Set<HiveTaskAction> {
        if (archivedSegment) {
            return setOf(HiveTaskAction.UNARCHIVE)
        }

        val actions = mutableSetOf(
            HiveTaskAction.ARCHIVE,
            HiveTaskAction.DUPLICATE,
        )

        if (task.status == "TODO") {
            actions.add(HiveTaskAction.START)
            actions.add(HiveTaskAction.EDIT_FLAGS)
        }

        if (task.status == "TODO" || task.status == "IN_PROGRESS") {
            actions.add(HiveTaskAction.MARK_COMPLETE)
        }

        val prStatus = task.prStatus
        if (
            task.workflowStatus == "HALTED" &&
            task.prUrl.isNullOrBlank() &&
            (prStatus == null || prStatus !in retryBlockedPrStatuses)
        ) {
            actions.add(HiveTaskAction.RETRY)
        }

        return actions
    }

    fun isFailed(task: HiveTask): Boolean = task.workflowStatus == "FAILED"
}
