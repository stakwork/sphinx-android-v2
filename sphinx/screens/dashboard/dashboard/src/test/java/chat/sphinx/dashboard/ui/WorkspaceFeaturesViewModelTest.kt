package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import chat.sphinx.concept_repository_dashboard.model.HiveFeaturesPage
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

private class TestableWorkspaceFeaturesViewModel(
    private val fetchHiveFeatures: suspend (workspaceId: String, page: Int) -> Response<HiveFeaturesPage, ResponseError>,
    private val updateHiveFeature: suspend (featureId: String, status: String?, priority: String?) -> Response<HiveFeature, ResponseError> =
        { _, _, _ -> Response.Error(ResponseError("not stubbed")) },
    private val deleteHiveFeature: suspend (featureId: String) -> Response<Boolean, ResponseError> =
        { Response.Error(ResponseError("not stubbed")) },
) {
    companion object {
        const val PAGE_SIZE = 20
    }

    private val _features = MutableStateFlow<List<HiveFeature>>(emptyList())
    val features: StateFlow<List<HiveFeature>> = _features.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    val updateCalls = mutableListOf<Triple<String, String?, String?>>()
    val deleteCalls = mutableListOf<String>()
    val fetchCalls = mutableListOf<Pair<String, Int>>()
    val notifyErrors = mutableListOf<String>()

    private var workspaceId: String? = null
    private var currentPage: Int = 0
    private var hasMore: Boolean = false
    private var generation: Int = 0
    private var fetchJob: Job? = null

    suspend fun load(workspaceId: String) {
        if (this.workspaceId == workspaceId) {
            return
        }
        this.workspaceId = workspaceId
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
        coroutineScope {
            fetchAndApply(workspaceId, nextPage, expectedGeneration, replace = false)
        }
    }

    suspend fun updateFeature(featureId: String, status: String?, priority: String?) {
        if (!isFeatureInCurrentList(featureId)) {
            return
        }
        updateCalls.add(Triple(featureId, status, priority))
        when (val response = updateHiveFeature(featureId, status, priority)) {
            is Response.Success -> {
                _features.value = _features.value.map { feature ->
                    if (feature.id == featureId) response.value else feature
                }
            }
            is Response.Error -> {
                _error.value = true
                notifyErrors.add(response.cause.message ?: "Unable to update feature")
            }
        }
    }

    suspend fun deleteFeature(featureId: String) {
        if (!isFeatureInCurrentList(featureId)) {
            return
        }
        deleteCalls.add(featureId)
        when (deleteHiveFeature(featureId)) {
            is Response.Success -> {
                val remaining = _features.value.filterNot { it.id == featureId }
                _features.value = remaining
                when {
                    remaining.isEmpty() -> loadPage1()
                    hasMore && remaining.size < PAGE_SIZE -> loadNextPage()
                }
            }
            is Response.Error -> {
                _error.value = true
                notifyErrors.add("Unable to delete feature")
            }
        }
    }

    private fun isFeatureInCurrentList(featureId: String): Boolean {
        return _features.value.any { it.id == featureId }
    }

    private suspend fun loadPage1() {
        val workspaceId = workspaceId ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        _loading.value = _features.value.isEmpty()
        _error.value = false
        try {
            fetchAndApply(workspaceId, 1, expectedGeneration, replace = true)
        } finally {
            _loading.value = false
        }
    }

    private suspend fun fetchAndApply(
        workspaceId: String,
        page: Int,
        expectedGeneration: Int,
        replace: Boolean,
    ) {
        fetchCalls.add(workspaceId to page)
        try {
            when (val response = fetchHiveFeatures(workspaceId, page)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    currentPage = response.value.page
                    hasMore = response.value.hasMore
                    _features.value = if (replace) {
                        response.value.features
                    } else {
                        _features.value + response.value.features
                    }
                    _error.value = false
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _error.value = true
                    notifyErrors.add(response.cause.message ?: "Unable to load features")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (expectedGeneration != generation) {
                return
            }
            _error.value = true
            notifyErrors.add(e.message ?: "Unable to load features")
        }
    }

    // Test-only: simulate an in-flight page-N that can be dropped by a later refresh.
    suspend fun fetchPageWithGeneration(page: Int, expectedGeneration: Int) {
        val workspaceId = workspaceId ?: return
        fetchAndApply(workspaceId, page, expectedGeneration, replace = false)
    }

    fun currentGeneration(): Int = generation
}

class WorkspaceFeaturesViewModelTest {

    private fun feature(
        id: String,
        title: String = "Feature $id",
        status: String? = "BACKLOG",
        priority: String? = "LOW",
    ) = HiveFeature(id, title, status, priority)

    private fun page(
        features: List<HiveFeature>,
        page: Int = 1,
        hasMore: Boolean = false,
        totalPages: Int = 1,
        totalCount: Int = features.size,
    ) = HiveFeaturesPage(features, page, hasMore, totalPages, totalCount)

    @Test
    fun `load fetches page 1 and populates features`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                assertEquals(1, page)
                Response.Success(page(listOf(feature("feat-1")), page = 1))
            }
        )

        subject.load("ws-1")

        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
        assertFalse(subject.error.value)
        assertFalse(subject.loading.value)
        assertEquals(listOf("ws-1" to 1), subject.fetchCalls)
    }

    @Test
    fun `loadNextPage appends while hasMore`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                if (page == 1) {
                    Response.Success(page(listOf(feature("feat-1")), page = 1, hasMore = true, totalPages = 2))
                } else {
                    Response.Success(page(listOf(feature("feat-2")), page = 2, hasMore = false, totalPages = 2))
                }
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()

        assertEquals(listOf("feat-1", "feat-2"), subject.features.value.map { it.id })
        assertEquals(listOf("ws-1" to 1, "ws-1" to 2), subject.fetchCalls)
    }

    @Test
    fun `loadNextPage is a no-op when hasMore is false`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ ->
                Response.Success(page(listOf(feature("feat-1")), hasMore = false))
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()

        assertEquals(1, subject.fetchCalls.size)
        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
    }

    @Test
    fun `refresh resets to page 1`() = runBlocking {
        var page1Loads = 0
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                if (page == 1) {
                    page1Loads++
                    if (page1Loads == 1) {
                        Response.Success(page(listOf(feature("feat-1")), page = 1, hasMore = true))
                    } else {
                        Response.Success(page(listOf(feature("feat-refreshed")), page = 1, hasMore = false))
                    }
                } else {
                    Response.Success(page(listOf(feature("feat-2")), page = 2, hasMore = false))
                }
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()
        subject.refresh()

        assertEquals(listOf("feat-refreshed"), subject.features.value.map { it.id })
        assertEquals(1, subject.fetchCalls.last().second)
        assertEquals(2, page1Loads)
    }

    @Test
    fun `stale page-N response from older generation is dropped after refresh`() = runBlocking {
        val page2Gate = CompletableDeferred<Unit>()
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                if (page == 1) {
                    Response.Success(page(listOf(feature("feat-1")), page = 1, hasMore = true))
                } else {
                    page2Gate.await()
                    Response.Success(page(listOf(feature("stale-2")), page = 2, hasMore = false))
                }
            }
        )

        subject.load("ws-1")
        val staleGeneration = subject.currentGeneration()

        val staleJob = launch {
            subject.fetchPageWithGeneration(page = 2, expectedGeneration = staleGeneration)
        }

        subject.refresh()
        page2Gate.complete(Unit)
        staleJob.join()

        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
        assertFalse(subject.features.value.any { it.id == "stale-2" })
    }

    @Test
    fun `successful edit updates the row in place`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ ->
                Response.Success(page(listOf(feature("feat-1", status = "BACKLOG", priority = "LOW"))))
            },
            updateHiveFeature = { id, status, priority ->
                Response.Success(HiveFeature(id, "Feature $id", status, priority))
            }
        )

        subject.load("ws-1")
        subject.updateFeature("feat-1", "IN_PROGRESS", "HIGH")

        assertEquals("IN_PROGRESS", subject.features.value.single().status)
        assertEquals("HIGH", subject.features.value.single().priority)
        assertFalse(subject.error.value)
        assertEquals(1, subject.updateCalls.size)
    }

    @Test
    fun `failed edit leaves the list untouched and sets error`() = runBlocking {
        val original = feature("feat-1", status = "BACKLOG", priority = "LOW")
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ -> Response.Success(page(listOf(original))) },
            updateHiveFeature = { _, _, _ -> Response.Error(ResponseError("boom")) }
        )

        subject.load("ws-1")
        subject.updateFeature("feat-1", "COMPLETED", "CRITICAL")

        assertEquals(listOf(original), subject.features.value)
        assertTrue(subject.error.value)
        assertEquals(listOf("boom"), subject.notifyErrors)
    }

    @Test
    fun `failed delete leaves the list untouched and sets error`() = runBlocking {
        val original = feature("feat-1")
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ -> Response.Success(page(listOf(original))) },
            deleteHiveFeature = { Response.Error(ResponseError("nope")) }
        )

        subject.load("ws-1")
        subject.deleteFeature("feat-1")

        assertEquals(listOf(original), subject.features.value)
        assertTrue(subject.error.value)
        assertEquals(listOf("Unable to delete feature"), subject.notifyErrors)
    }

    @Test
    fun `successful delete removes the row`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ ->
                Response.Success(page(listOf(feature("feat-1"), feature("feat-2"))))
            },
            deleteHiveFeature = { Response.Success(true) }
        )

        subject.load("ws-1")
        subject.deleteFeature("feat-1")

        assertEquals(listOf("feat-2"), subject.features.value.map { it.id })
        assertFalse(subject.error.value)
    }

    @Test
    fun `successful delete of last item reloads page 1`() = runBlocking {
        var fetchCount = 0
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ ->
                fetchCount++
                if (fetchCount == 1) {
                    Response.Success(page(listOf(feature("feat-1"))))
                } else {
                    Response.Success(page(emptyList()))
                }
            },
            deleteHiveFeature = { Response.Success(true) }
        )

        subject.load("ws-1")
        subject.deleteFeature("feat-1")

        assertTrue(subject.features.value.isEmpty())
        assertEquals(2, subject.fetchCalls.size)
        assertEquals(1, subject.fetchCalls.last().second)
    }

    @Test
    fun `successful delete with hasMore and short list fetches next page to fill the gap`() = runBlocking {
        val page1 = (1..5).map { feature("feat-$it") }
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                if (page == 1) {
                    Response.Success(page(page1, page = 1, hasMore = true, totalPages = 2, totalCount = 6))
                } else {
                    Response.Success(page(listOf(feature("feat-6")), page = 2, hasMore = false, totalPages = 2, totalCount = 6))
                }
            },
            deleteHiveFeature = { Response.Success(true) }
        )

        subject.load("ws-1")
        subject.deleteFeature("feat-1")

        assertEquals(
            listOf("feat-2", "feat-3", "feat-4", "feat-5", "feat-6"),
            subject.features.value.map { it.id }
        )
        assertEquals(listOf("ws-1" to 1, "ws-1" to 2), subject.fetchCalls)
    }

    @Test
    fun `edit is refused for a featureId not in the current list`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ -> Response.Success(page(listOf(feature("feat-1")))) },
            updateHiveFeature = { _, _, _ -> error("should not be called") }
        )

        subject.load("ws-1")
        subject.updateFeature("not-in-list", "PLANNED", null)

        assertTrue(subject.updateCalls.isEmpty())
        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
        assertFalse(subject.error.value)
    }

    @Test
    fun `delete is refused for a featureId not in the current list`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ -> Response.Success(page(listOf(feature("feat-1")))) },
            deleteHiveFeature = { error("should not be called") }
        )

        subject.load("ws-1")
        subject.deleteFeature("not-in-list")

        assertTrue(subject.deleteCalls.isEmpty())
        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
        assertFalse(subject.error.value)
    }

    @Test
    fun `failed page load leaves current list intact and sets error`() = runBlocking {
        var attempt = 0
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, page ->
                attempt++
                if (page == 1) {
                    Response.Success(page(listOf(feature("feat-1")), page = 1, hasMore = true))
                } else {
                    Response.Error(ResponseError("page 2 failed"))
                }
            }
        )

        subject.load("ws-1")
        subject.loadNextPage()

        assertEquals(listOf("feat-1"), subject.features.value.map { it.id })
        assertTrue(subject.error.value)
        assertEquals(listOf("page 2 failed"), subject.notifyErrors)
        assertEquals(2, attempt)
    }

    @Test
    fun `empty success list does not set error`() = runBlocking {
        val subject = TestableWorkspaceFeaturesViewModel(
            fetchHiveFeatures = { _, _ -> Response.Success(page(emptyList())) }
        )

        subject.load("ws-1")

        assertTrue(subject.features.value.isEmpty())
        assertFalse(subject.error.value)
    }
}
