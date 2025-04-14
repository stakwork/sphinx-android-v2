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

    private val viewModel: CallViewModel by viewModels {
        val args = intent.getParcelableExtra<BundleArgs>(KEY_ARGS)
            ?: throw NullPointerException("args is null!")

        CallViewModel.provideFactory(
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
                    updateParticipantsBottomSheet(participants)
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

        lifecycleScope.launchWhenCreated {
            viewModel.participants.collect { participants ->
                updateParticipantsBottomSheet(participants)
                updateParticipantCountBadge(participants.size)
            }
        }

        // Controls setup
        setupCameraControls()
        setupMicControls()
        setupOtherControls()
    }

    private fun updateParticipantsBottomSheet(participants: List<Participant>) {
        if (bottomSheet == null) {
            bottomSheet = ParticipantsBottomSheetFragment.newInstance(
                participants.toMutableList(), 
                viewModel.participantColors
            )
        } else {
            bottomSheet?.updateParticipants(
                participants.toMutableList(), 
                viewModel.participantColors
            )
        }
    }

    private fun updateParticipantCountBadge(count: Int) {
        val participantCountBadge = findViewById<TextView>(R.id.participantCountBadge)
        if (count > 0) {
            participantCountBadge.visibility = View.VISIBLE
            participantCountBadge.text = count.toString()
        } else {
            participantCountBadge.visibility = View.GONE
        }
    }

    private fun setupCameraControls() {
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
                binding.camera.setColorFilter(ContextCompat.getColor(this, R.color.disabled_icons_color))
            }

            binding.camera.background = if (enabled) {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button)
            } else {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button_disabled)
            }

            binding.flipCamera.visibility = if (enabled) View.VISIBLE else View.GONE
            binding.flipCamera.isEnabled = enabled
        }

        binding.flipCamera.setOnClickListener { viewModel.flipCamera() }
    }

    private fun setupMicControls() {
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
                binding.mic.setColorFilter(ContextCompat.getColor(this, R.color.disabled_icons_color))
            }

            binding.mic.background = if (enabled) {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button)
            } else {
                ContextCompat.getDrawable(this, R.drawable.circle_icon_call_button_disabled)
            }
        }
    }

    private fun setupOtherControls() {
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
            bottomSheet?.let { sheet ->
                if (!sheet.isAdded) {
                    sheet.show(supportFragmentManager, sheet.tag)
                }
            }
        }

        binding.recordButton.setOnClickListener {
            viewModel.toggleRecording()
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
            val aspectRatio = Rational(9, 16)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)

            binding.controlsBox.visibility = View.GONE
            binding.controlsBox2.visibility = View.GONE
            binding.audienceRow.visibility = View.GONE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(9, 16)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)

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