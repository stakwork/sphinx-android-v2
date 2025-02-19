package chat.sphinx.concept_network_query_chat.model.state

sealed class StopRecordingState {
    object Stopped: StopRecordingState()
    object Loading: StopRecordingState()
    object Empty: StopRecordingState()
    object Error: StopRecordingState()
}