package chat.sphinx.chat_common.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextPaint
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CallSuper
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavArgs
import app.cash.exhaustive.Exhaustive
import chat.sphinx.camera_view_model_coordinator.request.CameraRequest
import chat.sphinx.camera_view_model_coordinator.response.CameraResponse
import chat.sphinx.chat_common.BuildConfig
import chat.sphinx.chat_common.R
import chat.sphinx.chat_common.adapters.MessageListAdapter
import chat.sphinx.resources.R as R_common
import chat.sphinx.chat_common.model.*
import chat.sphinx.chat_common.navigation.ChatNavigator
import chat.sphinx.chat_common.ui.viewstate.InitialHolderViewState
import chat.sphinx.chat_common.ui.viewstate.attachment.AttachmentFullscreenViewState
import chat.sphinx.chat_common.ui.viewstate.attachment.AttachmentSendViewState
import chat.sphinx.chat_common.ui.viewstate.footer.FooterViewState
import chat.sphinx.chat_common.ui.viewstate.header.ChatHeaderViewState
import chat.sphinx.chat_common.ui.viewstate.mentions.MessageMentionsViewState
import chat.sphinx.chat_common.ui.viewstate.menu.ChatMenuViewState
import chat.sphinx.chat_common.ui.viewstate.messageholder.*
import chat.sphinx.chat_common.ui.viewstate.messagereply.MessageReplyViewState
import chat.sphinx.chat_common.ui.viewstate.scrolldown.ScrollDownViewState
import chat.sphinx.chat_common.ui.viewstate.search.MessagesSearchViewState
import chat.sphinx.chat_common.ui.viewstate.selected.SelectedMessageViewState
import chat.sphinx.chat_common.ui.viewstate.shimmer.ShimmerViewState
import chat.sphinx.chat_common.ui.viewstate.thread.ThreadHeaderViewState
import chat.sphinx.chat_common.util.AudioPlayerController
import chat.sphinx.chat_common.util.AudioPlayerControllerImpl
import chat.sphinx.chat_common.util.AudioRecorderController
import chat.sphinx.highlighting_tool.SphinxLinkify
import chat.sphinx.highlighting_tool.highlightedTexts
import chat.sphinx.concept_image_loader.ImageLoaderOptions
import chat.sphinx.concept_link_preview.LinkPreviewHandler
import chat.sphinx.concept_link_preview.model.TribePreviewName
import chat.sphinx.concept_link_preview.model.toPreviewImageUrlOrNull
import chat.sphinx.concept_meme_input_stream.MemeInputStreamHandler
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_people.NetworkQueryPeople
import chat.sphinx.concept_repository_actions.ActionsRepository
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.concept_repository_feed.FeedRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_media.RepositoryMedia
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.concept_repository_message.model.SendMessage
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.highlighting_tool.boldTexts
import chat.sphinx.highlighting_tool.markDownLinkTexts
import chat.sphinx.highlighting_tool.replacingMarkdown
import chat.sphinx.kotlin_response.*
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.d
import chat.sphinx.logger.e
import chat.sphinx.menu_bottom.ui.MenuBottomViewState
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.resources.getString
import chat.sphinx.wrapper_chat.*
import chat.sphinx.wrapper_common.*
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.feed.FeedItemLink
import chat.sphinx.wrapper_common.feed.toFeedItemLink
import chat.sphinx.wrapper_common.lightning.*
import chat.sphinx.wrapper_common.message.*
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import chat.sphinx.wrapper_common.tribe.toTribeJoinLink
import chat.sphinx.wrapper_common.util.toFormattedDate
import chat.sphinx.wrapper_contact.*
import chat.sphinx.wrapper_feed.Feed
import chat.sphinx.wrapper_feed.isNewsletter
import chat.sphinx.wrapper_feed.isPodcast
import chat.sphinx.wrapper_feed.isVideo
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_message.*
import chat.sphinx.wrapper_message_media.*
import com.example.call_activity.CallActivity
import com.example.call_activity.StressTest
import com.giphy.sdk.core.models.Media
import com.giphy.sdk.ui.GPHContentType
import com.giphy.sdk.ui.GPHSettings
import com.giphy.sdk.ui.themes.GPHTheme
import com.giphy.sdk.ui.themes.GridType
import com.giphy.sdk.ui.utils.aspectRatio
import com.giphy.sdk.ui.views.GiphyDialogFragment
import com.squareup.moshi.Moshi
import io.matthewnelson.android_feature_viewmodel.MotionLayoutViewModel
import io.matthewnelson.android_feature_viewmodel.currentViewState
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_media_cache.MediaCacheHandler
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import io.matthewnelson.concept_views.viewstate.value
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import java.io.*
import java.util.concurrent.atomic.AtomicReference


@JvmSynthetic
@Suppress("NOTHING_TO_INLINE")
internal inline fun <ARGS : NavArgs> ChatViewModel<ARGS>.isMessageSelected(): Boolean =
    getSelectedMessageViewStateFlow().value is SelectedMessageViewState.SelectedMessage

abstract class ChatViewModel<ARGS : NavArgs>(
    protected val app: Application,
    dispatchers: CoroutineDispatchers,
    val memeServerTokenHandler: MemeServerTokenHandler,
    val chatNavigator: ChatNavigator,
    protected val repositoryMedia: RepositoryMedia,
    protected val feedRepository: FeedRepository,
    protected val chatRepository: ChatRepository,
    protected val contactRepository: ContactRepository,
    protected val messageRepository: MessageRepository,
    protected val actionsRepository: ActionsRepository,
    protected val lightningRepository: LightningRepository,
    protected val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    protected val networkQueryPeople: NetworkQueryPeople,
    val mediaCacheHandler: MediaCacheHandler,
    protected val savedStateHandle: SavedStateHandle,
    protected val cameraCoordinator: ViewModelCoordinator<CameraRequest, CameraResponse>,
    protected val linkPreviewHandler: LinkPreviewHandler,
    private val memeInputStreamHandler: MemeInputStreamHandler,
    private val connectManagerRepository: ConnectManagerRepository,
    val moshi: Moshi,
    protected val LOG: SphinxLogger,
    ) : MotionLayoutViewModel<
        Nothing,
        ChatSideEffectFragment,
        ChatSideEffect,
        ChatMenuViewState,
        >(dispatchers, ChatMenuViewState.Closed) {
    companion object {
        const val TAG = "ChatViewModel"
        const val CONFIG_PLACE_HOLDER = "PLACE_HOLDER"
        const val SERVER_SETTINGS_SHARED_PREFERENCES = "server_ip_settings"
        const val ENVIRONMENT_TYPE = "environment_type"
    }

    protected abstract val args: ARGS
    protected abstract val chatId: ChatId?
    protected abstract val contactId: ContactId?

    private val activeDownloadJobs = mutableSetOf<Job>()

    private val dims by lazy { MessageDimensions(app.applicationContext) }

    private var textPaint: Paint = Paint().apply {
        textSize = app.applicationContext.resources.getDimensionPixelSize(R.dimen.chat_message_text_size).toFloat()
        typeface = ResourcesCompat.getFont(app.applicationContext, chat.sphinx.resources.R.font.roboto_regular)
        isAntiAlias = true
        textScaleX = 1.0f
    }

    private var amountPaint: Paint = Paint().apply {
        textSize = app.applicationContext.resources.getDimensionPixelSize(R.dimen.chat_message_text_size).toFloat()
        typeface = ResourcesCompat.getFont(app.applicationContext, chat.sphinx.resources.R.font.roboto_bold)
        isAntiAlias = true
        textScaleX = 1.0f
    }

    private var dimensionCache = mutableMapOf<String, Pair<Int, Int>>()

    val recyclerWidth = app.applicationContext.getScreenWidth()

    val imageLoaderDefaults by lazy {
        ImageLoaderOptions.Builder()
            .placeholderResId(R_common.drawable.ic_profile_avatar_circle)
            .build()
    }

    val messagesSearchViewStateContainer: ViewStateContainer<MessagesSearchViewState> by lazy {
        ViewStateContainer(MessagesSearchViewState.Idle)
    }

    val messageReplyViewStateContainer: ViewStateContainer<MessageReplyViewState> by lazy {
        ViewStateContainer(MessageReplyViewState.ReplyingDismissed)
    }

    val callMenuHandler: ViewStateContainer<MenuBottomViewState> by lazy {
        ViewStateContainer(MenuBottomViewState.Closed)
    }

    val moreOptionsMenuHandler: ViewStateContainer<MenuBottomViewState> by lazy {
        ViewStateContainer(MenuBottomViewState.Closed)
    }

    val messageMentionsViewStateContainer: ViewStateContainer<MessageMentionsViewState> by lazy {
        ViewStateContainer(MessageMentionsViewState.MessageMentions(listOf()))
    }

    val scrollDownViewStateContainer: ViewStateContainer<ScrollDownViewState> by lazy {
        ViewStateContainer(ScrollDownViewState.Off)
    }

    val chatHeaderViewStateContainer: ViewStateContainer<ChatHeaderViewState> by lazy {
        ChatHeaderViewStateContainer()
    }

    val colorCache = ColorCache(dispatchers)

    private val _remoteTimezoneStateFlow: MutableStateFlow<String?> by lazy {
        MutableStateFlow(null)
    }

    val remoteTimezoneStateFlow: StateFlow<String?>
        get() = _remoteTimezoneStateFlow.asStateFlow()

    val threadHeaderViewState: ViewStateContainer<ThreadHeaderViewState> by lazy {
        ViewStateContainer(
            if (isThreadChat()) {
                ThreadHeaderViewState.BasicHeader(false)
            } else {
                ThreadHeaderViewState.Idle
            }
        )
    }

    val shimmerViewState: ViewStateContainer<ShimmerViewState> by lazy {
        ViewStateContainer(ShimmerViewState.On)
    }

    private val latestThreadMessagesFlow: MutableStateFlow<List<Message>?> = MutableStateFlow(null)
    private val scrollDownButtonCount: MutableStateFlow<Long?> = MutableStateFlow(null)

    private suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        lightningRepository.getAccountBalance()

    protected abstract val chatSharedFlow: SharedFlow<Chat?>

    protected abstract val threadSharedFlow: SharedFlow<List<Message>>?

    abstract val headerInitialHolderSharedFlow: SharedFlow<InitialHolderViewState>

    private val _visibleRange = MutableStateFlow(IntRange.EMPTY)

    fun updateVisibleRange(range: IntRange) {
        _visibleRange.value = range
    }

    protected abstract suspend fun getChatInfo(): Triple<ChatName?, PhotoUrl?, String>?

    abstract suspend fun shouldStreamSatsFor(podcastClip: PodcastClip, messageUUID: MessageUUID?)

    private inner class ChatHeaderViewStateContainer :
        ViewStateContainer<ChatHeaderViewState>(ChatHeaderViewState.Idle) {

        private var contactCollectionJob: Job? = null
        private var chatCollectionJob: Job? = null

        @RequiresApi(Build.VERSION_CODES.N)
        override val viewStateFlow: StateFlow<ChatHeaderViewState> = flow {

            contactId?.let { nnContactId ->
                contactCollectionJob = viewModelScope.launch(mainImmediate) {
                    // Ensure that chat collection sets state first before collecting the contact
                    // as we must have present the current value for mute
                    while (isActive && _viewStateFlow.value is ChatHeaderViewState.Idle) {
                        delay(25L)
                    }

                    contactRepository.getContactById(nnContactId).collect { contact ->
                        val currentState = _viewStateFlow.value

                        if (contact != null && currentState is ChatHeaderViewState.Initialized) {
                            _viewStateFlow.value = ChatHeaderViewState.Initialized(
                                chatHeaderName = contact.alias?.value ?: "",
                                showLock = currentState.showLock || contact.isEncrypted(),
                                isMuted = currentState.isMuted,
                                isChatAvailable = currentState.isChatAvailable,
                                isTribe = currentState.isTribe,
                                createdAt = contact.createdAt.time.toFormattedDate(),
                                colorKey = contact.getColorKey()
                            )
                        }
                    }
                }
            }

            chatCollectionJob = viewModelScope.launch {
                chatSharedFlow.collect { chat ->
                    chatCache.set(chat)

                    val timezoneString: String? = chat?.remoteTimezoneIdentifier?.value?.let {
                        DateTime.getLocalTimeFor(it, null)
                    }

                    _remoteTimezoneStateFlow.value = timezoneString

                    _viewStateFlow.value = ChatHeaderViewState.Initialized(
                        chatHeaderName = chat?.name?.value ?: getChatInfo()?.first?.value ?: "",
                        showLock = chat != null,
                        isMuted = chat?.notify?.isMuteChat() == true,
                        isChatAvailable = chat?.status?.isApproved() ?: false,
                        isTribe = chat?.isTribe() ?: false,
                        createdAt = chat?.createdAt?.time?.toFormattedDate(),
                        colorKey = chat?.getColorKey() ?: chat?.getColorKey() ?: ""
                    )

                    chat?.let { nnChat ->
                        if (nnChat.isPrivateTribe()) {
                            handleDisabledFooterState(nnChat)
                        }
                    }
                }
            }

            emitAll(_viewStateFlow)
        }.onCompletion {
            contactCollectionJob?.cancel()
            chatCollectionJob?.cancel()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChatHeaderViewState.Idle
        )
    }

    private fun collectThread() {
        viewModelScope.launch(io) {
            threadSharedFlow?.collect { messages ->
                latestThreadMessagesFlow.value = messages
            }
        }
    }

    private fun collectUnseenMessagesNumber() {
        viewModelScope.launch(io) {
            if (!isThreadChat()) {
                repositoryDashboard.getUnseenMessagesByChatId(getChat().id).collect { unseenMessagesCount ->
                    scrollDownButtonCount.value = unseenMessagesCount
                }
            }
        }
    }

    fun updateScrollDownButton(showButton: Boolean) {
        val newState = if (showButton) {
            val count = scrollDownButtonCount.value?.takeIf { it > 0 }?.toString()
            ScrollDownViewState.On(count)
        } else {
            ScrollDownViewState.Off
        }
        scrollDownViewStateContainer.updateViewState(newState)
    }

    private val chatCache = AtomicReference<Chat?>(null)
    private fun getChatOrNull(): Chat? = chatCache.get()

    suspend fun getChat(): Chat {
        chatSharedFlow.replayCache.firstOrNull()?.let { chat ->
            return chat
        }

        chatSharedFlow.firstOrNull()?.let { chat ->
            return chat
        }

        var chat: Chat? = null

        try {
            chatSharedFlow.collect {
                if (it != null) {
                    chat = it
                    throw Exception()
                }
            }
        } catch (e: Exception) {}
        delay(25L)

        return chat!!
    }

    private fun handleDisabledFooterState(chat: Chat) {
        if (chat.status.isPending()) {
            viewModelScope.launch(mainImmediate) {
                submitSideEffect(
                    ChatSideEffect.Notify(
                        app.getString(R.string.waiting_for_admin_approval),
                        false
                    )
                )
            }
        }

        if (!chat.status.isApproved()) {
            footerViewStateContainer.updateViewState(
                FooterViewState.Disabled
            )
        } else if (chat.status.isApproved() && footerViewStateContainer.value == FooterViewState.Disabled) {
            footerViewStateContainer.updateViewState(
                FooterViewState.Default
            )
        }
    }

    private suspend fun preComputeInitialHolders(
        messages: List<Message>,
        owner: Contact,
        chat: Chat
    ): Map<Long, InitialHolderViewState> {
        val initialHolders = mutableMapOf<Long, InitialHolderViewState>()

        for (message in messages) {
            val sent = message.sender == chat.contactIds.firstOrNull()
            if (!sent) {
                val holder = getInitialHolderViewStateForReceivedMessage(message, owner)
                initialHolders[message.id.value] = holder
            }
        }

        return initialHolders
    }

    abstract suspend fun getInitialHolderViewStateForReceivedMessage(
        message: Message,
        owner: Contact
    ): InitialHolderViewState

    suspend fun getOwner(): Contact {
        return contactRepository.accountOwner.value.let { contact ->
            if (contact != null) {
                contact
            } else {
                var resolvedOwner: Contact? = null
                try {
                    contactRepository.accountOwner.collect { ownerContact ->
                        if (ownerContact != null) {
                            resolvedOwner = ownerContact
                            throw Exception()
                        }
                    }
                } catch (e: Exception) {
                }
                delay(25L)

                resolvedOwner!!
            }
        }
    }

    private fun getBubbleBackgroundForMessage(
        message: Message,
        previousMessage: Message?,
        nextMessage: Message?,
        groupingDate: DateTime?,
    ): Pair<DateTime?, BubbleBackground> {

        val groupingMinutesLimit = 5.0
        var date = groupingDate ?: message.date

        val isPreviousMessageThreadHeader = (previousMessage?.uuid?.value == getThreadUUID()?.value && previousMessage?.type?.isGroupAction() == false)

        val shouldAvoidGroupingWithPrevious =
            (previousMessage?.shouldAvoidGrouping() ?: true) || message.shouldAvoidGrouping()
        val isGroupedBySenderWithPrevious =
            previousMessage?.hasSameSenderThanMessage(message) ?: false
        val isGroupedByDateWithPrevious =
            message.date.getMinutesDifferenceWithDateTime(date) < groupingMinutesLimit

        val groupedWithPrevious =
            (!shouldAvoidGroupingWithPrevious && isGroupedBySenderWithPrevious && isGroupedByDateWithPrevious && !isPreviousMessageThreadHeader)

        date = if (groupedWithPrevious) date else message.date

        val shouldAvoidGroupingWithNext =
            (nextMessage?.shouldAvoidGrouping() ?: true) || message.shouldAvoidGrouping()
        val isGroupedBySenderWithNext = nextMessage?.hasSameSenderThanMessage(message) ?: false
        val isGroupedByDateWithNext =
            if (nextMessage != null) nextMessage.date.getMinutesDifferenceWithDateTime(date) < groupingMinutesLimit else false

        val groupedWithNext =
            (!shouldAvoidGroupingWithNext && isGroupedBySenderWithNext && isGroupedByDateWithNext)

        when {
            (!groupedWithPrevious && !groupedWithNext) -> {
                return Pair(date, BubbleBackground.First.Isolated)
            }
            (groupedWithPrevious && !groupedWithNext) -> {
                return Pair(date, BubbleBackground.Last)
            }
            (!groupedWithPrevious && groupedWithNext) -> {
                return Pair(date, BubbleBackground.First.Grouped)
            }
            (groupedWithPrevious && groupedWithNext) -> {
                return Pair(date, BubbleBackground.Middle)
            }
        }

        return Pair(date, BubbleBackground.First.Isolated)
    }

    data class PreComputedData(
        val chatInfo: Triple<ChatName?, PhotoUrl?, String>?,
        val owner: Contact,
        val tribeAdmin: Contact?,
        val initialHolders: Map<Long, InitialHolderViewState>,
        val memberTimezones: Map<String, String>
    )

    private fun processMessagesWithData(
        messages: List<Message>,
        chat: Chat,
        preComputedData: PreComputedData
    ): List<MessageHolderViewState> {
        val chatName = preComputedData.chatInfo?.first
        val chatPhotoUrl = preComputedData.chatInfo?.second
        val chatColorKey = preComputedData.chatInfo?.third ?: app.getRandomHexCode()
        val owner = preComputedData.owner
        val tribeAdmin = preComputedData.tribeAdmin
        val memberTimezones = preComputedData.memberTimezones

        var unseenSeparatorAdded = false

        val newList = ArrayList<MessageHolderViewState>(messages.size)

        var groupingDate: DateTime? = null
        var openSentPaidInvoicesCount = 0
        var openReceivedPaidInvoicesCount = 0

        val messagesList = filterAndSortMessagesIfNecessary(chat, messages)

        for ((index, message) in messagesList.withIndex()) {

//            Log.d("MediaRepository Download", "Index $itemIndex")

            val previousMessage: Message? = if (index > 0) messagesList[index - 1] else null
            val nextMessage: Message? = if (index < messagesList.size - 1) messagesList[index + 1] else null

            val groupingDateAndBubbleBackground = getBubbleBackgroundForMessage(
                message,
                previousMessage,
                nextMessage,
                groupingDate
            )

            groupingDate = groupingDateAndBubbleBackground.first

            val sent = message.sender == chat.contactIds.firstOrNull()

            if (message.type.isInvoicePayment()) {
                if (sent) {
                    openReceivedPaidInvoicesCount -= 1
                } else {
                    openSentPaidInvoicesCount -= 1
                }
            }

            val invoiceLinesHolderViewState = InvoiceLinesHolderViewState(
                openSentPaidInvoicesCount > 0,
                openReceivedPaidInvoicesCount > 0
            )

            val isThreadHeaderMessage = (message.uuid?.value == getThreadUUID()?.value && index == 0 && !message.type.isGroupAction())

            if (isThreadHeaderMessage) {
                newList.add(
                    MessageHolderViewState.ThreadHeader(
                        message,
                        MessageHolderType.ThreadHeader,
                        chat,
                        tribeAdmin,
                        InitialHolderViewState.None,
                        index = newList.size,
                        messageSenderInfo = { messageCallback ->
                            when {
                                messageCallback.sender == chat.contactIds.firstOrNull() -> {
                                    val accountOwner = contactRepository.accountOwner.value

                                    Triple(
                                        accountOwner?.photoUrl,
                                        accountOwner?.alias,
                                        accountOwner?.getColorKey() ?: ""
                                    )
                                }

                                chat.type.isConversation() -> {
                                    Triple(
                                        chatPhotoUrl,
                                        chatName?.value?.toContactAlias(),
                                        chatColorKey
                                    )
                                }

                                else -> {
                                    Triple(
                                        messageCallback.senderPic,
                                        messageCallback.senderAlias?.value?.toContactAlias(),
                                        messageCallback.getColorKey()
                                    )
                                }
                            }
                        },
                        accountOwner = { owner },
                        message.date.chatTimeFormat(),
                        isExpanded = false
                    )
                )
                continue
            }

            if (!message.seen.isTrue() && !sent && !unseenSeparatorAdded) {
                newList.add(
                    MessageHolderViewState.Separator(
                        MessageHolderType.UnseenSeparator,
                        null,
                        chat,
                        tribeAdmin,
                        BubbleBackground.Gone(setSpacingEqual = true),
                        index = newList.size,
                        invoiceLinesHolderViewState,
                        InitialHolderViewState.None,
                        accountOwner = { owner }
                    )
                )
                unseenSeparatorAdded = true
            }
            // Consider last reply or message and last reply of previous message if exists
            val actualMessage = message.thread?.first() ?: message
            val actualPreviousMessage = previousMessage?.thread?.first() ?: previousMessage

            if (actualPreviousMessage == null || actualMessage.date.isDifferentDayThan(actualPreviousMessage.date)) {
                newList.add(
                    MessageHolderViewState.Separator(
                        MessageHolderType.DateSeparator,
                        message.date,
                        chat,
                        tribeAdmin,
                        BubbleBackground.Gone(setSpacingEqual = true),
                        index = newList.size,
                        invoiceLinesHolderViewState,
                        InitialHolderViewState.None,
                        accountOwner = { owner }
                    )
                )
            }

            val isDeleted = message.status.isDeleted()

            val cacheKey = generateCacheKey(
                messageId = message.id.toString(),
                messageText = message.retrieveTextToShow()?.replacingMarkdown(),
                background = groupingDateAndBubbleBackground.second,
                shouldAdaptBubbleWidth = message.shouldAdaptBubbleWidth,
                reactionsCount = message.reactions?.size ?: 0
            )

            if (message.isOnlyTextMessage) {
                val spaces = dimensionCache.getOrPut(cacheKey) {
                    calculateOnlyTextMessageDimensions(
                        message.retrieveTextToShow()?.replacingMarkdown(),
                        !sent,
                        groupingDateAndBubbleBackground.second,
                        message.shouldAdaptBubbleWidth
                    )
                }

                if (sent) {
                    newList.add(
                        MessageHolderViewState.MessageOnlyTextHolderViewState.Sent(
                            message,
                            chat,
                            background = groupingDateAndBubbleBackground.second,
                            highlightedText = null,
                            index = newList.size,
                            messageSenderInfo = { messageCallback ->
                                when {
                                    messageCallback.sender == chat.contactIds.firstOrNull() -> {
                                        val accountOwner = contactRepository.accountOwner.value

                                        Triple(
                                            accountOwner?.photoUrl,
                                            accountOwner?.alias,
                                            accountOwner?.getColorKey() ?: ""
                                        )
                                    }
                                    chat.type.isConversation() -> {
                                        Triple(
                                            chatPhotoUrl,
                                            chatName?.value?.toContactAlias(),
                                            chatColorKey
                                        )
                                    }
                                    else -> {
                                        Triple(
                                            messageCallback.senderPic,
                                            messageCallback.senderAlias?.value?.toContactAlias(),
                                            messageCallback.getColorKey()
                                        )
                                    }
                                }
                            },
                            accountOwner = { owner },
                            memberTimezoneIdentifier = if (message.senderAlias != null) memberTimezones[message.senderAlias!!.value] else null,
                            spaceLeft = spaces.first,
                            spaceRight = spaces.second
                        )
                    )
                } else {
                    newList.add(
                        MessageHolderViewState.MessageOnlyTextHolderViewState.Received(
                            message,
                            chat,
                            background = groupingDateAndBubbleBackground.second,
                            initialHolder = preComputedData.initialHolders[message.id.value] ?: InitialHolderViewState.None,
                            highlightedText = null,
                            index = newList.size,
                            messageSenderInfo = { messageCallback ->
                                when {
                                    messageCallback.sender == chat.contactIds.firstOrNull() -> {
                                        val accountOwner = contactRepository.accountOwner.value

                                        Triple(
                                            accountOwner?.photoUrl,
                                            accountOwner?.alias,
                                            accountOwner?.getColorKey() ?: ""
                                        )
                                    }

                                    chat.type.isConversation() -> {
                                        Triple(
                                            chatPhotoUrl,
                                            chatName?.value?.toContactAlias(),
                                            chatColorKey
                                        )
                                    }

                                    else -> {
                                        Triple(
                                            messageCallback.senderPic,
                                            messageCallback.senderAlias?.value?.toContactAlias(),
                                            messageCallback.getColorKey()
                                        )
                                    }
                                }
                            },
                            accountOwner = { owner },
                            memberTimezoneIdentifier = if (message.senderAlias != null) memberTimezones[message.senderAlias!!.value] else null,
                            spaceLeft = spaces.first,
                            spaceRight = spaces.second
                        )
                    )
                }
            } else {
                val sentDirectionBubble = ((sent && !message.isPaidInvoice) || (!sent && message.isPaidInvoice))

                val background = if (sentDirectionBubble) {
                    when {
                        isDeleted -> {
                            BubbleBackground.Gone(setSpacingEqual = false)
                        }
                        message.type.isInvoicePayment() -> {
                            BubbleBackground.Gone(setSpacingEqual = false)
                        }
                        message.type.isGroupAction() -> {
                            BubbleBackground.Gone(setSpacingEqual = true)
                        }
                        else -> {
                            groupingDateAndBubbleBackground.second
                        }
                    }
                } else {
                    when {
                        isDeleted -> {
                            BubbleBackground.Gone(setSpacingEqual = false)
                        }

                        message.isFlagged -> {
                            BubbleBackground.Gone(setSpacingEqual = false)
                        }

                        message.type.isInvoicePayment() -> {
                            BubbleBackground.Gone(setSpacingEqual = false)
                        }

                        message.type.isGroupAction() -> {
                            BubbleBackground.Gone(setSpacingEqual = true)
                        }

                        else -> {
                            groupingDateAndBubbleBackground.second
                        }
                    }
                }

                val spaces = dimensionCache.getOrPut(cacheKey) {
                    calculateMessageDimensions(
                        message.retrieveTextToShow()?.replacingMarkdown(),
                        if (message.isDirectPayment) message.amount.asFormattedString() else null,
                        message.reactions?.size ?: 0,
                        message.isDirectPayment,
                        message.retrieveImageUrlAndMessageMedia() != null,
                        message.isPodcastBoost,
                        message.isExpiredInvoice(),
                        message.isSphinxCallLink,
                        !sentDirectionBubble,
                        chat.isTribe(),
                        background,
                        message.shouldAdaptBubbleWidth
                    )
                }

                if (sentDirectionBubble) {
                    newList.add(
                        MessageHolderViewState.Sent(
                            message,
                            chat,
                            tribeAdmin,
                            background = background,
                            invoiceLinesHolderViewState = invoiceLinesHolderViewState,
                            highlightedText = null,
                            index = newList.size,
                            messageSenderInfo = { messageCallback ->
                                when {
                                    messageCallback.sender == chat.contactIds.firstOrNull() -> {
                                        val accountOwner = contactRepository.accountOwner.value

                                        Triple(
                                            accountOwner?.photoUrl,
                                            accountOwner?.alias,
                                            accountOwner?.getColorKey() ?: ""
                                        )
                                    }
                                    chat.type.isConversation() -> {
                                        Triple(
                                            chatPhotoUrl,
                                            chatName?.value?.toContactAlias(),
                                            chatColorKey
                                        )
                                    }
                                    else -> {
                                        Triple(
                                            messageCallback.senderPic,
                                            messageCallback.senderAlias?.value?.toContactAlias(),
                                            messageCallback.getColorKey()
                                        )
                                    }
                                }
                            },
                            accountOwner = { owner },
                            urlLinkPreviewsEnabled = areUrlLinkPreviewsEnabled(),
                            previewProvider = { link, itemIndex ->
                                delay(500L)

                                if (itemIndex in _visibleRange.value) {
                                    handleLinkPreview(link)
                                } else {
                                    null
                                }
                            },
                            paidTextMessageContentProvider = { messageCallback ->
                                handlePaidTextMessageContent(messageCallback)
                            },
                            onBindDownloadMedia = { itemIndex ->
                                viewModelScope.launch {
                                    delay(500L)

                                    if (itemIndex in _visibleRange.value) {
                                        val job = repositoryMedia.downloadMediaIfApplicable(message, sent)
                                        activeDownloadJobs.add(job)

                                        job.invokeOnCompletion {
                                            activeDownloadJobs.remove(job)
                                        }
                                    }
                                }
                            },
                            onBindThreadDownloadMedia = { itemIndex ->
                                message.thread?.first()?.let { lastReplyMessage ->
                                    viewModelScope.launch() {
                                        if (itemIndex in _visibleRange.value) {
                                            val job = repositoryMedia.downloadMediaIfApplicable(
                                                lastReplyMessage,
                                                sent
                                            )
                                            activeDownloadJobs.add(job)

                                            job.invokeOnCompletion {
                                                activeDownloadJobs.remove(job)
                                            }
                                        }
                                    }
                                }
                            },
                            memberTimezoneIdentifier = if (message.senderAlias != null) memberTimezones[message.senderAlias!!.value] else null,
                            spaceLeft = spaces.first,
                            spaceRight = spaces.second
                        )
                    )
                } else {
                    newList.add(
                        MessageHolderViewState.Received(
                            message,
                            chat,
                            tribeAdmin,
                            background = background,
                            invoiceLinesHolderViewState = invoiceLinesHolderViewState,
                            initialHolder = when {
                                isDeleted || message.type.isGroupAction() -> {
                                    InitialHolderViewState.None
                                }

                                else -> {
                                    preComputedData.initialHolders[message.id.value] ?: InitialHolderViewState.None
                                }
                            },
                            highlightedText = null,
                            index = newList.size,
                            messageSenderInfo = { messageCallback ->
                                when {
                                    messageCallback.sender == chat.contactIds.firstOrNull() -> {
                                        val accountOwner = contactRepository.accountOwner.value

                                        Triple(
                                            accountOwner?.photoUrl,
                                            accountOwner?.alias,
                                            accountOwner?.getColorKey() ?: ""
                                        )
                                    }

                                    chat.type.isConversation() -> {
                                        Triple(
                                            chatPhotoUrl,
                                            chatName?.value?.toContactAlias(),
                                            chatColorKey
                                        )
                                    }

                                    else -> {
                                        Triple(
                                            messageCallback.senderPic,
                                            messageCallback.senderAlias?.value?.toContactAlias(),
                                            messageCallback.getColorKey()
                                        )
                                    }
                                }
                            },
                            accountOwner = { owner },
                            urlLinkPreviewsEnabled = areUrlLinkPreviewsEnabled(),
                            previewProvider = { link, itemIndex ->
                                delay(500L)

                                if (itemIndex in _visibleRange.value) {
                                    handleLinkPreview(link)
                                } else {
                                    null
                                }
                            },
                            paidTextMessageContentProvider = { messageCallback ->
                                handlePaidTextMessageContent(messageCallback)
                            },
                            onBindDownloadMedia = { itemIndex ->
                                viewModelScope.launch() {
                                    delay(500L)

                                    if (itemIndex in _visibleRange.value) {
                                        val job = repositoryMedia.downloadMediaIfApplicable(message, sent)
                                        activeDownloadJobs.add(job)

                                        job.invokeOnCompletion {
                                            activeDownloadJobs.remove(job)
                                        }
                                    }
                                }
                            },
                            onBindThreadDownloadMedia = { itemIndex ->
                                message.thread?.first()?.let { lastReplyMessage ->
                                    viewModelScope.launch() {
                                        if (itemIndex in _visibleRange.value) {
                                            val job = repositoryMedia.downloadMediaIfApplicable(
                                                lastReplyMessage,
                                                sent
                                            )
                                            activeDownloadJobs.add(job)

                                            job.invokeOnCompletion {
                                                activeDownloadJobs.remove(job)
                                            }
                                        }
                                    }
                                }
                            },
                            memberTimezoneIdentifier = if (message.senderAlias != null) memberTimezones[message.senderAlias!!.value] else null,
                            spaceLeft = spaces.first,
                            spaceRight = spaces.second
                        )
                    )
                }
            }

            if (message.isPaidInvoice) {
                if (sent) {
                    openSentPaidInvoicesCount += 1
                } else {
                    openReceivedPaidInvoicesCount += 1
                }
            }

        }

        return newList
    }

    private fun calculateOnlyTextMessageDimensions(
        messageText: String?,
        isReceived: Boolean,
        background: BubbleBackground,
        shouldAdaptBubbleWidth: Boolean
    ): Pair<Int, Int> {

        val context = app.applicationContext
        val defaultMargins = dims.defaultMargin

        if (background is BubbleBackground.Gone && background.setSpacingEqual) {
            return Pair(defaultMargins, defaultMargins)
        } else {
            val defaultReceivedLeftMargin = dims.defaultReceivedLeftMargin
            val defaultSentRightMargin = dims.defaultSentRightMargin

            val holderWidth = recyclerWidth - (defaultMargins * 2)
            val bubbleFixedWidth =
                (holderWidth - defaultReceivedLeftMargin - defaultSentRightMargin - (holderWidth * BubbleBackground.SPACE_WIDTH_MULTIPLE)).toInt()

            var bubbleWidth: Int = when {
                shouldAdaptBubbleWidth -> {
                    val text = messageText ?: context.getString(R_common.string.decryption_error)
                    val textWidth = (textPaint.measureText(
                        text
                    ) + (defaultMargins * 2)).toInt()
                    textWidth
                }
                else -> {
                    bubbleFixedWidth
                }
            }

            bubbleWidth = bubbleWidth.coerceAtMost(bubbleFixedWidth)

            if (isReceived) {
                return Pair(
                    defaultReceivedLeftMargin,
                    (holderWidth - defaultReceivedLeftMargin - bubbleWidth)
                )
            } else {
                return Pair(
                    (holderWidth - defaultSentRightMargin - bubbleWidth),
                    defaultSentRightMargin
                )
            }

        }
    }

    private fun calculateMessageDimensions(
        messageText: String?,
        amountText: String?,
        reactionsCount: Int,
        isDirectPayment: Boolean,
        isImageAttachment: Boolean,
        isPodcastBoost: Boolean,
        isExpiredInvoice: Boolean,
        isSphinxCallLink: Boolean,
        isReceived: Boolean,
        isTribe: Boolean,
        background: BubbleBackground,
        shouldAdaptBubbleWidth: Boolean
    ): Pair<Int, Int> {

        val context = app.applicationContext
        val defaultMargins = dims.defaultMargin

        if (background is BubbleBackground.Gone && background.setSpacingEqual) {
            return Pair(defaultMargins, defaultMargins)
        } else {
            val defaultReceivedLeftMargin = dims.defaultReceivedLeftMargin
            val defaultSentRightMargin = dims.defaultSentRightMargin

            val holderWidth = recyclerWidth - (defaultMargins * 2)
            val bubbleFixedWidth = (holderWidth - defaultReceivedLeftMargin - defaultSentRightMargin - (holderWidth * BubbleBackground.SPACE_WIDTH_MULTIPLE)).toInt()

            val messageReactionsWidth = if (reactionsCount > 0) dims.boostWidth else 0

            var bubbleWidth: Int = when {
                shouldAdaptBubbleWidth == true -> {

                    val text = messageText ?: context.getString(R_common.string.decryption_error)
                    val textWidth = (textPaint.measureText(
                        text
                    ) + (defaultMargins * 2)).toInt()

                    var amountWidth = 0

                    if (isDirectPayment) {
                        var paymentMargin = 0
                        if (isTribe) {
                            paymentMargin = dims.tribePaymentMargin
                        } else {
                            paymentMargin = dims.paymentMargin
                        }

                        amountText?.let {
                            amountWidth = (amountPaint.measureText(
                                it
                            ) + paymentMargin).toInt()
                        }
                    }

                    val imageWidth = if (isImageAttachment) (bubbleFixedWidth * 0.8F).toInt() else 0

                    textWidth
                        .coerceAtLeast(amountWidth)
                        .coerceAtLeast(imageWidth)
                }
                isPodcastBoost -> {
                    dims.podcastBoostWidth
                }
                isExpiredInvoice -> {
                    dims.expiredInvoiceWidth
                }
                isSphinxCallLink -> {
                    (bubbleFixedWidth * 0.8F).toInt()
                }
                else -> {
                    bubbleFixedWidth
                }
            }

            bubbleWidth = bubbleWidth
                .coerceAtLeast(messageReactionsWidth)
                .coerceAtMost(bubbleFixedWidth)

            if (isReceived) {
                return Pair(defaultReceivedLeftMargin, (holderWidth - defaultReceivedLeftMargin - bubbleWidth))
            } else {
                return Pair((holderWidth - defaultSentRightMargin - bubbleWidth), defaultSentRightMargin)
            }
        }
    }

    private fun generateCacheKey(
        messageId: String,
        messageText: String?,
        background: BubbleBackground,
        shouldAdaptBubbleWidth: Boolean,
        reactionsCount: Int
    ): String {
        return buildString {
            append(messageId)
            append("_")
            append(messageText?.hashCode() ?: "null")
            append("_")
            append(background.javaClass.simpleName)
            append("_")
            append(shouldAdaptBubbleWidth)
            append("_")
            append(recyclerWidth)
            append("_")
            append(reactionsCount)
        }
    }

    private suspend fun getMessageHolderViewStateList(messages: List<Message>): List<MessageHolderViewState> {
        if (messages.isEmpty()) return emptyList()
        val chat = getChatOrNull() ?: return emptyList()

        val preComputedData = withContext(Dispatchers.IO) {
            val chatInfoDeferred = async { getChatInfo() }
            val ownerDeferred = async { getOwner() }
            val tribeAdminDeferred = async {
                chat.ownerPubKey?.let {
                    contactRepository.getContactByPubKey(it).firstOrNull()
                }
            }

            val owner = ownerDeferred.await()

            val initialHoldersJob = async {
                preComputeInitialHolders(messages, owner, chat)
            }

            val memberTimezones: MutableMap<String, String> = mutableMapOf()
            val missingMemberTimezones: MutableList<SenderAlias> = mutableListOf()

            if (chat.type.isTribe()) {
                messages.forEach { message ->
                    val alias = message.senderAlias?.value
                    val tz = message.remoteTimezoneIdentifier?.value
                    if (!alias.isNullOrEmpty() && !tz.isNullOrEmpty()) {
                        memberTimezones[alias] = tz
                        missingMemberTimezones.remove(alias.toSenderAlias())
                    } else if (!alias.isNullOrEmpty() && !missingMemberTimezones.contains(alias.toSenderAlias())) {
                        alias.toSenderAlias()?.let {
                            missingMemberTimezones.add(it)
                        }
                    }
                }
            }

            val missingAliasesMessages = messageRepository.getRemoteTimezoneForAliases(
                chat.id,
                missingMemberTimezones
            ).firstOrNull()

            missingAliasesMessages?.forEach { message ->
                val alias = message.senderAlias?.value
                val tz = message.remoteTimezoneIdentifier?.value

                if (!alias.isNullOrEmpty() && !tz.isNullOrEmpty() && !memberTimezones.contains(alias)) {
                    memberTimezones[alias] = tz
                }
            }

            PreComputedData(
                chatInfo = chatInfoDeferred.await(),
                owner = owner,
                tribeAdmin = tribeAdminDeferred.await(),
                initialHolders = initialHoldersJob.await(),
                memberTimezones = memberTimezones
            )
        }


        return withContext(Dispatchers.Default) {
            processMessagesWithData(messages, chat, preComputedData)
        }
    }

    private fun filterAndSortMessagesIfNecessary(
        chat: Chat,
        messages: List<Message>,
    ): List<Message> {
        val filteredMessages: MutableList<Message> = mutableListOf()
        val threadMessageMap: MutableMap<String, Int> = mutableMapOf()

        // Filter messages to do not show thread replies on chat
        if (chat.isTribe() && !isThreadChat()) {
            for (message in messages) {

                if (message.thread?.isNotEmpty() == true) {
                    message.uuid?.value?.let { uuid ->
                        threadMessageMap[uuid] = message.thread?.count() ?: 0
                    }
                }

                val shouldAddMessage = message.threadUUID?.let { threadUUID ->
                    val count = threadMessageMap[threadUUID.value] ?: 0
                    count <= 1
                } ?: true

                if (shouldAddMessage) {
                    filteredMessages.add(message)
                }
            }
        } else {
            filteredMessages.addAll(messages)
        }

        // Sort messages list by the last thread message date if applicable

        return filteredMessages.sortedBy { it.thread?.first()?.date?.value ?: it.date.value }
    }

    internal val messageHolderViewStateFlow: MutableStateFlow<List<MessageHolderViewState>> by lazy {
        MutableStateFlow(listOf())
    }

    private suspend fun handleLinkPreview(link: MessageLinkPreview): LayoutState.Bubble.ContainerThird.LinkPreview? {
        var preview: LayoutState.Bubble.ContainerThird.LinkPreview? = null

        viewModelScope.launch(default) {
            // TODO: Implement
            @Exhaustive
            when (link) {
                is NodeDescriptor -> {

                    val pubKey: LightningNodePubKey? = when (link.nodeDescriptor) {
                        is LightningNodePubKey -> {
                            link.nodeDescriptor
                        }
                        is VirtualLightningNodeAddress -> {
                            link.nodeDescriptor.getPubKey()
                        }
                    }

                    if (pubKey != null) {
                        val existingContact: Contact? =
                            contactRepository.getContactByPubKey(pubKey).firstOrNull()

                        if (existingContact != null) {

                            preview = LayoutState.Bubble.ContainerThird.LinkPreview.ContactPreview(
                                alias = existingContact.alias,
                                photoUrl = existingContact.photoUrl,
                                showBanner = false,
                                lightningNodeDescriptor = link.nodeDescriptor,
                            )

                        } else {

                            preview = LayoutState.Bubble.ContainerThird.LinkPreview.ContactPreview(
                                alias = null,
                                photoUrl = null,
                                showBanner = true,
                                lightningNodeDescriptor = link.nodeDescriptor
                            )

                        }
                    }

                }
                is TribeLink -> {
                    try {
                        val uuid = ChatUUID(link.tribeJoinLink.tribePubkey)

                        val thisChat = getChat()

                        if (thisChat?.uuid == uuid) {

                            preview =
                                LayoutState.Bubble.ContainerThird.LinkPreview.TribeLinkPreview(
                                    name = TribePreviewName(thisChat.name?.value ?: ""),
                                    description = null,
                                    imageUrl = thisChat.photoUrl?.toPreviewImageUrlOrNull(),
                                    showBanner = true,
                                    joinLink = link.tribeJoinLink,
                                )

                        } else {
                            val existingChat = chatRepository.getChatByUUID(uuid).firstOrNull()
                            if (existingChat != null) {

                                preview =
                                    LayoutState.Bubble.ContainerThird.LinkPreview.TribeLinkPreview(
                                        name = TribePreviewName(existingChat.name?.value ?: ""),
                                        description = null,
                                        imageUrl = existingChat.photoUrl?.toPreviewImageUrlOrNull(),
                                        showBanner = false,
                                        joinLink = link.tribeJoinLink,
                                    )

                            } else {

                                val isProductionEnvironment = app.getSharedPreferences(
                                    SERVER_SETTINGS_SHARED_PREFERENCES,
                                    Context.MODE_PRIVATE
                                ).getBoolean(ENVIRONMENT_TYPE, true)

                                val tribePreview =
                                    linkPreviewHandler.retrieveTribeLinkPreview(link.tribeJoinLink, isProductionEnvironment)

                                if (tribePreview != null) {
                                    preview =
                                        LayoutState.Bubble.ContainerThird.LinkPreview.TribeLinkPreview(
                                            name = tribePreview.name,
                                            description = tribePreview.description,
                                            imageUrl = tribePreview.imageUrl,
                                            showBanner = true,
                                            joinLink = link.tribeJoinLink,
                                        )
                                } // else do nothing
                            }
                        }
                    } catch (_: Exception) {
                        // no - op
                    }
                }
                is FeedItemPreview -> {}
                is UnspecifiedUrl -> {
                    if (areUrlLinkPreviewsEnabled()) {
                        val htmlPreview = linkPreviewHandler.retrieveHtmlPreview(link.url)

                        preview = if (htmlPreview != null) {
                            LayoutState.Bubble.ContainerThird.LinkPreview.HttpUrlPreview(
                                title = htmlPreview.title,
                                domainHost = htmlPreview.domainHost,
                                description = htmlPreview.description,
                                imageUrl = htmlPreview.imageUrl,
                                favIconUrl = htmlPreview.favIconUrl,
                                url = link.url
                            )
                        } else {
                            LayoutState.Bubble.ContainerThird.LinkPreview.NoAvailablePreview(
                                url = link.url
                            )
                        }
                    }
                }
            }
        }.join()

        return preview
    }

    var urlLinkPreviewsEnabled: Boolean? = null
    private fun areUrlLinkPreviewsEnabled(): Boolean {
        urlLinkPreviewsEnabled?.let {
            return it
        }

        val appContext: Context = app.applicationContext
        val serverUrlsSharedPreferences = appContext.getSharedPreferences(
            PreviewsEnabled.LINK_PREVIEWS_SHARED_PREFERENCES,
            Context.MODE_PRIVATE
        )

        urlLinkPreviewsEnabled = serverUrlsSharedPreferences.getBoolean(
            PreviewsEnabled.LINK_PREVIEWS_ENABLED_KEY,
            PreviewsEnabled.True.isTrue()
        )

        return urlLinkPreviewsEnabled!!
    }

    private suspend fun handlePaidTextMessageContent(message: Message): LayoutState.Bubble.ContainerThird.Message? {
        var messageLayoutState: LayoutState.Bubble.ContainerThird.Message? = null

        viewModelScope.launch(io) {
            message.retrievePaidTextAttachmentUrlAndMessageMedia()?.let { urlAndMedia ->
                urlAndMedia.second?.host?.let { host ->
                    urlAndMedia.second?.mediaKeyDecrypted?.let { mediaKeyDecrypted ->
                        memeServerTokenHandler.retrieveAuthenticationToken(host)?.let { token ->

                            val streamAndFileName = memeInputStreamHandler.retrieveMediaInputStream(
                                urlAndMedia.first,
                                token,
                                mediaKeyDecrypted
                            )

                            var text: String? = null

                            viewModelScope.launch(io) {
                                text = streamAndFileName?.first?.bufferedReader()
                                    .use { it?.readText() }
                            }.join()

                            text?.let { nnText ->
                                messageLayoutState = LayoutState.Bubble.ContainerThird.Message(
                                    text = nnText.replacingMarkdown(),
                                    highlightedTexts = nnText.highlightedTexts(),
                                    boldTexts = nnText.boldTexts(),
                                    markdownLinkTexts = nnText.markDownLinkTexts(),
                                    decryptionError = false,
                                    isThread = false
                                )

                                nnText.toMessageContentDecrypted()?.let { messageContentDecrypted ->
                                    messageRepository.updateMessageContentDecrypted(
                                        message.id,
                                        messageContentDecrypted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }.join()

        return messageLayoutState
    }

    fun init() {
        initializeFlowStates()
        setupAudioPlayerHandler()
        collectConnectManagerErrorState()
    }

    private fun initializeFlowStates() {
        // Use a single coroutine to initialize all flows
        viewModelScope.launch(dispatchers.default) {
            // Prime flows in parallel without blocking main thread
            val initJobs = listOf(
                async { chatSharedFlow.firstOrNull() },
                async { headerInitialHolderSharedFlow.firstOrNull() },
                async { viewStateContainer.viewStateFlow.firstOrNull() }
            )

            // Wait for all to complete or timeout after reasonable time
            withTimeoutOrNull(2000) {
                initJobs.awaitAll()
            }
        }
    }

    private fun setupAudioPlayerHandler() {
        audioPlayerController.streamSatsHandler = { messageUUID, podcastClip ->
            podcastClip?.let { nnPodcastClip ->
                viewModelScope.launch(dispatchers.io) {
                    try {
                        shouldStreamSatsFor(nnPodcastClip, messageUUID)
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to stream sats", e)
                    }
                }
            }
        }
    }

    private fun collectConnectManagerErrorState(){
        viewModelScope.launch(mainImmediate) {
            connectManagerRepository.connectManagerErrorState.collect { connectManagerError ->
                when (connectManagerError) {
                    is ConnectManagerError.SendMessageError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_send_message_error))
                        )
                    }
                    is ConnectManagerError.PayContactInvoiceError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_pay_contact_invoice_error))
                        )
                    }
                    is ConnectManagerError.PayInvoiceError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_pay_invoice_error))
                        )
                    }
                    is ConnectManagerError.PaymentHashError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_payment_hash_error))
                        )
                    }
                    is ConnectManagerError.ReadMessageError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_read_message_error))
                        )
                    }
                    is ConnectManagerError.SignBytesError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_sign_bytes_error))
                        )
                    }
                    is ConnectManagerError.MediaTokenError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_media_token_error))
                        )
                    }
                    is ConnectManagerError.SetMuteError -> {
                        submitSideEffect(ChatSideEffect.NotifyError(
                            app.getString(R_common.string.connect_manager_set_mute_error))
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    var messagesLoadJob: Job? = null
    private val messageLimitFlow = MutableStateFlow(100L)
    private var isLoadingMore = false

    fun screenInit() {
        if (messageHolderViewStateFlow.value.isNotEmpty()) {
            return
        }

        if (chatId == null) {
            shimmerViewState.updateViewState(ShimmerViewState.Off)
        }

        if (messagesLoadJob?.isActive == true) {
            return
        }

        messagesLoadJob = viewModelScope.launch(Dispatchers.IO) {
            if (isThreadChat()) {
                messageRepository.getAllMessagesToShowByChatId(getChat().id, 0, getThreadUUID())
                    .distinctUntilChanged()
                    .flowOn(Dispatchers.IO)
                    .collect { messages ->
                        viewModelScope.launch {
                            val processedData = withContext(Dispatchers.Default) {
                                val originalMessage = messageRepository.getMessageByUUID(
                                    MessageUUID(getThreadUUID()?.value!!)
                                ).firstOrNull()

                                val completeThread = listOf(originalMessage) + messages.reversed()
                                val list =
                                    getMessageHolderViewStateList(completeThread.filterNotNull()).toList()

                                Triple(list, messages.size.toLong(), originalMessage != null)
                            }

                            withContext(Dispatchers.Main.immediate) {
                                messageHolderViewStateFlow.value = processedData.first
                                changeThreadHeaderState(processedData.third)
                                scrollDownButtonCount.value = processedData.second

                                delay(25)
                                hideShimmeringView()
                            }
                        }
                    }
            } else {
                messageLimitFlow
                    .flatMapLatest { limit ->
                        messageRepository.getAllMessagesToShowByChatId(getChat().id, limit).distinctUntilChanged()
                    }
                    .flowOn(Dispatchers.IO)
                    .collect { messages ->
                        viewModelScope.launch {
                            val processedData = withContext(Dispatchers.Default) {
                                val list = getMessageHolderViewStateList(messages).toList()

                                reloadPinnedMessage()

                                val lastMessage = messages.lastOrNull()
                                val showClockIcon = lastMessage?.let {
                                    it.status == MessageStatus.Pending &&
                                            System.currentTimeMillis() - it.date.time > 30_000
                                } == true

                                Pair(list, showClockIcon)
                            }

                            withContext(Dispatchers.Main.immediate) {
                                messageHolderViewStateFlow.value = processedData.first

                                updateScrollDownButtonCount()
                                updateClockIconState(processedData.second)

                                delay(25)
                                hideShimmeringView()
                            }
                        }
                    }
            }
        }
        collectThread()
        collectUnseenMessagesNumber()
        collectItemsFetched()
        fetchMoreItems()

        viewModelScope.launch(Dispatchers.IO) {
            connectManagerRepository.getTagsByChatId(getChat().id)
        }
    }

    fun loadMoreMessages() {
        if (isThreadChat()) return
        if (isLoadingMore) return

        isLoadingMore = true
        fetchMoreItems()
    }

    private fun fetchMoreItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val chat = getChat()
            chat.ownerPubKey?.value?.let { publicKey ->
                messageRepository.fetchMessagesPerContact(
                    chat.id,
                    publicKey
                )
            }
        }
    }

    private fun collectItemsFetched() {
        viewModelScope.launch(mainImmediate) {
            val chat = getChat()

            connectManagerRepository.fetchProcessState.collect { pair ->
                if (pair?.second == chat.ownerPubKey?.value) {
                    if ((pair?.first ?: 0) > 0) {
                        messageLimitFlow.value += 100

                        reloadSearchWithFetchedMsgs()
                    } else {
                        reachEndOfResults()
                    }
                    connectManagerRepository.getTagsByChatId(chat.id)
                    delay(4000L)
                    isLoadingMore = false
                }
            }
        }
    }

    fun resetMessageLimit() {
        messageLimitFlow.value = 100
    }

    private fun hideShimmeringView() {
        shimmerViewState.updateViewState(ShimmerViewState.Off)
    }

    private val _clockIconState = MutableStateFlow(false)
    val clockIconState: StateFlow<Boolean> = _clockIconState

    private fun updateClockIconState(showClockIcon: Boolean) {
        _clockIconState.value = showClockIcon
    }

    private fun setupScrollDownButtonCount() {
        val unseenMessagesCount = scrollDownButtonCount.value ?: 0
        if (unseenMessagesCount > 0.toLong()) {
            scrollDownViewStateContainer.updateViewState(
                ScrollDownViewState.On(unseenMessagesCount.toString())
            )
        } else {
            scrollDownViewStateContainer.updateViewState(
                ScrollDownViewState.Off
            )
        }
    }

    private fun updateScrollDownButtonCount() {
        (scrollDownViewStateContainer.value as? ScrollDownViewState.On)?.let {
            val unseenMessagesCount = scrollDownButtonCount.value ?: 0

            scrollDownViewStateContainer.updateViewState(
                ScrollDownViewState.On(
                    if (unseenMessagesCount > 0) {
                        unseenMessagesCount.toString()
                    } else {
                        null
                    }
                )
            )
        }
    }

    abstract fun readMessages()

    abstract fun reloadPinnedMessage()

    abstract fun getThreadUUID(): ThreadUUID?

    abstract fun isThreadChat(): Boolean

    suspend fun createPaidMessageFile(text: String?): File? {
        if (text.isNullOrEmpty()) {
            return null
        }

        return try {
            val output = mediaCacheHandler.createPaidTextFile("txt")
            mediaCacheHandler.copyTo(text.byteInputStream(), output)
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Builds the [SendMessage] and returns it (or null if it was invalid),
     * then passes it off to the [MessageRepository] for processing.
     * */
    /**
     * Builds the [SendMessage] and returns it (or null if it was invalid),
     * then passes it off to the [MessageRepository] for processing.
     * */
    @CallSuper
    open suspend fun sendMessage(builder: SendMessage.Builder): SendMessage? {
        val msg = builder.build()

        msg.second?.let { validationError ->
            val errorMessageRes = when (validationError) {
                SendMessage.Builder.ValidationError.EMPTY_PRICE -> {
                    R.string.send_message_empty_price_error
                }
                SendMessage.Builder.ValidationError.EMPTY_CONTENT -> {
                    R.string.send_message_empty_content_error
                }
                SendMessage.Builder.ValidationError.EMPTY_DESTINATION -> {
                    R.string.send_message_empty_destination_error
                }
            }

            viewModelScope.launch(mainImmediate) {
                submitSideEffect(
                    ChatSideEffect.Notify(
                        app.getString(errorMessageRes)
                    )
                )
            }

        } ?: msg.first?.let { message ->
            messageRepository.sendMessage(message)

            joinCallIfNeeded(message)
//            trackMessage(message.text)
        }

        return msg.first
    }

    private fun joinCallIfNeeded(message: SendMessage) {
        if (message.isCall) {
            message.text
                ?.replaceFirst(CallLinkMessage.MESSAGE_PREFIX, "")
                ?.toCallLinkMessageOrNull(moshi)?.link?.let { link ->
                    joinCall(link, link.startAudioOnly)
            }
        }
    }

//    private fun trackMessage(text: String?) {
//        viewModelScope.launch(io) {
//            if (text.isNullOrEmpty()) {
//                return@launch
//            }
//
//            val keywordList = extractKeywords(text)
//            keywordList?.let { list ->
//                actionsRepository.trackMessageContent(list)
//            }
//        }
//    }

//    private fun extractKeywords(text: String): List<String>? {
//        val pyObj = python.getModule("keyword_extractor")
//        val obj = pyObj.callAttr("extract_keywords", text)
//
//        val keywords = obj.asList().map {
//            it.toString().substringAfter("(\'").substringBefore("',")
//        }
//
//        return keywords.take(5)
//    }


    /**
     * Remotely and locally Deletes a [Message] through the [MessageRepository]
     */
    /**
     * Remotely and locally Deletes a [Message] through the [MessageRepository]
     */
    open fun deleteMessage(message: Message) {
        val sideEffect = ChatSideEffect.AlertConfirmDeleteMessage {
            viewModelScope.launch(mainImmediate) {
                messageRepository.deleteMessage(message)
            }
        }

        viewModelScope.launch(mainImmediate) {
            submitSideEffect(sideEffect)
        }
    }

    private var toggleChatMutedJob: Job? = null
    fun toggleChatMuted() {
        chatSharedFlow.replayCache.firstOrNull()?.let { chat ->

            if (chat.isTribe()) {
                navigateToNotificationLevel()
                return@let
            }

            if (toggleChatMutedJob?.isActive == true) {
                return
            }

            toggleChatMutedJob = viewModelScope.launch(default) {

                submitSideEffect(ChatSideEffect.ProduceHapticFeedback)

                val newLevel =
                    if (chat.notify?.isMuteChat() == true) NotificationLevel.SeeAll else NotificationLevel.MuteChat
                val response = chatRepository.setNotificationLevel(chat, newLevel)

                @Exhaustive
                when (response) {
                    is Response.Error -> {
                        withContext(mainImmediate) {
                            submitSideEffect(
                                ChatSideEffect.Notify(response.message)
                            )
                        }
                        delay(500)
                    }
                    is Response.Success -> {
                        if (response.value) {
                            withContext(mainImmediate) {
                                submitSideEffect(
                                    ChatSideEffect.Notify(
                                        app.getString(R.string.chat_muted_message)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun getPubKeyByEncryptedChild(child: String): ChatId? {
        return connectManagerRepository.getChatIdByEncryptedChild(child).firstOrNull()
    }

    abstract fun navigateToNotificationLevel()

    private var messagesSearchJob: Job? = null
    fun searchMessages(
        text: String?,
        index: Int? = null
    ) {
        moreOptionsMenuHandler.updateViewState(
            MenuBottomViewState.Closed
        )

        chatId?.let { nnChatId ->
            text?.let { nnText ->
                if (nnText.toCharArray().size > 2) {
                    messagesSearchViewStateContainer.updateViewState(
                        MessagesSearchViewState.Searching(
                            true,
                            nnText,
                            false,
                            emptyList(),
                            index ?: 0,
                            false
                        )
                    )

                    messagesSearchJob?.cancel()
                    messagesSearchJob = viewModelScope.launch(io) {
                        delay(500L)

                        messageRepository.searchMessagesBy(nnChatId, nnText).firstOrNull()
                            ?.let { messages ->
                                messagesSearchViewStateContainer.updateViewState(
                                    MessagesSearchViewState.Searching(
                                        false,
                                        nnText,
                                        true,
                                        messages,
                                        index ?: 0,
                                        true
                                    )
                                )
                            }
                    }
                    return
                }
            }
        }

        messagesSearchViewStateContainer.updateViewState(
            MessagesSearchViewState.Searching(
                false,
                text,
                (text ?: "").isNotEmpty(),
                emptyList(),
                index ?: 0,
                true
            )
        )
    }

    fun cancelSearch() {
        messagesSearchViewStateContainer.updateViewState(
            MessagesSearchViewState.Cancel
        )
    }

    fun clearSearch() {
        messagesSearchViewStateContainer.updateViewState(
            MessagesSearchViewState.Clear
        )
    }

    fun navigateResults(
        advanceBy: Int
    ) {
        val searchViewState = messagesSearchViewStateContainer.viewStateFlow.value
        if (searchViewState is MessagesSearchViewState.Searching) {
            var didReachLimit = false

            if (advanceBy > 0) {
                didReachLimit = searchViewState.index + advanceBy == searchViewState.messages.size - 1

                if (didReachLimit) {
                    fetchMoreItems()
                }
            }

            messagesSearchViewStateContainer.updateViewState(
                MessagesSearchViewState.Searching(
                    didReachLimit,
                    searchViewState.term,
                    searchViewState.clearButtonVisible,
                    searchViewState.messages,
                    searchViewState.index + advanceBy,
                    advanceBy > 0
                )
            )
        }
    }

    private fun reloadSearchWithFetchedMsgs() {
        val searchViewState = messagesSearchViewStateContainer.viewStateFlow.value
        if (searchViewState is MessagesSearchViewState.Searching) {
            searchMessages(
                searchViewState.term,
                searchViewState.index
            )
        }
    }

    private fun reachEndOfResults() {
        val searchViewState = messagesSearchViewStateContainer.viewStateFlow.value
        if (searchViewState is MessagesSearchViewState.Searching) {
            messagesSearchViewStateContainer.updateViewState(
                MessagesSearchViewState.Searching(
                    false,
                    searchViewState.term,
                    searchViewState.clearButtonVisible,
                    searchViewState.messages,
                    searchViewState.index,
                    searchViewState.navigatingForward
                )
            )
        }
    }

    private val selectedMessageContainer: ViewStateContainer<SelectedMessageViewState> by lazy {
        ViewStateContainer(SelectedMessageViewState.None)
    }

    @JvmSynthetic
    internal fun getSelectedMessageViewStateFlow(): StateFlow<SelectedMessageViewState> =
        selectedMessageContainer.viewStateFlow

    @JvmSynthetic
    internal fun updateSelectedMessageViewState(selectedMessageViewState: SelectedMessageViewState?) {
        if (selectedMessageViewState == null) return

        selectedMessageContainer.updateViewState(selectedMessageViewState)
    }

    private val footerViewStateContainer: ViewStateContainer<FooterViewState> by lazy {
        ViewStateContainer(FooterViewState.Default)
    }

    @JvmSynthetic
    internal fun getFooterViewStateFlow(): StateFlow<FooterViewState> =
        footerViewStateContainer.viewStateFlow

    @JvmSynthetic
    internal fun updateFooterViewState(viewState: FooterViewState) {
        footerViewStateContainer.updateViewState(viewState)
    }

    private inner class AttachmentSendStateContainer :
        ViewStateContainer<AttachmentSendViewState>(AttachmentSendViewState.Idle) {
        override fun updateViewState(viewState: AttachmentSendViewState) {
            if (viewState is AttachmentSendViewState.Preview) {

                // Only delete the previous file in the event that a new pic is chosen
                // to send when one is currently being previewed.
                val current = viewStateFlow.value
                if (current is AttachmentSendViewState.Preview) {
                    if (current.file?.path != viewState.file?.path) {
                        try {
                            current.file?.delete()
                        } catch (e: Exception) {

                        }
                    }
                }
            } else if (viewState is AttachmentSendViewState.PreviewGiphy) {

                // Only delete the previous file in the event that a new pic is chosen
                // to send when one is currently being previewed.
                val current = viewStateFlow.value
                if (current is AttachmentSendViewState.Preview) {
                    try {
                        current.file?.delete()
                    } catch (e: Exception) {

                    }
                }
            }

            super.updateViewState(viewState)
        }
    }

    private val attachmentSendStateContainer: ViewStateContainer<AttachmentSendViewState> by lazy {
        AttachmentSendStateContainer()
    }

    @JvmSynthetic
    internal fun getAttachmentSendViewStateFlow(): StateFlow<AttachmentSendViewState> =
        attachmentSendStateContainer.viewStateFlow

    @JvmSynthetic
    internal fun updateAttachmentSendViewState(viewState: AttachmentSendViewState) {
        attachmentSendStateContainer.updateViewState(viewState)
    }

    @JvmSynthetic
    internal fun deleteUnsentAttachment(viewState: AttachmentSendViewState.Preview) {
        viewModelScope.launch(io) {
            try {
                viewState.file?.delete()
            } catch (e: Exception) {
            }
        }
    }

    private val attachmentFullscreenStateContainer: ViewStateContainer<AttachmentFullscreenViewState> by lazy {
        ViewStateContainer(AttachmentFullscreenViewState.Idle)
    }

    @JvmSynthetic
    internal fun getAttachmentFullscreenViewStateFlow(): StateFlow<AttachmentFullscreenViewState> =
        attachmentFullscreenStateContainer.viewStateFlow

    @JvmSynthetic
    internal fun updateAttachmentFullscreenViewState(viewState: AttachmentFullscreenViewState) {
        if (viewState is AttachmentFullscreenViewState.Idle) {
            val currentState = attachmentFullscreenStateContainer.viewStateFlow.value

            if (currentState is AttachmentFullscreenViewState.PdfFullScreen) {
                currentState.pdfRender.close()
            }
        }
        attachmentFullscreenStateContainer.updateViewState(viewState)
    }

    suspend fun handleCommonChatOnBackPressed() {
        val attachmentSendViewState = getAttachmentSendViewStateFlow().value
        val attachmentFullscreenViewState = getAttachmentFullscreenViewStateFlow().value
        val messageSearchViewState = messagesSearchViewStateContainer.value

        when {
            currentViewState is ChatMenuViewState.Open -> {
                updateViewState(ChatMenuViewState.Closed)
            }
            moreOptionsMenuHandler.value is MenuBottomViewState.Open -> {
                moreOptionsMenuHandler.updateViewState(MenuBottomViewState.Closed)
            }
            callMenuHandler.value is MenuBottomViewState.Open -> {
                callMenuHandler.updateViewState(MenuBottomViewState.Closed)
            }
            attachmentFullscreenViewState is AttachmentFullscreenViewState.ImageFullscreen -> {
                updateAttachmentFullscreenViewState(AttachmentFullscreenViewState.Idle)
            }
            attachmentSendViewState is AttachmentSendViewState.Preview -> {
                updateAttachmentSendViewState(AttachmentSendViewState.Idle)
                updateFooterViewState(FooterViewState.Default)
                deleteUnsentAttachment(attachmentSendViewState)
            }
            attachmentSendViewState is AttachmentSendViewState.PreviewGiphy -> {
                updateAttachmentSendViewState(AttachmentSendViewState.Idle)
                updateFooterViewState(FooterViewState.Default)
            }
            getSelectedMessageViewStateFlow().value is SelectedMessageViewState.SelectedMessage -> {
                updateSelectedMessageViewState(SelectedMessageViewState.None)
            }
            messageSearchViewState is MessagesSearchViewState.Searching -> {
                messagesSearchViewStateContainer.updateViewState(MessagesSearchViewState.Idle)
            }
            else -> {
                chatNavigator.popBackStack()
            }
        }
    }

    fun boostMessage(messageUUID: MessageUUID?) {
        if (messageUUID == null) return

        viewModelScope.launch(default) {
            val chat = getChat()
            val response = messageRepository.boostMessage(
                chatId = chat.id,
                pricePerMessage = chat.pricePerMessage ?: Sat(0),
                escrowAmount = chat.escrowAmount ?: Sat(0),
                messageUUID = messageUUID,
            )

            @Exhaustive
            when (response) {
                is Response.Error -> {
                    submitSideEffect(
                        ChatSideEffect.Notify(app.getString(R.string.notify_boost_failure))
                    )
                }
                is Response.Success -> {}
            }
        }
    }

    fun toggleThreadDescriptionExpanded() {
        val currentList = messageHolderViewStateFlow.value

        val newList = currentList.map { item ->
            if (item is MessageHolderViewState.ThreadHeader) {
                item.copy(isExpanded = !item.isExpanded)
            } else {
                item
            }
        }
        messageHolderViewStateFlow.value = newList
    }

    fun changeThreadHeaderState(isFullHeader: Boolean){
        if (isFullHeader) {
            (messageHolderViewStateFlow.value.getOrNull(0) as? MessageHolderViewState.ThreadHeader)?.let { viewState ->
                if (threadHeaderViewState.value is ThreadHeaderViewState.FullHeader) {
                    return
                }
                val isFullHeaderInitialized = (threadHeaderViewState.value as? ThreadHeaderViewState.BasicHeader)?.isFullHeaderInitialized ?: false

                val threadHeader = ThreadHeaderViewState.FullHeader(
                    viewState.messageSenderInfo(viewState.message!!),
                    viewState.timestamp,
                    viewState.bubbleMessage?.text,
                    viewState.bubbleMessage?.highlightedTexts,
                    viewState.bubbleImageAttachment,
                    viewState.bubbleVideoAttachment,
                    viewState.bubbleFileAttachment,
                    viewState.bubbleAudioAttachment,
                    !isFullHeaderInitialized // TRUE
                )
                threadHeaderViewState.updateViewState(threadHeader)
            }
        } else {
            if (threadHeaderViewState.value is ThreadHeaderViewState.BasicHeader) {
                return
            }
            val isFullHeaderInitialized = (threadHeaderViewState.value as? ThreadHeaderViewState.FullHeader)?.let {
                true
            } ?: false

            threadHeaderViewState.updateViewState(
                ThreadHeaderViewState.BasicHeader(
                    isFullHeaderInitialized
                )
            )
        }
    }

    fun copyMessageText(message: Message) {
        viewModelScope.launch(mainImmediate) {
            message.retrieveTextToShow()?.let { text ->
                submitSideEffect(
                    ChatSideEffect.CopyTextToClipboard(text)
                )
            }
        }
    }

    fun copyMessageLink(message: Message) {
        viewModelScope.launch(mainImmediate) {
            message.retrieveTextToShow()?.let { text ->
                val matcher = SphinxLinkify.SphinxPatterns.COPYABLE_LINKS.matcher(text)
                if (matcher.find()) {
                    submitSideEffect(
                        ChatSideEffect.CopyLinkToClipboard(matcher.group())
                    )
                } else {
                    submitSideEffect(
                        ChatSideEffect.Notify(app.getString(R.string.side_effect_no_link_to_copy))
                    )
                }
            }
        }
    }

    fun replyToMessage(message: Message?) {
        if (message != null) {
            viewModelScope.launch(default) {
                val chat = getChat()

                val senderAlias = when {
                    message.sender == chat.contactIds.firstOrNull() -> {
                        contactRepository.accountOwner.value?.alias?.value ?: ""
                    }
                    chat.type.isConversation() -> {
                        getChatInfo()?.first?.value ?: ""
                    }
                    else -> {
                        message.senderAlias?.value ?: ""
                    }
                }

                withContext(mainImmediate) {
                    messageReplyViewStateContainer.updateViewState(
                        MessageReplyViewState.ReplyingToMessage(
                            message,
                            senderAlias
                        )
                    )
                }
            }
        } else {
            viewModelScope.launch(mainImmediate) {
                messageReplyViewStateContainer.updateViewState(MessageReplyViewState.ReplyingDismissed)
            }
        }
    }

    fun resendMessage(message: Message) {
//        viewModelScope.launch(io) {
//            val chat = getChat()
//            messageRepository.resendMessage(message, chat)
//        }
    }

//    fun flagMessage(message: Message) {
//        val sideEffect = ChatSideEffect.AlertConfirmFlagMessage {
//            viewModelScope.launch(mainImmediate) {
//                val chat = getChat()
//                messageRepository.flagMessage(message, chat)
//            }
//        }
//
//        viewModelScope.launch(mainImmediate) {
//            submitSideEffect(sideEffect)
//        }
//    }

    @JvmSynthetic
    internal fun chatMenuOptionCamera() {
        viewModelScope.launch(mainImmediate) {
            val response = cameraCoordinator.submitRequest(CameraRequest)
            @Exhaustive
            when (response) {
                is Response.Error -> {}
                is Response.Success -> {
                    val ext = response.value.value.extension

                    val mediaType: MediaType = when (response.value) {
                        is CameraResponse.Image -> {
                            MediaType.Image("${MediaType.IMAGE}/$ext")
                        }
                        is CameraResponse.Video -> {
                            MediaType.Video("${MediaType.VIDEO}/$ext")
                        }
                    }

                    updateViewState(ChatMenuViewState.Closed)

                    updateAttachmentSendViewState(
                        AttachmentSendViewState.Preview(response.value.value, mediaType, null, null)
                    )

                    updateFooterViewState(FooterViewState.Attachment)
                }
            }
        }
    }

    @JvmSynthetic
    internal fun chatMenuOptionMediaLibrary() {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(ChatSideEffect.RetrieveImageOrVideo)
        }
    }

    @JvmSynthetic
    internal fun chatMenuOptionGif(parentFragmentManager: FragmentManager) {
        if (BuildConfig.GIPHY_API_KEY != CONFIG_PLACE_HOLDER) {
            val settings = GPHSettings(GridType.waterfall, GPHTheme.Dark)
            settings.mediaTypeConfig =
                arrayOf(GPHContentType.gif, GPHContentType.sticker, GPHContentType.recents)

            val giphyDialogFragment =
                GiphyDialogFragment.newInstance(settings, BuildConfig.GIPHY_API_KEY)

            giphyDialogFragment.gifSelectionListener =
                object : GiphyDialogFragment.GifSelectionListener {
                    override fun didSearchTerm(term: String) {}

                    override fun onDismissed(selectedContentType: GPHContentType) {}

                    override fun onGifSelected(
                        media: Media,
                        searchTerm: String?,
                        selectedContentType: GPHContentType
                    ) {
                        updateViewState(ChatMenuViewState.Closed)
                        val giphyData = GiphyData(
                            media.id,
                            "https://media.giphy.com/media/${media.id}/giphy.gif",
                            media.aspectRatio.toDouble(),
                            null
                        )

                        updateAttachmentSendViewState(
                            AttachmentSendViewState.PreviewGiphy(giphyData)
                        )

                        updateFooterViewState(FooterViewState.Attachment)
                    }
                }
            giphyDialogFragment.show(parentFragmentManager, "giphy_search")
        } else {
            viewModelScope.launch(mainImmediate) {
                submitSideEffect(
                    ChatSideEffect.Notify("Giphy search not available")
                )
            }
        }
    }

    @JvmSynthetic
    internal val onIMEContent =
        InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
            val lacksPermission =
                (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && lacksPermission) {
                try {
                    inputContentInfo.requestPermission()
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "Failed to get content from IME", e)

                    viewModelScope.launch(mainImmediate) {
                        submitSideEffect(
                            ChatSideEffect.Notify("Require permission for this content")
                        )
                    }
                    return@OnCommitContentListener false
                }
            }
            handleActivityResultUri(inputContentInfo.contentUri)
            inputContentInfo.releasePermission()
            true
        }

    @JvmSynthetic
    internal fun chatMenuOptionFileLibrary() {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(
                ChatSideEffect.RetrieveFile
            )
        }
    }

    @JvmSynthetic
    internal fun chatMenuOptionPaidMessage() {
        updateAttachmentSendViewState(
            AttachmentSendViewState.Preview(null, MediaType.Text, null, null)
        )
        updateViewState(ChatMenuViewState.Closed)
        updateFooterViewState(FooterViewState.Attachment)
    }

    @JvmSynthetic
    internal fun chatMenuOptionPaymentRequest() {
        contactId?.let { id ->
            viewModelScope.launch(mainImmediate) {
                audioPlayerController.pauseMediaIfPlaying()
                chatNavigator.toPaymentReceiveDetail(id, chatId)
            }
            updateViewState(ChatMenuViewState.Closed)
        }
    }

    @JvmSynthetic
    internal fun chatMenuOptionPaymentSend() {
        contactId?.let { id ->
            viewModelScope.launch(mainImmediate) {
                audioPlayerController.pauseMediaIfPlaying()
                chatNavigator.toPaymentSendDetail(id, chatId)
            }
            updateViewState(ChatMenuViewState.Closed)
        }
    }

    private fun openCamera() {
        viewModelScope.launch(mainImmediate) {
            val response = cameraCoordinator.submitRequest(CameraRequest)
            @Exhaustive
            when (response) {
                is Response.Error -> {}
                is Response.Success -> {

                    val ext = response.value.value.extension

                    val mediaType: MediaType = when (response.value) {
                        is CameraResponse.Image -> {
                            MediaType.Image("${MediaType.IMAGE}/$ext")
                        }
                        is CameraResponse.Video -> {
                            MediaType.Image("${MediaType.VIDEO}/$ext")
                        }
                    }

                    updateAttachmentSendViewState(
                        AttachmentSendViewState.Preview(response.value.value, mediaType, null, null)
                    )

                    updateFooterViewState(FooterViewState.Attachment)
                }
            }
        }
    }

    fun handleActivityResultUri(uri: Uri?) {
        if (uri == null) {
            return
        }

        val cr = app.contentResolver

        cr.getType(uri)?.let { crType ->

            MimeTypeMap.getSingleton().getExtensionFromMimeType(crType)?.let { ext ->

                val stream: InputStream = try {
                    cr.openInputStream(uri) ?: return
                } catch (e: Exception) {
                    return
                }

                crType.toMediaType().let { mType ->

                    val newFile: File? = when (mType) {
                        is MediaType.Image -> {
                            mediaCacheHandler.createImageFile(ext)
                        }
                        is MediaType.Video -> {
                            mediaCacheHandler.createVideoFile(ext)
                        }
                        is MediaType.Pdf -> {
                            mediaCacheHandler.createPdfFile(ext)
                        }
                        is MediaType.Text,
                        is MediaType.Unknown -> {
                            mediaCacheHandler.createFile(mType, ext)
                        }
                        else -> {
                            null
                        }
                    }

                    newFile?.let { nnNewFile ->

                        val fileName: String? =
                            cr.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                cursor.moveToFirst()
                                cursor.getString(nameIndex)
                            } ?: null

                        viewModelScope.launch(mainImmediate) {
                            try {
                                mediaCacheHandler.copyTo(stream, nnNewFile)
                                updateViewState(ChatMenuViewState.Closed)
                                updateFooterViewState(FooterViewState.Attachment)

                                attachmentSendStateContainer.updateViewState(
                                    AttachmentSendViewState.Preview(
                                        nnNewFile,
                                        mType,
                                        fileName?.toFileName(),
                                        null
                                    )
                                )
                            } catch (e: Exception) {
                                nnNewFile.delete()
                                LOG.e(
                                    TAG,
                                    "Failed to copy content to new file: ${nnNewFile.path}",
                                    e
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun confirmToggleBlockContactState() {
        val alertConfirmCallback: () -> Unit = {
            contactId?.let { contactId ->
                viewModelScope.launch(mainImmediate) {
                    contactRepository.getContactById(contactId).firstOrNull()?.let { contact ->
                        contactRepository.toggleContactBlocked(contact)
                    }
                }
            }
        }

        submitSideEffect(
            ChatSideEffect.AlertConfirmBlockContact {
                alertConfirmCallback().also {
                    viewModelScope.launch(mainImmediate) {
                        chatNavigator.popBackStack()
                    }
                }
            }
        )

    }

    suspend fun confirmDeleteContact() {
        val alertConfirmDeleteContact: () -> Unit = {
            contactId?.let { contactId ->
                viewModelScope.launch(mainImmediate) {
                    contactRepository.deleteContactById(contactId)
                }
            }
        }

        submitSideEffect(
            ChatSideEffect.AlertConfirmDeleteContact {
                alertConfirmDeleteContact().also {
                    viewModelScope.launch(mainImmediate) {
                        chatNavigator.popBackStack()
                    }
                }
            }
        )
    }

    fun sendCallInvite(audioOnly: Boolean) {
        callMenuHandler.updateViewState(
            MenuBottomViewState.Closed
        )

        val appContext: Context = app.applicationContext
        val serverUrlsSharedPreferences =
            appContext.getSharedPreferences("server_urls", Context.MODE_PRIVATE)

        val meetingServerUrl = serverUrlsSharedPreferences.getString(
            SphinxCallLink.CALL_SERVER_URL_KEY,
            SphinxCallLink.DEFAULT_CALL_SERVER_URL
        ) ?: SphinxCallLink.DEFAULT_CALL_SERVER_URL

        viewModelScope.launch(mainImmediate) {
            val messageText = SphinxCallLink.newCallLinkMessage(meetingServerUrl, audioOnly, moshi)

            messageText?.let { newCallLink ->
                val messageBuilder = SendMessage.Builder()
                messageBuilder.setText(newCallLink)
                messageBuilder.setIsCall(true)
                sendMessage(messageBuilder)
            }
        }
    }

    fun copyCallLink(message: Message) {
        message.retrieveSphinxCallLink()?.let { callLink ->
            viewModelScope.launch(mainImmediate) {
                submitSideEffect(
                    ChatSideEffect.CopyCallLinkToClipboard(callLink.value)
                )
            }
        }
    }

    fun joinCall(message: Message, audioOnly: Boolean) {
        message.retrieveSphinxCallLink()?.let { sphinxCallLink ->
            joinCall(sphinxCallLink, audioOnly)
        }
    }

    private fun joinCall(link: SphinxCallLink, audioOnly: Boolean) {
        if (link.isLiveKitLink) {
            viewModelScope.launch(io) {
                val owner = getOwner()

                networkQueryPeople.getLiveKitToken(
                    room = link.callRoom,
                    alias = owner.alias?.value ?: "Unknown",
                    profilePictureUrl = owner.photoUrl?.value
                ).collect { loadResponse ->
                    when(loadResponse) {
                        is LoadResponse.Loading -> {}
                        is Response.Error -> {}
                        is Response.Success -> {
                            withContext(mainImmediate) {
                                val appContext: Context = app.applicationContext

                                val intent = Intent(appContext, CallActivity::class.java).apply {
                                    putExtra(
                                        CallActivity.KEY_ARGS,
                                        CallActivity.BundleArgs(
                                            url = loadResponse.value.serverUrl,
                                            token = loadResponse.value.participantToken,
                                            e2eeOn = false,
                                            e2eeKey = "",
                                            stressTest = StressTest.None,
                                            videoEnabled = !audioOnly,
                                            roomName = loadResponse.value.roomName
                                        ),
                                    )
                                }
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                                appContext.startActivity(intent)
                            }
                        }
                    }
                }
            }
        } else if (link.isJitsiLink) {
            link.callServerUrl?.let { nnCallUrl ->

                viewModelScope.launch(mainImmediate) {

                    val owner = getOwner()

                    val userInfo = JitsiMeetUserInfo()
                    userInfo.displayName = owner.alias?.value ?: ""

                    owner.avatarUrl?.let { nnAvatarUrl ->
                        userInfo.avatar = nnAvatarUrl
                    }

                    val options = JitsiMeetConferenceOptions.Builder()
                        .setServerURL(nnCallUrl)
                        .setRoom(link.callRoom)
                        .setAudioMuted(false)
                        .setVideoMuted(false)
                        .setFeatureFlag("welcomepage.enabled", false)
                        .setAudioOnly(audioOnly)
                        .setUserInfo(userInfo)
                        .build()

                    JitsiMeetActivity.launch(app, options)
                }
            }
        } else {
            val url = link.value
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            app.applicationContext.startActivity(intent)
        }
    }

    internal val audioPlayerController: AudioPlayerController by lazy {
        AudioPlayerControllerImpl(
            app,
            viewModelScope,
            dispatchers,
            LOG,
        )
    }

    internal val audioRecorderController: AudioRecorderController<ARGS> by lazy {
        AudioRecorderController(
            viewModelScope = viewModelScope,
            mediaCacheHandler = mediaCacheHandler,
            updateDurationCallback = { duration ->
                updateFooterViewState(
                    FooterViewState.RecordingAudioAttachment(duration)
                )
            },
            dispatchers
        )
    }

    fun stopAndDeleteAudioRecording() {
        audioRecorderController.stopAndDeleteAudioRecording()
        updateFooterViewState(FooterViewState.Default)
    }

    fun goToChatDetailScreen() {
        audioPlayerController.pauseMediaIfPlaying()
        navigateToChatDetailScreen()
    }

    fun goToFullscreenVideo(
        messageId: MessageId,
        videoFilepath: String? = null
    ) {
        viewModelScope.launch(mainImmediate) {
            chatNavigator.toFullscreenVideo(
                messageId,
                videoFilepath
            )
        }
    }

    protected abstract fun navigateToChatDetailScreen()

    open fun handleContactTribeLinks(url: String?) {
        if (url != null) {

            viewModelScope.launch(io) {

                url.toLightningNodePubKey()?.let { lightningNodePubKey ->

                    handleContactLink(lightningNodePubKey, null)

                } ?: url.toVirtualLightningNodeAddress()?.let { virtualNodeAddress ->

                    virtualNodeAddress.getPubKey()?.let { lightningNodePubKey ->
                        handleContactLink(
                            lightningNodePubKey,
                            virtualNodeAddress.getRouteHint()
                        )
                    }

                } ?: url.toTribeJoinLink()?.let { tribeJoinLink ->

                    handleTribeLink(tribeJoinLink)

                } ?: url.toFeedItemLink()?.let { feedItemLink ->

                    handleFeedItemLink(feedItemLink)

                } ?: url.toSphinxCallLink()?.let { sphinxCallLink ->

                    joinCall(sphinxCallLink, sphinxCallLink.startAudioOnly)

                }
            }

        }
    }

    private suspend fun goToFeedDetailView(feed: Feed) {
        when {
            feed.isPodcast -> {
                chatNavigator.toPodcastPlayer(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.id,
                    feed.feedUrl
                )
            }
            feed.isVideo -> {
                chatNavigator.toVideoWatchScreen(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.id,
                    feed.feedUrl
                )
            }
            feed.isNewsletter -> {
                chatNavigator.toNewsletterDetail(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.feedUrl
                )
            }
        }
    }

    private fun handleFeedItemLink(link: FeedItemLink) {
        viewModelScope.launch(io) {
            feedRepository.getFeedForLink(link).firstOrNull()?.let { feed ->
                viewModelScope.launch(mainImmediate) {
                    goToFeedDetailView(feed)
                }
            }
        }
    }

    private fun handleTribeLink(tribeJoinLink: TribeJoinLink) {
        viewModelScope.launch(io) {
            chatRepository.getChatByUUID(ChatUUID(tribeJoinLink.tribePubkey)).firstOrNull()?.let { chat ->
                viewModelScope.launch(mainImmediate) {
                    chatNavigator.toChat(chat, null, null)
                }
            } ?: run {
                viewModelScope.launch(mainImmediate) {
                    chatNavigator.toJoinTribeDetail(tribeJoinLink).also {
                        audioPlayerController.pauseMediaIfPlaying()
                    }
                }
            }
        }
    }

    fun navigateToChatThread(uuid: MessageUUID) {
        viewModelScope.launch(mainImmediate) {
            chatNavigator.toChatThread(getChat().id, ThreadUUID(uuid.value))
        }
    }

    fun navigateToTribeFromThread() {
        viewModelScope.launch(mainImmediate) {
            chatNavigator.toChat(getChat(), null, null)
        }
    }

    private suspend fun handleContactLink(
        pubKey: LightningNodePubKey,
        routeHint: LightningRouteHint?
    ) {
        contactRepository.getContactByPubKey(pubKey).firstOrNull()?.let { contact ->
            chatRepository.getConversationByContactId(contact.id).firstOrNull().let { chat ->
                viewModelScope.launch(mainImmediate) {
                    chatNavigator.toChat(chat, contact.id, null)
                }
            }

        } ?: run {
            viewModelScope.launch(mainImmediate) {
                chatNavigator.toAddContactDetail(pubKey, routeHint).also {
                    audioPlayerController.pauseMediaIfPlaying()
                }
            }
        }
    }

    open suspend fun processMemberRequest(
        chatId: ChatId,
        messageUuid: MessageUUID,
        type: MessageType.GroupAction,
        senderAlias: SenderAlias?
    ) {
    }

    open suspend fun deleteTribe() {}

    open fun onSmallProfileImageClick(message: Message) {}

    open fun pinMessage(message: Message) {}

    open fun unPinMessage(message: Message? = null) {}

    override suspend fun onMotionSceneCompletion(value: Nothing) {
        // unused
    }

    fun saveFile(
        message: Message,
        drawable: Drawable?
    ) {
        viewModelScope.launch(mainImmediate) {
            if (message.isMediaAttachmentAvailable) {

                val originalMessageMessageMedia = message.messageMedia

                //Getting message media from purchase accept item if is paid.
                //LocalFile and mediaType should be returned from original message
                val mediaUrlAndMessageMedia = message.retrieveImageUrlAndMessageMedia()
                    ?: message.retrieveUrlAndMessageMedia()


                mediaUrlAndMessageMedia?.second?.let { messageMedia ->
                    originalMessageMessageMedia?.retrieveContentValues(message)
                        ?.let { mediaContentValues ->
                            originalMessageMessageMedia?.retrieveMediaStorageUri()
                                ?.let { mediaStorageUri ->
                                    app.contentResolver.insert(mediaStorageUri, mediaContentValues)
                                        ?.let { savedFileUri ->
                                            val inputStream: InputStream? = when {
                                                (drawable != null) -> {
                                                    drawable?.drawableToBitmap()?.toInputStream()
                                                }
                                                (originalMessageMessageMedia?.localFile != null) -> {
                                                    FileInputStream(originalMessageMessageMedia?.localFile)
                                                }
                                                else -> {
                                                    messageMedia.retrieveRemoteMediaInputStream(
                                                        mediaUrlAndMessageMedia.first,
                                                        memeServerTokenHandler,
                                                        memeInputStreamHandler
                                                    )
                                                }
                                            }

                                            try {
                                                inputStream?.use { nnInputStream ->
                                                    app.contentResolver.openOutputStream(
                                                        savedFileUri
                                                    ).use { savedFileOutputStream ->
                                                        if (savedFileOutputStream != null) {
                                                            nnInputStream.copyTo(
                                                                savedFileOutputStream,
                                                                1024
                                                            )

                                                            submitSideEffect(
                                                                ChatSideEffect.Notify(
                                                                    app.getString(
                                                                        R.string.saved_attachment_successfully
                                                                    )
                                                                )
                                                            )
                                                            return@launch
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                LOG.e(TAG, "Failed to store file: ", e)
                                            }

                                            submitSideEffect(
                                                ChatSideEffect.Notify(app.getString(R.string.failed_to_save_file))
                                            )
                                            try {
                                                app.contentResolver.delete(savedFileUri, null, null)
                                            } catch (fileE: Exception) {
                                                LOG.e(TAG, "Failed to delete file: ", fileE)
                                            }
                                        }
                                }
                        }
                }
            }
        }
    }

    fun showAttachmentImageFullscreen(message: Message) {
        message.retrieveImageUrlAndMessageMedia()?.let {
            updateAttachmentFullscreenViewState(
                AttachmentFullscreenViewState.ImageFullscreen(it.first, it.second)
            )
        }
    }

    fun navigateToPdfPage(pageDiff: Int) {
        val viewState = getAttachmentFullscreenViewStateFlow().value
        if (viewState is AttachmentFullscreenViewState.PdfFullScreen) {
            showAttachmentPdfFullscreen(null, viewState.currentPage + pageDiff)
        }
    }

    fun showAttachmentPdfFullscreen(
        message: Message?,
        page: Int
    ) {
        val fullscreenViewState = getAttachmentFullscreenViewStateFlow().value

        if (fullscreenViewState is AttachmentFullscreenViewState.PdfFullScreen) {
            updateAttachmentFullscreenViewState(
                AttachmentFullscreenViewState.PdfFullScreen(
                    fullscreenViewState.fileName,
                    fullscreenViewState.pdfRender.pageCount,
                    page,
                    fullscreenViewState.pdfRender
                )
            )
        } else {
            if (message?.messageMedia?.mediaType?.isPdf == true) {
                message.messageMedia?.localFile?.let { localFile ->

                    val pfd = ParcelFileDescriptor.open(localFile, MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)

                    updateAttachmentFullscreenViewState(
                        AttachmentFullscreenViewState.PdfFullScreen(
                            message.messageMedia?.fileName ?: FileName("File.txt"),
                            renderer.pageCount,
                            page,
                            renderer
                        )
                    )
                }
            }
        }
    }

    // TODO: Re-work to track messageID + job such that multiple paid messages can
    //  be fired at a time, but only once for that particular message until a response
    //  is had. Current implementation requires 1 Paid message confirmation to complete
    //  before allowing another one to be fired off.
    private var payAttachmentJob: Job? = null
    fun payAttachment(message: Message) {
        if (payAttachmentJob?.isActive == true) {
            return
        }

        val sideEffect = ChatSideEffect.AlertConfirmPayAttachment {
            payAttachmentJob = viewModelScope.launch(mainImmediate) {
                getAccountBalance().firstOrNull()?.let { balance ->
                    val price = message.messageMedia?.mediaToken?.getPriceFromMediaToken()?.value

                    if (price == null || price > balance.balance.value) {
                        submitSideEffect(
                            ChatSideEffect.Notify(app.getString(R_common.string.balance_too_low))
                        )
                    } else {
                        messageRepository.payAttachment(message)
                    }
                }
            }
        }

        viewModelScope.launch(mainImmediate) {
            submitSideEffect(sideEffect)
        }
    }

    private var payInvoiceJob: Job? = null
    fun payInvoice(message: Message) {
        if (payInvoiceJob?.isActive == true) {
            return
        }

        val sideEffect = ChatSideEffect.AlertConfirmPayInvoice {
            payInvoiceJob = viewModelScope.launch(mainImmediate) {
                getAccountBalance().firstOrNull()?.let { balance ->
                    if (message.amount.value > balance.balance.value) {
                        submitSideEffect(
                            ChatSideEffect.Notify(app.getString(R_common.string.balance_too_low))
                        )
                    } else {
                        connectManagerRepository.payContactPaymentRequest(message.paymentRequest)
                    }
                }
            }
        }

        viewModelScope.launch(mainImmediate) {
            submitSideEffect(sideEffect)
        }
    }

    abstract fun shouldProcessMemberMentions(s: CharSequence?)

    fun processMemberMention(s: CharSequence?) {
        val lastWord = s?.split(" ")?.last()?.toString() ?: ""

        if (lastWord.startsWith("@") && lastWord.length > 1) {
            val matchingMessages: MutableList<MessageHolderViewState> = mutableListOf();

            messageHolderViewStateFlow.value.forEach loop@{ messageHolder ->
                if (messageHolder is MessageHolderViewState.Sent) {
                    return@loop
                }
                messageHolder.message?.date?.let { nnDate ->
                    if (nnDate.before(DateTime.getThreeMonthsAgo())) {
                        return@loop
                    }
                }
                messageHolder.message?.senderAlias?.value?.let { alias ->
                    if (alias.startsWith(lastWord.replace("@", ""), true)) {
                        messageHolder.message.senderPic?.value?.let { picture ->
                            if (messageHolder.message.type.isGroupLeave()) {
                                val index = matchingMessages.indexOfFirst {
                                    it.message?.senderPic?.value == picture || it.message?.senderAlias?.value == alias
                                }
                                if (index != -1) {
                                    matchingMessages.removeAt(index)
                                }
                            }

                            val index = matchingMessages.indexOfFirst {
                                it.message?.senderAlias?.value == alias
                            }

                            if (index != -1) {
                                matchingMessages[index] = messageHolder
                            } else if (!matchingMessages.any { it.message?.senderPic?.value == picture || it.message?.senderAlias?.value == alias }) {
                                matchingMessages.add(messageHolder)
                            }
                        } ?: run {
                            if (messageHolder.message.type.isGroupLeave()) {
                                val index = matchingMessages.indexOfFirst {
                                    it.message?.senderAlias?.value == alias
                                }
                                if (index != -1) {
                                    matchingMessages.removeAt(index)
                                }
                            }
                            val index = matchingMessages.indexOfFirst {
                                it.message?.senderAlias?.value == alias
                            }

                            if (index != -1) {
                                matchingMessages[index] = messageHolder
                            } else {
                                matchingMessages.add(messageHolder)
                            }
                        }
                    }
                }
            }

            val matchingAliases = matchingMessages.map {
                Triple(
                    it.message?.senderAlias?.value ?: "",
                    it.message?.senderPic?.value ?: "",
                    it.message?.getColorKey() ?: ""
                )
            }

            messageMentionsViewStateContainer.updateViewState(
                MessageMentionsViewState.MessageMentions(matchingAliases)
            )

        } else {
            messageMentionsViewStateContainer.updateViewState(
                MessageMentionsViewState.MessageMentions(listOf())
            )
        }

    }

    fun sendAppLog(appLog: String) {
        actionsRepository.setAppLog(appLog)
    }

    override fun onCleared() {
        super.onCleared()

        chatId?.let {
            messageRepository.cleanupOldMessages(it)
        }

        (audioPlayerController as AudioPlayerControllerImpl).onCleared()
        audioRecorderController.clear()

        activeDownloadJobs.forEach { it.cancel() }
        activeDownloadJobs.clear()
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun MessageMedia.retrieveMediaStorageUri(): Uri? {
    return when {
        this.mediaType.isImage -> {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        this.mediaType.isVideo -> {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        this.mediaType.isAudio -> {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                null
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun MessageMedia.retrieveContentValues(message: Message): ContentValues? {
    val fileName = "${this.fileName?.value ?: message.id.value}"

    if (this.mediaType.isImage) {
        return ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, fileName)
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
    } else if (this.mediaType.isVideo) {
        return ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, fileName)
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
    } else if (this.mediaType.isPdf) {
        return ContentValues().apply {
            put(MediaStore.Downloads.TITLE, fileName)
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        }

    } else if (this.mediaType.isUnknown) {
        return ContentValues().apply {
            put(MediaStore.Downloads.TITLE, fileName)
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mediaType.value)
        }
    }
    return null
}


@Suppress("NOTHING_TO_INLINE")
suspend inline fun MessageMedia.retrieveRemoteMediaInputStream(
    url: String,
    memeServerTokenHandler: MemeServerTokenHandler,
    memeInputStreamHandler: MemeInputStreamHandler
): InputStream? {
    return localFile?.inputStream() ?: host?.let { mediaHost ->
        memeServerTokenHandler.retrieveAuthenticationToken(mediaHost)?.let { authenticationToken ->
            memeInputStreamHandler.retrieveMediaInputStream(
                url,
                authenticationToken,
                mediaKeyDecrypted
            )?.first
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Drawable.drawableToBitmap(): Bitmap? {
    return try {
        val bitDw = this as BitmapDrawable
        bitDw.bitmap
    } catch (e: Exception) {
        null
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Bitmap.toInputStream(): InputStream? {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 100, stream)
    val imageInByte: ByteArray = stream.toByteArray()
    return ByteArrayInputStream(imageInByte)
}

data class MessageDimensions(val context: Context) {
    val defaultMargin = context.resources.getDimensionPixelSize(chat.sphinx.resources.R.dimen.default_layout_margin)
    val defaultReceivedLeftMargin = context.resources.getDimensionPixelSize(R.dimen.message_holder_space_width_left)
    val defaultSentRightMargin = context.resources.getDimensionPixelSize(R.dimen.message_holder_space_width_right)
    val podcastBoostWidth = context.resources.getDimensionPixelSize(R.dimen.message_type_podcast_boost_width)
    val expiredInvoiceWidth = context.resources.getDimensionPixelSize(R.dimen.message_type_expired_invoice_width)
    val boostWidth = context.resources.getDimensionPixelSize(R.dimen.message_type_boost_width)
    val tribePaymentMargin = context.resources.getDimensionPixelSize(R.dimen.tribe_payment_row_margin)
    val paymentMargin = context.resources.getDimensionPixelSize(R.dimen.payment_row_margin)
}


