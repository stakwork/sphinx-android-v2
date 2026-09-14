package chat.sphinx.dashboard.graphchat

import kotlinx.coroutines.flow.Flow

fun interface GraphChatStreamSource {
    fun stream(
        workspaceSlug: String?,
        messages: List<GraphChatMessage>,
    ): Flow<GraphChatSseEvent>
}
