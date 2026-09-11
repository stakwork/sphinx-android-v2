package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.HivePod
import chat.sphinx.concept_repository_dashboard.model.HivePodResourceUsage
import chat.sphinx.concept_repository_dashboard.model.HivePoolStatus
import chat.sphinx.concept_repository_dashboard.model.mergeHivePods
import chat.sphinx.concept_repository_dashboard.model.sortHivePods
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestableWorkspacePodsViewModel(
    private val fetchHivePoolStatus: suspend (slug: String) -> Response<HivePoolStatus, ResponseError>,
    private val fetchHiveBasicPods: suspend (slug: String) -> Response<List<HivePod>, ResponseError>,
    private val fetchHiveFullPods: suspend (slug: String) -> Response<List<HivePod>, ResponseError>,
) {
    private val _pods = MutableStateFlow<List<HivePod>>(emptyList())
    val pods: StateFlow<List<HivePod>> = _pods.asStateFlow()

    private val _poolStatus = MutableStateFlow<HivePoolStatus?>(null)
    val poolStatus: StateFlow<HivePoolStatus?> = _poolStatus.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    val poolCalls = mutableListOf<String>()
    val basicCalls = mutableListOf<String>()
    val fullCalls = mutableListOf<String>()

    private var workspaceSlug: String? = null
    private var generation: Int = 0
    private var fetchJob: Job? = null

    suspend fun load(workspaceId: String, workspaceSlug: String?) {
        if (workspaceSlug.isNullOrBlank()) {
            this.workspaceSlug = workspaceSlug
            _pods.value = emptyList()
            _poolStatus.value = null
            _error.value = true
            _loading.value = false
            return
        }
        this.workspaceSlug = workspaceSlug
        _pods.value = emptyList()
        _poolStatus.value = null
        _error.value = false
        loadAll()
    }

    suspend fun refresh() {
        if (workspaceSlug.isNullOrBlank()) {
            _pods.value = emptyList()
            _poolStatus.value = null
            _error.value = true
            _loading.value = false
            return
        }
        loadAll()
    }

    fun currentGeneration(): Int = generation

    // Test-only: simulate an in-flight full-list that can be dropped by a later refresh.
    suspend fun applyFullWithGeneration(
        basicPods: List<HivePod>,
        fullPods: List<HivePod>,
        expectedGeneration: Int,
    ) {
        if (expectedGeneration != generation) {
            return
        }
        _pods.value = sortHivePods(mergeHivePods(basicPods, fullPods))
    }

    private suspend fun loadAll() {
        val slug = workspaceSlug ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        _loading.value = _pods.value.isEmpty()
        _error.value = false
        try {
            coroutineScope {
                launch { fetchPoolStatus(slug, expectedGeneration) }
                fetchBasicThenFull(slug, expectedGeneration)
            }
        } finally {
            if (expectedGeneration == generation) {
                _loading.value = false
            }
        }
    }

    private suspend fun fetchPoolStatus(slug: String, expectedGeneration: Int) {
        poolCalls.add(slug)
        try {
            when (val response = fetchHivePoolStatus(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) return
                    _poolStatus.value = response.value
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) return
                    _poolStatus.value = null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (expectedGeneration != generation) return
            _poolStatus.value = null
        }
    }

    private suspend fun fetchBasicThenFull(slug: String, expectedGeneration: Int) {
        basicCalls.add(slug)
        try {
            when (val basicResponse = fetchHiveBasicPods(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) return
                    _pods.value = sortHivePods(basicResponse.value)
                    _error.value = false
                    fetchFullAndMerge(slug, basicResponse.value, expectedGeneration)
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) return
                    _pods.value = emptyList()
                    _error.value = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (expectedGeneration != generation) return
            _pods.value = emptyList()
            _error.value = true
        }
    }

    private suspend fun fetchFullAndMerge(
        slug: String,
        basicPods: List<HivePod>,
        expectedGeneration: Int,
    ) {
        fullCalls.add(slug)
        try {
            when (val fullResponse = fetchHiveFullPods(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) return
                    _pods.value = sortHivePods(mergeHivePods(basicPods, fullResponse.value))
                }
                is Response.Error -> {
                    // Keep basic without capacity.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep basic without capacity.
        }
    }
}

class WorkspacePodsViewModelTest {

    private fun pod(
        id: String,
        subdomain: String = id,
        state: String = "running",
        internalState: String? = null,
        usageStatus: String? = "unused",
        resourceUsage: HivePodResourceUsage? = null,
    ) = HivePod(
        id = id,
        subdomain = subdomain,
        state = state,
        internalState = internalState,
        usageStatus = usageStatus,
        resourceUsage = resourceUsage,
    )

    private fun usage() = HivePodResourceUsage(
        available = true,
        requestsCpu = "100m",
        requestsMemory = "128Mi",
        usageCpu = "50m",
        usageMemory = "64Mi",
    )

    @Test
    fun `blank slug sets error with zero repository calls`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { error("pool should not be called") },
            fetchHiveBasicPods = { error("basic should not be called") },
            fetchHiveFullPods = { error("full should not be called") },
        )

        subject.load("ws-1", "   ")

        assertTrue(subject.error.value)
        assertTrue(subject.pods.value.isEmpty())
        assertNull(subject.poolStatus.value)
        assertTrue(subject.poolCalls.isEmpty())
        assertTrue(subject.basicCalls.isEmpty())
        assertTrue(subject.fullCalls.isEmpty())
    }

    @Test
    fun `null slug sets error with zero repository calls`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { error("pool should not be called") },
            fetchHiveBasicPods = { error("basic should not be called") },
            fetchHiveFullPods = { error("full should not be called") },
        )

        subject.load("ws-1", null)

        assertTrue(subject.error.value)
        assertTrue(subject.pods.value.isEmpty())
        assertTrue(subject.poolCalls.isEmpty())
        assertTrue(subject.basicCalls.isEmpty())
        assertTrue(subject.fullCalls.isEmpty())
    }

    @Test
    fun `two-phase load renders basic before full resolves then merges`() = runBlocking {
        val fullGate = CompletableDeferred<Unit>()
        val basicSeen = CompletableDeferred<Unit>()
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Success(HivePoolStatus(1, 2)) },
            fetchHiveBasicPods = {
                Response.Success(
                    listOf(pod(id = "pod-1", state = "pending", usageStatus = "used"))
                )
            },
            fetchHiveFullPods = {
                basicSeen.complete(Unit)
                fullGate.await()
                Response.Success(
                    listOf(
                        pod(
                            id = "pod-1",
                            subdomain = "full-sub",
                            state = "running",
                            internalState = "ready",
                            usageStatus = "unused",
                            resourceUsage = usage(),
                        )
                    )
                )
            },
        )

        val loadJob = launch { subject.load("ws-1", "slug-1") }
        basicSeen.await()

        assertEquals(listOf("pod-1"), subject.pods.value.map { it.id })
        assertEquals("pending", subject.pods.value.single().state)
        assertNull(subject.pods.value.single().resourceUsage)
        assertFalse(subject.error.value)

        fullGate.complete(Unit)
        loadJob.join()

        val merged = subject.pods.value.single()
        assertEquals("pod-1", merged.id)
        assertEquals("pod-1", merged.subdomain)
        assertEquals("used", merged.usageStatus)
        assertEquals("running", merged.state)
        assertEquals("ready", merged.internalState)
        assertEquals("100m", merged.resourceUsage!!.requestsCpu)
        assertEquals(HivePoolStatus(1, 2), subject.poolStatus.value)
        assertFalse(subject.error.value)
        assertFalse(subject.loading.value)
    }

    @Test
    fun `full failure keeps basic list without error`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Success(HivePoolStatus(0, 1)) },
            fetchHiveBasicPods = {
                Response.Success(listOf(pod(id = "pod-1", state = "pending")))
            },
            fetchHiveFullPods = { Response.Error(ResponseError("metrics failed")) },
        )

        subject.load("ws-1", "slug-1")

        assertEquals(listOf("pod-1"), subject.pods.value.map { it.id })
        assertNull(subject.pods.value.single().resourceUsage)
        assertFalse(subject.error.value)
        assertEquals(HivePoolStatus(0, 1), subject.poolStatus.value)
    }

    @Test
    fun `basic failure sets error and hides list`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Success(HivePoolStatus(3, 4)) },
            fetchHiveBasicPods = { Response.Error(ResponseError("basic failed")) },
            fetchHiveFullPods = { error("full should not be called") },
        )

        subject.load("ws-1", "slug-1")

        assertTrue(subject.pods.value.isEmpty())
        assertTrue(subject.error.value)
        assertEquals(HivePoolStatus(3, 4), subject.poolStatus.value)
        assertTrue(subject.fullCalls.isEmpty())
    }

    @Test
    fun `pool failure is independent of pod error state`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Error(ResponseError("pool failed")) },
            fetchHiveBasicPods = {
                Response.Success(listOf(pod(id = "pod-1", state = "running", usageStatus = "used")))
            },
            fetchHiveFullPods = { Response.Success(emptyList()) },
        )

        subject.load("ws-1", "slug-1")

        assertNull(subject.poolStatus.value)
        assertFalse(subject.error.value)
        assertEquals(listOf("pod-1"), subject.pods.value.map { it.id })
    }

    @Test
    fun `successful empty list does not set error`() = runBlocking {
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Success(HivePoolStatus(0, 0)) },
            fetchHiveBasicPods = { Response.Success(emptyList()) },
            fetchHiveFullPods = { Response.Success(emptyList()) },
        )

        subject.load("ws-1", "slug-1")

        assertTrue(subject.pods.value.isEmpty())
        assertFalse(subject.error.value)
        assertEquals(HivePoolStatus(0, 0), subject.poolStatus.value)
    }

    @Test
    fun `stale full response from older generation is dropped after refresh`() = runBlocking {
        var basicLoads = 0
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = { Response.Success(HivePoolStatus(0, 0)) },
            fetchHiveBasicPods = {
                basicLoads++
                Response.Success(listOf(pod(id = "basic-$basicLoads", state = "pending")))
            },
            fetchHiveFullPods = { Response.Success(emptyList()) },
        )

        subject.load("ws-1", "slug-1")
        val staleGeneration = subject.currentGeneration()
        val staleBasic = listOf(pod(id = "basic-1", state = "pending"))
        val staleFull = listOf(pod(id = "stale-full", subdomain = "stale", state = "running"))

        subject.refresh()

        subject.applyFullWithGeneration(staleBasic, staleFull, staleGeneration)

        assertEquals(listOf("basic-2"), subject.pods.value.map { it.id })
        assertFalse(subject.pods.value.any { it.id == "stale-full" })
        assertFalse(subject.error.value)
    }

    @Test
    fun `refresh re-runs all three calls`() = runBlocking {
        var poolLoads = 0
        var basicLoads = 0
        var fullLoads = 0
        val subject = TestableWorkspacePodsViewModel(
            fetchHivePoolStatus = {
                poolLoads++
                Response.Success(HivePoolStatus(poolLoads, 0))
            },
            fetchHiveBasicPods = {
                basicLoads++
                Response.Success(listOf(pod(id = "pod-$basicLoads", state = "pending")))
            },
            fetchHiveFullPods = {
                fullLoads++
                Response.Success(emptyList())
            },
        )

        subject.load("ws-1", "slug-1")
        subject.refresh()

        assertEquals(2, poolLoads)
        assertEquals(2, basicLoads)
        assertEquals(2, fullLoads)
        assertEquals(listOf("pod-2"), subject.pods.value.map { it.id })
        assertEquals(HivePoolStatus(2, 0), subject.poolStatus.value)
    }
}
