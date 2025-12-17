package chat.sphinx.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.*
import android.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.RotationProvider
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.cash.exhaustive.Exhaustive
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.camera.R
import chat.sphinx.resources.R as R_common
import com.example.android.camera.utils.R as R_camera
import chat.sphinx.camera.databinding.FragmentCameraBinding
import chat.sphinx.camera.model.CameraItem
import chat.sphinx.camera.model.LensFacing
import chat.sphinx.camera.ui.viewstate.CameraViewState
import chat.sphinx.camera.ui.viewstate.CapturePreviewViewState
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.insetter_activity.InsetterActivity
import chat.sphinx.insetter_activity.addNavigationBarPadding
import chat.sphinx.screen_detail_fragment.SideEffectDetailFragment
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.android_feature_viewmodel.collectViewState
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject


@AndroidEntryPoint
internal class CameraFragment: SideEffectDetailFragment<
        FragmentActivity,
        CameraSideEffect,
        CameraViewState,
        CameraViewModel,
        FragmentCameraBinding,
        >(R.layout.fragment_camera)
{
    @Suppress("PrivatePropertyName")
    private val PERMISSIONS_REQUIRED = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    override val binding: FragmentCameraBinding by viewBinding(FragmentCameraBinding::bind)

    override val viewModel: CameraViewModel by viewModels()

    @Volatile
    private var rotationProvider: RotationProvider? = null
    private var lastRotation: Int? = null

    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>

    private var activeRecording: Recording? = null
    private lateinit var recordingState: VideoRecordEvent

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(requireContext()) }
    private lateinit var cameraExecutor: ExecutorService

    private val captureListener = Consumer<VideoRecordEvent> { event ->
        // cache the recording state
        if (event !is VideoRecordEvent.Status)
            recordingState = event

        updateUI(event)

        if (event is VideoRecordEvent.Finalize) {
            event.outputResults.outputUri.path?.let {
                lifecycleScope.launch(viewModel.io) {
                    viewModel.updateMediaPreviewViewState(
                        CapturePreviewViewState.Preview.VideoPreview(File(it))
                    )
                }
            }
        }
    }

    private val imageSavedCallback = object : ImageCapture.OnImageSavedCallback {
        override fun onError(exc: ImageCaptureException) {
            // TODO: Give user feedback on failure
        }

        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            lifecycleScope.launch(viewModel.io) {
                output.savedUri?.let { photoUri ->
                    val imageFile = photoUri.toFile()

                    viewModel.updateMediaPreviewViewState(
                        CapturePreviewViewState.Preview.ImagePreview(imageFile)
                    )
                    delay(200L)
                }
            }
        }
    }

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var imageLoader: ImageLoader<ImageView>

    private val requestPermissionLauncher by lazy(LazyThreadSafetyMode.NONE) {
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { response ->

            try {
                for (permission in PERMISSIONS_REQUIRED) {
                    if (response[permission] != true) {
                        throw Exception()
                    }
                }

                if (currentViewState !is CameraViewState.Active) {
                    viewModel.updateViewState(
                        CameraViewState.Active.BackCamera(viewModel.getBackCamera())
                    )
                }
            } catch (e: Exception) {
                lifecycleScope.launch(viewModel.mainImmediate) {
                    viewModel.submitSideEffect(
                        CameraSideEffect.Notify(getString(R.string.camera_permissions_required))
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        (requireActivity() as InsetterActivity)
            .addNavigationBarPadding(binding.includeCameraFooter.root)
            .addNavigationBarPadding(binding.includeCameraMediaPreview.layoutConstraintCameraMediaPreviewFooter)

        if (!hasPermissions(requireContext())) {
            requestPermissionLauncher.launch(PERMISSIONS_REQUIRED)
        } else {
            if (currentViewState !is CameraViewState.Active) {
                viewModel.updateViewState(
                    CameraViewState.Active.BackCamera(viewModel.getBackCamera())
                )
            }
        }
        rotationProvider = RotationProvider(binding.root.context)

        binding.includeCameraFooter.imageViewCameraFooterBackFront.setOnClickListener {
            @Exhaustive
            when (currentViewState) {
                is CameraViewState.Active.BackCamera -> {
                    viewModel.updateViewState(
                        CameraViewState.Active.FrontCamera(viewModel.getFrontCamera())
                    )
                }
                null,
                is CameraViewState.Idle,
                is CameraViewState.Active.FrontCamera -> {
                    viewModel.updateViewState(
                        CameraViewState.Active.BackCamera(viewModel.getBackCamera())
                    )
                }
            }
        }

        binding.includeCameraMediaPreview.apply {
            imageViewCameraImagePreview.setOnClickListener {
                viewModel
            }
            textViewCameraMediaPreviewRetake.setOnClickListener {
                @Exhaustive
                when (val vs = viewModel.currentCapturePreviewViewState) {
                    is CapturePreviewViewState.None -> {}
                    is CapturePreviewViewState.Preview.ImagePreview -> {
                        viewModel.deleteMedia(vs.media)
                        viewModel.updateMediaPreviewViewState(CapturePreviewViewState.None)
                    }
                    is CapturePreviewViewState.Preview.VideoPreview -> {
                        viewModel.deleteMedia(vs.media)
                        viewModel.updateMediaPreviewViewState(CapturePreviewViewState.None)
                    }
                }
            }
            textViewCameraMediaPreviewUse.setOnClickListener {
                @Exhaustive
                when (val vs = viewModel.currentCapturePreviewViewState) {
                    is CapturePreviewViewState.None -> {}
                    is CapturePreviewViewState.Preview -> {
                        textViewCameraMediaPreviewRetake.isEnabled = false
                        viewModel.processSuccessfulResponse(vs)
                    }
                }
            }
        }

        binding.includeCameraFooter.textViewCameraFooterCancel.setOnClickListener {
            viewModel.processCancellationResponse()
        }
    }

    override fun closeDetailsScreen() {
        lifecycleScope.launch(viewModel.mainImmediate) {
            viewModel.processCancellationResponse()
        }
    }

    private fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera(cameraItem: CameraItem) {
        lifecycleScope.launch(viewModel.mainImmediate) {
            val cameraProvider = ProcessCameraProvider.getInstance(requireContext()).await()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(cameraItem.lensFacing.toInt())
                .build()

            val currentRotation = binding.previewViewCamera.display.rotation

            val displayMetrics = requireContext().resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val screenRatio = Rational(screenWidth, screenHeight)

            val viewport = ViewPort.Builder(screenRatio, currentRotation).build()

            val preview = Preview.Builder()
                .setTargetRotation(currentRotation)
                .build().apply {
                    setSurfaceProvider(binding.previewViewCamera.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(currentRotation)
                .build()

            val recorder = Recorder.Builder().build()
            videoCapture = VideoCapture.withOutput(recorder)

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .addUseCase(videoCapture)
                .setViewPort(viewport)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    requireParentFragment(),
                    cameraSelector,
                    useCaseGroup
                )

            } catch (exc: Exception) {
                Log.e("CAMERA_SETUP", "Camera binding failed: ${exc.message}")
            }

            binding.includeCameraFooter.imageViewCameraFooterShutter.setOnClickListener {
                lifecycleScope.launch(viewModel.io) {
                    val photoFile = viewModel.createFile(IMAGE_EXTENSION, true)

                    val metadata = ImageCapture.Metadata().apply {
                        isReversedHorizontal = cameraItem.lensFacing == LensFacing.Front
                    }

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                        .setMetadata(metadata)
                        .build()

                    imageCapture.takePicture(
                        outputOptions,
                        cameraExecutor,
                        imageSavedCallback
                    )
                }

            }
            binding.includeCameraFooter.imageViewCameraFooterShutter.setOnLongClickListener { view ->
                lifecycleScope.launch(viewModel.mainImmediate) {
                    if (activeRecording == null || recordingState is VideoRecordEvent.Finalize) {
                        startRecording()
                    }
                }

                return@setOnLongClickListener true
            }

            binding.includeCameraFooter.imageViewCameraFooterShutter.setOnTouchListener { view, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP) {
                    lifecycleScope.launch(viewModel.io) {
                        if (activeRecording != null && recordingState !is VideoRecordEvent.Finalize) {
                            activeRecording?.stop()
                            activeRecording = null
                            delay(200L)
                        }
                    }
                }
                return@setOnTouchListener view.onTouchEvent(motionEvent)
            }

            // re-enable button to switch between back/front camera
            binding.includeCameraFooter.imageViewCameraFooterBackFront.isEnabled = true
        }
    }

    @SuppressLint("RestrictedApi")
    private val rotationListener = { rotation: Int ->
        Log.d("ROTATION_LISTENER", "Rotation changed from $lastRotation to $rotation")

        // Always update the target rotation for capture
        if (::imageCapture.isInitialized) {
            imageCapture.targetRotation = rotation
        }
        if (::videoCapture.isInitialized) {
            videoCapture.targetRotation = rotation
        }

        lastRotation = rotation
    }


    /**
     * UpdateUI according to CameraX VideoRecordEvent type
     */
    private fun updateUI(event: VideoRecordEvent) {
        lifecycleScope.launch(viewModel.mainImmediate) {
            binding.includeCameraFooter.imageViewCameraFooterShutter.setImageDrawable(
                AppCompatResources.getDrawable(requireContext(),
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            R_camera.drawable.ic_shutter_recording
                        }
                        is VideoRecordEvent.Finalize-> {
                            R_camera.drawable.ic_shutter
                        }
                        else -> {
                            R_camera.drawable.ic_shutter_recording
                        }
                    }
                )
            )
        }
    }

    /**
     * Kick start the video recording
     *   - config Recorder to capture to MediaStoreOutput
     *   - register RecordEvent Listener
     *   - apply audio request from user
     *   - start recording!
     * After this function, user could start/pause/resume/stop recording and application listens
     * to VideoRecordEvent for the current recording status.
     */
    @SuppressLint("MissingPermission")
    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val fileOutputOptions = FileOutputOptions.Builder(
            viewModel.createFile(VIDEO_EXTENSION, false)
        ).build()

        val pendingRecording = videoCapture.output
            .prepareRecording(requireActivity(), fileOutputOptions)
            .withAudioEnabled()

        activeRecording = pendingRecording.start(mainThreadExecutor, captureListener)
    }

    override suspend fun onSideEffectCollect(sideEffect: CameraSideEffect) {
        sideEffect.execute(requireActivity())
    }

    override suspend fun onViewStateFlowCollect(viewState: CameraViewState) {
        @Exhaustive
        when (viewState) {
            is CameraViewState.Idle -> {}
            is CameraViewState.Active -> {
                viewState.cameraItem?.let { item ->

                    binding.includeCameraFooter.imageViewCameraFooterBackFront.isEnabled = false

                    try {
                        rotationProvider?.removeListener(rotationListener)
                        startCamera(item)
                        rotationProvider?.addListener(mainThreadExecutor, rotationListener)
                    } catch (e: Exception) {}
                } // TODO: handle null case with no camera available view
            }
        }
    }

    override fun subscribeToViewStateFlow() {
        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.collectViewState { viewState ->
                currentViewState = viewState
                onViewStateFlowCollect(viewState)
            }
        }
        onStopSupervisor.scope.launch(viewModel.io) {
            viewModel.collectImagePreviewViewState { viewState ->
                binding.includeCameraMediaPreview.apply {
                    lifecycleScope.launch(viewModel.mainImmediate) {
                        @Exhaustive
                        when (viewState) {
                            is CapturePreviewViewState.Preview.VideoPreview -> {
                                if (viewState.media.exists()) {
                                    root.visible
                                    videoViewCameraVideoPreview.visible
                                    imageViewCameraImagePreview.gone
                                    imageViewVideoPreviewPlayPause.gone

                                    textViewCameraMediaPreviewUse.text = getString(R.string.camera_use_video)

                                    val uri = viewState.media.toUri()
                                    videoViewCameraVideoPreview.setVideoURI(uri)
                                    videoViewCameraVideoPreview.setOnPreparedListener { mediaPlayer ->
                                        mediaPlayer.start()
                                        imageViewVideoPreviewPlayPause.visible
                                        imageViewVideoPreviewPlayPause.setImageDrawable(
                                            AppCompatResources.getDrawable(
                                                requireContext(),
                                                R_common.drawable.ic_podcast_pause_circle
                                            )
                                        )
                                    }
                                    videoViewCameraVideoPreview.setOnCompletionListener {
                                        imageViewVideoPreviewPlayPause.setImageDrawable(
                                            AppCompatResources.getDrawable(
                                                requireContext(),
                                                R_common.drawable.ic_podcast_play_circle
                                            )
                                        )
                                    }
                                    imageViewVideoPreviewPlayPause.setOnClickListener {
                                        if (videoViewCameraVideoPreview.isPlaying) {
                                            videoViewCameraVideoPreview.pause()
                                            imageViewVideoPreviewPlayPause.setImageDrawable(
                                                AppCompatResources.getDrawable(
                                                    requireContext(),
                                                    R_common.drawable.ic_podcast_play_circle
                                                )
                                            )
                                        } else {
                                            videoViewCameraVideoPreview.start()
                                            imageViewVideoPreviewPlayPause.setImageDrawable(
                                                AppCompatResources.getDrawable(
                                                    requireContext(),
                                                    R_common.drawable.ic_podcast_pause_circle
                                                )
                                            )
                                        }
                                    }
                                    videoViewCameraVideoPreview.setOnErrorListener { _, _, _ ->
                                        return@setOnErrorListener true
                                    }
                                } else {
                                    viewModel.updateMediaPreviewViewState(CapturePreviewViewState.None)
                                }
                            }
                            is CapturePreviewViewState.Preview.ImagePreview -> {
                                if (viewState.media.exists()) {
                                    root.visible
                                    imageViewCameraImagePreview.visible
                                    videoViewCameraVideoPreview.gone
                                    imageViewVideoPreviewPlayPause.gone

                                    textViewCameraMediaPreviewUse.text = getString(R.string.camera_use_photo)

                                    loadImageWithCorrectOrientation(imageViewCameraImagePreview, viewState.media)
                                } else {
                                    viewModel.updateMediaPreviewViewState(CapturePreviewViewState.None)
                                }
                            }
                            is CapturePreviewViewState.None -> {
                                root.gone

                                imageViewCameraImagePreview.setImageDrawable(null)

                                videoViewCameraVideoPreview.stopPlayback()
                                videoViewCameraVideoPreview.setVideoURI(null)
                            }
                        }
                    }

                }
            }
        }
    }

    private fun loadImageWithCorrectOrientation(imageView: ImageView, imageFile: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

                val exif = ExifInterface(imageFile.absolutePath)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                val rotatedBitmap = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }

                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(rotatedBitmap)
                }

                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                }

            } catch (e: Exception) {
                Log.e("IMAGE_ROTATION", "Failed to load image with rotation: ${e.message}")
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    companion object {
        private const val IMAGE_EXTENSION = "jpg"
        private const val VIDEO_EXTENSION = "mp4"
    }
}