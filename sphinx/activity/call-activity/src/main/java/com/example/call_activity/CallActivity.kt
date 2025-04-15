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
import android.util.Log
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
                            imageLoader = imageLoader,
                            color = viewModel.participantColors[participant.getNonEmptySCI()]
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
                            imageLoader = imageLoader,
                            color = viewModel.participantColors[participant.getNonEmptySCI()]
                        )
                    }
                speakerAdapter.update(items)
            }
        }

        lifecycleScope.launch {
            viewModel.startRecordingState
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest {
                    (recordingState: StartRecordingState, isLocalParticipantRecording: Boolean)  ->

                when (recordingState) {
                    StartRecordingState.Error -> {
                        binding.recordButton.isEnabled = true
                        showCustomToastMessage(messageRes = R.string.error_message_call_recording)
                    }

                    StartRecordingState.Empty -> {}

                    StartRecordingState.Recording -> {
                        showCustomToastMessage(messageRes = R.string.call_recording_in_progress_message)

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
                        showCustomToastMessage(messageRes = R.string.starting_call_recording_message)

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
                        showCustomToastMessage(messageRes = R.string.error_message_call_recording)
                    }

                    StopRecordingState.Empty -> {}

                    StopRecordingState.Stopped -> {
                        binding.apply {
                            showCustomToastMessage(messageRes = R.string.stopped_call_record_message)

                            recordButton.also {
                                it.clearAnimation()
                                it.isEnabled = true
                                it.setImageResource(R.drawable.radio_button_checked)
                            }
                        }
                    }

                    StopRecordingState.Loading -> {
                        showCustomToastMessage(messageRes = R.string.stopping_call_recording_message)

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
lifecycleScope.launchWhenCreated {
    viewModel.participants.collect { participants ->
        // Update or create bottom sheet
        if (bottomSheet == null) {
            bottomSheet = ParticipantsBottomSheetFragment.newInstance(
                participants.toMutableList(), 
                viewModel.participantColors
            ).apply {
                // Set up the fragment to handle updates
                this.participantsList = participants.toMutableList()
                this.colorsMap = viewModel.participantColors.toMutableMap()
            }
        }
        
        // Update existing bottom sheet
        bottomSheet?.let { sheet ->
            sheet.participantsList?.clear()
            sheet.participantsList?.addAll(participants)
            sheet.colorsMap?.clear()
            sheet.colorsMap?.putAll(viewModel.participantColors)
            
            // Force UI update
            sheet.view?.let { view ->
                val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
                recyclerView?.adapter?.notifyDataSetChanged()
                
                // Update count if available
                val countView = view.findViewById<TextView>(R.id.participantCount)
                countView?.text = participants.size.toString()
            }
        }
        
        // Update the badge in activity
        val participantCountBadge = findViewById<TextView>(R.id.participantCountBadge)
        participantCountBadge.visibility = if (participants.isNotEmpty()) View.VISIBLE else View.GONE
        participantCountBadge.text = participants.size.toString()
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

        binding.exit.setOnClickListener {
            lastToast?.cancel()
            viewModel.stopRecording()
            finish()
        }


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
            if (bottomSheet?.isAdded == false) {
                bottomSheet?.show(supportFragmentManager, bottomSheet?.tag)
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

    private fun showCustomToastMessage(messageRes: Int) {
        ToastUtils(
            toastLengthLong = true,
            textColor = android.R.color.white,
            toastBackgroundTint = R.color.color_recording_background,
            showOnTop = true,
            verticalPositionInPixels = 140
        ).show(
            this@CallActivity,
            messageRes
        ).let { response ->

            if (response is ToastUtilsResponse.Success) {
                lastToast = response.toast
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
        lastToast?.cancel()
        lastToast = null

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

// Extensions to access fragment properties directly
var ParticipantsBottomSheetFragment.participantsList: MutableList<Participant>?
    get() = arguments?.getParcelableArrayList<Participant>("participants")
    set(value) {
        arguments = (arguments ?: Bundle()).apply {
            putParcelableArrayList("participants", ArrayList(value ?: emptyList()))
        }
    }

var ParticipantsBottomSheetFragment.colorsMap: MutableMap<String, Int>?
    get() = arguments?.getSerializable("colors") as? MutableMap<String, Int>
    set(value) {
        arguments = (arguments ?: Bundle()).apply {
            putSerializable("colors", HashMap(value ?: emptyMap()))
        }
    }