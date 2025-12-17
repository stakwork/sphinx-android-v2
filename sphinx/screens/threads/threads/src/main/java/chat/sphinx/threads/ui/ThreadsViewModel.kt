package chat.sphinx.threads.ui

import android.app.Application
import android.content.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.chat_common.util.AudioPlayerController
import chat.sphinx.chat_common.util.AudioPlayerControllerImpl
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_media.RepositoryMedia
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.resources.R as R_common
import chat.sphinx.threads.model.ThreadItemViewState
import chat.sphinx.threads.navigation.ThreadsNavigator
import chat.sphinx.threads.viewstate.ThreadsViewState
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.message.toMessageUUID
import chat.sphinx.wrapper_common.timeAgo
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.ThreadUUID
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ThreadsViewModel @Inject constructor(
    private val app: Application,
    val navigator: ThreadsNavigator,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    val memeServerTokenHandler: MemeServerTokenHandler,
    private val repositoryMedia: RepositoryMedia,
    dispatchers: CoroutineDispatchers,
    savedStateHandle: SavedStateHandle,
    protected val LOG: SphinxLogger,
): SideEffectViewModel<
        Context,
        ThreadsSideEffect,
        ThreadsViewState
        >(dispatchers, ThreadsViewState.Idle)
{
    private val args: ThreadsFragmentArgs by savedStateHandle.navArgs()

    private val activeDownloadJobs = mutableSetOf<Job>()

    private val _ownerStateFlow: MutableStateFlow<Contact?> by lazy {
        MutableStateFlow(null)
    }
    private val ownerStateFlow: StateFlow<Contact?>?
        get() = _ownerStateFlow.asStateFlow()

    private val chatSharedFlow: SharedFlow<Chat?> = flow {
        emitAll(chatRepository.getChatById(ChatId(args.argChatId)))
    }.distinctUntilChanged().shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(2_000),
        replay = 1,
    )

    private suspend fun getOwner(): Contact {
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
        } catch (e: Exception) {
        }
        delay(25L)

        return chat!!
    }

    internal val audioPlayerController: AudioPlayerController by lazy {
        AudioPlayerControllerImpl(
            app,
            viewModelScope,
            dispatchers,
            LOG,
        )
    }

    init {
        initializeOwner()
        updateThreads()
    }

    private fun initializeOwner() {
        viewModelScope.launch(mainImmediate) {
            _ownerStateFlow.value = getOwner()
        }
    }

    private fun updateThreads() {
        viewModelScope.launch(mainImmediate) {
            messageRepository.getThreadUUIDMessagesByChatId(ChatId(args.argChatId))
                .distinctUntilChanged()
                .flatMapLatest { threadMessages ->
                    val groupedMessagesByThread = threadMessages.groupBy { it.threadUUID }.filter {
                        it.value.size > 1
                    }

                    val uuids = groupedMessagesByThread.keys.mapNotNull { it?.value?.toMessageUUID() }

                    if (uuids.isEmpty()) {
                        flowOf(Triple(threadMessages, groupedMessagesByThread, emptyList()))
                    } else {
                        messageRepository.getAllMessagesByUUIDFlow(uuids)
                            .distinctUntilChanged()
                            .map { allMessages ->
                                Triple(threadMessages, groupedMessagesByThread, allMessages)
                            }
                    }
                }
                .flowOn(Dispatchers.IO)
                .collect { result ->
                    val threadItems = generateThreadItemsList(result.second, result.third)

                    if (threadItems.isNotEmpty()) {
                        updateViewState(ThreadsViewState.ThreadList(threadItems))
                    } else {
                        updateViewState(ThreadsViewState.NoThreadsFound)
                    }
                }
        }
    }

    private suspend fun generateThreadItemsList(
        groupedMessagesByThread: Map<ThreadUUID?, List<Message>>,
        headerMessages: List<Message>
    ): List<ThreadItemViewState> {
        val headerMessagesMappedByUUID = headerMessages.associateBy { it.uuid?.value }

        val memberTimezones: MutableMap<String, String> = mutableMapOf()

        headerMessages.forEach { message ->
            val alias = message.senderAlias?.value
            val tz = message.remoteTimezoneIdentifier?.value
            if (!alias.isNullOrEmpty() && !tz.isNullOrEmpty()) {
                memberTimezones[alias] = tz
            }
        }

        val completeThreads = groupedMessagesByThread.mapValues { entry ->
            val threadUUID = entry.key
            val threadMessages = entry.value

            val threadHeaderMessage = headerMessagesMappedByUUID[threadUUID?.value]

            if (threadHeaderMessage != null) {
                listOf(threadHeaderMessage) + threadMessages
            } else {
                threadMessages
            }
        }

        return completeThreads.keys.mapNotNull { uuid ->

            val owner = ownerStateFlow?.value
            val messagesForThread = completeThreads[uuid]

            messagesForThread?.get(0)?.let { message ->
                val chat = getChat()
                val isSenderOwner: Boolean = message.sender == chat.contactIds.firstOrNull()

                createThreadItem(
                    uuid?.value,
                    owner,
                    messagesForThread,
                    message,
                    memberTimezones,
                    chat,
                    isSenderOwner
                )
            }
        }
    }

    private fun createThreadItem(
        uuid: String?,
        owner: Contact?,
        messagesForThread: List<Message>?,
        originalMessage: Message,
        memberTimezones: MutableMap<String, String>,
        chat: Chat,
        isSenderOwner: Boolean
    ): ThreadItemViewState {

        val sent = originalMessage.sender == chat.contactIds.firstOrNull()
        val repliesList = messagesForThread?.drop(1)?.distinctBy { it.senderAlias } ?: emptyList()

        return ThreadItemViewState(
            message = originalMessage,
            threadMessages = messagesForThread ?: emptyList(),
            chat = chat,
            sent = sent,
            owner = owner,
            usersCount = repliesList?.size ?: 0,
            repliesAmount = String.format(app.getString(R_common.string.replies_amount), messagesForThread?.drop(1)?.size?.toString() ?: "0"),
            lastReplyDate = messagesForThread?.first()?.date?.timeAgo(),
            uuid = uuid ?: "",
            memberTimezoneIdentifier = if (originalMessage.senderAlias != null) memberTimezones[originalMessage.senderAlias!!.value] else null,
            onBindDownloadMedia = {
                val job = repositoryMedia.downloadMediaIfApplicable(originalMessage, isSenderOwner)
                activeDownloadJobs.add(job)

                job.invokeOnCompletion {
                    activeDownloadJobs.remove(job)
                }
            }
        )
    }

    fun navigateToThreadDetail(uuid: String) {
        viewModelScope.launch(mainImmediate) {
            navigator.toChatTribeThread(ChatId(args.argChatId), ThreadUUID(uuid))
        }
    }

}
