package com.example.call_activity.state

sealed class StartRecordingState {
    object Loading: StartRecordingState()
    object Recording: StartRecordingState()
    object Empty: StartRecordingState()
    object Error: StartRecordingState()
}