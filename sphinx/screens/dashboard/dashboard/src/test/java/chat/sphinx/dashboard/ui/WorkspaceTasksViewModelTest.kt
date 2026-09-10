package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.HiveTask
import chat.sphinx.concept_repository_dashboard.model.HiveTasksPage
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestableWorkspaceTasksViewModel(
    private val fetchHiveTasks: suspend (
        workspaceId: String,
        page: Int,
        includeArchived: Boolean,
    ) -> Response<HiveTasksPage, ResponseError>,
    private val startHiveTask: suspend (taskId: String) -> Response<HiveTask?, ResponseError> =
        { Response.Error(ResponseError("not stubbed")) },
    private val retryHiveTask: suspend (taskId: String) -> Response<HiveTask?, ResponseError> =
        { Response.Error(ResponseError("not stubbed")) },
    private val updateHiveTaskStatus: suspend (taskId: String, status: String) -> Response<HiveTask?, ResponseError> =
        { _, _ -> Response.Error(ResponseError("not stubbed")) },
    private val setHiveTaskArchived: suspend (taskId: String, archived: Boolean) -> Response<HiveTask?, ResponseError> =
        { _, _ -> Response.Error(ResponseError("not stubbed")) },
    private val updateHiveTaskFlags: suspend (
        taskId: String,
        autoMerge: Boolean,
        runBuild: Boolean,
        runTestSuite: Boolean,
    ) -> Response<HiveTask?, ResponseError> =
        { _, _, _, _ -> Response.Error(ResponseError("not stubbed")) },
    private val duplicateHiveTask: suspend (task: HiveTask) -> Response<HiveTask, ResponseError> =
        { Response.Error(ResponseError("not stubbed")) },
    private val updateHiveTaskDependsOn: suspend (
        taskId: String,
        dependsOnTaskIds: List<String>,
    ) -> Response<Boolean, ResponseError> =
        { _, _ -> Response.Error(ResponseError("not stubbed")) },
) {
    companion object {
        const val PAGE_SIZE = 20
    }

    private val _tasks = MutableStateFlow<List<HiveTask>>(emptyList())
    val tasks: StateFlow<List<HiveTask>> = _tasks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _archivedSegment = MutableStateFlow(false)
    val archivedSegment: StateFlow<Boolean> = _archivedSegment.asStateFlow()

    val fetchCalls = mutableListOf<Triple<String, Int, Boolean>>()
    val startCalls = mutableListOf<String>()
    val retryCalls = mutableListOf<String>()
    val statusCalls = mutableListOf<Pair<String, String>>()
    val archiveCalls = mutableListOf<Pair<String, Boolean>>()
    val flagCalls = mutableListOf<String>()
    val duplicateCalls = mutableListOf<String>()
    val dependsOnCalls = mutableListOf<Pair<String, List<String>>>()
    val notifyErrors = mutableListOf<String>()

    private var workspaceId: String? = null
    private var currentPage: Int = 0
    private var hasMore: Boolean = false
    private var generation: Int = 0
    private var fetchJob: Job? = null
    private val mutatingTaskIds: MutableSet<String> = mutableSetOf()

    suspend fun load(workspaceId: String) {
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

    suspend fun setArchivedSegment(archived: Boolean) {
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

    suspend fun refresh() {
        loadPage1()
    }

    suspend fun loadNextPage() {
        if (!hasMore || fetchJob?.isActive == true || _loading.value) {
            return
        }
        val workspaceId = workspaceId ?: return
        val nextPage = currentPage + 1
        val expectedGeneration = generation
        val includeArchived = _archivedSegment.value
        coroutineScope {
            fetchAndApply(workspaceId, nextPage, includeArchived, expectedGeneration, replace = false)
        }
    }

    suspend fun onStartClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.START) {
            startCalls.add(task.id)
            when (val response = startHiveTask(task.id)) {
                is Response.Success -> applyMutationResult(task.id, response.value)
                is Response.Error -> notifyMutationError(response.cause.message ?: "Unable to start task")
            }
        }
    }

    suspend fun onRetryClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.RETRY) {
            retryCalls.add(task.id)
            when (val response = retryHiveTask(task.id)) {
                is Response.Success -> applyMutationResult(task.id, response.value)
                is Response.Error -> notifyMutationError(response.cause.message ?: "Unable to retry task")
            }
        }
    }

    suspend fun onMarkCompleteClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.MARK_COMPLETE) {
            statusCalls.add(task.id to "DONE")
            when (val response = updateHiveTaskStatus(task.id, "DONE")) {
                is Response.Success -> applyMutationResult(task.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to mark task complete"
                )
            }
        }
    }

    suspend fun onArchiveClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.ARCHIVE) {
            archiveCalls.add(task.id to true)
            when (setHiveTaskArchived(task.id, true)) {
                is Response.Success -> removeTaskAndBackfill(task.id)
                is Response.Error -> notifyMutationError("Unable to archive task")
            }
        }
    }

    suspend fun onUnarchiveClicked(task: HiveTask) {
        mutate(task, HiveTaskAction.UNARCHIVE) {
            archiveCalls.add(task.id to false)
            when (setHiveTaskArchived(task.id, false)) {
                is Response.Success -> removeTaskAndBackfill(task.id)
                is Response.Error -> notifyMutationError("Unable to unarchive task")
            }
        }
    }

    suspend fun onFlagsChanged(
        task: HiveTask,
        autoMerge: Boolean,
        runBuild: Boolean,
        runTestSuite: Boolean,
    ) {
        mutate(task, HiveTaskAction.EDIT_FLAGS) {
            flagCalls.add(task.id)
            when (val response = updateHiveTaskFlags(task.id, autoMerge, runBuild, runTestSuite)) {
                is Response.Success -> applyMutationResult(task.id, response.value)
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to update task flags"
                )
            }
        }
    }

    suspend fun onDuplicateClicked(task: HiveTask) {
        if (workspaceId.isNullOrBlank() || !isTaskInCurrentList(task.id)) {
            return
        }
        if (task.featureId.isNullOrBlank()) {
            notifyErrors.add("Unable to duplicate task without a feature")
            return
        }
        mutate(task, HiveTaskAction.DUPLICATE) {
            duplicateCalls.add(task.id)
            when (val response = duplicateHiveTask(task)) {
                is Response.Success -> {
                    val copy = response.value
                    if (!_archivedSegment.value) {
                        _tasks.value = listOf(copy) + _tasks.value
                    }
                    rewireLoadedDependents(originalId = task.id, newTaskId = copy.id)
                }
                is Response.Error -> notifyMutationError(
                    response.cause.message ?: "Unable to duplicate task"
                )
            }
        }
    }

    private suspend fun mutate(
        task: HiveTask,
        action: HiveTaskAction,
        block: suspend () -> Unit,
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
        try {
            val snapshot = _tasks.value.firstOrNull { it.id == task.id } ?: return
            val available = HiveTaskActions.available(snapshot, _archivedSegment.value)
            if (action !in available) {
                return
            }
            block()
        } finally {
            mutatingTaskIds.remove(task.id)
        }
    }

    fun isMutating(taskId: String): Boolean = taskId in mutatingTaskIds

    fun addMutating(taskId: String) {
        mutatingTaskIds.add(taskId)
    }

    fun removeMutating(taskId: String) {
        mutatingTaskIds.remove(taskId)
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
            dependsOnCalls.add(dependent.id to updatedDeps)
            when (updateHiveTaskDependsOn(dependent.id, updatedDeps)) {
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
            notifyErrors.add("Task copied but some dependencies were not rewired")
        }
    }

    private fun notifyMutationError(message: String) {
        _error.value = true
        notifyErrors.add(message)
    }

    private suspend fun loadPage1() {
        val workspaceId = workspaceId ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        val includeArchived = _archivedSegment.value
        _loading.value = _tasks.value.isEmpty()
        _error.value = false
        try {
            fetchAndApply(workspaceId, 1, includeArchived, expectedGeneration, replace = true)
        } finally {
            _loading.value = false
        }
    }

    private suspend fun fetchAndApply(
        workspaceId: String,
        page: Int,
        includeArchived: Boolean,
        expectedGeneration: Int,
        replace: Boolean,
    ) {
        fetchCalls.add(Triple(workspaceId, page, includeArchived))
        try {
            when (val response = fetchHiveTasks(workspaceId, page, includeArchived)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    currentPage = response.value.page
                    hasMore = response.value.hasMore
                    _tasks.value = if (replace) {
                        response.value.tasks
                    } else {
                        _tasks.value + response.value.tasks
                    }
                    _error.value = false
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _error.value = true
                    notifyErrors.add(response.cause.message ?: "Unable to load tasks")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (expectedGeneration != generation) {
                return
            }
            _error.value = true
            notifyErrors.add(e.message ?: "Unable to load tasks")
        }
    }

    suspend fun fetchPageWithGeneration(page: Int, expectedGeneration: Int) {
        val workspaceId = workspaceId ?: return
        fetchAndApply(
            workspaceId,
            page,
            _archivedSegment.value,
            expectedGeneration,
            replace = false,
        )
    }

    fun currentGeneration(): Int = generation
    fun currentPage(): Int = currentPage
}

class WorkspaceTasksViewModelTest {

    private fun task(
        id: String,
        title: String = "Task $id",
        status: String? = "TODO",
        featureId: String? = "feat-1",
        workflowStatus: String? = null,
        prUrl: String? = null,
        prStatus: String? = null,
        dependsOnTaskIds: List<String> = emptyList(),
        autoMerge: Boolean? = false,
        runBuild: Boolean? = false,
        runTestSuite: Boolean? = false,
    ) = HiveTask(
        id = id,
        title = title,
        status = status,
        featureId = featureId,
        workflowStatus = workflowStatus,
        prUrl = prUrl,
        prStatus = prStatus,
        dependsOnTaskIds = dependsOnTaskIds,
        autoMerge = autoMerge,
        runBuild = runBuild,
        runTestSuite = runTestSuite,
    )

    private fun tasksPage(
        tasks: List<HiveTask>,
        page: Int = 1,
        hasMore: Boolean = false,
        totalPages: Int = 1,
        totalCount: Int = tasks.size,
    ) = HiveTasksPage(tasks, page, hasMore, totalPages, totalCount)

    @Test
    fun `load fetches page 1 of Active tasks`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, page, includeArchived ->
                assertEquals(1, page)
                assertFalse(includeArchived)
                Response.Success(tasksPage(listOf(task("task-1")), page = 1))
            }
        )

        subject.load("ws-1")

        assertEquals(listOf("task-1"), subject.tasks.value.map { it.id })
        assertFalse(subject.error.value)
        assertFalse(subject.archivedSegment.value)
        assertEquals(listOf(Triple("ws-1", 1, false)), subject.fetchCalls)
    }

    @Test
    fun `switching to Archived resets to page 1 and includes archived`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, page, includeArchived ->
                if (!includeArchived) {
                    Response.Success(tasksPage(listOf(task("active-1")), page = 1, hasMore = true))
                } else {
                    Response.Success(tasksPage(listOf(task("arch-1")), page = 1, hasMore = false))
                }
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()
        subject.setArchivedSegment(true)

        assertTrue(subject.archivedSegment.value)
        assertEquals(listOf("arch-1"), subject.tasks.value.map { it.id })
        assertEquals(1, subject.currentPage())
        assertEquals(Triple("ws-1", 1, true), subject.fetchCalls.last())
    }

    @Test
    fun `re-toggling Active refetches page 1 even when workspaceId is unchanged`() = runBlocking {
        var activeLoads = 0
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, includeArchived ->
                if (includeArchived) {
                    Response.Success(tasksPage(listOf(task("arch-1"))))
                } else {
                    activeLoads++
                    Response.Success(tasksPage(listOf(task("active-$activeLoads"))))
                }
            }
        )

        subject.load("ws-1")
        subject.setArchivedSegment(true)
        subject.setArchivedSegment(false)

        assertFalse(subject.archivedSegment.value)
        assertEquals(listOf("active-2"), subject.tasks.value.map { it.id })
        assertEquals(2, activeLoads)
        assertEquals(
            listOf(
                Triple("ws-1", 1, false),
                Triple("ws-1", 1, true),
                Triple("ws-1", 1, false),
            ),
            subject.fetchCalls
        )
    }

    @Test
    fun `setArchivedSegment is a no-op when already on that loaded segment`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ ->
                Response.Success(tasksPage(listOf(task("task-1"))))
            }
        )

        subject.load("ws-1")
        subject.setArchivedSegment(false)

        assertEquals(1, subject.fetchCalls.size)
        assertEquals(listOf("task-1"), subject.tasks.value.map { it.id })
    }

    @Test
    fun `loadNextPage appends while hasMore and passes current segment`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, page, includeArchived ->
                assertFalse(includeArchived)
                if (page == 1) {
                    Response.Success(tasksPage(listOf(task("task-1")), page = 1, hasMore = true, totalPages = 2))
                } else {
                    Response.Success(tasksPage(listOf(task("task-2")), page = 2, hasMore = false, totalPages = 2))
                }
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()

        assertEquals(listOf("task-1", "task-2"), subject.tasks.value.map { it.id })
        assertEquals(
            listOf(Triple("ws-1", 1, false), Triple("ws-1", 2, false)),
            subject.fetchCalls
        )
    }

    @Test
    fun `stale page-N response from older generation is dropped after segment switch`() = runBlocking {
        val page2Gate = CompletableDeferred<Unit>()
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, page, includeArchived ->
                if (!includeArchived && page == 1) {
                    Response.Success(tasksPage(listOf(task("active-1")), page = 1, hasMore = true))
                } else if (!includeArchived) {
                    page2Gate.await()
                    Response.Success(tasksPage(listOf(task("stale-2")), page = 2, hasMore = false))
                } else {
                    Response.Success(tasksPage(listOf(task("arch-1")), page = 1, hasMore = false))
                }
            }
        )

        subject.load("ws-1")
        val staleGeneration = subject.currentGeneration()

        val staleJob = launch {
            subject.fetchPageWithGeneration(page = 2, expectedGeneration = staleGeneration)
        }

        subject.setArchivedSegment(true)
        page2Gate.complete(Unit)
        staleJob.join()

        assertEquals(listOf("arch-1"), subject.tasks.value.map { it.id })
        assertFalse(subject.tasks.value.any { it.id == "stale-2" })
    }

    @Test
    fun `membership gate blocks mutating an id not in the loaded list`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(task("task-1")))) },
            startHiveTask = { error("should not be called") },
        )

        subject.load("ws-1")
        subject.onStartClicked(task("not-in-list"))

        assertTrue(subject.startCalls.isEmpty())
        assertEquals(listOf("task-1"), subject.tasks.value.map { it.id })
    }

    @Test
    fun `in-flight set swallows a duplicate Start tap`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(task("task-1")))) },
            startHiveTask = { error("should not be called while mutating") },
        )

        subject.load("ws-1")
        subject.addMutating("task-1")
        subject.onStartClicked(task("task-1"))

        assertTrue(subject.startCalls.isEmpty())
        subject.removeMutating("task-1")
    }

    @Test
    fun `Start success with data replaces the row so Start is no longer available`() = runBlocking {
        val original = task("task-1", status = "TODO")
        val started = original.copy(status = "IN_PROGRESS")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            startHiveTask = { Response.Success(started) },
        )

        subject.load("ws-1")
        subject.onStartClicked(original)

        val updated = subject.tasks.value.single()
        assertEquals("IN_PROGRESS", updated.status)
        assertFalse(
            HiveTaskAction.START in HiveTaskActions.available(updated, archivedSegment = false)
        )
        assertEquals(listOf("task-1"), subject.startCalls)
    }

    @Test
    fun `Start success without data refetches page 1 of the current segment`() = runBlocking {
        val original = task("task-1", status = "TODO")
        var page1Loads = 0
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ ->
                page1Loads++
                if (page1Loads == 1) {
                    Response.Success(tasksPage(listOf(original)))
                } else {
                    Response.Success(tasksPage(listOf(original.copy(status = "IN_PROGRESS"))))
                }
            },
            startHiveTask = { Response.Success(null) },
        )

        subject.load("ws-1")
        subject.onStartClicked(original)

        assertEquals("IN_PROGRESS", subject.tasks.value.single().status)
        assertEquals(2, subject.fetchCalls.size)
        assertEquals(1, subject.fetchCalls.last().second)
    }

    @Test
    fun `Retry success with data replaces the row so Retry is no longer available`() = runBlocking {
        val original = task("task-1", status = "IN_PROGRESS", workflowStatus = "HALTED")
        val retried = original.copy(workflowStatus = "IN_PROGRESS")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            retryHiveTask = { Response.Success(retried) },
        )

        subject.load("ws-1")
        subject.onRetryClicked(original)

        val updated = subject.tasks.value.single()
        assertEquals("IN_PROGRESS", updated.workflowStatus)
        assertFalse(
            HiveTaskAction.RETRY in HiveTaskActions.available(updated, archivedSegment = false)
        )
    }

    @Test
    fun `Mark Complete sends DONE and replaces the row`() = runBlocking {
        val original = task("task-1", status = "TODO")
        val done = original.copy(status = "DONE")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            updateHiveTaskStatus = { _, status ->
                assertEquals("DONE", status)
                Response.Success(done)
            },
        )

        subject.load("ws-1")
        subject.onMarkCompleteClicked(original)

        assertEquals("DONE", subject.tasks.value.single().status)
        assertEquals(listOf("task-1" to "DONE"), subject.statusCalls)
        assertFalse(
            HiveTaskAction.MARK_COMPLETE in HiveTaskActions.available(
                subject.tasks.value.single(),
                archivedSegment = false,
            )
        )
    }

    @Test
    fun `stale Start is aborted when snapshot is no longer TODO`() = runBlocking {
        val original = task("task-1", status = "IN_PROGRESS")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            startHiveTask = { error("should not start a non-TODO task") },
        )

        subject.load("ws-1")
        subject.onStartClicked(original.copy(status = "TODO"))

        assertTrue(subject.startCalls.isEmpty())
    }

    @Test
    fun `successful archive removes the row`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ ->
                Response.Success(tasksPage(listOf(task("task-1"), task("task-2"))))
            },
            setHiveTaskArchived = { _, _ -> Response.Success(null) },
        )

        subject.load("ws-1")
        subject.onArchiveClicked(task("task-1"))

        assertEquals(listOf("task-2"), subject.tasks.value.map { it.id })
        assertEquals(listOf("task-1" to true), subject.archiveCalls)
    }

    @Test
    fun `successful archive of last item reloads page 1`() = runBlocking {
        var fetchCount = 0
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ ->
                fetchCount++
                if (fetchCount == 1) {
                    Response.Success(tasksPage(listOf(task("task-1"))))
                } else {
                    Response.Success(tasksPage(emptyList()))
                }
            },
            setHiveTaskArchived = { _, _ -> Response.Success(null) },
        )

        subject.load("ws-1")
        subject.onArchiveClicked(task("task-1"))

        assertTrue(subject.tasks.value.isEmpty())
        assertEquals(2, subject.fetchCalls.size)
        assertEquals(1, subject.fetchCalls.last().second)
    }

    @Test
    fun `successful archive with hasMore and short list fetches next page`() = runBlocking {
        val page1 = (1..5).map { task("task-$it") }
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, page, _ ->
                if (page == 1) {
                    Response.Success(tasksPage(page1, page = 1, hasMore = true, totalPages = 2, totalCount = 6))
                } else {
                    Response.Success(tasksPage(listOf(task("task-6")), page = 2, hasMore = false, totalPages = 2, totalCount = 6))
                }
            },
            setHiveTaskArchived = { _, _ -> Response.Success(null) },
        )

        subject.load("ws-1")
        subject.onArchiveClicked(task("task-1"))

        assertEquals(
            listOf("task-2", "task-3", "task-4", "task-5", "task-6"),
            subject.tasks.value.map { it.id }
        )
    }

    @Test
    fun `Unarchive on archived segment removes the row`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, includeArchived ->
                if (includeArchived) {
                    Response.Success(tasksPage(listOf(task("task-1"), task("task-2"))))
                } else {
                    Response.Success(tasksPage(emptyList()))
                }
            },
            setHiveTaskArchived = { _, archived ->
                assertFalse(archived)
                Response.Success(null)
            },
        )

        subject.load("ws-1")
        subject.setArchivedSegment(true)
        subject.onUnarchiveClicked(task("task-1"))

        assertEquals(listOf("task-2"), subject.tasks.value.map { it.id })
        assertEquals(listOf("task-1" to false), subject.archiveCalls)
    }

    @Test
    fun `duplicate with blank featureId notifies without calling the repository`() = runBlocking {
        val original = task("task-1", featureId = null)
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            duplicateHiveTask = { error("should not be called") },
        )

        subject.load("ws-1")
        subject.onDuplicateClicked(original)

        assertTrue(subject.duplicateCalls.isEmpty())
        assertEquals(listOf("Unable to duplicate task without a feature"), subject.notifyErrors)
        assertEquals(listOf("task-1"), subject.tasks.value.map { it.id })
    }

    @Test
    fun `duplicate prepends the copy on Active and rewires only loaded dependents`() = runBlocking {
        val original = task("task-1", featureId = "feat-1")
        val loadedDependent = task("task-2", dependsOnTaskIds = listOf("task-1"))
        val unrelated = task("task-3")
        val copy = task("task-copy", featureId = "feat-1")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ ->
                Response.Success(tasksPage(listOf(original, loadedDependent, unrelated)))
            },
            duplicateHiveTask = { Response.Success(copy) },
            updateHiveTaskDependsOn = { _, _ -> Response.Success(true) },
        )

        subject.load("ws-1")
        subject.onDuplicateClicked(original)

        assertEquals(
            listOf("task-copy", "task-1", "task-2", "task-3"),
            subject.tasks.value.map { it.id }
        )
        assertEquals(listOf("task-1"), subject.duplicateCalls)
        assertEquals(listOf("task-2" to listOf("task-1", "task-copy")), subject.dependsOnCalls)
        assertEquals(
            listOf("task-1", "task-copy"),
            subject.tasks.value.first { it.id == "task-2" }.dependsOnTaskIds
        )
    }

    @Test
    fun `partial rewire failure notifies without dropping the new task`() = runBlocking {
        val original = task("task-1", featureId = "feat-1")
        val dependent = task("task-2", dependsOnTaskIds = listOf("task-1"))
        val copy = task("task-copy", featureId = "feat-1")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original, dependent))) },
            duplicateHiveTask = { Response.Success(copy) },
            updateHiveTaskDependsOn = { _, _ -> Response.Error(ResponseError("rewire failed")) },
        )

        subject.load("ws-1")
        subject.onDuplicateClicked(original)

        assertTrue(subject.tasks.value.any { it.id == "task-copy" })
        assertEquals(
            listOf("Task copied but some dependencies were not rewired"),
            subject.notifyErrors
        )
        assertEquals(listOf("task-1"), subject.tasks.value.first { it.id == "task-2" }.dependsOnTaskIds)
    }

    @Test
    fun `flags success replaces the row`() = runBlocking {
        val original = task("task-1", status = "TODO", autoMerge = false)
        val updated = original.copy(autoMerge = true)
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            updateHiveTaskFlags = { _, autoMerge, _, _ ->
                assertTrue(autoMerge)
                Response.Success(updated)
            },
        )

        subject.load("ws-1")
        subject.onFlagsChanged(original, autoMerge = true, runBuild = false, runTestSuite = false)

        assertEquals(true, subject.tasks.value.single().autoMerge)
        assertEquals(listOf("task-1"), subject.flagCalls)
    }

    @Test
    fun `failed start leaves the list untouched and sets error`() = runBlocking {
        val original = task("task-1")
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(listOf(original))) },
            startHiveTask = { Response.Error(ResponseError("boom")) },
        )

        subject.load("ws-1")
        subject.onStartClicked(original)

        assertEquals(listOf(original), subject.tasks.value)
        assertTrue(subject.error.value)
        assertEquals(listOf("boom"), subject.notifyErrors)
    }

    @Test
    fun `empty success list does not set error`() = runBlocking {
        val subject = TestableWorkspaceTasksViewModel(
            fetchHiveTasks = { _, _, _ -> Response.Success(tasksPage(emptyList())) }
        )

        subject.load("ws-1")

        assertTrue(subject.tasks.value.isEmpty())
        assertFalse(subject.error.value)
    }
}
