package chat.sphinx.dashboard.graphchat

import chat.sphinx.concept_repository_dashboard_android.GraphChatHistoryClearer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, process-lifetime Graph Chat transcripts keyed by workspaceId.
 * Each workspace keeps at most [MAX_MESSAGES] messages (`takeLast` / suffix).
 */
@Singleton
class GraphChatHistory @Inject constructor() : GraphChatHistoryClearer {

    private val lock = Any()
    private val messagesByWorkspace = mutableMapOf<String, List<GraphChatMessage>>()

    fun get(workspaceId: String): List<GraphChatMessage> {
        synchronized(lock) {
            return messagesByWorkspace[workspaceId].orEmpty().toList()
        }
    }

    fun append(workspaceId: String, message: GraphChatMessage) {
        synchronized(lock) {
            val updated = (messagesByWorkspace[workspaceId].orEmpty() + message)
                .takeLast(MAX_MESSAGES)
            messagesByWorkspace[workspaceId] = updated
        }
    }

    fun replace(workspaceId: String, messages: List<GraphChatMessage>) {
        synchronized(lock) {
            messagesByWorkspace[workspaceId] = messages.takeLast(MAX_MESSAGES)
        }
    }

    override fun clearAll() {
        synchronized(lock) {
            messagesByWorkspace.clear()
        }
    }

    companion object {
        const val MAX_MESSAGES = 20
    }
}
