package com.example.call_activity

import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import chat.sphinx.call_activity.R
import chat.sphinx.call_activity.databinding.CallActivityBinding
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import chat.sphinx.resources.getRandomColor
import chat.sphinx.resources.setBackgroundRandomColor
import com.example.call_activity.dialog.showDebugMenuDialog
import com.example.call_activity.state.StartRecordingState
import com.example.call_activity.state.StopRecordingState
import com.squareup.moshi.Moshi
import com.xwray.groupie.GroupieAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_toast_utils.ToastUtils
import io.matthewnelson.android_feature_toast_utils.ToastUtilsResponse
import io.matthewnelson.android_feature_toast_utils.show
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@AndroidEntryPoint
class CallActivity : AppCompatActivity() {

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var imageLoader: ImageLoader<ImageView>

    @Inject
    lateinit var networkQueryChat: NetworkQueryChat

    @Inject
    lateinit var dispatchers: CoroutineDispatchers

    private var lastToast: Toast? = null
    private var bottomSheet: ParticipantsBottomSheetFragment? = null
    private lateinit var binding: CallActivityBinding

    private val viewModel: CallViewModel by viewModelByFactory {
        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")

        CallViewModel(
            url = args.url,
            token = args.token,
            e2ee = args.e2eeOn,
            e2eeKey = args.e2eeKey,
            videoEnabled = args.videoEnabled,
            stressTest = args.stressTest,
            application = application,
            roomName = args.roomName ?: "",
            networkQueryChat = networkQueryChat,
            dispatchers = dispatchers
        )
    }

    private val screenCaptureIntentLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val resultCode = result.resultCode
            val data = result.data
            if (resultCode != Activity.RESULT_OK || data == null) {
                return@registerForActivityResult
            }
            viewModel.startScreenCapture(data)
        }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = CallActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNeededPermissions {
            viewModel.reconnect()
        }

        // Initialize bottom sheet with empty data
        bottomSheet = ParticipantsBottomSheetFragment.newInstance(
            emptyList(),
            mutableMapOf()
        )

        setupAudienceView()
        setupSpeakerView()
        setupRecordingStateObservers()
        setupControls()
        setupParticipantCountObserver()
    }

    private fun setupAudienceView() {
        val audienceAdapter = GroupieAdapter()
        binding.audienceRow.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = audienceAdapter
        }

        lifecycleScope.launchWhenCreated {
            viewModel.participants.collect { participants ->
                val items = participants.map { participant ->
                    ParticipantItem(
                        viewModel.room,
                        participant,
                        moshi = moshi,
                        imageLoader = imageLoader,
                        color = viewModel.participantColors[participant.getNonEmptySCI()]
                    )
                }
                audienceAdapter.update(items)

                // Update bottom sheet with current participants
                bottomSheet?.updateParticipants(participants, viewModel.participantColors)
            }
        }
    }

    private fun setupSpeakerView() {
        val speakerAdapter = GroupieAdapter()
        binding.speakerView.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = speakerAdapter
        }

        lifecycleScope.launchWhenCreated {
            viewModel.primarySpeaker.collectLatest { speaker ->
                val items = listOfNotNull(speaker).map { participant ->
                    ParticipantItem(
                        viewModel.room,
                        participant,
                        speakerView = true,
                        moshi = moshi,
                        imageLoader = imageLoader,
                        color = viewModel.participantColors[participant.getNonEmptySCI()]
                    )
                }
                speakerAdapter.update(items)
            }
        }
    }

    private fun setupRecordingStateObservers() {
        lifecycleScope.launch {
            viewModel.startRecordingState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { (recordingState, isLocalParticipantRecording) ->
                    when (recordingState) {
                        StartRecordingState.Error -> {
                            binding.recordButton.isEnabled = true
                            showCustomToastMessage(R.string.error_message_call_recording)
                        }
                        StartRecordingState.Empty -> {}
                        StartRecordingState.Recording -> {
                            showCustomToastMessage(R.string.call_recording_in_progress_message)
                            binding.apply {
                                recordButton.also {
                                    it.setImageDrawable(getRecordDrawable(isLocalParticipantRecording))
                                    it.isEnabled = isLocalParticipantRecording
                                }
                                fadeInFadeOutAnimation(
                                    viewToFadeOut = recordButton,
                                    durationMillis = 700,
                                    alphaStartValue = 1.0f,
                                    alphaEndValue = 0.5f,
                                )
                            }
                        }
                        StartRecordingState.Loading -> {
                            showCustomToastMessage(R.string.starting_call_recording_message)
                            binding.recordButton.isEnabled = false
                        }
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.stopRecordingState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest { state ->
                    when(state) {
                        StopRecordingState.Error -> {
                            showCustomToastMessage(R.string.error_message_call_recording)
                        }
                        StopRecordingState.Empty -> {}
                        StopRecordingState.Stopped -> {
                            binding.apply {
                                showCustomToastMessage(R.string.stopped_call_record_message)
                                recordButton.also {
                                    it.clearAnimation()
                                    it.isEnabled = true
                                    it.setImageResource(R.drawable.radio_button_checked)
                                }
                            }
                        }
                        StopRecordingState.Loading -> {
                            showCustomToastMessage(R.string.stopping_call_recording_message)
                            binding.apply {
                                recordButton.also {
                                    it.isEnabled = false
                                    it.setImageResource(R.drawable.radio_button_checked_recording)
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun setupControls() {
        viewModel.cameraEnabled.observe(this) { enabled ->
            binding.camera.apply {
                setOnClickListener { viewModel.setCameraEnabled(!enabled) }
                setImageResource(if (enabled) R.drawable.camera else R.drawable.camera_off)
                if (enabled) {
                    clearColorFilter()
                } else {
                    setColorFilter(ContextCompat.getColor(this@CallActivity, R.color.disabled_icons_color))
                }
                background = ContextCompat.getDrawable(
                    this@CallActivity,
                    if (enabled) R.drawable.circle_icon_call_button else R.drawable.circle_icon_call_button_disabled
                )
            }
            binding.flipCamera.apply {
                visibility = if (enabled) View.VISIBLE else View.GONE
                isEnabled = enabled
                setOnClickListener { viewModel.flipCamera() }
            }
        }

        viewModel.micEnabled.observe(this) { enabled ->
            binding.mic.apply {
                setOnClickListener { viewModel.setMicEnabled(!enabled) }
                setImageResource(if (enabled) R.drawable.mic else R.drawable.mic_off)
                if (enabled) {
                    clearColorFilter()
                } else {
                    setColorFilter(ContextCompat.getColor(this@CallActivity, R.color.disabled_icons_color))
                }
                background = ContextCompat.getDrawable(
                    this@CallActivity,
                    if (enabled) R.drawable.circle_icon_call_button else R.drawable.circle_icon_call_button_disabled
                )
            }
        }

        binding.exit.setOnClickListener {
            lastToast?.cancel()
            viewModel.stopRecording()
            finish()
        }

        binding.debugMenu.setOnClickListener {
            showDebugMenuDialog(viewModel)
        }

        binding.pipButtonContainer.setOnClickListener {
            enterPictureInPictureMode()
        }

        binding.listParticipants.setOnClickListener {
            bottomSheet?.let {
                if (!it.isAdded) {
                    it.show(supportFragmentManager, it.tag)
                }
            }
        }

        binding.recordButton.setOnClickListener {
            viewModel.toggleRecording()
        }
    }

    private fun setupParticipantCountObserver() {
        lifecycleScope.launchWhenCreated {
            viewModel.participants.collect { participants ->
                updateParticipantCountBadge(participants.size)
            }
        }
    }

    private fun updateParticipantCountBadge(count: Int) {
        binding.participantCountBadge.apply {
            visibility = if (count > 0) View.VISIBLE else View.GONE
            text = count.toString()
        }
    }

    private fun showCustomToastMessage(messageRes: Int) {
        ToastUtils(
            toastLengthLong = true,
            textColor = android.R.color.white,
            toastBackgroundTint = R.color.color_recording_background,
            showOnTop = true,
            verticalPositionInPixels = 140
        ).show(this, messageRes).let { response ->
            if (response is ToastUtilsResponse.Success) {
                lastToast = response.toast
            }
        }
    }

    private fun getRecordDrawable(isLocalParticipantRecording: Boolean): Drawable? {
        val recordButtonActiveColor = resources.getColor(R.color.disabled_icons_color, null)
        return ResourcesCompat.getDrawable(
            resources,
            if (isLocalParticipantRecording) R.drawable.stop_circle else R.drawable.radio_button_checked,
            null
        )?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                colorFilter = BlendModeColorFilter(recordButtonActiveColor, BlendMode.SRC_ATOP)
            } else {
                setColorFilter(recordButtonActiveColor, PorterDuff.Mode.SRC_ATOP)
            }
        }
    }

    private fun fadeInFadeOutAnimation(
        viewToFadeOut: View,
        durationMillis: Long = 500,
        alphaStartValue: Float = 1.0f,
        alphaEndValue: Float = 0.5f,
    ) {
        val animation = AlphaAnimation(alphaStartValue, alphaEndValue).apply {
            fillAfter = true
            duration = durationMillis
            repeatCount = Animation.INFINITE
            interpolator = AccelerateInterpolator()
            repeatMode = Animation.REVERSE
        }
        viewToFadeOut.startAnimation(animation)
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            )
            binding.controlsBox.visibility = View.GONE
            binding.controlsBox2.visibility = View.GONE
            binding.audienceRow.visibility = View.GONE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            )
            binding.controlsBox.visibility = View.GONE
            binding.controlsBox2.visibility = View.GONE
            binding.audienceRow.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        binding.controlsBox.visibility = View.VISIBLE
        binding.controlsBox2.visibility = View.VISIBLE
        binding.audienceRow.visibility = View.VISIBLE

        lifecycleScope.launchWhenResumed {
            viewModel.error.collect {
                it?.let { error ->
                    Toast.makeText(this@CallActivity, "Error: $error", Toast.LENGTH_LONG).show()
                    viewModel.dismissError()
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.dataReceived.collect {
                Toast.makeText(this@CallActivity, "Data received: $it", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        binding.audienceRow.adapter = null
        binding.speakerView.adapter = null
        lastToast?.cancel()
        lastToast = null
        bottomSheet = null
        super.onDestroy()
    }

    companion object {
        const val KEY_ARGS = "args"
    }

    @Parcelize
    data class BundleArgs(
        val url: String,
        val token: String,
        val e2eeKey: String,
        val e2eeOn: Boolean,
        val stressTest: StressTest,
        val videoEnabled: Boolean,
        val roomName: String? = null
    ) : Parcelable
}