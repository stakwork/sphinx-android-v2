package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto
import chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto
import chat.sphinx.concept_network_query_hive.model.HiveFeaturesPaginationDto
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SphinxRepositoryHiveFeaturesTest {

    private val storage = FakeImageAuthStorage()
    private val owner = MutableStateFlow<Contact?>(makeOwner())
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers: CoroutineDispatchers = CoroutineTestHelper.TestCoroutineDispatchers(
        unconfined, unconfined, unconfined, unconfined, unconfined
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + unconfined)

    private var authResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
    private var featureListResponses: MutableList<LoadResponse<HiveFeaturesListDto, ResponseError>> =
        mutableListOf()
    private var updateResponses: MutableList<LoadResponse<HiveFeatureUpdateDto, ResponseError>> =
        mutableListOf()
    private var deleteResponses: MutableList<LoadResponse<HiveDeleteResponseDto, ResponseError>> =
        mutableListOf()
    private val featureListCallCount = AtomicInteger(0)
    private val updateCallCount = AtomicInteger(0)
    private val deleteCallCount = AtomicInteger(0)
    private var authCallCount = 0
    private var lastPatch: HiveFeaturePatchDto? = null

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
            val index = featureListCallCount.getAndIncrement()
            val response = if (index < featureListResponses.size) {
                featureListResponses[index]
            } else {
                featureListResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no features response"))
            }
            emit(response)
        }

        override fun updateFeature(
            featureId: String,
            patch: HiveFeaturePatchDto,
            authToken: String,
        ): Flow<LoadResponse<HiveFeatureUpdateDto, ResponseError>> = flow {
            lastPatch = patch
            val index = updateCallCount.getAndIncrement()
            val response = if (index < updateResponses.size) {
                updateResponses[index]
            } else {
                updateResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no update response"))
            }
            emit(response)
        }

        override fun deleteFeature(
            featureId: String,
            authToken: String,
        ): Flow<LoadResponse<HiveDeleteResponseDto, ResponseError>> = flow {
            val index = deleteCallCount.getAndIncrement()
            val response = if (index < deleteResponses.size) {
                deleteResponses[index]
            } else {
                deleteResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no delete response"))
            }
            emit(response)
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
    }

    @Before
    fun setUp() {
        storage.clear()
        authResponse = Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
        featureListResponses = mutableListOf()
        updateResponses = mutableListOf()
        deleteResponses = mutableListOf()
        featureListCallCount.set(0)
        updateCallCount.set(0)
        deleteCallCount.set(0)
        authCallCount = 0
        lastPatch = null
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
        vararg features: HiveFeatureDto,
        page: Int = 1,
        hasMore: Boolean = false,
    ): LoadResponse<HiveFeaturesListDto, ResponseError> =
        Response.Success(
            HiveFeaturesListDto(
                success = true,
                data = features.toList(),
                pagination = HiveFeaturesPaginationDto(
                    page = page,
                    totalPages = 1,
                    totalCount = features.size,
                    hasMore = hasMore,
                )
            )
        )

    @Test
    fun `fetchHiveFeatures maps a successful page`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        featureListResponses.add(
            successList(HiveFeatureDto(id = "feat-1", title = "One", status = "BACKLOG", priority = "LOW"))
        )

        val response = repo.fetchHiveFeatures("ws-1", 1)
        assertTrue(response is Response.Success)
        val page = (response as Response.Success).value
        assertEquals(1, page.features.size)
        assertEquals("feat-1", page.features[0].id)
        assertEquals("One", page.features[0].title)
        assertEquals(1, page.page)
        assertFalse(page.hasMore)
        assertEquals(1, featureListCallCount.get())
        assertEquals(0, authCallCount)
    }

    @Test
    fun `fetchHiveFeatures applies pagination defaults when pagination is missing`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        featureListResponses.add(
            Response.Success(
                HiveFeaturesListDto(
                    success = true,
                    data = listOf(HiveFeatureDto(id = "feat-1", title = "One")),
                    pagination = null,
                )
            )
        )

        val response = repo.fetchHiveFeatures("ws-1", 3)
        assertTrue(response is Response.Success)
        val page = (response as Response.Success).value
        assertEquals(1, page.page)
        assertEquals(1, page.totalPages)
        assertEquals(0, page.totalCount)
        assertFalse(page.hasMore)
        assertEquals(1, page.features.size)
    }

    @Test
    fun `fetchHiveFeatures treats success false as error`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        featureListResponses.add(
            Response.Success(HiveFeaturesListDto(success = false, data = emptyList()))
        )

        val response = repo.fetchHiveFeatures("ws-1", 1)
        assertTrue(response is Response.Error)
        assertEquals(1, featureListCallCount.get())
        assertEquals(0, storage.removeCallCount)
    }

    @Test
    fun `fetchHiveFeatures retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        featureListResponses.add(errorWithCode(401))
        featureListResponses.add(
            successList(HiveFeatureDto(id = "feat-retry", title = "Retry"))
        )

        val response = repo.fetchHiveFeatures("ws-1", 1)
        assertTrue(response is Response.Success)
        assertEquals("feat-retry", (response as Response.Success).value.features[0].id)
        assertEquals(2, featureListCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `fetchHiveFeatures 403 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        featureListResponses.add(errorWithCode(403))

        val response = repo.fetchHiveFeatures("ws-1", 1)
        assertTrue(response is Response.Error)
        assertEquals(1, featureListCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }

    @Test
    fun `updateHiveFeature rejects both null without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val response = repo.updateHiveFeature("feat-1", null, null)
        assertTrue(response is Response.Error)
        assertEquals(0, updateCallCount.get())
    }

    @Test
    fun `updateHiveFeature rejects status outside allowlist without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val response = repo.updateHiveFeature("feat-1", "DONE", null)
        assertTrue(response is Response.Error)
        assertEquals(0, updateCallCount.get())
    }

    @Test
    fun `updateHiveFeature rejects priority outside allowlist without network`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()

        val response = repo.updateHiveFeature("feat-1", null, "URGENT")
        assertTrue(response is Response.Error)
        assertEquals(0, updateCallCount.get())
    }

    @Test
    fun `updateHiveFeature retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        updateResponses.add(errorWithCode(401))
        updateResponses.add(
            Response.Success(
                HiveFeatureUpdateDto(
                    data = HiveFeatureDto(id = "feat-1", title = "One", status = "PLANNED", priority = "HIGH")
                )
            )
        )

        val response = repo.updateHiveFeature("feat-1", "PLANNED", "HIGH")
        assertTrue(response is Response.Success)
        assertEquals("PLANNED", (response as Response.Success).value.status)
        assertEquals(2, updateCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
        assertEquals("PLANNED", lastPatch?.status)
        assertEquals("HIGH", lastPatch?.priority)
    }

    @Test
    fun `updateHiveFeature 404 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        updateResponses.add(errorWithCode(404))

        val response = repo.updateHiveFeature("feat-1", "PLANNED", null)
        assertTrue(response is Response.Error)
        assertEquals(1, updateCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }

    @Test
    fun `updateHiveFeature fails when error is set`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        updateResponses.add(
            Response.Success(HiveFeatureUpdateDto(error = "nope", data = null))
        )

        val response = repo.updateHiveFeature("feat-1", "PLANNED", null)
        assertTrue(response is Response.Error)
        assertEquals(1, updateCallCount.get())
    }

    @Test
    fun `updateHiveFeature fails when data is missing`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        updateResponses.add(Response.Success(HiveFeatureUpdateDto(data = null)))

        val response = repo.updateHiveFeature("feat-1", "PLANNED", null)
        assertTrue(response is Response.Error)
    }

    @Test
    fun `deleteHiveFeature empty 2xx body is success`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        deleteResponses.add(Response.Success(HiveDeleteResponseDto(success = null)))

        val response = repo.deleteHiveFeature("feat-1")
        assertTrue(response is Response.Success)
        assertEquals(true, (response as Response.Success).value)
        assertEquals(1, deleteCallCount.get())
    }

    @Test
    fun `deleteHiveFeature success false is failure`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        deleteResponses.add(Response.Success(HiveDeleteResponseDto(success = false)))

        val response = repo.deleteHiveFeature("feat-1")
        assertTrue(response is Response.Error)
        assertEquals(1, deleteCallCount.get())
        assertEquals(0, storage.removeCallCount)
    }

    @Test
    fun `deleteHiveFeature retries once on 401`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        deleteResponses.add(errorWithCode(401))
        deleteResponses.add(Response.Success(HiveDeleteResponseDto(success = true)))

        val response = repo.deleteHiveFeature("feat-1")
        assertTrue(response is Response.Success)
        assertEquals(2, deleteCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `deleteHiveFeature 500 is terminal`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        deleteResponses.add(errorWithCode(500))

        val response = repo.deleteHiveFeature("feat-1")
        assertTrue(response is Response.Error)
        assertEquals(1, deleteCallCount.get())
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
    }
}
