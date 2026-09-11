package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
import chat.sphinx.concept_network_query_hive.model.HivePodDto
import chat.sphinx.concept_network_query_hive.model.HivePodResourceQuantityDto
import chat.sphinx.concept_network_query_hive.model.HivePodResourceUsageDto
import chat.sphinx.concept_network_query_hive.model.HivePodsListDataDto
import chat.sphinx.concept_network_query_hive.model.HivePodsListDto
import chat.sphinx.concept_network_query_hive.model.HivePoolStatusCountsDto
import chat.sphinx.concept_network_query_hive.model.HivePoolStatusDataDto
import chat.sphinx.concept_network_query_hive.model.HivePoolStatusDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto
import chat.sphinx.concept_network_query_hive.model.HiveTasksListDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.concept_relay.CustomException
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SphinxRepositoryHivePodsTest {

    private val storage = FakeImageAuthStorage()
    private val owner = MutableStateFlow<Contact?>(makeOwner())
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers: CoroutineDispatchers = CoroutineTestHelper.TestCoroutineDispatchers(
        unconfined, unconfined, unconfined, unconfined, unconfined
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + unconfined)

    private var authResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
    private var poolResponses: MutableList<LoadResponse<HivePoolStatusDto, ResponseError>> =
        mutableListOf()
    private var basicResponses: MutableList<LoadResponse<HivePodsListDto, ResponseError>> =
        mutableListOf()
    private var fullResponses: MutableList<LoadResponse<HivePodsListDto, ResponseError>> =
        mutableListOf()
    private val poolCallCount = AtomicInteger(0)
    private val basicCallCount = AtomicInteger(0)
    private val fullCallCount = AtomicInteger(0)
    private var authCallCount = 0

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
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun startTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun retryTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun updateTaskStatus(
            taskId: String,
            status: String,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun setTaskArchived(
            taskId: String,
            archived: Boolean,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun updateTaskFlags(
            taskId: String,
            autoMerge: Boolean,
            runBuild: Boolean,
            runTestSuite: Boolean,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun duplicateTask(
            featureId: String,
            body: HiveTaskDuplicateDto,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun updateTaskDependsOn(
            taskId: String,
            dependsOnTaskIds: List<String>,
            authToken: String,
        ): Flow<LoadResponse<HiveTaskMutationDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun getPoolStatus(
            slug: String,
            authToken: String,
        ): Flow<LoadResponse<HivePoolStatusDto, ResponseError>> = flow {
            val index = poolCallCount.getAndIncrement()
            val response = if (index < poolResponses.size) {
                poolResponses[index]
            } else {
                poolResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no pool response"))
            }
            emit(response)
        }

        override fun getBasicPods(
            slug: String,
            authToken: String,
        ): Flow<LoadResponse<HivePodsListDto, ResponseError>> = flow {
            val index = basicCallCount.getAndIncrement()
            val response = if (index < basicResponses.size) {
                basicResponses[index]
            } else {
                basicResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no basic pods response"))
            }
            emit(response)
        }

        override fun getFullPods(
            slug: String,
            authToken: String,
        ): Flow<LoadResponse<HivePodsListDto, ResponseError>> = flow {
            val index = fullCallCount.getAndIncrement()
            val response = if (index < fullResponses.size) {
                fullResponses[index]
            } else {
                fullResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no full pods response"))
            }
            emit(response)
        }
    }

    @Before
    fun setUp() {
        storage.clear()
        authResponse = Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
        poolResponses = mutableListOf()
        basicResponses = mutableListOf()
        fullResponses = mutableListOf()
        poolCallCount.set(0)
        basicCallCount.set(0)
        fullCallCount.set(0)
        authCallCount = 0
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

    private fun poolSuccess(
        queuedCount: Int? = 2,
        unusedVms: Int? = 3,
        success: Boolean = true,
    ): LoadResponse<HivePoolStatusDto, ResponseError> =
        Response.Success(
            HivePoolStatusDto(
                success = success,
                data = HivePoolStatusDataDto(
                    status = HivePoolStatusCountsDto(
                        queuedCount = queuedCount,
                        unusedVms = unusedVms,
                    )
                )
            )
        )

    private fun podsSuccess(
        vararg pods: HivePodDto,
        success: Boolean = true,
    ): LoadResponse<HivePodsListDto, ResponseError> =
        Response.Success(
            HivePodsListDto(
                success = success,
                data = HivePodsListDataDto(workspaces = pods.toList()),
            )
        )

    private fun podDto(
        id: String? = "pod-1",
        subdomain: String? = "alpha",
        state: String? = "running",
        internalState: String? = null,
        usageStatus: String? = "used",
        resourceUsage: HivePodResourceUsageDto? = null,
    ) = HivePodDto(
        id = id,
        subdomain = subdomain,
        state = state,
        internalState = internalState,
        usageStatus = usageStatus,
        resourceUsage = resourceUsage,
    )

    @Test
    fun `fetchHivePoolStatus maps queuedCount and unusedVms`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        poolResponses.add(poolSuccess(queuedCount = 4, unusedVms = 7))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Success)
        val status = (response as Response.Success).value
        assertEquals(4, status.queuedCount)
        assertEquals(7, status.unusedVms)
        assertEquals(1, poolCallCount.get())
        assertEquals(0, authCallCount)
    }

    @Test
    fun `fetchHivePoolStatus treats success false as error`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        poolResponses.add(poolSuccess(success = false))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, poolCallCount.get())
        assertEquals(0, storage.removeCallCount)
    }

    @Test
    fun `fetchHivePoolStatus missing queuedCount fails`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        poolResponses.add(poolSuccess(queuedCount = null, unusedVms = 1))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, poolCallCount.get())
    }

    @Test
    fun `fetchHivePoolStatus missing unusedVms fails`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        poolResponses.add(poolSuccess(queuedCount = 1, unusedVms = null))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, poolCallCount.get())
    }

    @Test
    fun `fetchHivePoolStatus retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        poolResponses.add(errorWithCode(401))
        poolResponses.add(poolSuccess(queuedCount = 1, unusedVms = 2))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Success)
        assertEquals(1, (response as Response.Success).value.queuedCount)
        assertEquals(2, poolCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `fetchHivePoolStatus 403 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        poolResponses.add(errorWithCode(403))

        val response = repo.fetchHivePoolStatus("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, poolCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }

    @Test
    fun `fetchHiveBasicPods maps pods and drops incomplete rows`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        basicResponses.add(
            podsSuccess(
                podDto(id = "pod-1", subdomain = "alpha", state = "running"),
                podDto(id = null, subdomain = "missing-id", state = "running"),
                podDto(id = "pod-blank", subdomain = " ", state = "pending"),
                podDto(id = "pod-no-state", subdomain = "gamma", state = null),
            )
        )

        val response = repo.fetchHiveBasicPods("ws-slug")
        assertTrue(response is Response.Success)
        val pods = (response as Response.Success).value
        assertEquals(1, pods.size)
        assertEquals("pod-1", pods[0].id)
        assertEquals("alpha", pods[0].subdomain)
        assertEquals("running", pods[0].state)
        assertEquals(1, basicCallCount.get())
    }

    @Test
    fun `fetchHiveBasicPods treats success false as error`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        basicResponses.add(podsSuccess(success = false))

        val response = repo.fetchHiveBasicPods("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, basicCallCount.get())
    }

    @Test
    fun `fetchHiveBasicPods retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        basicResponses.add(errorWithCode(401))
        basicResponses.add(podsSuccess(podDto(id = "pod-retry", subdomain = "retry", state = "pending")))

        val response = repo.fetchHiveBasicPods("ws-slug")
        assertTrue(response is Response.Success)
        assertEquals("pod-retry", (response as Response.Success).value[0].id)
        assertEquals(2, basicCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `fetchHiveFullPods maps resource usage strings`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        fullResponses.add(
            podsSuccess(
                podDto(
                    id = "pod-1",
                    subdomain = "alpha",
                    state = "running",
                    internalState = "ready",
                    usageStatus = "used",
                    resourceUsage = HivePodResourceUsageDto(
                        available = true,
                        requests = HivePodResourceQuantityDto(cpu = "100m", memory = "128Mi"),
                        usage = HivePodResourceQuantityDto(cpu = "50m", memory = "64Mi"),
                    ),
                )
            )
        )

        val response = repo.fetchHiveFullPods("ws-slug")
        assertTrue(response is Response.Success)
        val pod = (response as Response.Success).value.single()
        assertEquals("ready", pod.internalState)
        assertEquals("100m", pod.resourceUsage!!.requestsCpu)
        assertEquals("128Mi", pod.resourceUsage!!.requestsMemory)
        assertEquals("50m", pod.resourceUsage!!.usageCpu)
        assertEquals("64Mi", pod.resourceUsage!!.usageMemory)
    }

    @Test
    fun `fetchHiveFullPods treats success false as error`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        fullResponses.add(podsSuccess(success = false))

        val response = repo.fetchHiveFullPods("ws-slug")
        assertTrue(response is Response.Error)
        assertEquals(1, fullCallCount.get())
    }

    @Test
    fun `fetchHiveFullPods retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        fullResponses.add(errorWithCode(401))
        fullResponses.add(podsSuccess(podDto(id = "pod-full", subdomain = "full", state = "running")))

        val response = repo.fetchHiveFullPods("ws-slug")
        assertTrue(response is Response.Success)
        assertEquals("pod-full", (response as Response.Success).value[0].id)
        assertEquals(2, fullCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `fetchHiveFullPods empty workspaces is success`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        fullResponses.add(podsSuccess())

        val response = repo.fetchHiveFullPods("ws-slug")
        assertTrue(response is Response.Success)
        assertTrue((response as Response.Success).value.isEmpty())
    }
}
