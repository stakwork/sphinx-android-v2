package chat.sphinx.dashboard.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import chat.sphinx.concept_repository_dashboard.model.HiveFeaturesPage
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

internal sealed class WorkspaceFeaturesViewState : ViewState<WorkspaceFeaturesViewState>() {
    object Idle : WorkspaceFeaturesViewState()
}

@HiltViewModel
internal class WorkspaceFeaturesViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    private val dashboardNavigator: DashboardNavigator,
) : SideEffectViewModel<
        Context,
        ChatListSideEffect,
        WorkspaceFeaturesViewState
        >(dispatchers, WorkspaceFeaturesViewState.Idle) {

    companion object {
        const val PAGE_SIZE = 20
    }

    private val _features = MutableStateFlow<List<HiveFeature>>(emptyList())
    val features: StateFlow<List<HiveFeature>> = _features.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Distinguishes "fetch failed" from "fetch succeeded with zero features".
    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private var workspaceId: String? = null
    private var currentPage: Int = 0
    private var hasMore: Boolean = false
    private var generation: Int = 0
    private var fetchJob: Job? = null

    fun load(workspaceId: String) {
        if (workspaceId.isBlank() || this.workspaceId == workspaceId) {
            return
        }
        this.workspaceId = workspaceId
        _features.value = emptyList()
        currentPage = 0
        hasMore = false
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
        fetchJob = viewModelScope.launch {
            fetchAndApply(
                workspaceId = workspaceId,
                page = nextPage,
                expectedGeneration = expectedGeneration,
                replace = false,
            )
        }
    }

    fun onFeatureClicked(feature: HiveFeature) {
        if (!isFeatureInCurrentList(feature.id)) {
            return
        }
        viewModelScope.launch(mainImmediate) {
            dashboardNavigator.toFeaturePlan(feature.id, feature.title)
        }
    }

    fun onEditClicked(feature: HiveFeature) {
        if (!isFeatureInCurrentList(feature.id)) {
            return
        }
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(
                ChatListSideEffect.AlertEditHiveFeature(
                    currentStatus = feature.status,
                    currentPriority = feature.priority,
                    onConfirm = { status, priority ->
                        updateFeature(feature.id, status, priority)
                    }
                )
            )
        }
    }

    fun onDeleteClicked(feature: HiveFeature) {
        if (!isFeatureInCurrentList(feature.id)) {
            return
        }
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(
                ChatListSideEffect.AlertConfirmDeleteFeature(
                    onConfirm = { deleteFeature(feature.id) },
                    onDismiss = {}
                )
            )
        }
    }

    fun updateFeature(featureId: String, status: String?, priority: String?) {
        viewModelScope.launch {
            if (!isFeatureInCurrentList(featureId)) {
                return@launch
            }
            when (val response = repositoryDashboard.updateHiveFeature(featureId, status, priority)) {
                is Response.Success -> {
                    _features.value = _features.value.map { feature ->
                        if (feature.id == featureId) response.value else feature
                    }
                }
                is Response.Error -> {
                    _error.value = true
                    submitSideEffect(
                        ChatListSideEffect.NotifyError(
                            msg = response.cause.message
                                ?: "Unable to update feature"
                        )
                    )
                }
            }
        }
    }

    fun deleteFeature(featureId: String) {
        viewModelScope.launch {
            if (!isFeatureInCurrentList(featureId)) {
                return@launch
            }
            when (repositoryDashboard.deleteHiveFeature(featureId)) {
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
                    submitSideEffect(
                        ChatListSideEffect.NotifyError("Unable to delete feature")
                    )
                }
            }
        }
    }

    fun dismissError() {
        _error.value = false
    }

    private fun isFeatureInCurrentList(featureId: String): Boolean {
        return _features.value.any { it.id == featureId }
    }

    private fun loadPage1(isRefresh: Boolean = false) {
        val workspaceId = workspaceId ?: return
        fetchJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        fetchJob = viewModelScope.launch {
            if (isRefresh) {
                _refreshing.value = true
            } else if (_features.value.isEmpty()) {
                _loading.value = true
            }
            _error.value = false
            try {
                fetchAndApply(
                    workspaceId = workspaceId,
                    page = 1,
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
        expectedGeneration: Int,
        replace: Boolean,
    ) {
        try {
            when (val response = repositoryDashboard.fetchHiveFeatures(workspaceId, page)) {
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
                            msg = response.cause.message
                                ?: "Unable to load features"
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
                    msg = e.message ?: "Unable to load features"
                )
            )
        }
    }

    private fun applyPage(page: HiveFeaturesPage, replace: Boolean) {
        currentPage = page.page
        hasMore = page.hasMore
        _features.value = if (replace) {
            page.features
        } else {
            _features.value + page.features
        }
        _error.value = false
    }
}
