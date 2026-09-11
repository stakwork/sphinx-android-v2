package chat.sphinx.dashboard.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_dashboard.model.HivePod
import chat.sphinx.concept_repository_dashboard.model.HivePoolStatus
import chat.sphinx.concept_repository_dashboard.model.mergeHivePods
import chat.sphinx.concept_repository_dashboard.model.sortHivePods
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.kotlin_response.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed class WorkspacePodsViewState : ViewState<WorkspacePodsViewState>() {
    object Idle : WorkspacePodsViewState()
}

@HiltViewModel
internal class WorkspacePodsViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
) : SideEffectViewModel<
        Context,
        ChatListSideEffect,
        WorkspacePodsViewState
        >(dispatchers, WorkspacePodsViewState.Idle) {

    private val _pods = MutableStateFlow<List<HivePod>>(emptyList())
    val pods: StateFlow<List<HivePod>> = _pods.asStateFlow()

    private val _poolStatus = MutableStateFlow<HivePoolStatus?>(null)
    val poolStatus: StateFlow<HivePoolStatus?> = _poolStatus.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Distinguishes "fetch failed" from "fetch succeeded with zero pods".
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    @Suppress("unused")
    private var workspaceId: String? = null
    private var workspaceSlug: String? = null
    private var generation: Int = 0
    private var fetchJob: Job? = null

    fun load(workspaceId: String, workspaceSlug: String?) {
        this.workspaceId = workspaceId
        this.workspaceSlug = workspaceSlug
        if (workspaceSlug.isNullOrBlank()) {
            failClosed()
            return
        }
        _pods.value = emptyList()
        _poolStatus.value = null
        _error.value = false
        loadAll(isRefresh = false)
    }

    fun refresh() {
        if (workspaceSlug.isNullOrBlank()) {
            failClosed()
            return
        }
        loadAll(isRefresh = true)
    }

    private fun failClosed() {
        fetchJob?.cancel()
        generation += 1
        _pods.value = emptyList()
        _poolStatus.value = null
        _error.value = true
        _loading.value = false
        _refreshing.value = false
    }

    fun dismissError() {
        _error.value = false
    }

    private fun loadAll(isRefresh: Boolean) {
        val slug = workspaceSlug ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _refreshing.value = true
            } else if (_pods.value.isEmpty()) {
                _loading.value = true
            }
            _error.value = false
            try {
                val poolJob = launch { fetchPoolStatus(slug, expectedGeneration) }
                fetchBasicThenFull(slug, expectedGeneration)
                poolJob.join()
            } finally {
                if (expectedGeneration == generation) {
                    _loading.value = false
                    _refreshing.value = false
                }
            }
        }
    }

    private suspend fun fetchPoolStatus(slug: String, expectedGeneration: Int) {
        try {
            when (val response = repositoryDashboard.fetchHivePoolStatus(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _poolStatus.value = response.value
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _poolStatus.value = null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (expectedGeneration != generation) {
                return
            }
            _poolStatus.value = null
        }
    }

    private suspend fun fetchBasicThenFull(slug: String, expectedGeneration: Int) {
        try {
            when (val basicResponse = repositoryDashboard.fetchHiveBasicPods(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _pods.value = sortHivePods(basicResponse.value)
                    _error.value = false
                    fetchFullAndMerge(slug, basicResponse.value, expectedGeneration)
                }
                is Response.Error -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _pods.value = emptyList()
                    _error.value = true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (expectedGeneration != generation) {
                return
            }
            _pods.value = emptyList()
            _error.value = true
        }
    }

    private suspend fun fetchFullAndMerge(
        slug: String,
        basicPods: List<HivePod>,
        expectedGeneration: Int,
    ) {
        try {
            when (val fullResponse = repositoryDashboard.fetchHiveFullPods(slug)) {
                is Response.Success -> {
                    if (expectedGeneration != generation) {
                        return
                    }
                    _pods.value = sortHivePods(mergeHivePods(basicPods, fullResponse.value))
                }
                is Response.Error -> {
                    // Keep the basic list without capacity; do not flip error.
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the basic list without capacity; do not flip error.
        }
    }
}
