package chat.sphinx.dashboard.graphchat

sealed class GraphChatSseEvent {
    data class Token(val text: String) : GraphChatSseEvent()
    data class ToolStatus(val name: String?) : GraphChatSseEvent()
    object Finished : GraphChatSseEvent()
    data class Error(val message: String?) : GraphChatSseEvent()
}

data class GraphChatMessage(
    val role: String,
    val content: String,
)
