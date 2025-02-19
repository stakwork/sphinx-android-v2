package chat.sphinx.concept_network_query_chat.model.state

sealed class StartRecordingState {
    object Loading: StartRecordingState()
    object Recording: StartRecordingState()
    object Empty: StartRecordingState()
    object Error: StartRecordingState()
}