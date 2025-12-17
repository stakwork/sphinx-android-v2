package chat.sphinx.chat_contact.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.camera_view_model_coordinator.request.CameraRequest
import chat.sphinx.camera_view_model_coordinator.response.CameraResponse
import chat.sphinx.chat_common.ui.ChatViewModel
import chat.sphinx.chat_common.ui.viewstate.InitialHolderViewState
import chat.sphinx.chat_contact.navigation.ContactChatNavigator
import chat.sphinx.concept_link_preview.LinkPreviewHandler
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
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.resources.getRandomHexCode
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.ChatName
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.message.MessageUUID
import chat.sphinx.wrapper_common.util.getInitials
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_contact.ContactStatus
import chat.sphinx.wrapper_contact.getColorKey
import chat.sphinx.wrapper_contact.isPending
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.PodcastClip
import chat.sphinx.wrapper_message.ThreadUUID
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_media_cache.MediaCacheHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

internal inline val ChatContactFragmentArgs.chatId: ChatId?
    get() = if (argChatId == ChatId.NULL_CHAT_ID.toLong()) {
        null
    } else {
        ChatId(argChatId)
    }

internal inline val ChatContactFragmentArgs.contactId: ContactId
    get() = ContactId(argContactId)

@HiltViewModel
internal class ChatContactViewModel @Inject constructor(
    app: Application,
    dispatchers: CoroutineDispatchers,
    memeServerTokenHandler: MemeServerTokenHandler,
    contactChatNavigator: ContactChatNavigator,
    repositoryMedia: RepositoryMedia,
    feedRepository: FeedRepository,
    chatRepository: ChatRepository,
    contactRepository: ContactRepository,
    messageRepository: MessageRepository,
    actionsRepository: ActionsRepository,
    lightningRepository: LightningRepository,
    repositoryDashboard: RepositoryDashboardAndroid<Any>,
    networkQueryPeople: NetworkQueryPeople,
    mediaCacheHandler: MediaCacheHandler,
    savedStateHandle: SavedStateHandle,
    cameraViewModelCoordinator: ViewModelCoordinator<CameraRequest, CameraResponse>,
    linkPreviewHandler: LinkPreviewHandler,
    memeInputStreamHandler: MemeInputStreamHandler,
    connectManagerRepository: ConnectManagerRepository,
    moshi: Moshi,
    LOG: SphinxLogger,
) : ChatViewModel<ChatContactFragmentArgs>(
    app,
    dispatchers,
    memeServerTokenHandler,
    contactChatNavigator,
    repositoryMedia,
    feedRepository,
    chatRepository,
    contactRepository,
    messageRepository,
    actionsRepository,
    lightningRepository,
    repositoryDashboard,
    networkQueryPeople,
    mediaCacheHandler,
    savedStateHandle,
    cameraViewModelCoordinator,
    linkPreviewHandler,
    memeInputStreamHandler,
    connectManagerRepository,
    moshi,
    LOG,
) {
    private var isViewModelActive = true

    override val args: ChatContactFragmentArgs by savedStateHandle.navArgs()
    private var _chatId: ChatId? = args.chatId
    override val chatId: ChatId?
        get() = _chatId

    override val contactId: ContactId = args.contactId

    private val contactSharedFlow: SharedFlow<Contact?> = flow {
        emitAll(contactRepository.getContactById(contactId))
    }.distinctUntilChanged().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        replay = 1,
    )

    override val chatSharedFlow: SharedFlow<Chat?> = flow {
        chatId?.let { chatId ->
            emitAll(chatRepository.getChatById(chatId))
        } ?: chatRepository.getConversationByContactId(contactId).collect { chat ->
            if (isViewModelActive) {
                _chatId = chat?.id
                emit(chat)
            }
        }
    }.distinctUntilChanged().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        replay = 1
    )

    override val headerInitialHolderSharedFlow: SharedFlow<InitialHolderViewState> = flow {
        contactSharedFlow.collect { contact ->
            if (!isViewModelActive) return@collect

            if (contact != null) {
                contact.photoUrl?.let { photoUrl ->
                    emit(
                        InitialHolderViewState.Url(photoUrl)
                    )
                } ?: contact.alias?.let { alias ->
                    emit(
                        InitialHolderViewState.Initials(
                            alias.value.getInitials(),
                            contact.getColorKey(),
                            contact.status.isPending()
                        )
                    )
                } ?: emit(
                    InitialHolderViewState.None
                )
            }
        }
    }.distinctUntilChanged().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        replay = 1
    )

    init {
        viewModelScope.launch(dispatchers.io) {
            withTimeoutOrNull(1000) {
                contactSharedFlow.firstOrNull()
            }
        }
    }

    override suspend fun getChatInfo(): Triple<ChatName?, PhotoUrl?, String>? {
        if (!isViewModelActive) return null

        return contactSharedFlow.replayCache.firstOrNull()?.let { contact ->
            Triple(
                contact.alias?.value?.let { ChatName(it) },
                contact.photoUrl?.value?.let { PhotoUrl(it) },
                contact.getColorKey()
            )
        } ?: withTimeoutOrNull(1000) {
            contactSharedFlow.first { it != null }?.let { contact ->
                if (isViewModelActive) {
                    Triple(
                        contact.alias?.value?.let { ChatName(it) },
                        contact.photoUrl?.value?.let { PhotoUrl(it) },
                        contact.getColorKey()
                    )
                } else null
            }
        }
    }

    override val threadSharedFlow: SharedFlow<List<Message>>?
        get() = null

    override suspend fun shouldStreamSatsFor(podcastClip: PodcastClip, messageUUID: MessageUUID?) {
        TODO("Not yet implemented")
    }

    private var cachedInitialHolder: InitialHolderViewState? = null
    override suspend fun getInitialHolderViewStateForReceivedMessage(
        message: Message,
        owner: Contact
    ): InitialHolderViewState {
        if (!isViewModelActive) return InitialHolderViewState.None

        if (message.sender == owner.id) {
            owner.photoUrl?.let { photoUrl ->
                return InitialHolderViewState.Url(photoUrl)
            } ?: owner.alias?.let { alias ->
                return InitialHolderViewState.Initials(
                    alias.value.getInitials(),
                    owner.getColorKey()
                )
            }
        }

        cachedInitialHolder?.let {
            return it
        }

        if (isViewModelActive) {
            cachedInitialHolder = getHeaderInitialHolderOnce()
            return cachedInitialHolder ?: InitialHolderViewState.None
        }

        return InitialHolderViewState.None
    }

    private suspend fun getHeaderInitialHolderOnce(): InitialHolderViewState? {
        if (!isViewModelActive) return InitialHolderViewState.None

        headerInitialHolderSharedFlow.replayCache.firstOrNull()?.let { holder ->
            if (holder !is InitialHolderViewState.None) {
                return holder
            }
        }

        return withTimeoutOrNull(500) {
            headerInitialHolderSharedFlow.first { it !is InitialHolderViewState.None }
        } ?: InitialHolderViewState.None
    }

    override fun readMessages() {
        if (!isViewModelActive) return

        viewModelScope.launch(io) {
            if (!isViewModelActive) return@launch

            val idResolved: ChatId? = chatId ?: withTimeoutOrNull(500) {
                chatSharedFlow.first { it != null }?.id
            }

            if (idResolved != null && isViewModelActive) {
                messageRepository.readMessages(idResolved)
            }
        }
    }

    override suspend fun reloadPinnedMessage() {}

    override fun getThreadUUID(): ThreadUUID? {
        return null
    }

    override fun isThreadChat(): Boolean {
        return false
    }

    override suspend fun sendMessage(builder: SendMessage.Builder): SendMessage? {

        builder.setContactId(contactId)
        builder.setChatId(chatId)

        return super.sendMessage(builder)
    }

    override fun navigateToChatDetailScreen() {
        viewModelScope.launch(mainImmediate) {
            (chatNavigator as ContactChatNavigator).toEditContactDetail(contactId)
        }
    }

    override fun navigateToNotificationLevel() {}

    override fun onSmallProfileImageClick(message: Message) {
        navigateToChatDetailScreen()
    }

    override fun shouldProcessMemberMentions(s: CharSequence?) {
        // Nothing to do. Only implemented on Tribes
    }

    override fun onCleared() {
        isViewModelActive = false
        cachedInitialHolder = null

        super.onCleared()
    }
}
