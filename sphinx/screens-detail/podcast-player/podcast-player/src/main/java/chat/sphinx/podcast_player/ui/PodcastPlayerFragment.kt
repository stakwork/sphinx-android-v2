package chat.sphinx.podcast_player.ui

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import app.cash.exhaustive.Exhaustive
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.concept_connectivity_helper.ConnectivityHelper
import chat.sphinx.concept_image_loader.ImageLoader
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_image_loader.Transformation
import chat.sphinx.insetter_activity.InsetterActivity
import chat.sphinx.insetter_activity.addNavigationBarPadding
import chat.sphinx.podcast_player.R
import chat.sphinx.podcast_player.databinding.FragmentPodcastPlayerBinding
import chat.sphinx.podcast_player.ui.adapter.PodcastEpisodesFooterAdapter
import chat.sphinx.podcast_player.ui.adapter.PodcastEpisodesListAdapter
import chat.sphinx.podcast_player.ui.viewstates.BoostAnimationViewState
import chat.sphinx.podcast_player.ui.viewstates.FeedItemDetailsViewState
import chat.sphinx.podcast_player.ui.viewstates.PodcastPlayerViewState
import chat.sphinx.resources.inputMethodManager
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.feed.isTrue
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.asFormattedString
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.util.getHHMMSSString
import chat.sphinx.wrapper_podcast.Podcast
import chat.sphinx.wrapper_podcast.PodcastEpisode
import chat.sphinx.resources.R as R_common
import dagger.hilt.android.AndroidEntryPoint
import io.matthewnelson.android_feature_screens.ui.sideeffect.SideEffectFragment
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.invisible
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.concept_views.viewstate.collect
import io.matthewnelson.concept_views.viewstate.value
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@AndroidEntryPoint
internal class PodcastPlayerFragment : SideEffectFragment<
        FragmentActivity,
        PodcastPlayerSideEffect,
        PodcastPlayerViewState,
        PodcastPlayerViewModel,
        FragmentPodcastPlayerBinding
        >(R.layout.fragment_podcast_player) {

    override val viewModel: PodcastPlayerViewModel by viewModels()
    override val binding: FragmentPodcastPlayerBinding by viewBinding(FragmentPodcastPlayerBinding::bind)

    companion object {
        val SLIDER_VALUES = listOf(0,3,3,5,5,8,8,10,10,15,20,20,40,40,80,80,100)
    }

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var imageLoader: ImageLoader<ImageView>

    @Inject
    @Suppress("ProtectedInFinal")
    protected lateinit var connectivityHelper: ConnectivityHelper

    private val args: PodcastPlayerFragmentArgs by navArgs()
    private var isSkipAdEnabled: Boolean = false

    override fun onDestroyView() {
        super.onDestroyView()

        viewModel.trackPodcastConsumed()
        viewModel.forceFeedReload()
        lastChaptersEpisodeId = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        BackPressHandler(viewLifecycleOwner, requireActivity())

        binding.apply {
            textViewDismissButton.setOnClickListener {
                lifecycleScope.launch(viewModel.mainImmediate) {
                    viewModel.navigator.closeDetailScreen()
                }
            }

            root.post {
                val fragmentHeight = root.measuredHeight

                includeLayoutPodcastEpisodesList.layoutConstraintPodcastEpisodesList.apply {
                    kotlin.run {
                        layoutParams.height = (fragmentHeight.toDouble() * 0.975).toInt()
                        requestLayout()
                    }
                }
            }
        }

        setupFeedItemDetails()
        setupBoost()
        setupEpisodes()
        setupFragmentLayout()

        viewModel.forceFeedReload()
    }

    private fun setupFragmentLayout() {
        (requireActivity() as InsetterActivity)
            .addNavigationBarPadding(binding.includeLayoutFeedItem.includeLayoutFeedItemDetails.constraintLayoutFeedItem)
    }

    private inner class BackPressHandler(
        owner: LifecycleOwner,
        activity: FragmentActivity,
    ): OnBackPressedCallback(true) {

        init {
            activity.apply {
                onBackPressedDispatcher.addCallback(
                    owner,
                    this@BackPressHandler,
                )
            }
        }

        override fun handleOnBackPressed() {
            if (viewModel.feedItemDetailsViewStateContainer.value is FeedItemDetailsViewState.Open) {
                viewModel.feedItemDetailsViewStateContainer.updateViewState(FeedItemDetailsViewState.Closed)
            } else {
                lifecycleScope.launch(viewModel.mainImmediate) {
                    viewModel.navigator.closeDetailScreen()
                }
            }
        }
    }

    private fun setupFeedItemDetails() {
        binding.includeLayoutFeedItem.includeLayoutFeedItemDetails.apply {
            textViewClose.setOnClickListener {
                viewModel.feedItemDetailsViewStateContainer.updateViewState(
                    FeedItemDetailsViewState.Closed
                )
            }
            layoutConstraintDownloadRow.setOnClickListener {
                (viewModel.feedItemDetailsViewStateContainer.value as? FeedItemDetailsViewState.Open)?.let { viewState ->
                    viewState.feedItemDetail?.feedId?.let { feedId ->
                        if (viewState.feedItemDetail.downloaded == true) {
                            viewModel.deleteDownloadedMediaByItemId(feedId)
                        } else {
                            viewModel.downloadMediaByItemId(feedId)
                        }
                    }
                }
            }
            layoutConstraintCheckMarkRow.setOnClickListener {
                viewModel.updatePlayedMark()
            }
            layoutConstraintCopyLinkRow.setOnClickListener {
                (viewModel.feedItemDetailsViewStateContainer.value as? FeedItemDetailsViewState.Open)?.let { viewState ->
                    viewState.feedItemDetail?.feedId?.let { feedId ->
                        viewModel.copyCodeToClipboard(feedId)
                    }
                }
            }
            layoutConstraintShareRow.setOnClickListener {
                (viewModel.feedItemDetailsViewStateContainer.value as? FeedItemDetailsViewState.Open)?.let { viewState ->
                    viewState.feedItemDetail?.feedId?.let { feedId ->
                        viewModel.share(feedId, binding.root.context)
                    }
                }
            }
        }
    }

    private fun setupBoost() {
        binding.apply {
            includeLayoutBoostFireworks.apply {
                lottieAnimationView.addAnimatorListener(object : Animator.AnimatorListener{
                    override fun onAnimationEnd(animation: Animator) {
                        root.gone
                    }

                    override fun onAnimationRepeat(animation: Animator) {}

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationStart(animation: Animator) {}
                })
            }

            includeLayoutEpisodePlaybackControls.includeLayoutCustomBoost.apply {
                removeFocusOnEnter(editTextCustomBoost)
            }
        }
    }

    private fun removeFocusOnEnter(editText: EditText?) {
        editText?.setOnEditorActionListener(object:
            TextView.OnEditorActionListener {
            override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
                if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    editText.let { nnEditText ->
                        binding.root.context.inputMethodManager?.let { imm ->
                            if (imm.isActive(nnEditText)) {
                                imm.hideSoftInputFromWindow(nnEditText.windowToken, 0)
                                nnEditText.clearFocus()
                            }
                        }
                    }
                    return true
                }
                return false
            }
        })
    }

    private fun setupEpisodes() {
        binding.includeLayoutPodcastEpisodesList.recyclerViewEpisodesList.apply {
            val linearLayoutManager = LinearLayoutManager(context)
            val chatListAdapter = PodcastEpisodesListAdapter(
                this,
                linearLayoutManager,
                imageLoader,
                connectivityHelper,
                viewLifecycleOwner,
                onStopSupervisor,
                viewModel
            )

            val episodesListFooterAdapter = PodcastEpisodesFooterAdapter(requireActivity() as InsetterActivity)
            this.setHasFixedSize(false)
            layoutManager = linearLayoutManager
            adapter = ConcatAdapter(chatListAdapter, episodesListFooterAdapter)
            itemAnimator = null
        }
    }

    private var draggingTimeSlider: Boolean = false
    private var draggingSatsSlider: Boolean = false
    private fun addPodcastOnClickListeners(podcast: Podcast) {
        binding.apply {
            includeLayoutEpisodeSliderControl.apply {
                seekBarCurrentEpisodeProgress.setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {

                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (fromUser) {
                                val duration = podcast.getCurrentEpisodeDuration(viewModel::retrieveEpisodeDuration)
                                setTimeLabelsAndProgressBarTo(duration, null, progress)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                            draggingTimeSlider = true
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                            onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                                seekTo(podcast, seekBar?.progress ?: 0)
                            }
                            draggingTimeSlider = false
                        }
                    }
                )
            }

            includeLayoutEpisodePlaybackControls.apply {
                textViewPlaybackSpeedButton.setOnClickListener {
                    showSpeedPopup()
                }

                textViewShareClipButton.setOnClickListener {
                    viewModel.shouldShareClip()
                }

                textViewReplay15Button.setOnClickListener {
                    viewModel.seekTo(podcast.timeMilliSeconds - 15000L)
                    updateViewAfterSeek(podcast)
                }

                textViewPlayPauseButton.setOnClickListener {
                    if (!podcast.getCurrentEpisode().playing) {
                        toggleLoadingWheel(true)
                    }

                    viewModel.togglePlayState()
                }

                textViewForward30Button.setOnClickListener {
                    viewModel.seekTo(podcast.timeMilliSeconds + 30000L)
                    updateViewAfterSeek(podcast)
                }

                includeLayoutCustomBoost.apply customBoost@ {
                    this@customBoost.imageViewFeedBoostButton.setOnClickListener {
                        val amount = editTextCustomBoost.text.toString()
                            .replace(" ", "")
                            .toLongOrNull()?.toSat() ?: Sat(0)

                        viewModel.sendPodcastBoost(
                            amount,
                            fireworksCallback = {
                                onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                                    setupBoostAnimation(null, amount)

                                    includeLayoutBoostFireworks.apply fireworks@ {
                                        this@fireworks.root.visible
                                        this@fireworks.lottieAnimationView.playAnimation()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            textViewSubscribeButton.setOnClickListener {
                viewModel.toggleSubscribeState()
            }

            seekBarSatsPerMinute.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        SLIDER_VALUES[progress].let {
                            textViewPodcastSatsPerMinuteValue.text = it.toString()
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        draggingSatsSlider = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        draggingSatsSlider = false

                        seekBar?.let {
                            SLIDER_VALUES[seekBar.progress].let {
                                viewModel.updateSatsPerMinute(it.toLong())
                            }
                        }
                    }
                }
            )

        }
    }

    override suspend fun onViewStateFlowCollect(viewState: PodcastPlayerViewState) {
        @Exhaustive
        when (viewState) {
            is PodcastPlayerViewState.Idle -> {}

            is PodcastPlayerViewState.ServiceLoading -> {
                toggleLoadingWheel(true)
            }
            is PodcastPlayerViewState.ServiceInactive -> {
                togglePlayPauseButton(false)
            }

            is PodcastPlayerViewState.PodcastLoaded -> {
                showPodcastInfo(viewState.podcast)
            }

            is PodcastPlayerViewState.LoadingEpisode -> {
                loadingEpisode(viewState.episode)
            }

            is PodcastPlayerViewState.EpisodePlayed -> {
                showPodcastInfo(viewState.podcast)
            }

            is PodcastPlayerViewState.MediaStateUpdate -> {
                toggleLoadingWheel(false)
                showPodcastInfo(viewState.podcast)
            }
        }
    }
    private var lastChaptersEpisodeId: String? = null

    @SuppressLint("ClickableViewAccessibility")
    private suspend fun showPodcastInfo(podcast: Podcast) {
        binding.apply {


            val notLinkedToChat = podcast.chatId.value == ChatId.NULL_CHAT_ID.toLong()
            textViewSubscribeButton.goneIfFalse(notLinkedToChat)

            textViewSubscribeButton.text = if (podcast.subscribed.isTrue()) {
                getString(R_common.string.unsubscribe)
            } else {
                getString(R_common.string.subscribe)
            }

            var currentEpisode: PodcastEpisode? = podcast.getCurrentEpisode()
            currentEpisode =
                if (connectivityHelper.isNetworkConnected() || currentEpisode?.downloaded == true) {
                    currentEpisode
                } else {
                    podcast.getLastDownloadedEpisode()
                }

            val hasChapters = currentEpisode?.chapters?.nodes?.isNotEmpty() == true

            if (hasChapters) {
                binding.includeLayoutPodcastEpisodesList.buttonSkipAdd.visible
                updateSkipAdButtonUI()

                binding.includeLayoutPodcastEpisodesList.buttonSkipAdd.setOnClickListener {
                    isSkipAdEnabled = !isSkipAdEnabled
                    updateSkipAdButtonUI()
                }
            } else {
                binding.includeLayoutPodcastEpisodesList.buttonSkipAdd.gone
            }

            val episodeId = currentEpisode?.id?.value
            if (episodeId != null && lastChaptersEpisodeId != episodeId) {
                addChapterMarkers(podcast)
                lastChaptersEpisodeId = episodeId
            }
            textViewEpisodeTitleLabel.text = currentEpisode?.title?.value ?: ""

            podcast.imageToShow?.value?.let { podcastImage ->
                imageLoader.load(
                    imageViewPodcastImage,
                    podcastImage,
                    ImageLoaderOptions.Builder()
                        .placeholderResId(R_common.drawable.ic_podcast_placeholder)
                        .build()
                )
            }

            includeLayoutPodcastEpisodesList.textViewEpisodesListCount.text = podcast.episodesCount.toString()

            includeLayoutEpisodePlaybackControls.apply {

                textViewShareClipButton.apply {
                    val shareClipEnabled = podcast.hasDestinations && !args.fromFeed
                    alpha = if (shareClipEnabled) 1.0F else 0.3F
                    isEnabled = shareClipEnabled
                }

                textViewPlaybackSpeedButton.text = "${podcast.getSpeedString()}"

                includeLayoutCustomBoost.apply customBoost@ {
                    this@customBoost.layoutConstraintBoostButtonContainer.alpha = if (podcast.hasDestinations) 1.0f else 0.3f
                    this@customBoost.imageViewFeedBoostButton.isEnabled = podcast.hasDestinations
                    this@customBoost.editTextCustomBoost.isEnabled = podcast.hasDestinations
                }
            }

            if (!draggingSatsSlider) {
                val satsPerMinute = podcast.satsPerMinute
                val closest = SLIDER_VALUES.closestValue(satsPerMinute.toInt())
                val index = SLIDER_VALUES.indexOf(closest)

                seekBarSatsPerMinute.max = SLIDER_VALUES.size - 1
                seekBarSatsPerMinute.progress = index
                textViewPodcastSatsPerMinuteValue.text = closest.toString()

                constraintLayoutPodcastLightningControls.alpha = if (podcast.hasDestinations) 1.0F else 0.5F

                if (!podcast.hasDestinations) {
                    seekBarSatsPerMinute.setOnTouchListener { _, _ -> true }
                } else {
                    seekBarSatsPerMinute.setOnTouchListener(null)
                }
            }

            togglePlayPauseButton(podcast.isPlaying)

            if (!draggingTimeSlider && currentEpisode != null) setTimeLabelsAndProgressBar(podcast)

            if (isSkipAdEnabled && hasChapters) {
                checkAndSkipAds(podcast)
            }

            toggleLoadingWheel(false)
            addPodcastOnClickListeners(podcast)
        }
    }

    private fun addChapterMarkers(podcast: Podcast) {
        val slider = binding.includeLayoutEpisodeSliderControl.seekBarCurrentEpisodeProgress
        val overlay = binding.includeLayoutEpisodeSliderControl.frameLayoutSeekbarOverlay
        overlay.removeAllViews()

        val duration = podcast.getCurrentEpisodeDuration(viewModel::retrieveEpisodeDuration)

        val chapters = podcast.getCurrentEpisode()?.chapters?.nodes?.mapNotNull { it.properties }
            ?.filter { !it.timestamp.isNullOrBlank() }

        if (chapters.isNullOrEmpty() || duration <= 0) return

        slider.post {
            val sliderWidth = slider.width
            val leftPadding = slider.paddingLeft
            val rightPadding = slider.paddingRight
            val trackWidth = sliderWidth - leftPadding - rightPadding

            val density = resources.displayMetrics.density
            val markerSizeDp = 12
            val markerSizePx = (markerSizeDp * density).toInt()
            val halfMarker = markerSizePx / 2

            chapters.forEach { chapter ->
                val timeMs = viewModel.parseTimestampToMillis(chapter.timestamp ?: "00:00:00")
                val positionRatio = timeMs.toFloat() / duration.toFloat()

                val markerX = leftPadding + (trackWidth * positionRatio).toInt()
                val clampedMarkerX = markerX.coerceIn(leftPadding + halfMarker, sliderWidth - rightPadding - halfMarker)

                val isAd = chapter.isAdBoolean

                val marker = View(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(markerSizePx, markerSizePx).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        marginStart = clampedMarkerX - halfMarker
                    }
                    background = ContextCompat.getDrawable(
                        requireContext(),
                        if (isAd) R_common.drawable.podcast_player_seek_bar_ad else R_common.drawable.podcast_player_seek_bar_thumb
                    )
                    isClickable = true
                    setOnClickListener {
                        viewModel.seekTo(timeMs)
                    }
                    translationZ = 5f
                }

                overlay.addView(marker)
            }
        }
    }

    private var adSkipToastShown = false

    private fun checkAndSkipAds(podcast: Podcast) {
        val currentEpisode = podcast.getCurrentEpisode() ?: return

        val chaptersProperties = currentEpisode.chapters?.nodes
            ?.mapNotNull { it.properties }
            ?.filter { !it.timestamp.isNullOrBlank() }
            ?.sortedBy { viewModel.parseTimestampToMillis(it.timestamp) }
            ?: return

        val currentTime = podcast.timeMilliSeconds
        val episodeDuration = podcast.getCurrentEpisodeDuration(viewModel::retrieveEpisodeDuration)

        for (i in chaptersProperties.indices) {
            val chapterStart = viewModel.parseTimestampToMillis(chaptersProperties[i].timestamp)
            val chapterEnd = if (i + 1 < chaptersProperties.size) {
                viewModel.parseTimestampToMillis(chaptersProperties[i + 1].timestamp)
            } else {
                episodeDuration
            }

            if (!chaptersProperties[i].isAdBoolean && i + 1 < chaptersProperties.size) {
                val nextChapter = chaptersProperties[i + 1]
                val nextStart = viewModel.parseTimestampToMillis(nextChapter.timestamp)
                val timeUntilNext = nextStart - currentTime

                if (nextChapter.isAdBoolean && timeUntilNext in 2500..3500 && !adSkipToastShown) {
                    adSkipToastShown = true
                    viewModel.showSkipAdToast()
                }
            }

            if (chaptersProperties[i].isAdBoolean && currentTime in chapterStart until chapterEnd) {
                var targetIndex = i + 1
                while (targetIndex < chaptersProperties.size && chaptersProperties[targetIndex].isAdBoolean) {
                    targetIndex++
                }

                val skipToTime = if (targetIndex < chaptersProperties.size) {
                    viewModel.parseTimestampToMillis(chaptersProperties[targetIndex].timestamp)
                } else {
                    episodeDuration
                }

                adSkipToastShown = false
                viewModel.seekTo(skipToTime)
                break
            }
        }
    }

    private fun updateSkipAdButtonUI() {
        binding.includeLayoutPodcastEpisodesList.buttonSkipAdd.apply {
            if (isSkipAdEnabled) {
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), chat.sphinx.resources.R.color.primaryGreen)
                text = getString(R.string.podcast_skip_ad_enabled)
            } else {
                setTextColor(ContextCompat.getColor(requireContext(), chat.sphinx.resources.R.color.secondaryText))
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.secondary_text_dark)
                text = getString(R.string.podcast_skip_ad_disabled)
            }
        }
    }

    private suspend fun setupBoostAnimation(
        photoUrl: PhotoUrl?,
        amount: Sat?
    ) {
        binding.apply {
            includeLayoutEpisodePlaybackControls.includeLayoutCustomBoost.apply {
                editTextCustomBoost.setText(
                    (amount ?: Sat(100)).asFormattedString()
                )
            }

            includeLayoutBoostFireworks.apply {

                photoUrl?.let { photoUrl ->
                    imageLoader.load(
                        imageViewProfilePicture,
                        photoUrl.value,
                        ImageLoaderOptions.Builder()
                            .placeholderResId(R_common.drawable.ic_podcast_placeholder)
                            .transformation(Transformation.CircleCrop)
                            .build()
                    )
                }

                textViewSatsAmount.text = amount?.asFormattedString()
            }
        }
    }

    private fun toggleLoadingWheel(show: Boolean) {
        binding.apply {
            includeLayoutEpisodeSliderControl.apply layoutEpisodesSlider@ {
                this@layoutEpisodesSlider.progressBarAudioLoading.goneIfFalse(show)
            }
            includeLayoutEpisodePlaybackControls.apply layoutPlaybackControls@ {
                this@layoutPlaybackControls.textViewPlayPauseButton.isEnabled = !show
            }
        }
    }

    private fun togglePlayPauseButton(playing: Boolean) {
        binding.apply {
            includeLayoutEpisodePlaybackControls.apply {
                textViewPlayPauseButton.background =
                    ContextCompat.getDrawable(root.context,
                        if (playing) R_common.drawable.ic_podcast_pause_circle else R_common.drawable.ic_podcast_play_circle
                    )
            }
        }
    }

    private suspend fun loadingEpisode(episode: PodcastEpisode) {
        lastChaptersEpisodeId = null
        binding.apply {
            textViewEpisodeTitleLabel.text = episode.title.value

            episode.image?.value?.let { podcastImage ->
                imageLoader.load(
                    imageViewPodcastImage,
                    podcastImage,
                    ImageLoaderOptions.Builder()
                        .placeholderResId(R_common.drawable.ic_podcast_placeholder)
                        .build()
                )
            }

            includeLayoutEpisodeSliderControl.apply {
                textViewCurrentEpisodeDuration.text = 0.toLong().getHHMMSSString()
                textViewCurrentEpisodeProgress.text = 0.toLong().getHHMMSSString()

                seekBarCurrentEpisodeProgress.progress = 0

                toggleLoadingWheel(true)
            }
        }
    }

    private suspend fun seekTo(podcast: Podcast, progress: Int) {
        val duration = withContext(viewModel.io) {
            podcast.getCurrentEpisodeDuration(viewModel::retrieveEpisodeDuration)
        }
        val seekTime = (duration * (progress.toDouble() / 100.toDouble())).toLong()
        viewModel.seekTo(seekTime)
    }

    private fun updateViewAfterSeek(podcast: Podcast) {
        lifecycleScope.launch(viewModel.mainImmediate) {
            setTimeLabelsAndProgressBar(podcast)
        }
    }

    private suspend fun setTimeLabelsAndProgressBar(podcast: Podcast) {
        val currentTime = podcast.timeMilliSeconds

        toggleLoadingWheel(podcast.shouldLoadDuration)

        val duration = withContext(viewModel.io) {
            podcast.getCurrentEpisodeDuration(viewModel::retrieveEpisodeDuration)
        }

        val progress: Int =
            try {
                ((currentTime * 100) / duration).toInt()
            } catch (e: ArithmeticException) {
                0
            }

        setTimeLabelsAndProgressBarTo(duration, currentTime, progress)
    }

    private fun setTimeLabelsAndProgressBarTo(duration: Long, currentTime: Long? = null, progress: Int) {
        binding.includeLayoutEpisodeSliderControl.apply {
            val currentT: Double = currentTime?.toDouble() ?: (duration.toDouble() * (progress.toDouble()) / 100)

            textViewCurrentEpisodeDuration.text = duration.getHHMMSSString()
            textViewCurrentEpisodeProgress.text = currentT.toLong().getHHMMSSString()

            seekBarCurrentEpisodeProgress.progress = progress
        }
    }

    private fun showSpeedPopup() {
        binding.includeLayoutEpisodePlaybackControls.apply {
            val wrapper: Context = ContextThemeWrapper(context, R_common.style.speedMenu)
            val popup = PopupMenu(wrapper, textViewPlaybackSpeedButton)
            popup.inflate(R_common.menu.speed_menu)

            popup.setOnMenuItemClickListener { item: MenuItem? ->
                when (item!!.itemId) {
                    R_common.id.speed0_5 -> {
                        textViewPlaybackSpeedButton.text = "0.5x"
                        viewModel.adjustSpeed(0.5)
                    }
                    R_common.id.speed0_8 -> {
                        textViewPlaybackSpeedButton.text = "0.8x"
                        viewModel.adjustSpeed(0.8)
                    }
                    R_common.id.speed1 -> {
                        textViewPlaybackSpeedButton.text = "1x"
                        viewModel.adjustSpeed(1.0)
                    }
                    R_common.id.speed1_2 -> {
                        textViewPlaybackSpeedButton.text = "1.2x"
                        viewModel.adjustSpeed(1.2)
                    }
                    R_common.id.speed1_5 -> {
                        textViewPlaybackSpeedButton.text = "1.5x"
                        viewModel.adjustSpeed(1.5)
                    }
                    R_common.id.speed2_1 -> {
                        textViewPlaybackSpeedButton.text = "2.1x"
                        viewModel.adjustSpeed(2.1)
                    }
                }
                true
            }

            popup.show()
        }
    }

    override fun subscribeToViewStateFlow() {
        super.subscribeToViewStateFlow()

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.boostAnimationViewStateContainer.collect { viewState ->
                @Exhaustive
                when (viewState) {
                    is BoostAnimationViewState.Idle -> {}

                    is BoostAnimationViewState.BoosAnimationInfo -> {
                        setupBoostAnimation(
                            viewState.photoUrl,
                            viewState.amount
                        )
                    }
                }
            }
        }

        onStopSupervisor.scope.launch(viewModel.mainImmediate) {
            viewModel.feedItemDetailsViewStateContainer.collect { viewState ->

                binding.includeLayoutFeedItem.apply {
                    when (viewState) {
                        is FeedItemDetailsViewState.Open -> {
                            includeLayoutFeedItemDetails.apply {
                                feedItemDetailsCommonInfoBinding(viewState)
                                setFeedItemDetailsDownloadState(viewState)
                                setPlayedMarkState(viewState)
                            }

                        }
                        else -> {}
                    }

                    root.setTransitionDuration(300)
                    viewState.transitionToEndSet(root)
                }
            }
        }
    }

    private fun feedItemDetailsCommonInfoBinding(viewState: FeedItemDetailsViewState.Open) {
        binding.includeLayoutFeedItem.includeLayoutFeedItemDetails.apply {
            textViewMainEpisodeTitle.text = viewState.feedItemDetail?.header
            imageViewItemRowEpisodeType.setImageDrawable(ContextCompat.getDrawable(root.context, viewState.feedItemDetail?.episodeTypeImage ?: R_common.drawable.ic_podcast_type))
            textViewEpisodeType.text = viewState.feedItemDetail?.episodeTypeText
            textViewEpisodeDate.text = viewState.feedItemDetail?.episodeDate
            textViewEpisodeDuration.text = viewState.feedItemDetail?.episodeDuration
            textViewPodcastName.text = viewState.feedItemDetail?.podcastName

            onStopSupervisor.scope.launch(viewModel.mainImmediate) {
                viewState.feedItemDetail?.image?.let {
                    imageLoader.load(
                        imageViewEpisodeDetailImage,
                        it,
                        ImageLoaderOptions.Builder()
                            .placeholderResId(R_common.drawable.ic_podcast_placeholder)
                            .build()
                    )
                }
            }
        }
    }

    private fun setFeedItemDetailsDownloadState(viewState: FeedItemDetailsViewState.Open) {
        binding.includeLayoutFeedItem.includeLayoutFeedItemDetails.apply {
            if (viewState.feedItemDetail?.isDownloadInProgress == true) {
                buttonDownloadArrow.gone
                imageDownloadedEpisodeArrow.gone
                progressBarEpisodeDownload.visible
                buttonStop.visible
            } else {
                if (viewState.feedItemDetail?.downloaded == true) {
                    buttonDownloadArrow.gone
                    imageDownloadedEpisodeArrow.visible
                    progressBarEpisodeDownload.gone
                    buttonStop.gone
                    textViewDownload.text = getString(R_common.string.episode_detail_erase)
                }
                else {
                    buttonDownloadArrow.visible
                    imageDownloadedEpisodeArrow.gone
                    progressBarEpisodeDownload.gone
                    buttonStop.gone
                    textViewDownload.text = getString(R_common.string.episode_detail_download)
                }
            }
        }
    }

    private fun setPlayedMarkState(viewState: FeedItemDetailsViewState.Open) {
        binding.includeLayoutFeedItem.includeLayoutFeedItemDetails.apply {
            if (viewState.feedItemDetail?.played == true) {
                buttonCheckMarkPlayed.visible
                buttonCheckMark.invisible
                textViewCheckMark.text = getString(R_common.string.episode_detail_upplayed)
            } else {
                buttonCheckMarkPlayed.invisible
                buttonCheckMark.visible
                textViewCheckMark.text = getString(R_common.string.episode_detail_played)
            }
        }
    }

    override suspend fun onSideEffectCollect(sideEffect: PodcastPlayerSideEffect) {
        sideEffect.execute(requireActivity())
    }

    private fun List<Int>.closestValue(value: Int) = minByOrNull {
        kotlin.math.abs(value - it)
    }
}
