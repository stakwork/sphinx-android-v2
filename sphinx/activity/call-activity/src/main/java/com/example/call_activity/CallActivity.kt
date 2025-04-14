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
    private var isBottomSheetVisible = false

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
            }
        }

        // Speaker view setup
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

        // Participants list setup
        lifecycleScope.launchWhenCreated {
            viewModel.participants.collect { participants ->
                if (bottomSheet == null) {
                    bottomSheet = ParticipantsBottomSheetFragment.newInstance(
                        participants.toMutableList(),
                        viewModel.participantColors
                    ).apply {
                        dialog?.setOnDismissListener {
                            isBottomSheetVisible = false
                        }
                    }
                } else {
                    bottomSheet?.setParticipants(participants.toMutableList(), viewModel.participantColors)
                }

                // Update immediately if sheet is visible
                if (isBottomSheetVisible) {
                    bottomSheet?.setParticipants(participants.toMutableList(), viewModel.participantColors)
                }

                // Update participant count badge
                findViewById<TextView>(R.id.participantCountBadge)?.apply {
                    visibility = if (participants.isNotEmpty()) View.VISIBLE else View.GONE
                    text = participants.size.toString()
                }
            }
        }

        // [Rest of your existing onCreate code remains exactly the same...]
        // Including:
        // - Recording state handlers
        // - Control setups (camera, mic, etc.)
        // - Other click listeners

        binding.listParticipants.setOnClickListener {
            bottomSheet?.let { sheet ->
                if (!sheet.isAdded) {
                    sheet.show(supportFragmentManager, "participants_bottom_sheet")
                    isBottomSheetVisible = true
                }
            }
        }
    }

    // [All other existing methods remain exactly the same...]
    // Including:
    // - showCustomToastMessage()
    // - getRecordDrawable()
    // - fadeInFadeOutAnimation()
    // - onUserLeaveHint()
    // - enterPictureInPictureMode()
    // - onResume()
    // - requestMediaProjection()
    // - onDestroy()

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