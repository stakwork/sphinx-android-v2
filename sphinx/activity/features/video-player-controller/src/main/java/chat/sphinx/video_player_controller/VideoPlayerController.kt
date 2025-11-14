package chat.sphinx.video_player_controller

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.*

class VideoPlayerController(
    private val viewModelScope: CoroutineScope,
    private val updateIsPlaying: (Boolean) -> Unit,
    private val updateMetaDataCallback: (Int, Int, Int) -> Unit,
    private val updateCurrentTimeCallback: (Int) -> Unit,
    private val completePlaybackCallback: () -> Unit,
    private val showLoadingCallback: () -> Unit,  // NEW
    private val hideLoadingCallback: () -> Unit,  // NEW
    dispatchers: CoroutineDispatchers,
) : CoroutineDispatchers by dispatchers {

    private var videoView: VideoView? = null

    fun setVideo(videoView: VideoView) {
        this.videoView = videoView
    }

    fun initializeVideo(
        videoUri: Uri,
        videoDuration: Int? = null
    ) {
        showLoadingCallback()

        videoView?.apply {
            setOnCompletionListener {
                completePlaybackCallback()
            }
            setOnPreparedListener {
                updateMetaDataCallback(
                    videoDuration ?: it.duration,
                    it.videoWidth,
                    it.videoHeight
                )
                hideLoadingCallback()
                play()
            }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                        showLoadingCallback()
                    }
                    android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        hideLoadingCallback()
                    }
                }
                false
            }

            setVideoURI(videoUri)
        }
    }

    private fun play() {
        videoView?.start()
        startDispatchStateJob()
        updateIsPlaying(true)
    }

    fun seekTo(progress: Int) {
        showLoadingCallback()
        videoView?.seekTo(progress)
    }

    fun seekToTime(timeMillis: Long) {
        showLoadingCallback()
        videoView?.let { video ->
            if (video.canSeekForward() || video.canSeekBackward()) {
                video.seekTo(timeMillis.toInt())

                viewModelScope.launch(mainImmediate) {
                    delay(500L) // Wait for seek to stabilize
                    hideLoadingCallback()
                }

                // If video is paused, start playing after seek
                if (!video.isPlaying) {
                    play()
                }
            }
        }
    }

    fun pause() {
        videoView?.pause()
        dispatchStateJob?.cancel()
        updateIsPlaying(false)
    }

    fun togglePlayPause() {
        if (videoView?.isPlaying == true) {
            pause()
        } else {
            play()
        }
    }

    fun clear() {
        videoView?.stopPlayback()
        dispatchStateJob?.cancel()
    }

    private var dispatchStateJob: Job? = null
    private fun startDispatchStateJob() {
        if (dispatchStateJob?.isActive == true) {
            return
        }

        dispatchStateJob = viewModelScope.launch(mainImmediate) {
            videoView?.let { video ->
                while (isActive) {
                    if (video.isPlaying) {
                        updateCurrentTimeCallback(video.currentPosition)
                        delay(250L)
                    } else {
                        break
                    }
                }
            }
        }
    }
}

fun Uri.getMediaDuration(
    isLocalFile: Boolean
): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        if (Build.VERSION.SDK_INT >= 14 && !isLocalFile) {
            retriever.setDataSource(this.toString(), HashMap<String, String>())
        } else {
            retriever.setDataSource(this.toString())
        }
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        duration?.toLongOrNull() ?: 0
    } catch (exception: Exception) {
        0
    }
}