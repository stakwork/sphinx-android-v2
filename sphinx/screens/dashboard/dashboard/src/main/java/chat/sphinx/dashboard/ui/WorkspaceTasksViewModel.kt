package chat.sphinx.dashboard.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_dashboard.model.HiveTask
import chat.sphinx.concept_repository_dashboard.model.HiveTasksPage
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.kotlin_response.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed class WorkspaceTasksViewState : ViewState<WorkspaceTasksViewState>() {
    object Idle : WorkspaceTasksViewState()
}

@HiltViewModel
internal class WorkspaceTasksViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    @Suppress("unused") private val dashboardNavigator: DashboardNavigator,
) : SideEffectViewModel<
        Context,
        ChatListSideEffect,
        WorkspaceTasksViewState
        >(dispatchers, WorkspaceTasksViewState.Idle) {

    companion object {
        const val PAGE_SIZE = 20
    }

    private val _tasks = MutableStateFlow<List<HiveTask>>(emptyList())
    val tasks: StateFlow<List<HiveTask>> = _tasks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _archivedSegment = MutableStateFlow(false)
    val archivedSegment: StateFlow<Boolean> = _archivedSegment.asStateFlow()

    private var workspaceId: String? = null
    private var currentPage: Int = 0
    private var hasMore: Boolean = false
    private var generation: Int = 0
    private var fetchJob: Job? = null
    private val mutatingTaskIds: MutableSet<String> = mutableSetOf()

    fun load(workspaceId: String) {
        if (workspaceId.isBlank()) {
            return
        }
        this.workspaceId = workspaceId
        _tasks.value = emptyList()
        currentPage = 0
        hasMore = false
        _error.value = false
        loadPage1()
    }

    fun setArchivedSegment(archived: Boolean) {
        val alreadyLoaded = workspaceId != null && currentPage > 0
        if (_archivedSegment.value == archived && alreadyLoaded) {
            return
        }
        _archivedSegment.value = archived
        currentPage = 0
        hasMore = false
        fetchJob?.cancel()
        _tasks.value = emptyList()
        _error.value = false
        loadPage1()
    }

    fun refresh() {
        loadPage1(isRefresh = true)
    }

    fun loadNextPage() {
        if (!hasMore || fetchJob?.isActive == true || _loading.value || _refreshing.value) {
            return
        }
        val workspaceId = workspaceId ?: return
        val nextPage = currentPage + 1
        val expectedGeneration = generation
        val includeArchived = _archivedSegment.value
        fetchJob = viewModelScope.launch {
            fetchAndApply(
                workspaceId = workspaceId,
                page = nextPage,
                includeArchived = includeArchived,
                expectedGeneration = expectedGeneration,
                replace = false,
            )
        }
    }

    fun onStartClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.START) { snapshot ->
            when (val response = repositoryDashboard.startHiveTask(snapshot.id)) {
                is Response.Success -> applyMutationResult(snapshot.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to start task"
                )
            }
        }
    }

    fun onRetryClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.RETRY) { snapshot ->
            when (val response = repositoryDashboard.retryHiveTask(snapshot.id)) {
                is Response.Success -> applyMutationResult(snapshot.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to retry task"
                )
            }
        }
    }

    fun onMarkCompleteClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.MARK_COMPLETE) { snapshot ->
            when (val response = repositoryDashboard.updateHiveTaskStatus(snapshot.id, "DONE")) {
                is Response.Success -> applyMutationResult(snapshot.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to mark task complete"
                )
            }
        }
    }

    fun onArchiveClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.ARCHIVE) { snapshot ->
            when (repositoryDashboard.setHiveTaskArchived(snapshot.id, true)) {
                is Response.Success -> removeTaskAndBackfill(snapshot.id)
                is Response.Error -> notifyMutationError("Unable to archive task")
            }
        }
    }

    fun onUnarchiveClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.UNARCHIVE) { snapshot ->
            when (repositoryDashboard.setHiveTaskArchived(snapshot.id, false)) {
                is Response.Success -> removeTaskAndBackfill(snapshot.id)
                is Response.Error -> notifyMutationError("Unable to unarchive task")
            }
        }
    }

    fun onFlagsChanged(
        task: HiveTask,
        autoMerge: Boolean,
        runBuild: Boolean,
        runTestSuite: Boolean,
    ) {
        mutate(task, HiveTaskAction.EDIT_FLAGS) { snapshot ->
            when (
                val response = repositoryDashboard.updateHiveTaskFlags(
                    snapshot.id,
                    autoMerge,
                    runBuild,
                    runTestSuite,
                )
            ) {
                is Response.Success -> applyMutationResult(snapshot.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to update task flags"
                )
            }
        }
    }

    fun onDuplicateClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.DUPLICATE) { snapshot ->
            if (snapshot.featureId.isNullOrBlank()) {
                submitSideEffect(
                    ChatListSideEffect.NotifyError("Unable to duplicate task without a feature")
                )
                return@mutate
            }
            when (val response = repositoryDashboard.duplicateHiveTask(snapshot)) {
                is Response.Success -> {
                    val copy = response.value
                    if (!_archivedSegment.value) {
                        _tasks.value = listOf(copy) + _tasks.value
                    }
                    rewireLoadedDependents(originalId = snapshot.id, newTaskId = copy.id)
                }
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to duplicate task"
                )
            }
        }
    }

    fun dismissError() {
        _error.value = false
    }

    private fun mutate(
        task: HiveTask,
        action: HiveTaskAction,
        block: suspend (HiveTask) -> Unit,
    ) {
        if (workspaceId.isNullOrBlank()) {
            return
        }
        if (!isTaskInCurrentList(task.id)) {
            return
        }
        if (!mutatingTaskIds.add(task.id)) {
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = _tasks.value.firstOrNull { it.id == task.id } ?: return@launch
                val available = HiveTaskActions.available(snapshot, _archivedSegment.value)
                if (action !in available) {
                    return@launch
                }
                block(snapshot)
            } finally {
                mutatingTaskIds.remove(task.id)
            }
        }
    }

    private fun isTaskInCurrentList(taskId: String): Boolean {
        return _tasks.value.any { it.id == taskId }
    }

    private suspend fun applyMutationResult(taskId: String, updated: HiveTask?) {
        if (updated != null) {
            _tasks.value = _tasks.value.map { task ->
                if (task.id == taskId) updated else task
            }
        } else {
            loadPage1()
        }
    }

    private suspend fun removeTaskAndBackfill(taskId: String) {
        val remaining = _tasks.value.filterNot { it.id == taskId }
        _tasks.value = remaining
        when {
            remaining.isEmpty() -> loadPage1()
            hasMore && remaining.size < PAGE_SIZE -> loadNextPage()
        }
    }

    private suspend fun rewireLoadedDependents(originalId: String, newTaskId: String) {
        val dependents = _tasks.value.filter { originalId in it.dependsOnTaskIds }
        var rewireFailed = false
        for (dependent in dependents) {
            val updatedDeps = dependent.dependsOnTaskIds + newTaskId
            when (repositoryDashboard.updateHiveTaskDependsOn(dependent.id, updatedDeps)) {
                is Response.Success -> {
                    _tasks.value = _tasks.value.map { task ->
                        if (task.id == dependent.id) {
                            task.copy(dependsOnTaskIds = updatedDeps)
                        } else {
                            task
                        }
                    }
                }
                is Response.Error -> rewireFailed = true
            }
        }
        if (rewireFailed) {
            submitSideEffect(
                ChatListSideEffect.NotifyError(
                    "Task copied but some dependencies were not rewired"
                )
            )
        }
    }

    private suspend fun notifyMutationError(message: String) {
        _error.value = true
        submitSideEffect(ChatListSideEffect.NotifyError(message))
    }

    private fun loadPage1(isRefresh: Boolean = false) {
        val workspaceId = workspaceId ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        val includeArchived = _archivedSegment.value
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _refreshing.value = true
            } else if (_tasks.value.isEmpty()) {
                _loading.value = true
            }
            _error.value = false
            try {
                fetchAndApply(
                    workspaceId = workspaceId,
                    page = 1,
                    includeArchived = includeArchived,
                    expectedGeneration = expectedGeneration,
                    replace = true,
                )
            } finally {
                _loading.value = false
                _refreshing.value = false
            }
        }
    }

    private suspend fun fetchAndApply(
        workspaceId: String,
        page: Int,
        includeArchived: Boolean,
        expectedGeneration: Int,
        replace: Boolean,
    ) {
        try {
            when (
                val response = repositoryDashboard.fetchHiveTasks(
                    workspaceId,
                    page,
                    includeArchived,
                )
            ) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    applyPage(response.value, replace)
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _error.value = true
                    submitSideEffect(
                        ChatListSideEffect.NotifyError(
                            msg = response.cause.message ?: "Unable to load tasks"
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (expectedGeneration != generation) {
                return
            }
            _error.value = true
            submitSideEffect(
                ChatListSideEffect.NotifyError(
                    msg = e.message ?: "Unable to load tasks"
                )
            )
        }
    }

    private fun applyPage(page: HiveTasksPage, replace: Boolean) {
        currentPage = page.page
        hasMore = page.hasMore
        _tasks.value = if (replace) {
            page.tasks
        } else {
            _tasks.value + page.tasks
        }
        _error.value = false
    }
}
