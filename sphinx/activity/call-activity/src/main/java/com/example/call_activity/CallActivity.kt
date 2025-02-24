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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import chat.sphinx.call_activity.R
import chat.sphinx.call_activity.databinding.CallActivityBinding
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import com.example.call_activity.dialog.showDebugMenuDialog
import com.example.call_activity.state.StartRecordingState
import com.example.call_activity.state.StopRecordingState
import com.squareup.moshi.Moshi
import com.xwray.groupie.GroupieAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
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

    private lateinit var binding: CallActivityBinding
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

        // Audience row setup
        val audienceAdapter = GroupieAdapter()
        binding.audienceRow.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = audienceAdapter
        }

        lifecycleScope.launchWhenCreated {
            viewModel.participants
                .collect { participants ->
                    val items = participants.map { participant ->
                        ParticipantItem(
                            viewModel.room,
                            participant,
                            moshi = moshi,
                            imageLoader = imageLoader
                        )
                    }
                    audienceAdapter.update(items)
                }
        }

        // speaker view setup
        val speakerAdapter = GroupieAdapter()
        binding.speakerView.apply {
            layoutManager = LinearLayoutManager(this@CallActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = speakerAdapter
        }
        lifecycleScope.launchWhenCreated {
            viewModel.primarySpeaker.collectLatest { speaker ->
                val items = listOfNotNull(speaker)
                    .map { participant ->
                        ParticipantItem(
                            viewModel.room,
                            participant,
                            speakerView = true,
                            moshi = moshi,
                            imageLoader = imageLoader
                        )
                    }
                speakerAdapter.update(items)
            }
        }

        lifecycleScope.launch {
            viewModel.startRecordingState.collectLatest {
                (recordingState: StartRecordingState, isLocalParticipantRecording: Boolean)  ->

                when (recordingState) {
                    StartRecordingState.Error -> {
                        binding.apply {
                            recordingMessageBox.clearAnimation()
                            recordingMessage.text = getString(R.string.error_message_call_recording)
                            fadeOutAnimation(viewToFadeOut = recordingMessageBox)
                        }
                    }

                    StartRecordingState.Empty -> {}

                    StartRecordingState.Recording -> {
                        binding.apply {
                            recordingMessageBox.also {
                                it.clearAnimation()
                                it.visibility = View.VISIBLE
                            }

                            recordingMessage.text = getString(R.string.call_recording_in_progress_message)
                            recordButton.setImageDrawable(getRecordDrawable(isLocalParticipantRecording))

                            fadeInFadeOutAnimation(
                                viewToFadeOut = recordButton,
                                durationMillis = 700,
                                alphaStartValue = 1.0f,
                                alphaEndValue = 0.5f,
                            )
                        }
                    }

                    StartRecordingState.Loading -> {
                        binding.apply {
                            recordingMessageBox.also {
                                it.clearAnimation()
                                it.visibility = View.VISIBLE
                            }

                            recordingMessage.text = getString(R.string.starting_call_recording_message)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.stopRecordingState.collectLatest { state ->
                when(state) {
                    StopRecordingState.Error -> {
                        binding.apply {
                            recordingMessageBox.clearAnimation()
                            recordButton.isEnabled = true
                            recordingMessage.text = getString(R.string.error_message_call_recording)

                            fadeOutAnimation(
                                viewToFadeOut = recordingMessageBox,
                                durationMillis = 10000
                            )
                        }
                    }

                    StopRecordingState.Empty -> {}

                    StopRecordingState.Stopped -> {
                        binding.apply {
                            recordingMessageBox.clearAnimation()
                            recordingMessage.text = getString(R.string.stopped_call_record_message)

                            recordButton.also {
                                it.clearAnimation()
                                it.isEnabled = true
                                it.setImageResource(R.drawable.radio_button_checked)
                            }

                            fadeOutAnimation(
                                viewToFadeOut = recordingMessageBox,
                                durationMillis = 10000
                            )
                        }
                    }

                    StopRecordingState.Loading -> {
                        binding.apply {
                            recordingMessageBox.also {
                                it.clearAnimation()
                                it.visibility = View.VISIBLE
                            }

                            recordButton.isEnabled = false
                            recordingMessage.text = getString(R.string.stopping_call_recording_message)
                        }
                    }
                }
            }
        }

        // Controls setup
        viewModel.cameraEnabled.observe(this) { enabled ->
            binding.camera.setOnClickListener { viewModel.setCameraEnabled(!enabled) }

            binding.camera.setImageResource(
                if (enabled) {
                    R.drawable.camera
                } else {
                    R.drawable.camera_off
                }
            )

            if (enabled) {
                binding.camera.clearColorFilter()
            } else {
                binding.camera.setColorFilter(ContextCompat.getColor(this, R.color.disabled_icons_color)) // Apply red tint when off
            }

            binding.camera.background = if (enabled) {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button)
            } else {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button_disabled)
            }

            binding.flipCamera.visibility = if (enabled) {
                View.VISIBLE
            } else {
                View.GONE
            }


            binding.flipCamera.isEnabled = enabled
        }

        viewModel.micEnabled.observe(this) { enabled ->
            binding.mic.setOnClickListener { viewModel.setMicEnabled(!enabled) }
            binding.mic.setImageResource(
                if (enabled) {
                    R.drawable.mic
                } else {
                    R.drawable.mic_off
                }
            )

            if (enabled) {
                binding.mic.clearColorFilter()
            } else {
                binding.mic.setColorFilter(ContextCompat.getColor(this, R.color.disabled_icons_color)) // Apply red tint when off
            }

            binding.mic.background = if (enabled) {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button)
            } else {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button_disabled)
            }
        }


        binding.flipCamera.setOnClickListener { viewModel.flipCamera() }

        /* viewModel.screenshareEnabled.observe(this) { enabled ->
            binding.screenShare.setOnClickListener {
                if (enabled) {
                    viewModel.stopScreenCapture()
                } else {
                    requestMediaProjection()
                }
            }
            binding.screenShare.setImageResource(
                if (enabled) {
                    R.drawable.baseline_cast_connected_24
                } else {
                    R.drawable.baseline_cast_24
                },
            )
        }

        binding.message.setOnClickListener {
            val editText = EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Send Message")
                .setView(editText)
                .setPositiveButton("Send") { dialog, _ ->
                    viewModel.sendData(editText.text?.toString() ?: "")
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .create()
                .show()
        }
©
        viewModel.enhancedNsEnabled.observe(this) { enabled ->
            binding.enhancedNs.visibility = if (enabled) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        binding.enhancedNs.setOnClickListener {
            showAudioProcessorSwitchDialog(viewModel)
        }*/

        binding.exit.setOnClickListener { finish() }


//        binding.audioSelect.setOnClickListener {
//            showSelectAudioDeviceDialog(viewModel)
//        }
        /* lifecycleScope.launchWhenCreated {
            viewModel.permissionAllowed.collect { allowed ->
                val resource = if (allowed) R.drawable.account_cancel_outline else R.drawable.account_cancel
                binding.permissions.setImageResource(resource)
            }
        }
        binding.permissions.setOnClickListener {
            viewModel.toggleSubscriptionPermissions()
        }*/

        binding.debugMenu.setOnClickListener {
            showDebugMenuDialog(viewModel)
        }

        binding.pipButtonContainer.setOnClickListener {
            enterPictureInPictureMode()
        }

        binding.listParticipants.setOnClickListener {
            lifecycleScope.launchWhenCreated {
                // Collect participants list only when the button is clicked
                viewModel.participants.collect { participants ->

                    // Only show BottomSheet when user clicks the button
                    val bottomSheet = ParticipantsBottomSheetFragment(participants)
                    bottomSheet.show(supportFragmentManager, bottomSheet.tag)
                }
            }
        }

        binding.recordButton.setOnClickListener {
            viewModel.toggleRecording()
        }

        lifecycleScope.launchWhenCreated {
            viewModel.participants.collect { participants ->
                // Update the participant count on the badge
                val participantCountBadge = findViewById<TextView>(R.id.participantCountBadge)
                if (participants.isNotEmpty()) {
                    // Show badge and set the count
                    participantCountBadge.visibility = View.VISIBLE
                    participantCountBadge.text = participants.size.toString()
                } else {
                    // Hide badge if no participants
                    participantCountBadge.visibility = View.GONE
                }
            }
        }

    }

    private fun getRecordDrawable(isLocalParticipantRecording: Boolean): Drawable? {
        val recordButtonActiveColor = resources.getColor(
            R.color.disabled_icons_color,
            null
        )

        val drawable = ResourcesCompat.getDrawable(
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
        return drawable
    }

    private fun fadeOutAnimation(
        viewToFadeOut: View,
        durationMillis: Long = 3500
    ) {
        val animation = AlphaAnimation(1f, 0f).apply {
            fillAfter = true
            duration = durationMillis
        }

        viewToFadeOut.startAnimation(animation)
    }

    private fun fadeInFadeOutAnimation(
        viewToFadeOut: View,
        durationMillis: Long = 500,
        alphaStartValue: Float =  1.0f,
        alphaEndValue: Float =  0.5f,
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
        // Trigger PiP mode when the user presses the home button or switches apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(9, 16) // Aspect ratio of the PiP window
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)

            binding.controlsBox.visibility = android.view.View.GONE
            binding.controlsBox2.visibility = android.view.View.GONE
            binding.audienceRow.visibility = android.view.View.GONE

        }
    }

    // Function to start Picture-in-Picture mode
    @Deprecated("Deprecated in Java")
    override fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(9, 16) // Define the aspect ratio of the PiP window
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)  // Set aspect ratio
                .build()
            enterPictureInPictureMode(pipParams)

            binding.controlsBox.visibility = android.view.View.GONE
            binding.controlsBox2.visibility = android.view.View.GONE
            binding.audienceRow.visibility = android.view.View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launchWhenResumed {
            viewModel.error.collect {
                if (it != null) {
                    Toast.makeText(this@CallActivity, "Error: $it", Toast.LENGTH_LONG).show()
                    viewModel.dismissError()
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            viewModel.dataReceived.collect {
                Toast.makeText(this@CallActivity, "Data received: $it", Toast.LENGTH_LONG).show()
            }
        }

        binding.controlsBox.visibility = View.VISIBLE
        binding.controlsBox2.visibility = View.VISIBLE
        binding.audienceRow.visibility = View.VISIBLE
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    override fun onDestroy() {
        binding.audienceRow.adapter = null
        binding.speakerView.adapter = null
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