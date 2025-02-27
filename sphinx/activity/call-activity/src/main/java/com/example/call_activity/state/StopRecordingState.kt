package com.example.call_activity.state

sealed class StopRecordingState {
    object Stopped: StopRecordingState()
    object Loading: StopRecordingState()
    object Empty: StopRecordingState()
    object Error: StopRecordingState()
}