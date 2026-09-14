package chat.sphinx.dashboard.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import chat.sphinx.dashboard.graphchat.GraphChatHistory
import chat.sphinx.dashboard.graphchat.GraphChatMessage
import chat.sphinx.dashboard.graphchat.GraphChatSseEvent
import chat.sphinx.dashboard.graphchat.GraphChatStreamSource
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

internal sealed class WorkspaceGraphChatViewState : ViewState<WorkspaceGraphChatViewState>() {
    object Idle : WorkspaceGraphChatViewState()
}

data class GraphChatUiMessage(
    val id: Long,
    val role: String,
    val content: String,
)

@HiltViewModel
internal class WorkspaceGraphChatViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val history: GraphChatHistory,
    private val streamSource: GraphChatStreamSource,
) : SideEffectViewModel<
        Context,
        ChatListSideEffect,
        WorkspaceGraphChatViewState
        >(dispatchers, WorkspaceGraphChatViewState.Idle) {

    private val _messages = MutableStateFlow<List<GraphChatUiMessage>>(emptyList())
    val messages: StateFlow<List<GraphChatUiMessage>> = _messages.asStateFlow()

    private val _inFlight = MutableStateFlow(false)
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    private val _statusDetail = MutableStateFlow<String?>(null)
    val statusDetail: StateFlow<String?> = _statusDetail.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private var workspaceId: String? = null
    private var workspaceSlug: String? = null
    private var nextMessageId: Long = 0L
    private var generation: Int = 0
    private var streamJob: Job? = null

    fun load(workspaceId: String, workspaceSlug: String?) {
        if (this.workspaceId == workspaceId) {
            this.workspaceSlug = workspaceSlug
            if (workspaceSlug.isNullOrBlank()) {
                failClosed()
            }
            return
        }
        if (this.workspaceId != null) {
            cancelInFlight()
        }
        this.workspaceId = workspaceId
        this.workspaceSlug = workspaceSlug
        restoreFromHistory(workspaceId)
        if (workspaceSlug.isNullOrBlank()) {
            failClosed()
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _inFlight.value) {
            return
        }
        val workspaceId = workspaceId ?: return
        if (workspaceSlug.isNullOrBlank()) {
            failClosed()
            return
        }
        _error.value = false
        val user = GraphChatUiMessage(nextId(), GraphChatMessage.ROLE_USER, trimmed)
        _messages.value = (_messages.value + user).takeLast(GraphChatHistory.MAX_MESSAGES)
        history.append(workspaceId, GraphChatMessage(GraphChatMessage.ROLE_USER, trimmed))
        startStream()
    }

    fun retry() {
        if (_inFlight.value) {
            return
        }
        if (workspaceSlug.isNullOrBlank()) {
            failClosed()
            return
        }
        startStream()
    }

    fun cancelInFlight() {
        if (!_inFlight.value && streamJob?.isActive != true) {
            return
        }
        generation += 1
        streamJob?.cancel()
        streamJob = null
        workspaceId?.let { persistShown(it) }
        _inFlight.value = false
        _statusDetail.value = null
    }

    fun dismissError() {
        _error.value = false
    }

    private fun failClosed() {
        generation += 1
        streamJob?.cancel()
        streamJob = null
        _inFlight.value = false
        _statusDetail.value = null
        _error.value = true
    }

    private fun restoreFromHistory(workspaceId: String) {
        nextMessageId = 0L
        _messages.value = history.get(workspaceId).map { stored ->
            GraphChatUiMessage(nextId(), stored.role, stored.content)
        }
        _error.value = false
        _inFlight.value = false
        _statusDetail.value = null
    }

    private fun startStream() {
        val workspaceId = workspaceId ?: return
        val slug = workspaceSlug
        if (slug.isNullOrBlank()) {
            failClosed()
            return
        }
        streamJob?.cancel()
        generation += 1
        val expectedGeneration = generation
        _inFlight.value = true
        _statusDetail.value = null
        _error.value = false

        var assistantId: Long? = null
        streamJob = viewModelScope.launch {
            try {
                streamSource.stream(slug, history.get(workspaceId)).collect { event ->
                    if (expectedGeneration != generation || !_inFlight.value) {
                        return@collect
                    }
                    when (event) {
                        is GraphChatSseEvent.Token -> {
                            assistantId = appendToken(assistantId, event.text)
                        }
                        is GraphChatSseEvent.ToolStatus -> {
                            _statusDetail.value = event.name
                        }
                        GraphChatSseEvent.Finished -> {
                            persistShown(workspaceId)
                            finishIdle()
                        }
                        is GraphChatSseEvent.Error -> {
                            persistShown(workspaceId)
                            finishError()
                        }
                    }
                }
                if (expectedGeneration == generation && _inFlight.value) {
                    persistShown(workspaceId)
                    finishIdle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (expectedGeneration == generation) {
                    persistShown(workspaceId)
                    finishError()
                }
            }
        }
    }

    private fun appendToken(assistantId: Long?, token: String): Long {
        val id = assistantId
        if (id == null) {
            val newId = nextId()
            _messages.value = (_messages.value + GraphChatUiMessage(
                newId,
                GraphChatMessage.ROLE_ASSISTANT,
                token,
            )).takeLast(GraphChatHistory.MAX_MESSAGES)
            return newId
        }
        _messages.value = _messages.value.map { message ->
            if (message.id == id) {
                message.copy(content = message.content + token)
            } else {
                message
            }
        }
        return id
    }

    private fun persistShown(workspaceId: String) {
        history.replace(
            workspaceId,
            _messages.value.map { GraphChatMessage(it.role, it.content) },
        )
    }

    private fun finishIdle() {
        _inFlight.value = false
        _statusDetail.value = null
        _error.value = false
    }

    private fun finishError() {
        _inFlight.value = false
        _statusDetail.value = null
        _error.value = true
    }

    private fun nextId(): Long {
        nextMessageId += 1L
        return nextMessageId
    }

    override fun onCleared() {
        cancelInFlight()
        super.onCleared()
    }
}
