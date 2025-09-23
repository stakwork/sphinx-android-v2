package chat.sphinx.chat_common.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavArgs
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import by.kirich1409.viewbindingdelegate.viewBinding
import chat.sphinx.chat_common.R
import chat.sphinx.resources.R as R_common
import chat.sphinx.chat_common.databinding.*
import chat.sphinx.chat_common.model.NodeDescriptor
import chat.sphinx.chat_common.model.TribeLink
import chat.sphinx.chat_common.ui.ChatViewModel
import chat.sphinx.chat_common.ui.isMessageSelected
import chat.sphinx.chat_common.ui.viewstate.audio.AudioMessageState
import chat.sphinx.chat_common.ui.viewstate.audio.AudioPlayState
import chat.sphinx.chat_common.ui.viewstate.messageholder.*
import chat.sphinx.chat_common.ui.viewstate.selected.SelectedMessageViewState
import chat.sphinx.chat_common.util.*
import chat.sphinx.concept_image_loader.*
import chat.sphinx.concept_user_colors_helper.UserColorsHelper
import chat.sphinx.highlighting_tool.SphinxHighlightingTool
import chat.sphinx.highlighting_tool.SphinxLinkify
import chat.sphinx.highlighting_tool.SphinxUrlSpan
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.resources.getString
import chat.sphinx.resources.setBackgroundRandomColor
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.asFormattedString
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.message.MessageUUID
import chat.sphinx.wrapper_common.util.getInitials
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.MessageType
import chat.sphinx.wrapper_message.SenderAlias
import chat.sphinx.wrapper_view.Px
import io.matthewnelson.android_feature_screens.util.gone
import io.matthewnelson.android_feature_screens.util.goneIfFalse
import io.matthewnelson.android_feature_screens.util.visible
import io.matthewnelson.android_feature_viewmodel.util.OnStopSupervisor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

internal class MessageListAdapter<ARGS : NavArgs>(
    private val recyclerView: RecyclerView,
    private val headerBinding: LayoutChatHeaderBinding,
    private val threadHeaderBinding: LayoutThreadHeaderBinding?,
    private val headerPinBinding: LayoutChatPinedMessageHeaderBinding?,
    private val layoutManager: LinearLayoutManager,
    private val lifecycleOwner: LifecycleOwner,
    private val onStopSupervisor: OnStopSupervisor,
    private val viewModel: ChatViewModel<ARGS>,
    private val imageLoader: ImageLoader<ImageView>,
    private val userColorsHelper: UserColorsHelper
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    DefaultLifecycleObserver,
    View.OnLayoutChangeListener
{

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_THREAD_HEADER = 1
        private const val VIEW_TYPE_ONLY_TEXT_SENT_MSG = 2
        private const val VIEW_TYPE_ONLY_TEXT_RECEIVED_MSG = 3
    }

    interface OnRowLayoutListener {
        fun onRowHeightChanged()
    }

    private val onRowLayoutListener: OnRowLayoutListener = object: OnRowLayoutListener {
        override fun onRowHeightChanged() {
            val lastVisibleItemPosition = (recyclerView.layoutManager as? LinearLayoutManager)?.findLastVisibleItemPosition()
            val itemsCount = (recyclerView.layoutManager?.itemCount ?: 0)
            val isScrolledAtLastRow = lastVisibleItemPosition == (itemsCount - 1)

            if (isScrolledAtLastRow) {
                forceScrollToBottom()
            }
        }
    }

    // AsyncListDiffer setup
    private val diffCallback = object : DiffUtil.ItemCallback<MessageHolderViewState>() {

        override fun areItemsTheSame(oldItem: MessageHolderViewState, newItem: MessageHolderViewState): Boolean {
            return oldItem.message?.id == newItem.message?.id &&
                   oldItem.separatorDate == newItem.separatorDate &&
                   oldItem.messageHolderType.value == newItem.messageHolderType.value
        }

        override fun areContentsTheSame(oldItem: MessageHolderViewState, newItem: MessageHolderViewState): Boolean {
            return when {
                oldItem is MessageHolderViewState.Received && newItem is MessageHolderViewState.Received -> {
                            oldItem.background                      == newItem.background                   &&
                            oldItem.message                         == newItem.message                      &&
                            oldItem.invoiceLinesHolderViewState     == newItem.invoiceLinesHolderViewState  &&
                            oldItem.message?.thread?.size           == newItem.message?.thread?.size        &&
                            oldItem.message?.thread?.first()        == newItem.message?.thread?.first()
                }
                oldItem is MessageHolderViewState.Sent && newItem is MessageHolderViewState.Sent -> {
                            oldItem.background                      == newItem.background                    &&
                            oldItem.message                         == newItem.message                       &&
                            oldItem.invoiceLinesHolderViewState     == newItem.invoiceLinesHolderViewState   &&
                            oldItem.isPinned                        == newItem.isPinned                      &&
                            oldItem.message?.thread?.size           == newItem.message?.thread?.size         &&
                            oldItem.message?.thread?.first()        == newItem.message?.thread?.first()
                }
                oldItem is MessageHolderViewState.MessageOnlyTextHolderViewState.Received && newItem is MessageHolderViewState.MessageOnlyTextHolderViewState.Received -> {
                            oldItem.background                      == newItem.background                   &&
                            oldItem.message                         == newItem.message                      &&
                            oldItem.invoiceLinesHolderViewState     == newItem.invoiceLinesHolderViewState
                }
                oldItem is MessageHolderViewState.MessageOnlyTextHolderViewState.Sent && newItem is MessageHolderViewState.MessageOnlyTextHolderViewState.Sent -> {
                            oldItem.background                      == newItem.background                   &&
                            oldItem.message                         == newItem.message                      &&
                            oldItem.invoiceLinesHolderViewState     == newItem.invoiceLinesHolderViewState
                }
                else -> false
            }
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)

        onStopSupervisor.scope.launch {
            viewModel.messageHolderViewStateFlow
                .collect { list ->
                if (differ.currentList.isEmpty()) {
                    differ.submitList(list) {
                        scrollToUnseenSeparatorOrBottom(list)
                    }
                } else {
                    scrollToPreviousPositionWithCallback(list.size) {
                        differ.submitList(list)
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (differ.currentList.getOrNull(position)) {
            is MessageHolderViewState.ThreadHeader -> VIEW_TYPE_THREAD_HEADER
            is MessageHolderViewState.MessageOnlyTextHolderViewState.Sent -> VIEW_TYPE_ONLY_TEXT_SENT_MSG
            is MessageHolderViewState.MessageOnlyTextHolderViewState.Received -> VIEW_TYPE_ONLY_TEXT_RECEIVED_MSG
            else -> VIEW_TYPE_MESSAGE
        }
    }

    private fun scrollToUnseenSeparatorOrBottom(messageHolders: List<MessageHolderViewState>) {
        viewModel.viewModelScope.launch(viewModel.mainImmediate) {
            delay(500L)

            for ((index, message) in messageHolders.withIndex()) {
                (message as? MessageHolderViewState.Separator)?.let {
                    if (it.messageHolderType.isUnseenSeparatorHolder()) {
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, recyclerView.measuredHeight / 4)
                        return@launch
                    }
                }
            }

            if (messageHolders.isNotEmpty()) {
                recyclerView.layoutManager?.scrollToPosition(
                    messageHolders.size
                )
            }
        }
    }

    fun scrollToBottomIfNeeded(
        callback: (() -> Unit)? = null,
        replyingToMessage: Boolean = false,
        itemsDiff: Int = 0
    ) {
        val lastVisibleItemPositionBeforeDispatch = layoutManager.findLastVisibleItemPosition()
        val listSizeBeforeDispatch = differ.currentList.size

        if (callback != null) {
            callback()
        }

        val listSizeAfterDispatch = differ.currentList.size
        val lastItemPosition = differ.currentList.size - 1

        if (
            (!viewModel.isMessageSelected() || replyingToMessage)                    &&
            listSizeAfterDispatch >= listSizeBeforeDispatch                          &&
            recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE               &&
            lastVisibleItemPositionBeforeDispatch + itemsDiff >= lastItemPosition
        ) {
            recyclerView.scrollToPosition(listSizeAfterDispatch)
        }
    }

    private fun scrollToPreviousPositionWithCallback(
        newListSize: Int,
        callback: (() -> Unit)? = null,
    ) {
        val firstItemBeforeUpdate = differ.currentList.first()
        val lastVisibleItemPositionBeforeDispatch = layoutManager.findLastVisibleItemPosition()
        val listSizeBeforeDispatch = differ.currentList.size
        val diffToBottom = listSizeBeforeDispatch - lastVisibleItemPositionBeforeDispatch

        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
        val currentFirstVisible = layoutManager.findFirstVisibleItemPosition()
        val currentFirstView = layoutManager.findViewByPosition(currentFirstVisible)
        val currentOffset = currentFirstView?.top ?: 0
        val currentListSize = itemCount

        if (callback != null) {
            callback()
        }

        val firstItemAfterUpdate = differ.currentList.first()
        val isLoadingMore = !diffCallback.areItemsTheSame(firstItemBeforeUpdate, firstItemAfterUpdate)
        val newItemsAdded = newListSize - currentListSize
        val newTargetPosition = currentFirstVisible + (if (isLoadingMore) newItemsAdded else 0)

        if (diffToBottom <= 1) {
            recyclerView.post {
                recyclerView.smoothScrollToPosition(
                    newListSize - 1
                )
            }
        } else {
            if (!isLoadingMore) {
                return
            }
            recyclerView.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    newTargetPosition,
                    currentOffset
                )
            }
        }
    }

    fun forceScrollToBottom() {
        recyclerView.layoutManager?.scrollToPosition(differ.currentList.size)
    }

    fun highlightAndScrollToSearchResult(
        result: Pair<Long, String>,
        previousResult: Pair<Long, String>?,
        searchTerm: String
    ) {
        var previousMessageUpdated = (previousResult == null)
        var indexToScroll: Int? = null

        for ((index, messageHolderVS) in differ.currentList.withIndex()) {
            if (messageHolderVS.message?.id?.value == previousResult?.first && !previousMessageUpdated) {

                (messageHolderVS as? MessageHolderViewState.Sent)?.let {
                    it.highlightedText = null
                } ?: (messageHolderVS as? MessageHolderViewState.Received)?.let {
                    it.highlightedText = null
                } ?: (messageHolderVS as? MessageHolderViewState.MessageOnlyTextHolderViewState.Sent)?.let {
                    it.highlightedText = null
                } ?: (messageHolderVS as? MessageHolderViewState.MessageOnlyTextHolderViewState.Received)?.let {
                    it.highlightedText = null
                }

                notifyItemChanged(index)

                previousMessageUpdated = true
            }

            if (messageHolderVS.message?.id?.value == result.first && indexToScroll == null) {
                (messageHolderVS as? MessageHolderViewState.Sent)?.let {
                    it.highlightedText = searchTerm
                } ?: (messageHolderVS as? MessageHolderViewState.Received)?.let {
                    it.highlightedText = searchTerm
                } ?: (messageHolderVS as? MessageHolderViewState.MessageOnlyTextHolderViewState.Sent)?.let {
                    it.highlightedText = searchTerm
                } ?: (messageHolderVS as? MessageHolderViewState.MessageOnlyTextHolderViewState.Received)?.let {
                    it.highlightedText = searchTerm
                }

                notifyItemChanged(index)

                indexToScroll = index
            }

            if (previousMessageUpdated) {
                indexToScroll?.let {
                    recyclerView.scrollToPosition(it)
                    return
                }
            }
        }
    }

    fun resetHighlighted() {
        for ((index, messageHolderVS) in differ.currentList.withIndex()) {
            if (messageHolderVS.highlightedText != null) {
                messageHolderVS.highlightedText = null
                notifyItemChanged(index)
            }
        }
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if (bottom != oldBottom) {
            val lastPosition = differ.currentList.size - 1
            if (
                !viewModel.isMessageSelected()                              &&
                recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE  &&
                layoutManager.findLastVisibleItemPosition() == lastPosition
            ) {
                recyclerView.scrollToPosition(lastPosition)
            }
        }
    }

    init {
        recyclerView.addOnLayoutChangeListener(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        recyclerView.removeOnLayoutChangeListener(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MESSAGE -> {
                val binding = LayoutMessageHolderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MessageViewHolder(binding)
            }
            VIEW_TYPE_ONLY_TEXT_SENT_MSG -> {
                val binding = LayoutOnlyTextSentMessageHolderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MessageOnlyTextSentViewHolder(binding)
            }
            VIEW_TYPE_ONLY_TEXT_RECEIVED_MSG -> {
                val binding = LayoutOnlyTextReceivedMessageHolderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MessageOnlyTextReceivedViewHolder(binding)
            }
            VIEW_TYPE_THREAD_HEADER -> {
                val binding = LayoutThreadMessageHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ThreadHeaderViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageListAdapter<*>.ThreadHeaderViewHolder -> {
                holder.bind(position)
            }
            is MessageListAdapter<*>.MessageOnlyTextSentViewHolder -> {
                holder.bind(position)
            }
            is MessageListAdapter<*>.MessageOnlyTextReceivedViewHolder -> {
                holder.bind(position)
            }
            is MessageListAdapter<*>.MessageViewHolder -> {
                holder.bind(position)
            }
            else -> {
                throw IllegalArgumentException("Unknown ViewHolder type: ${holder::class.java}")
            }
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    private val recyclerViewWidth: Px by lazy(LazyThreadSafetyMode.NONE) {
        Px(recyclerView.measuredWidth.toFloat())
    }

    private val headerHeight: Px by lazy(LazyThreadSafetyMode.NONE) {
        Px(headerBinding.root.measuredHeight.toFloat())
    }

    private val screenHeight: Px by lazy(LazyThreadSafetyMode.NONE) {
        Px(recyclerView.rootView.measuredHeight.toFloat())
    }

    private val pinedMessageHeader: Px
        get() {
            return headerPinBinding?.let {
                if (headerPinBinding.root.isVisible) {
                    Px(headerPinBinding.root.measuredHeight.toFloat())
                } else {
                    Px(0f)
                }
            } ?: Px(0f)
        }

    private val threadHeaderHeight: Px
        get() {
            return threadHeaderBinding?.let {
                if (threadHeaderBinding.root.isVisible) {
                    Px(threadHeaderBinding.root.measuredHeight.toFloat())
                } else {
                    Px(0f)
                }
            } ?: Px(0f)
        }

    @SuppressLint("ClickableViewAccessibility")
    inner class MessageViewHolder(
        private val binding: LayoutMessageHolderBinding
    ): RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {

        private val holderJobs: ArrayList<Job> = ArrayList(17)
        private val disposables: ArrayList<Disposable> = ArrayList(6)
        private var currentViewState: MessageHolderViewState? = null

        private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener
        init {
            binding.includeMessageHolderBubble.apply {

                val linkPreviewClickListener = View.OnClickListener {
                    currentViewState?.messageLinkPreview?.let { preview ->
                        if (preview is NodeDescriptor) {
                            viewModel.handleContactTribeLinks(preview.nodeDescriptor.value)
                        } else if (preview is TribeLink) {
                            viewModel.handleContactTribeLinks(preview.tribeJoinLink.value)
                        }
                    }
                }

                val selectedMessageLongClickListener = OnLongClickListener {
                    SelectedMessageViewState.SelectedMessage.instantiate(
                        messageHolderViewState = currentViewState,
                        holderYPosTop = Px(binding.root.y + binding.includeMessageHolderBubble.root.y),
                        holderHeight = Px(binding.root.measuredHeight.toFloat()),
                        holderWidth = Px(binding.root.measuredWidth.toFloat()),
                        bubbleXPosStart = Px(root.x),
                        bubbleWidth = Px(root.measuredWidth.toFloat()),
                        bubbleHeight = Px(root.measuredHeight.toFloat()),
                        headerHeight = headerHeight,
                        threadHeaderHeight = threadHeaderHeight,
                        recyclerViewWidth = recyclerViewWidth,
                        screenHeight = screenHeight,
                        pinedHeaderHeight = pinedMessageHeader
                    ).let { vs ->
                        viewModel.updateSelectedMessageViewState(vs)
                    }
                    true
                }

                onSphinxInteractionListener = object: SphinxUrlSpan.OnInteractionListener(
                    selectedMessageLongClickListener
                ) {
                    override fun onClick(url: String?) {
                        viewModel.handleContactTribeLinks(url)
                    }
                }

                root.setOnLongClickListener(onSphinxInteractionListener)

                SphinxLinkify.addLinks(textViewMessageText, SphinxLinkify.ALL, binding.root.context, onSphinxInteractionListener)

                textViewMessageText.setOnLongClickListener(onSphinxInteractionListener)

                includeMessageTypeBotResponse.webViewMessageTypeBotResponse.setOnLongClickListener(onSphinxInteractionListener)

                includeMessageLinkPreviewContact.apply contact@ {
                    root.setOnLongClickListener(selectedMessageLongClickListener)
                    root.setOnClickListener(linkPreviewClickListener)
                }

                includeMessageLinkPreviewTribe.apply tribe@ {
                    root.setOnLongClickListener(selectedMessageLongClickListener)
                    root.setOnClickListener(linkPreviewClickListener)
                }

                includeMessageTypeCallInvite.let { holder ->
                    holder.layoutConstraintCallInviteJoinByAudio.setOnClickListener {
                        currentViewState?.message?.let { nnMessage ->
                            joinCall(nnMessage, true)
                        }
                    }

                    holder.layoutConstraintCallInviteJoinByVideo.setOnClickListener {
                        currentViewState?.message?.let { nnMessage ->
                            joinCall(nnMessage, false)
                        }
                    }

                    holder.buttonCallInviteCopyLink.setOnClickListener {
                        currentViewState?.message?.let { nnMessage ->
                            viewModel.copyCallLink(nnMessage)
                        }
                    }
                }

                includeMessageTypeImageAttachment.apply {
                    imageViewAttachmentImage.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.showAttachmentImageFullscreen(message)
                        }
                    }
                    imageViewAttachmentImage.setOnLongClickListener(selectedMessageLongClickListener)
                }

                includeMessageTypeVideoAttachment.apply {
                    textViewAttachmentPlayButton.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.goToFullscreenVideo(message.id)
                        }
                    }
                }

                includeMessageTypeFileAttachment.apply {
                    buttonAttachmentFileDownload.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.saveFile(message, null)
                        }
                    }
                    layoutConstraintAttachmentFileMainInfoGroup.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.showAttachmentPdfFullscreen(message, 0)
                        }
                    }
                    layoutConstraintAttachmentFileMainInfoGroup.setOnLongClickListener(selectedMessageLongClickListener)
                }

                includeLayoutMessageThread.apply {
                    root.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            message.uuid?.let { nnUUID -> viewModel.navigateToChatThread(nnUUID) }
                        }
                    }
                }

                includePaidMessageReceivedDetailsHolder.apply {
                    buttonPayAttachment.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.payAttachment(message)
                        }
                    }
                    buttonPayAttachment.setOnLongClickListener(selectedMessageLongClickListener)
                }

                includeMessageTypeInvoice.apply {
                    buttonPay.setOnClickListener {
                        currentViewState?.message?.let { message ->
                            viewModel.payInvoice(message)
                        }
                    }
                }

                includeMessageTypeAudioAttachment.apply {
                    textViewAttachmentPlayPauseButton.setOnClickListener {
                        viewModel.audioPlayerController.togglePlayPause(
                            currentViewState?.bubbleAudioAttachment
                        )
                    }
                    seekBarAttachmentAudio.setOnTouchListener { _, _ -> true }
                }

                includeMessageTypePodcastClip.apply {
                    layoutConstraintPlayPauseButton.setOnClickListener {
                        viewModel.audioPlayerController.togglePlayPause(
                            currentViewState?.bubblePodcastClip
                        )
                    }
                    seekBarPodcastClip.setOnTouchListener { _, _ -> true }
                    seekBarPodcastClip.setPadding(0,0,0,0)
                }
            }

            binding.includeMessageTypeGroupActionHolder.let { holder ->
                holder.includeMessageTypeGroupActionJoinRequest.apply {
                    textViewGroupActionJoinRequestAcceptAction.setOnClickListener {
                        currentViewState?.message?.let { nnMessage ->

                            nnMessage.uuid?.let { messageUuid ->
                                processMemberRequest(
                                    nnMessage.chatId,
                                    messageUuid,
                                    MessageType.GroupAction.MemberApprove,
                                    nnMessage.senderAlias
                                )
                            }
                            if (nnMessage.type is MessageType.GroupAction.MemberRequest) { }
                        }
                    }

                    textViewGroupActionJoinRequestRejectAction.setOnClickListener {
                        currentViewState?.message?.let { nnMessage ->

                            if (nnMessage.type is MessageType.GroupAction.MemberRequest) {

                                nnMessage.uuid?.let { messageUuid ->
                                    processMemberRequest(
                                        nnMessage.chatId,
                                        messageUuid,
                                        MessageType.GroupAction.MemberReject,
                                        nnMessage.senderAlias
                                    )
                                }
                            }
                        }
                    }
                }

                holder.includeMessageTypeGroupActionMemberRemoval.apply {
                    textViewGroupActionMemberRemovalDeleteGroup.setOnClickListener {
                        deleteTribe()
                    }
                }
            }

            binding.includeMessageHolderChatImageInitialHolder.root.setOnClickListener {
                currentViewState?.message?.let { nnMessage ->
                    viewModel.onSmallProfileImageClick(nnMessage)
                }
            }
        }

        private fun processMemberRequest(chatId: ChatId, messageUuid: MessageUUID, type: MessageType.GroupAction, senderAlias: SenderAlias?) {
            holderScope.launch(viewModel.mainImmediate) {
                binding.includeMessageTypeGroupActionHolder.includeMessageTypeGroupActionJoinRequest.apply {
                    layoutConstraintGroupActionJoinRequestProgressBarContainer.visible

                    viewModel.processMemberRequest(
                        chatId,
                        messageUuid,
                        type,
                        senderAlias
                    )

                    layoutConstraintGroupActionJoinRequestProgressBarContainer.gone
                }
            }.let { job ->
                holderJobs.add(job)
            }
        }

        private fun deleteTribe() {
            holderScope.launch(viewModel.mainImmediate) {
                binding.includeMessageTypeGroupActionHolder.includeMessageTypeGroupActionMemberRemoval.apply {
                    layoutConstraintGroupActionMemberRemovalProgressBarContainer.visible

                    viewModel.deleteTribe()

                    layoutConstraintGroupActionMemberRemovalProgressBarContainer.gone
                }
            }.let { job ->
                holderJobs.add(job)
            }
        }

        private fun joinCall(message: Message, audioOnly: Boolean) {
            viewModel.joinCall(message, audioOnly)
        }

        fun bind(position: Int) {
            cleanup()

            val viewState = differ.currentList.elementAtOrNull(position).also { currentViewState = it } ?: return

            binding.setView(
                holderScope,
                holderJobs,
                disposables,
                viewModel.dispatchers,
                viewModel.audioPlayerController,
                imageLoader,
                viewModel.memeServerTokenHandler,
                recyclerViewWidth,
                viewState,
                userColorsHelper,
                viewModel.colorCache,
                onSphinxInteractionListener,
                onRowLayoutListener,
            )

            observeAudioAttachmentState()
        }

        fun cleanup() {
            holderJobs.forEach { it.cancel() }
            holderJobs.clear()

            disposables.forEach { it.dispose() }
            disposables.clear()

            audioAttachmentJob?.cancel()

            holderScope.coroutineContext.cancelChildren()
        }

        private var audioAttachmentJob: Job? = null
        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)

            audioAttachmentJob?.let { job ->
                if (!job.isActive) {
                    observeAudioAttachmentState()
                }
            }
        }

        private fun observeAudioAttachmentState() {
            currentViewState?.bubbleAudioAttachment?.let { audioAttachment ->
                if (audioAttachment is LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable) {
                    audioAttachmentJob?.cancel()
                    audioAttachmentJob = holderScope.launch(viewModel.mainImmediate) {
                        viewModel.audioPlayerController.getAudioState(audioAttachment)?.collect { audioState ->
                            binding.includeMessageHolderBubble
                                .includeMessageTypeAudioAttachment
                                .setAudioAttachmentLayoutForState(audioState)
                        }
                    }
                }
            }

            currentViewState?.bubblePodcastClip?.let { podcastClipViewState ->
                audioAttachmentJob?.cancel()
                audioAttachmentJob = holderScope.launch(viewModel.mainImmediate) {
                    viewModel.audioPlayerController.getAudioState(podcastClipViewState)?.collect { audioState ->
                        binding.includeMessageHolderBubble
                            .includeMessageTypePodcastClip
                            .setPodcastClipLayoutForState(audioState)
                    }
                }
            }
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }

    }

    inner class MessageOnlyTextSentViewHolder(
        binding: LayoutOnlyTextSentMessageHolderBinding
    ) : MessageOnlyTextCommonViewHolder(
        binding,
        LayoutOnlyTextMessageHolderBubbleBinding.bind(
            binding.root.findViewById(R.id.include_message_holder_bubble)
        )
    ), DefaultLifecycleObserver

    inner class MessageOnlyTextReceivedViewHolder(
        binding: LayoutOnlyTextReceivedMessageHolderBinding
    ) : MessageOnlyTextCommonViewHolder(
        binding,
        LayoutOnlyTextMessageHolderBubbleBinding.bind(
            binding.root.findViewById(R.id.include_message_holder_bubble)
        )
    ), DefaultLifecycleObserver

    abstract inner class MessageOnlyTextCommonViewHolder(
        val binding: ViewBinding,
        private val bubbleHolder: LayoutOnlyTextMessageHolderBubbleBinding
    ) : RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {

        private val holderJobs: ArrayList<Job> = ArrayList(2)
        private val disposables: ArrayList<Disposable> = ArrayList(1)
        private var currentViewState: MessageHolderViewState? = null
        private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener

        init {
            bubbleHolder.apply {
                val selectedMessageLongClickListener = OnLongClickListener {
                    SelectedMessageViewState.SelectedMessage.instantiate(
                        messageHolderViewState = currentViewState,
                        holderYPosTop = Px(binding.root.y + bubbleHolder.root.y),
                        holderHeight = Px(binding.root.measuredHeight.toFloat()),
                        holderWidth = Px(binding.root.measuredWidth.toFloat()),
                        bubbleXPosStart = Px(root.x),
                        bubbleWidth = Px(root.measuredWidth.toFloat()),
                        bubbleHeight = Px(root.measuredHeight.toFloat()),
                        headerHeight = headerHeight,
                        threadHeaderHeight = threadHeaderHeight,
                        recyclerViewWidth = recyclerViewWidth,
                        screenHeight = screenHeight,
                        pinedHeaderHeight = pinedMessageHeader
                    ).let { vs ->
                        viewModel.updateSelectedMessageViewState(vs)
                    }
                    true
                }

                onSphinxInteractionListener = object: SphinxUrlSpan.OnInteractionListener(
                    selectedMessageLongClickListener
                ) {
                    override fun onClick(url: String?) {
                        ///Do nothing
                    }
                }

                root.setOnLongClickListener(onSphinxInteractionListener)

                SphinxLinkify.addLinks(textViewMessageText, SphinxLinkify.ALL, binding.root.context, onSphinxInteractionListener)

                textViewMessageText.setOnLongClickListener(onSphinxInteractionListener)
            }
        }

        fun bind(position: Int) {
            cleanup()

            val viewState = differ.currentList.elementAtOrNull(position).also { currentViewState = it } ?: return

            (binding as? LayoutOnlyTextReceivedMessageHolderBinding)?.let {
                binding.setView(
                    holderScope,
                    holderJobs,
                    disposables,
                    viewModel.dispatchers,
                    imageLoader,
                    recyclerViewWidth,
                    viewState,
                    userColorsHelper,
                    viewModel.colorCache,
                    onSphinxInteractionListener,
                )
            } ?: (binding as? LayoutOnlyTextSentMessageHolderBinding)?.let {
                binding.setView(
                    holderScope,
                    holderJobs,
                    viewModel.dispatchers,
                    recyclerViewWidth,
                    viewState,
                    userColorsHelper,
                    viewModel.colorCache,
                    onSphinxInteractionListener
                )
            }
        }

        fun cleanup() {
            holderJobs.forEach { it.cancel() }
            holderJobs.clear()

            disposables.forEach { it.dispose() }
            disposables.clear()

            holderScope.coroutineContext.cancelChildren()
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ThreadHeaderViewHolder(
        private val binding: LayoutThreadMessageHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root), DefaultLifecycleObserver {
        private var threadHeaderViewState: MessageHolderViewState.ThreadHeader? = null

        private val holderJobs: ArrayList<Job> = ArrayList(17)
        private val disposables: ArrayList<Disposable> = ArrayList(6)
        private val holderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private var audioAttachmentJob: Job? = null

        private val onSphinxInteractionListener: SphinxUrlSpan.OnInteractionListener

        override fun onStart(owner: LifecycleOwner) {
            super.onStart(owner)

            audioAttachmentJob?.let { job ->
                if (!job.isActive) {
                    observeAudioAttachmentState()
                }
            }
        }

        init {
            binding.apply {

                constraintShowMoreContainer.setOnClickListener {
                    viewModel.toggleThreadDescriptionExpanded()
                }

                includeMessageTypeImageAttachment.imageViewAttachmentImage.setOnClickListener {
                    threadHeaderViewState?.message?.let { message ->
                        viewModel.showAttachmentImageFullscreen(message)
                    }
                }

                includeMessageTypeVideoAttachment.apply {
                    textViewAttachmentPlayButton.setOnClickListener {
                        threadHeaderViewState?.message?.let { message ->
                            viewModel.goToFullscreenVideo(message.id)
                        }
                    }
                }
                includeMessageTypeFileAttachment.apply {
                    buttonAttachmentFileDownload.setOnClickListener {
                        threadHeaderViewState?.message?.let { message ->
                            viewModel.saveFile(message, null)
                        }
                    }
                    layoutConstraintAttachmentFileMainInfoGroup.setOnClickListener {
                        threadHeaderViewState?.message?.let { message ->
                            viewModel.showAttachmentPdfFullscreen(message, 0)
                        }
                    }
                }

                includeMessageTypeAudioAttachment.apply {
                    textViewAttachmentPlayPauseButton.setOnClickListener {
                        threadHeaderViewState?.bubbleAudioAttachment?.let { bubbleAudioAttachment ->
                            viewModel.audioPlayerController.togglePlayPause(bubbleAudioAttachment)
                        }
                    }
                    this.seekBarAttachmentAudio.setOnTouchListener { _, _ -> true }
                }

                includeMessageTypeFileAttachment.root.setBackgroundResource(R_common.drawable.background_thread_file_attachment)
            }

            onSphinxInteractionListener = object: SphinxUrlSpan.OnInteractionListener(null) {
                override fun onClick(url: String?) {
                    viewModel.handleContactTribeLinks(url)
                }
            }
        }

        private fun observeAudioAttachmentState() {
            threadHeaderViewState?.bubbleAudioAttachment?.let { audioAttachment ->
                if (audioAttachment is LayoutState.Bubble.ContainerSecond.AudioAttachment.FileAvailable) {
                    audioAttachmentJob?.cancel()
                    audioAttachmentJob = holderScope.launch(viewModel.mainImmediate) {
                        viewModel.audioPlayerController.getAudioState(audioAttachment)
                            ?.collect { audioState ->
                                binding.includeMessageTypeAudioAttachment.setAudioAttachmentLayoutForState(audioState)
                            }
                    }
                }
            }
        }

        fun bind(position: Int) {
            cleanup()

            val threadHeader = differ.currentList.getOrNull(position) as MessageHolderViewState.ThreadHeader
            threadHeaderViewState = threadHeader

            binding.setView(
                holderScope,
                holderJobs,
                disposables,
                viewModel.dispatchers,
                imageLoader,
                viewModel.memeServerTokenHandler,
                viewModel.audioPlayerController,
                threadHeader,
                userColorsHelper,
                onSphinxInteractionListener
            )
            observeAudioAttachmentState()
        }

        fun cleanup() {
            holderJobs.forEach { it.cancel() }
            holderJobs.clear()

            disposables.forEach { it.dispose() }
            disposables.clear()

            audioAttachmentJob?.cancel()

            holderScope.coroutineContext.cancelChildren()
        }

        init {
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is MessageListAdapter<*>.MessageViewHolder -> holder.cleanup()
            is MessageListAdapter<*>.ThreadHeaderViewHolder -> holder.cleanup()
            is MessageListAdapter<*>.MessageOnlyTextSentViewHolder -> holder.cleanup()
            is MessageListAdapter<*>.MessageOnlyTextReceivedViewHolder -> holder.cleanup()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)

        for (i in 0 until itemCount) {
            when (val holder = recyclerView.findViewHolderForAdapterPosition(i)) {
                is MessageListAdapter<*>.MessageViewHolder -> holder.cleanup()
                is MessageListAdapter<*>.ThreadHeaderViewHolder -> holder.cleanup()
                is MessageListAdapter<*>.MessageOnlyTextSentViewHolder -> holder.cleanup()
                is MessageListAdapter<*>.MessageOnlyTextReceivedViewHolder -> holder.cleanup()
            }
        }
    }
}