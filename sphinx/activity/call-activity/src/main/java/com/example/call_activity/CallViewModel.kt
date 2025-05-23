package com.example.call_activity

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.res.Resources
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.resources.getRandomColor
import chat.sphinx.wrapper_common.toDateTime
import com.example.call_activity.service.ForegroundService
import com.example.call_activity.state.StartRecordingState
import com.example.call_activity.state.StopRecordingState
import com.github.ajalt.timberkt.Timber
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioProcessorOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalScreencastVideoTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import io.livekit.android.room.track.video.CameraCapturerUtils
import io.livekit.android.util.LKLog
import io.livekit.android.util.flow
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import livekit.org.webrtc.CameraXHelper

@OptIn(ExperimentalCamera2Interop::class)
class CallViewModel constructor(
    val url: String,
    val token: String,
    application: Application,
    val e2ee: Boolean = false,
    val e2eeKey: String? = "",
    val videoEnabled: Boolean = false,
    val audioProcessorOptions: AudioProcessorOptions? = null,
    val stressTest: StressTest = StressTest.None,
    private val roomName: String,
    private val networkQueryChat: NetworkQueryChat,
    private val dispatchers: CoroutineDispatchers
) : AndroidViewModel(application) {

    private fun getE2EEOptions(): E2EEOptions? {
        var e2eeOptions: E2EEOptions? = null
        if (e2ee && e2eeKey != null) {
            e2eeOptions = E2EEOptions()
        }
        e2eeOptions?.keyProvider?.setSharedKey(e2eeKey!!)
        return e2eeOptions
    }

    private fun getRoomOptions(): RoomOptions {
        return RoomOptions(
            adaptiveStream = true,
            dynacast = true,
            e2eeOptions = getE2EEOptions(),
        )
    }

    val room = LiveKit.create(
        appContext = application,
        options = getRoomOptions(),
        overrides = LiveKitOverrides(
            audioOptions = AudioOptions(audioProcessorOptions = audioProcessorOptions),
        ),
    )

    private var cameraProvider: CameraCapturerUtils.CameraProvider? = null
    val audioHandler = room.audioHandler as AudioSwitchHandler

    val participants = room::remoteParticipants.flow
        .map { remoteParticipants ->
            listOf<Participant>(room.localParticipant) +
                    remoteParticipants
                        .keys
                        .sortedBy { it.value }
                        .mapNotNull { remoteParticipants[it] }
        }

    val participantColors: MutableMap<String, Int> = mutableMapOf()

    private val mutableError = MutableStateFlow<Throwable?>(null)
    val error = mutableError.hide()

    private val mutablePrimarySpeaker = MutableStateFlow<Participant?>(null)
    val primarySpeaker: StateFlow<Participant?> = mutablePrimarySpeaker

    val activeSpeakers = room::activeSpeakers.flow

    private var localScreencastTrack: LocalScreencastVideoTrack? = null

    // Controls
    private val mutableMicEnabled = MutableLiveData(true)
    val micEnabled = mutableMicEnabled.hide()

    private val mutableCameraEnabled = MutableLiveData(true)
    val cameraEnabled = mutableCameraEnabled.hide()

    private val mutableScreencastEnabled = MutableLiveData(false)
    val screenshareEnabled = mutableScreencastEnabled.hide()

    private val mutableEnhancedNsEnabled = MutableLiveData(false)
    val enhancedNsEnabled = mutableEnhancedNsEnabled.hide()

    private val mutableEnableAudioProcessor = MutableLiveData(true)
    val enableAudioProcessor = mutableEnableAudioProcessor.hide()

    // Emits a string whenever a data message is received.
    private val mutableDataReceived = MutableSharedFlow<String>()
    val dataReceived = mutableDataReceived

    private var isRecording = false
    private var isLocalParticipantRecording = false

    private val mutableStartRecordingState: MutableStateFlow<StartRecordingState> = MutableStateFlow(StartRecordingState.Empty)
    private val mutableIsLocalParticipantRecording: MutableStateFlow<Boolean> = MutableStateFlow(false)

    val startRecordingState = combine(mutableStartRecordingState, mutableIsLocalParticipantRecording) {
        startRecordState:StartRecordingState, isLocalParticipantRecording: Boolean ->
        Pair(startRecordState, isLocalParticipantRecording)
    }

    private val mutableStopRecordingState: MutableStateFlow<StopRecordingState> = MutableStateFlow(StopRecordingState.Empty)
    val stopRecordingState: StateFlow<StopRecordingState> = mutableStopRecordingState.asStateFlow()

    // Whether other participants are allowed to subscribe to this participant's tracks.
    private val mutablePermissionAllowed = MutableStateFlow(true)
    val permissionAllowed = mutablePermissionAllowed.hide()

    init {
        CameraXHelper.createCameraProvider(ProcessLifecycleOwner.get()).let {
            if (it.isSupported(application)) {
                CameraCapturerUtils.registerCameraProvider(it)
                cameraProvider = it
            }
        }

        viewModelScope.launch {
            // Collect any errors.
            launch {
                error.collect { Timber.e(it) }
            }

            // Handle any changes in speakers.
            launch {
                combine(participants, activeSpeakers) { participants, speakers -> participants to speakers }
                    .collect { (participantsList, speakers) ->

                        participantsList.forEach { participant ->
                            if (!participantColors.containsKey(participant.getNonEmptySCI())) {
                                participantColors[participant.getNonEmptySCI()] = application.getRandomColor()
                            }
                        }

                        speakers.forEach { participant ->
                            if (!participantColors.containsKey(participant.getNonEmptySCI())) {
                                participantColors[participant.getNonEmptySCI()] = application.getRandomColor()
                            }
                        }

                        handlePrimarySpeaker(
                            participantsList,
                            speakers,
                            room,
                        )
                    }
            }

            // Handle room events.
            launch {
                room.events.collect {
                    when (it) {
                        is RoomEvent.FailedToConnect -> mutableError.value = it.error

                        is RoomEvent.DataReceived -> {
                            val identity = it.participant?.identity ?: "server"
                            val message = it.data.toString(Charsets.UTF_8)
                            mutableDataReceived.emit("$identity: $message")
                        }

                        is RoomEvent.RecordingStatusChanged -> handleRecordingStatus(it)

                        else -> {
                            Timber.e { "Room event: $it" }
                        }
                    }
                }
            }
        }

        // Start a foreground service to keep the call from being interrupted if the
        // app goes into the background.
        val foregroundServiceIntent = Intent(application, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(foregroundServiceIntent)
        } else {
            application.startService(foregroundServiceIntent)
        }
    }

    private fun handleRecordingStatus(roomEvent: RoomEvent) {
        if (roomEvent.room.isRecording) {
            mutableIsLocalParticipantRecording.value = isLocalParticipantRecording
            mutableStartRecordingState.value = StartRecordingState.Recording
            resetStopRecordingState()
         } else {
             if(isLocalParticipantRecording) {
                 isLocalParticipantRecording = false
             }

            mutableIsLocalParticipantRecording.value = isLocalParticipantRecording
            mutableStopRecordingState.value = StopRecordingState.Stopped
            resetStartRecordingState()
        }
    }

    private suspend fun collectTrackStats(event: RoomEvent.TrackSubscribed) {
        val pub = event.publication
        while (true) {
            delay(10000)
            if (pub.subscribed) {
                val statsReport = pub.track?.getRTCStats() ?: continue
                Timber.e { "stats for ${pub.sid}:" }

                for (entry in statsReport.statsMap) {
                    Timber.e { "${entry.key} = ${entry.value}" }
                }
            }
        }
    }

    fun toggleEnhancedNs(enabled: Boolean? = null) {
        if (enabled != null) {
            mutableEnableAudioProcessor.postValue(enabled)
            room.audioProcessingController.setBypassForCapturePostProcessing(!enabled)
            return
        }

        if (room.audioProcessorIsEnabled) {
            if (enableAudioProcessor.value == true) {
                room.audioProcessingController.setBypassForCapturePostProcessing(true)
                mutableEnableAudioProcessor.postValue(false)
            } else {
                room.audioProcessingController.setBypassForCapturePostProcessing(false)
                mutableEnableAudioProcessor.postValue(true)
            }
        }
    }

    private suspend fun connectToRoom() {
        try {
            room.e2eeOptions = getE2EEOptions()
            room.connect(
                url = url,
                token = token,
            )

            mutableEnhancedNsEnabled.postValue(room.audioProcessorIsEnabled)
            mutableEnableAudioProcessor.postValue(true)

            // Create and publish audio/video tracks
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            mutableMicEnabled.postValue(localParticipant.isMicrophoneEnabled())

            localParticipant.setCameraEnabled(videoEnabled)
            mutableCameraEnabled.postValue(localParticipant.isCameraEnabled())

            // Update the speaker
            handlePrimarySpeaker(emptyList(), emptyList(), room)
        } catch (e: Throwable) {
            mutableError.value = e
        }
    }

    private fun handlePrimarySpeaker(participantsList: List<Participant>, speakers: List<Participant>, room: Room?) {
        var speaker = mutablePrimarySpeaker.value

        // If speaker is local participant (due to defaults),
        // attempt to find another remote speaker to replace with.
        if (speaker is LocalParticipant) {
            val remoteSpeaker = participantsList
                .filterIsInstance<RemoteParticipant>() // Try not to display local participant as speaker.
                .firstOrNull()

            if (remoteSpeaker != null) {
                speaker = remoteSpeaker
            }
        }

        // If previous primary speaker leaves
        if (!participantsList.contains(speaker)) {
            // Default to another person in room, or local participant.
            speaker = participantsList.filterIsInstance<RemoteParticipant>()
                .firstOrNull()
                ?: room?.localParticipant
        }

        if (speakers.isNotEmpty() && !speakers.contains(speaker)) {
            val remoteSpeaker = speakers
                .filterIsInstance<RemoteParticipant>() // Try not to display local participant as speaker.
                .firstOrNull()

            if (remoteSpeaker != null) {
                speaker = remoteSpeaker
            }
        }

        mutablePrimarySpeaker.value = speaker
    }

    /**
     * Start a screen capture with the result intent from
     * [MediaProjectionManager.createScreenCaptureIntent]
     */
    fun startScreenCapture(mediaProjectionPermissionResultData: Intent) {
        val localParticipant = room.localParticipant
        viewModelScope.launch {
            localParticipant.setScreenShareEnabled(true, ScreenCaptureParams(mediaProjectionPermissionResultData))
            val screencastTrack = localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE)?.track as? LocalScreencastVideoTrack
            this@CallViewModel.localScreencastTrack = screencastTrack
            mutableScreencastEnabled.postValue(screencastTrack?.enabled)
        }
    }

    fun stopScreenCapture() {
        viewModelScope.launch {
            localScreencastTrack?.let { localScreencastVideoTrack ->
                localScreencastVideoTrack.stop()
                room.localParticipant.unpublishTrack(localScreencastVideoTrack)
                mutableScreencastEnabled.postValue(localScreencastTrack?.enabled ?: false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Make sure to release any resources associated with LiveKit
        room.disconnect()
        room.release()

        // Clean up foreground service
        val application = getApplication<Application>()
        val foregroundServiceIntent = Intent(application, ForegroundService::class.java)
        application.stopService(foregroundServiceIntent)
        cameraProvider?.let {
            CameraCapturerUtils.unregisterCameraProvider(it)
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            room.localParticipant.setMicrophoneEnabled(enabled)
            mutableMicEnabled.postValue(enabled)
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            room.localParticipant.setCameraEnabled(enabled)
            mutableCameraEnabled.postValue(enabled)
        }
    }

    fun flipCamera() {
        val videoTrack = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
            ?.track as? LocalVideoTrack
            ?: return

        val newPosition = when (videoTrack.options.position) {
            CameraPosition.FRONT -> CameraPosition.BACK
            CameraPosition.BACK -> CameraPosition.FRONT
            else -> null
        }

        videoTrack.switchCamera(position = newPosition)
    }

    fun toggleRecording() {
        isLocalParticipantRecording = !isLocalParticipantRecording
        isRecording = !isRecording

        viewModelScope.launch(dispatchers.io) {
            if (isRecording) {
                callStartRecordingApi()
            } else {
                callStopRecordingApi()
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch(dispatchers.io) {
            if (isRecording) {
                callStopRecordingApi()
            }
        }
    }

    private suspend fun callStopRecordingApi() {
        resetStartRecordingState()

        networkQueryChat.stopCallRecording(
            room = roomName
        ).collect { response ->
            when (response) {
                LoadResponse.Loading -> mutableStopRecordingState.value = StopRecordingState.Loading
                is Response.Error -> {
                    isRecording = true
                    mutableStopRecordingState.value = StopRecordingState.Error
                }
                else -> {}
            }
        }
    }

    private fun resetStartRecordingState() {
        if (mutableStartRecordingState.value != StartRecordingState.Empty) {
            mutableStartRecordingState.value = StartRecordingState.Empty
        }
    }

    private suspend fun callStartRecordingApi() {
        resetStopRecordingState()

        networkQueryChat.startCallRecording(
            room = roomName,
            timestamp = System.currentTimeMillis().toDateTime().toString(),
        ).collect { response ->
            when (response) {
                LoadResponse.Loading -> mutableStartRecordingState.value = StartRecordingState.Loading
                is Response.Error -> {
                    isRecording = false
                    mutableStartRecordingState.value = StartRecordingState.Error
                }
               else -> {}
            }
        }
    }

    private fun resetStopRecordingState() {
        if (mutableStopRecordingState.value != StopRecordingState.Empty) {
            mutableStopRecordingState.value = StopRecordingState.Empty
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    fun sendData(message: String) {
        viewModelScope.launch {
            room.localParticipant.publishData(message.toByteArray(Charsets.UTF_8))
        }
    }

    fun toggleSubscriptionPermissions() {
        mutablePermissionAllowed.value = !mutablePermissionAllowed.value
        room.localParticipant.setTrackSubscriptionPermissions(mutablePermissionAllowed.value)
    }

    // Debug functions
    fun simulateMigration() {
        room.sendSimulateScenario(Room.SimulateScenario.MIGRATION)
    }

    fun simulateNodeFailure() {
        room.sendSimulateScenario(Room.SimulateScenario.NODE_FAILURE)
    }

    fun simulateServerLeaveFullReconnect() {
        room.sendSimulateScenario(Room.SimulateScenario.SERVER_LEAVE_FULL_RECONNECT)
    }

    fun updateAttribute(key: String, value: String) {
        room.localParticipant.updateAttributes(mapOf(key to value))
    }

    fun reconnect() {
        Timber.e { "Reconnecting." }
        mutablePrimarySpeaker.value = null
        room.disconnect()
        viewModelScope.launch {
            connectToRoom()
        }
    }

    private suspend fun StressTest.SwitchRoom.execute() = coroutineScope {
        launch {
            while (isActive) {
                delay(2000)
                dumpReferenceTables()
            }
        }

        while (isActive) {
            Timber.d { "Stress test -> connect to first room" }
            launch { quickConnectToRoom(firstToken) }
            delay(200)
            room.disconnect()
            delay(50)
            Timber.d { "Stress test -> connect to second room" }
            launch { quickConnectToRoom(secondToken) }
            delay(200)
            room.disconnect()
            delay(50)
        }
    }

    private suspend fun quickConnectToRoom(token: String) {
        try {
            room.connect(
                url = url,
                token = token,
            )
        } catch (e: Throwable) {
            Timber.e(e) { "Failed to connect to room" }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun dumpReferenceTables() {
        try {
            val cls = Class.forName("android.os.Debug")
            val method = cls.getDeclaredMethod("dumpReferenceTables")
            val con = cls.getDeclaredConstructor().apply {
                isAccessible = true
            }
            method.invoke(con.newInstance())
        } catch (e: Exception) {
            LKLog.e(e) { "Unable to dump reference tables, you can try `adb shell settings put global hidden_api_policy 1`" }
        }
    }
}

private fun <T> LiveData<T>.hide(): LiveData<T> = this
private fun <T> MutableStateFlow<T>.hide(): StateFlow<T> = this
private fun <T> Flow<T>.hide(): Flow<T> = this

fun Participant.getNonEmptySCI(): String {
    (this as? LocalParticipant)?.let {
        return "local-participant"
    }
    return this.sid.value
}
