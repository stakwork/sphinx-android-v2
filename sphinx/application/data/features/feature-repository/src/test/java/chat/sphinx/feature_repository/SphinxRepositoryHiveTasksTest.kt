package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesPaginationDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto
import chat.sphinx.concept_network_query_hive.model.HiveTasksListDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.concept_relay.CustomException
import chat.sphinx.concept_repository_dashboard.model.HiveTask
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_contact.Contact
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.test_concept_coroutines.CoroutineTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SphinxRepositoryHiveTasksTest {

    private val storage = FakeImageAuthStorage()
    private val owner = MutableStateFlow<Contact?>(makeOwner())
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers: CoroutineDispatchers = CoroutineTestHelper.TestCoroutineDispatchers(
        unconfined, unconfined, unconfined, unconfined, unconfined
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + unconfined)

    private var authResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
    private var taskListResponses: MutableList<LoadResponse<HiveTasksListDto, ResponseError>> =
        mutableListOf()
    private var mutationResponses: MutableList<LoadResponse<HiveTaskMutationDto, ResponseError>> =
        mutableListOf()
    private val taskListCallCount = AtomicInteger(0)
    private val startCallCount = AtomicInteger(0)
    private val retryCallCount = AtomicInteger(0)
    private val statusCallCount = AtomicInteger(0)
    private val archivedCallCount = AtomicInteger(0)
    private val flagsCallCount = AtomicInteger(0)
    private val duplicateCallCount = AtomicInteger(0)
    private val dependsOnCallCount = AtomicInteger(0)
    private val mutationCallCount = AtomicInteger(0)
    private var authCallCount = 0
    private var lastIncludeArchived: Boolean? = null
    private var lastStatus: String? = null
    private var lastArchived: Boolean? = null
    private var lastFlags: Triple<Boolean, Boolean, Boolean>? = null
    private var lastDuplicateFeatureId: String? = null
    private var lastDuplicateBody: HiveTaskDuplicateDto? = null
    private var lastDependsOnTaskId: String? = null
    private var lastDependsOnIds: List<String>? = null

    private val fakeHive = object : NetworkQueryHive() {
        override fun authenticateWithHive(
            token: String,
            pubkey: String,
            timestamp: Long,
        ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> = flow {
            authCallCount++
            emit(authResponse)
        }

        override fun getWorkspaces(
            authToken: String,
        ): Flow<LoadResponse<WorkspacesListDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun getWorkspaceImage(
            slug: String,
            authToken: String,
        ): Flow<LoadResponse<WorkspaceImageDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun getFeatures(
            workspaceId: String,
            page: Int,
            authToken: String,
        ): Flow<LoadResponse<HiveFeaturesListDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun updateFeature(
            featureId: String,
            patch: HiveFeaturePatchDto,
            authToken: String,
        ): Flow<LoadResponse<HiveFeatureUpdateDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun deleteFeature(
            featureId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveDeleteResponseDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun getTasks(
            workspaceId: String,
            page: Int,
            includeArchived: Boolean,
            authToken: String,
        ): Flow<LoadResponse<HiveTasksListDto, ResponseError>> = flow {
            lastIncludeArchived = includeArchived
            val index = taskListCallCount.getAndIncrement()
            val response = if (index < taskListResponses.size) {
                taskListResponses[index]
            } else {
                taskListResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no tasks response"))
            }
            emit(response)
        }

        override fun startTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            startCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun retryTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            retryCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun updateTaskStatus(
            taskId: String,
            status: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            lastStatus = status
            statusCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun setTaskArchived(
            taskId: String,
            archived: Boolean,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            lastArchived = archived
            archivedCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun updateTaskFlags(
            taskId: String,
            autoMerge: Boolean,
            runBuild: Boolean,
            runTestSuite: Boolean,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            lastFlags = Triple(autoMerge, runBuild, runTestSuite)
            flagsCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun duplicateTask(
            featureId: String,
            body: HiveTaskDuplicateDto,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            lastDuplicateFeatureId = featureId
            lastDuplicateBody = body
            duplicateCallCount.getAndIncrement()
            emit(nextMutation())
        }

        override fun updateTaskDependsOn(
            taskId: String,
            dependsOnTaskIds: List<String>,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            lastDependsOnTaskId = taskId
            lastDependsOnIds = dependsOnTaskIds
            dependsOnCallCount.getAndIncrement()
            emit(nextMutation())
        }

        private fun nextMutation(): LoadResponse<HiveTaskMutationDto, ResponseError> {
            val index = mutationCallCount.getAndIncrement()
            return if (index < mutationResponses.size) {
                mutationResponses[index]
            } else {
                mutationResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no mutation response"))
            }
        }
    }

    @Before
    fun setUp() {
        storage.clear()
        authResponse = Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
        taskListResponses = mutableListOf()
        mutationResponses = mutableListOf()
        taskListCallCount.set(0)
        startCallCount.set(0)
        retryCallCount.set(0)
        statusCallCount.set(0)
        archivedCallCount.set(0)
        flagsCallCount.set(0)
        duplicateCallCount.set(0)
        dependsOnCallCount.set(0)
        mutationCallCount.set(0)
        authCallCount = 0
        lastIncludeArchived = null
        lastStatus = null
        lastArchived = null
        lastFlags = null
        lastDuplicateFeatureId = null
        lastDuplicateBody = null
        lastDependsOnTaskId = null
        lastDependsOnIds = null
    }

    private fun makeRepo(): TestSphinxRepository =
        TestSphinxRepository(
            accountOwner = owner,
            applicationScope = applicationScope,
            authenticationStorage = storage,
            networkQueryHive = fakeHive,
            connectManager = FakeConnectManager(),
            dispatchers = dispatchers,
        )

    private suspend fun storeValidToken(jwt: String = "valid-jwt") {
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L
        storage.putString(
            SphinxRepository.HIVE_AUTHENTICATION_TOKEN,
            "$jwt${SphinxRepository.HIVE_TOKEN_DELIMITER}$expiresAt"
        )
    }

    private fun errorWithCode(code: Int): Response.Error<ResponseError> =
        Response.Error(
            ResponseError(
                "HTTP $code",
                CustomException("HTTP $code", code),
            )
        )

    private fun successList(
        vararg tasks: HiveTaskDto,
        page: Int = 1,
        hasMore: Boolean = false,
    ): LoadResponse<HiveTasksListDto, ResponseError> =
        Response.Success(
            HiveTasksListDto(
                data = tasks.toList(),
                pagination = HiveFeaturesPaginationDto(
                    page = page,
                    totalPages = 1,
                    totalCount = tasks.size,
                    hasMore = hasMore,
                ),
                error = null,
            )
        )

    private fun mutationSuccess(
        task: HiveTaskDto? = HiveTaskDto(id = "task-1", title = "One", status = "IN_PROGRESS"),
    ): LoadResponse<HiveTaskMutationDto, ResponseError> =
        Response.Success(HiveTaskMutationDto(success = true, data = task))

    private fun sampleTask(
        featureId: String? = "feat-1",
        priority: String? = "HIGH",
        description: String? = "Details",
        phaseId: String? = "ph-1",
        repositoryId: String? = "repo-1",
        dependsOnTaskIds: List<String> = listOf("dep-1"),
    ): HiveTask = HiveTask(
        id = "task-1",
        title = "Copy me",
        description = description,
        status = "IN_PROGRESS",
        priority = priority,
        featureId = featureId,
        repositoryId = repositoryId,
        dependsOnTaskIds = dependsOnTaskIds,
        workflowId = "wf-1",
        workflowName = "Build",
        workflowRefId = "ref-1",
        workflowTaskType = "TICKET",
        workflowVersionId = "v-1",
        phaseId = phaseId,
    )

    @Test
    fun `fetchHiveTasks maps a successful page`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        taskListResponses.add(
            successList(HiveTaskDto(id = "task-1", title = "One", status = "TODO"))
        )

        val response = repo.fetchHiveTasks("ws-1", 1, includeArchived = false)
        assertTrue(response is Response.Success)
        val page = (response as Response.Success).value
        assertEquals(1, page.tasks.size)
        assertEquals("task-1", page.tasks[0].id)
        assertEquals("One", page.tasks[0].title)
        assertEquals("TODO", page.tasks[0].status)
        assertEquals(1, page.page)
        assertFalse(page.hasMore)
        assertEquals(false, lastIncludeArchived)
        assertEquals(1, taskListCallCount.get())
        assertEquals(0, authCallCount)
    }

    @Test
    fun `fetchHiveTasks applies pagination defaults when pagination is missing`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        taskListResponses.add(
            Response.Success(
                HiveTasksListDto(
                    data = listOf(HiveTaskDto(id = "task-1", title = "One")),
                    pagination = null,
                    error = null,
                )
            )
        )

        val response = repo.fetchHiveTasks("ws-1", 3, includeArchived = true)
        assertTrue(response is Response.Success)
        val page = (response as Response.Success).value
        assertEquals(1, page.page)
        assertEquals(1, page.totalPages)
        assertEquals(0, page.totalCount)
        assertFalse(page.hasMore)
        assertEquals(1, page.tasks.size)
        assertEquals(true, lastIncludeArchived)
    }

    @Test
    fun `fetchHiveTasks treats error field as failure`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        taskListResponses.add(
            Response.Success(
                HiveTasksListDto(
                    data = listOf(HiveTaskDto(id = "task-1", title = "One")),
                    error = "cannot list",
                )
            )
        )

        val response = repo.fetchHiveTasks("ws-1", 1, includeArchived = false)
        assertTrue(response is Response.Error)
        assertEquals(1, taskListCallCount.get())
        assertEquals(0, storage.removeCallCount)
    }

    @Test
    fun `fetchHiveTasks treats missing data as failure`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        taskListResponses.add(
            Response.Success(HiveTasksListDto(data = null, error = null))
        )

        val response = repo.fetchHiveTasks("ws-1", 1, includeArchived = false)
        assertTrue(response is Response.Error)
        assertEquals(1, taskListCallCount.get())
    }

    @Test
    fun `fetchHiveTasks retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        taskListResponses.add(errorWithCode(401))
        taskListResponses.add(
            successList(HiveTaskDto(id = "task-retry", title = "Retry"))
        )

        val response = repo.fetchHiveTasks("ws-1", 1, includeArchived = false)
        assertTrue(response is Response.Success)
        assertEquals("task-retry", (response as Response.Success).value.tasks[0].id)
        assertEquals(2, taskListCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `fetchHiveTasks 403 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        taskListResponses.add(errorWithCode(403))

        val response = repo.fetchHiveTasks("ws-1", 1, includeArchived = false)
        assertTrue(response is Response.Error)
        assertEquals(1, taskListCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }

    @Test
    fun `startHiveTask returns mapped data and never sends status`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", status = "IN_PROGRESS"))
        )

        val response = repo.startHiveTask("task-1")
        assertTrue(response is Response.Success)
        assertEquals("IN_PROGRESS", (response as Response.Success).value?.status)
        assertEquals(1, startCallCount.get())
        assertEquals(0, statusCallCount.get())
        assertNull(lastStatus)
    }

    @Test
    fun `startHiveTask success without data returns null`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(Response.Success(HiveTaskMutationDto(success = true, data = null)))

        val response = repo.startHiveTask("task-1")
        assertTrue(response is Response.Success)
        assertNull((response as Response.Success).value)
        assertEquals(1, startCallCount.get())
    }

    @Test
    fun `startHiveTask retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        mutationResponses.add(errorWithCode(401))
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", status = "IN_PROGRESS"))
        )

        val response = repo.startHiveTask("task-1")
        assertTrue(response is Response.Success)
        assertEquals(2, startCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `retryHiveTask never sends status`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", workflowStatus = "IN_PROGRESS"))
        )

        val response = repo.retryHiveTask("task-1")
        assertTrue(response is Response.Success)
        assertEquals(1, retryCallCount.get())
        assertEquals(0, statusCallCount.get())
        assertNull(lastStatus)
    }

    @Test
    fun `retryHiveTask 403 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(errorWithCode(403))

        val response = repo.retryHiveTask("task-1")
        assertTrue(response is Response.Error)
        assertEquals(1, retryCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }

    @Test
    fun `updateHiveTaskStatus rejects values outside allowlist without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val response = repo.updateHiveTaskStatus("task-1", "COMPLETED")
        assertTrue(response is Response.Error)
        assertEquals(0, statusCallCount.get())
        assertNull(lastStatus)
    }

    @Test
    fun `updateHiveTaskStatus sends DONE for mark complete`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", status = "DONE"))
        )

        val response = repo.updateHiveTaskStatus("task-1", "DONE")
        assertTrue(response is Response.Success)
        assertEquals("DONE", (response as Response.Success).value?.status)
        assertEquals("DONE", lastStatus)
        assertEquals(1, statusCallCount.get())
    }

    @Test
    fun `updateHiveTaskStatus allows TODO and IN_PROGRESS`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", status = "TODO"))
        )
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", status = "IN_PROGRESS"))
        )

        assertTrue(repo.updateHiveTaskStatus("task-1", "TODO") is Response.Success)
        assertTrue(repo.updateHiveTaskStatus("task-1", "IN_PROGRESS") is Response.Success)
        assertEquals(2, statusCallCount.get())
        assertEquals("IN_PROGRESS", lastStatus)
    }

    @Test
    fun `setHiveTaskArchived true and false hit network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", archived = true))
        )
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-1", title = "One", archived = false))
        )

        val archived = repo.setHiveTaskArchived("task-1", true)
        assertTrue(archived is Response.Success)
        assertEquals(true, lastArchived)

        val unarchived = repo.setHiveTaskArchived("task-1", false)
        assertTrue(unarchived is Response.Success)
        assertEquals(false, lastArchived)
        assertEquals(2, archivedCallCount.get())
    }

    @Test
    fun `updateHiveTaskFlags forwards all three flags`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(
                HiveTaskDto(
                    id = "task-1",
                    title = "One",
                    autoMerge = true,
                    runBuild = false,
                    runTestSuite = true,
                )
            )
        )

        val response = repo.updateHiveTaskFlags("task-1", true, false, true)
        assertTrue(response is Response.Success)
        assertEquals(Triple(true, false, true), lastFlags)
        assertEquals(true, (response as Response.Success).value?.autoMerge)
        assertEquals(false, response.value?.runBuild)
        assertEquals(true, response.value?.runTestSuite)
        assertEquals(1, flagsCallCount.get())
    }

    @Test
    fun `mutation success false is failure`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(Response.Success(HiveTaskMutationDto(success = false, data = null)))

        val response = repo.startHiveTask("task-1")
        assertTrue(response is Response.Error)
        assertEquals(1, startCallCount.get())
    }

    @Test
    fun `duplicateHiveTask rejects blank featureId without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val blank = repo.duplicateHiveTask(sampleTask(featureId = null))
        val empty = repo.duplicateHiveTask(sampleTask(featureId = "  "))
        assertTrue(blank is Response.Error)
        assertTrue(empty is Response.Error)
        assertEquals(0, duplicateCallCount.get())
        assertNull(lastDuplicateBody)
    }

    @Test
    fun `duplicateHiveTask sends complete body including optionals`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-copy", title = "Copy me", status = "TODO"))
        )

        val response = repo.duplicateHiveTask(sampleTask())
        assertTrue(response is Response.Success)
        assertEquals("task-copy", (response as Response.Success).value.id)
        assertEquals("feat-1", lastDuplicateFeatureId)
        val body = lastDuplicateBody!!
        assertEquals("Copy me", body.title)
        assertEquals("TODO", body.status)
        assertEquals(false, body.autoMerge)
        assertEquals("HIGH", body.priority)
        assertEquals("Details", body.description)
        assertEquals("ph-1", body.phaseId)
        assertEquals("repo-1", body.repositoryId)
        assertEquals(listOf("dep-1"), body.dependsOnTaskIds)
        assertEquals("wf-1", body.workflowId)
        assertEquals("Build", body.workflowName)
        assertEquals("ref-1", body.workflowRefId)
        assertEquals("TICKET", body.workflowTaskType)
        assertEquals("v-1", body.workflowVersionId)
        assertEquals(1, duplicateCallCount.get())
    }

    @Test
    fun `duplicateHiveTask uses LOW when source priority is null and omits empty deps`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-copy", title = "Copy me"))
        )

        val response = repo.duplicateHiveTask(
            sampleTask(
                priority = null,
                description = null,
                phaseId = null,
                repositoryId = null,
                dependsOnTaskIds = emptyList(),
            )
        )
        assertTrue(response is Response.Success)
        val body = lastDuplicateBody!!
        assertEquals("LOW", body.priority)
        assertEquals("TODO", body.status)
        assertEquals(false, body.autoMerge)
        assertNull(body.description)
        assertNull(body.phaseId)
        assertNull(body.repositoryId)
        assertNull(body.dependsOnTaskIds)
        assertEquals("wf-1", body.workflowId)
    }

    @Test
    fun `duplicateHiveTask fails when success is false or data is missing`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(Response.Success(HiveTaskMutationDto(success = false, data = null)))

        val failed = repo.duplicateHiveTask(sampleTask())
        assertTrue(failed is Response.Error)
        assertEquals(1, duplicateCallCount.get())

        mutationResponses = mutableListOf(
            Response.Success(HiveTaskMutationDto(success = true, data = null))
        )
        duplicateCallCount.set(0)
        mutationCallCount.set(0)
        val missing = repo.duplicateHiveTask(sampleTask())
        assertTrue(missing is Response.Error)
    }

    @Test
    fun `duplicateHiveTask retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        mutationResponses.add(errorWithCode(401))
        mutationResponses.add(
            mutationSuccess(HiveTaskDto(id = "task-copy", title = "Copy me"))
        )

        val response = repo.duplicateHiveTask(sampleTask())
        assertTrue(response is Response.Success)
        assertEquals(2, duplicateCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `updateHiveTaskDependsOn rejects blank ids without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val blankTask = repo.updateHiveTaskDependsOn("  ", listOf("dep-1"))
        val blankDep = repo.updateHiveTaskDependsOn("task-1", listOf("dep-1", ""))
        assertTrue(blankTask is Response.Error)
        assertTrue(blankDep is Response.Error)
        assertEquals(0, dependsOnCallCount.get())
    }

    @Test
    fun `updateHiveTaskDependsOn returns true on success`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(Response.Success(HiveTaskMutationDto(success = true, data = null)))

        val response = repo.updateHiveTaskDependsOn("task-1", listOf("dep-1", "dep-2"))
        assertTrue(response is Response.Success)
        assertEquals(true, (response as Response.Success).value)
        assertEquals("task-1", lastDependsOnTaskId)
        assertEquals(listOf("dep-1", "dep-2"), lastDependsOnIds)
        assertEquals(1, dependsOnCallCount.get())
    }

    @Test
    fun `updateHiveTaskDependsOn 403 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        mutationResponses.add(errorWithCode(403))

        val response = repo.updateHiveTaskDependsOn("task-1", listOf("dep-1"))
        assertTrue(response is Response.Error)
        assertEquals(1, dependsOnCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }
}
