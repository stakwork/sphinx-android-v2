package chat.sphinx.dashboard.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.navigation.DashboardNavDrawerNavigator
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.dashboard.ui.adapter.DashboardChat
import chat.sphinx.dashboard.ui.viewstates.*
import chat.sphinx.kotlin_response.Response
import chat.sphinx.tribes_discover_view_model_coordinator.request.TribesDiscoverRequest
import chat.sphinx.tribes_discover_view_model_coordinator.response.TribesDiscoverResponse
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.ChatStatus
import chat.sphinx.wrapper_chat.ChatType
import chat.sphinx.wrapper_chat.isConversation
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.isTrue
import chat.sphinx.wrapper_common.time
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import chat.sphinx.wrapper_common.tribe.toTribeJoinLink
import chat.sphinx.wrapper_contact.*
import chat.sphinx.wrapper_invite.Invite
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_message.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.SideEffectViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import io.matthewnelson.concept_views.viewstate.collect
import io.matthewnelson.concept_views.viewstate.value
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.collections.ArrayList


internal suspend inline fun ChatListViewModel.collectChatViewState(
    crossinline action: suspend (value: ChatViewState) -> Unit
): Unit =
    chatViewStateContainer.collect { action(it) }

internal val ChatListViewModel.currentChatViewState: ChatViewState
    get() = chatViewStateContainer.value

internal suspend inline fun ChatListViewModel.updateChatListFilter(filter: ChatFilter) {
    chatViewStateContainer.updateDashboardChats(null, filter)
}

internal inline val ChatListFragmentArgs.isChatListTypeConversation: Boolean
    get() = argChatListType == ChatType.CONVERSATION

@HiltViewModel
internal class ChatListViewModel @Inject constructor(
    private val app: Application,
    handler: SavedStateHandle,
    private val accountOwner: StateFlow<Contact?>,
    val dashboardNavigator: DashboardNavigator,
    val navDrawerNavigator: DashboardNavDrawerNavigator,
    dispatchers: CoroutineDispatchers,
    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val contactRepository: ContactRepository,
    private val connectManagerRepository: ConnectManagerRepository,
    private val tribesDiscoverCoordinator: ViewModelCoordinator<TribesDiscoverRequest, TribesDiscoverResponse>,
): SideEffectViewModel<
        Context,
        ChatListSideEffect,
        ChatListViewState
        >(dispatchers, ChatListViewState.Idle)
{

    private val args: ChatListFragmentArgs by handler.navArgs()

    val chatListFooterButtonsViewStateContainer: ViewStateContainer<ChatListFooterButtonsViewState> by lazy {
        ViewStateContainer(ChatListFooterButtonsViewState.Idle)
    }

    val chatViewStateContainer: ChatViewStateContainer by lazy {
        ChatViewStateContainer(dispatchers)
    }

    private val _accountOwnerStateFlow: MutableStateFlow<Contact?> by lazy {
        MutableStateFlow(null)
    }

    private val accountOwnerStateFlow: StateFlow<Contact?>
        get() = _accountOwnerStateFlow.asStateFlow()

    private suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        repositoryDashboard.getAccountBalance()

    private val _contactsStateFlow: MutableStateFlow<List<Contact>> by lazy {
        MutableStateFlow(emptyList())
    }


    private val _hasSingleContact: MutableStateFlow<Boolean?> by lazy {
        MutableStateFlow(null)
    }
    val hasSingleContact: StateFlow<Boolean?>
        get() = _hasSingleContact.asStateFlow()

    private val collectionLock = Mutex()

    private var contactsCollectionInitialized: Boolean = false
    private var chatsCollectionInitialized: Boolean = false

    private var hasLoadedOnce = false

    fun markAsLoaded() {
        hasLoadedOnce = true
    }

    fun isFirstLoad(): Boolean = !hasLoadedOnce

    val networkStatusStateFlow: StateFlow<NetworkStatus>
        get() = connectManagerRepository.networkStatus.asStateFlow()

    init {
        if (args.isChatListTypeConversation) {
            viewModelScope.launch(mainImmediate) {
                repositoryDashboard.getAllNotBlockedContacts.distinctUntilChanged().collect { contacts ->

                    val unseenMessages = repositoryDashboard.getUnseenReceivedMessages().firstOrNull()
                    val unseenMessagesByChatId: Map<ChatId, List<Message>> = unseenMessages?.groupBy { it.chatId } ?: mapOf()

                    updateChatListContacts(
                        contacts,
                        unseenMessagesByChatId
                    )
                }
            }
        }

        viewModelScope.launch(mainImmediate) {
            delay(25L)

            var allChats = when (args.argChatListType) {
                ChatType.CONVERSATION -> {
                    repositoryDashboard.getAllContactChats.distinctUntilChanged()
                }
                ChatType.TRIBE -> {
                    repositoryDashboard.getAllTribeChats.distinctUntilChanged()
                }
                else -> {
                    repositoryDashboard.getAllChats.distinctUntilChanged()
                }
            }

            allChats.collect { chats ->
                val unseenMessages = repositoryDashboard.getUnseenReceivedMessages().firstOrNull()
                val unseenMessagesByChatId: Map<ChatId, List<Message>> = unseenMessages?.groupBy { it.chatId } ?: mapOf()

                val unseenMentions = repositoryDashboard.getUnseenReceivedMentions().firstOrNull()
                val unseenMentionsByChatId: Map<ChatId, List<Message>> = unseenMentions?.groupBy { it.chatId } ?: mapOf()

                chatsCollectionInitialized = true
                val newList = ArrayList<DashboardChat>(chats.size)
                val contactsAdded = mutableListOf<ContactId>()

                val messageIds = chats.mapNotNull { it.latestMessageId }
                val contactIds = chats.mapNotNull { it.contactIds.lastOrNull() }

                val messagesMap = messageRepository.getMessagesByIds(messageIds).first().associateBy { it?.id }
                val contactsMap = contactRepository.getAllContactsByIds(contactIds).associateBy { it.id }

                val inviteIds = contactsMap.mapNotNull { it.value.inviteId }
                val invitesMap = contactRepository.getInvitesByIds(inviteIds).first().associateBy { it?.id }

                withContext(mainImmediate) {
                    for (chat in chats) {
                        val message: Message? = chat.latestMessageId?.let {
                            messagesMap[it]
                        }

                        val chatUnseenMessages = if (!chat.seen.isTrue()) unseenMessagesByChatId[chat.id] else emptyList()
                        val chatUnseenMentions = if (!chat.seen.isTrue()) unseenMentionsByChatId[chat.id] else emptyList()

                        if (chat.type.isConversation()) {
                            val contactId: ContactId = chat.contactIds.lastOrNull() ?: continue

                            val contact: Contact = contactsMap[contactId] ?: continue

                            if (contact.status is ContactStatus.Pending) {
                                if (contact.isInviteContact()) {
                                    var contactInvite: Invite? = null

                                    contact.inviteId?.let { inviteId ->
                                        contactInvite = invitesMap[inviteId]
                                    }
                                    if (contactInvite != null) {
                                        newList.add(
                                            DashboardChat.Inactive.Invite(contact, contactInvite, Long.MAX_VALUE)
                                        )
                                    }
                                } else {
                                    newList.add(
                                        DashboardChat.Inactive.Conversation(contact, contact.createdAt.time)
                                    )
                                }
                            }

                            if (!contact.isBlocked() && chat.status is ChatStatus.Approved) {
                                contactsAdded.add(contactId)

                                newList.add(
                                    DashboardChat.Active.Conversation(
                                        chat,
                                        message,
                                        contact,
                                        chatUnseenMessages?.size ?: 0,
                                        chat.contentSeenAt?.time ?: message?.date?.time ?: chat.createdAt.time
                                    )
                                )
                            }
                        } else {
                            newList.add(
                                DashboardChat.Active.GroupOrTribe(
                                    chat,
                                    message,
                                    accountOwnerStateFlow.value ?: getOwner(),
                                    chatUnseenMessages?.size ?: 0,
                                    chatUnseenMentions?.size ?: 0,
                                    chat.contentSeenAt?.time ?: message?.date?.time ?: chat.createdAt.time
                                )
                            )
                        }
                    }
                }
                collectionLock.withLock {
                    chatViewStateContainer.updateDashboardChats(newList)
                }
            }
        }

        viewModelScope.launch(mainImmediate) {
            chatListFooterButtonsViewStateContainer.updateViewState(
                ChatListFooterButtonsViewState.ButtonsVisibility(
                    addFriendVisible = args.argChatListType == ChatType.CONVERSATION,
                    createTribeVisible = args.argChatListType == ChatType.TRIBE,
                    discoverTribesVisible = args.argChatListType == ChatType.TRIBE,
                )
            )
        }
    }

    fun navigateToChatContact(
        contactId: ContactId,
        chatId: ChatId?
    ) {
        viewModelScope.launch(mainImmediate) {
            dashboardNavigator.toChatContact(
                chatId,
                contactId
            )
        }
    }

    fun navigateToChatTribe(
        chatId: ChatId
    ) {
        if (networkStatusStateFlow.value is NetworkStatus.Loading) {
            return
        }

        viewModelScope.launch(mainImmediate) {
            dashboardNavigator.toChatTribe(chatId)
        }
    }

    private suspend fun getOwner(): Contact {
        return accountOwner.value.let { contact ->
            if (contact != null) {
                contact
            } else {
                var resolvedOwner: Contact? = null
                try {
                    accountOwner.collect { ownerContact ->
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

    private suspend fun updateChatListContacts(
        contacts: List<Contact>,
        unseenMessagesByChatId: Map<ChatId, List<Message>>
    ) {
        collectionLock.withLock {
            contactsCollectionInitialized = true

            if (contacts.isEmpty()) {
                return@withLock
            }

            if (contacts.size == 1 && contacts.first().isOwner.isTrue()) {
                _hasSingleContact.value = true
            }

            val newList = ArrayList<Contact>(contacts.size)
            val contactIds = ArrayList<ContactId>(contacts.size)

            withContext(default) {
                for (contact in contacts) {
                    if (contact.isOwner.isTrue()) {
                        _accountOwnerStateFlow.value = contact
                        continue
                    }

                    contactIds.add(contact.id)
                    newList.add(contact)
                }
            }

            _contactsStateFlow.value = newList.toList()

            // Don't push update to chat view state, let it's collection do it.
            if (!chatsCollectionInitialized) {
                return@withLock
            }

            withContext(default) {
                val currentChats = currentChatViewState.originalList.toMutableList()
                val chatContactIds = mutableListOf<ContactId>()

                var updateChatViewState = false
                for (chat in currentChatViewState.originalList) {

                    val contact: Contact? = when (chat) {
                        is DashboardChat.Active.Conversation -> {
                            chat.contact
                        }
                        is DashboardChat.Active.GroupOrTribe -> {
                            null
                        }
                        is DashboardChat.Inactive.Conversation -> {
                            chat.contact
                        }
                        is DashboardChat.Inactive.Invite -> {
                            chat.contact
                        }
                    }

                    contact?.let {
                        chatContactIds.add(it.id)
                        // if the id of the currently displayed chat is not contained
                        // in the list collected here, it's either a new contact w/o
                        // a chat, or a contact that was deleted which we need to remove
                        // from the list of chats.

                        if (!contactIds.contains(it.id)) {
                            //Contact deleted
                            updateChatViewState = true
                            currentChats.remove(chat)
                            chatContactIds.remove(it.id)
                        }

                        if (repositoryDashboard.updatedContactIds.contains(it.id)) {
                            //Contact updated
                            currentChats.remove(chat)
                            chatContactIds.remove(it.id)
                        }
                    }
                }

                for (contact in _contactsStateFlow.value) {
                    //Contact added
                    if (!chatContactIds.contains(contact.id)) {
                        updateChatViewState = true

                        if (contact.isInviteContact()) {
                            var contactInvite: Invite? = null

                            contact.inviteId?.let { inviteId ->
                                contactInvite = withContext(io) {
                                    repositoryDashboard.getInviteById(inviteId).firstOrNull()
                                }
                            }
                            if (contactInvite != null) {
                                currentChats.add(
                                    DashboardChat.Inactive.Invite(contact, contactInvite, Long.MAX_VALUE)
                                )
                                continue
                            }
                        }

                        var updatedContactChat: DashboardChat = DashboardChat.Inactive.Conversation(contact, contact.createdAt.time)

                        for (chat in currentChatViewState.originalList) {
                            if (chat is DashboardChat.Active.Conversation) {
                                if (chat.contact.id == contact.id) {

                                    val chatUnseenMessages = if (!chat.chat.seen.isTrue()) unseenMessagesByChatId[chat.chat.id] else emptyList()

                                    updatedContactChat = DashboardChat.Active.Conversation(
                                        chat.chat,
                                        chat.message,
                                        contact,
                                        chatUnseenMessages?.size ?: 0,
                                        chat.chat.contentSeenAt?.time ?: chat.message?.date?.time ?: chat.chat.createdAt.time
                                    )
                                }
                            }
                        }

                        if (updatedContactChat is DashboardChat.Inactive.Conversation) {
                            //Contact unblocked
                            repositoryDashboard.getConversationByContactId(contact.id).firstOrNull()?.let { contactChat ->
                                val message: Message? = contactChat.latestMessageId?.let {
                                    repositoryDashboard.getMessageById(it).firstOrNull()
                                }

                                val chatUnseenMessages = if (!contactChat.seen.isTrue()) unseenMessagesByChatId[contactChat.id] else emptyList()

                                updatedContactChat = DashboardChat.Active.Conversation(
                                    contactChat,
                                    message,
                                    contact,
                                    chatUnseenMessages?.size ?: 0,
                                    contactChat.contentSeenAt?.time ?: message?.date?.time ?: contactChat.createdAt.time
                                )
                            }
                        }

                        currentChats.add(updatedContactChat)
                    }
                }

                if (updateChatViewState) {
                    chatViewStateContainer.updateDashboardChats(currentChats.toList())
                    repositoryDashboard.updatedContactIds = mutableListOf()
                }
            }
        }
    }

    suspend fun payForInvite(invite: Invite) {
        getAccountBalance().firstOrNull()?.let { balance ->
            if (balance.balance.value < (invite.price?.value ?: 0)) {
                submitSideEffect(
                    ChatListSideEffect.Notify(app.getString(R.string.pay_invite_balance_too_low))
                )
                return
            }
        }

        submitSideEffect(
            ChatListSideEffect.AlertConfirmPayInvite(invite.price?.value ?: 0) {
                viewModelScope.launch(mainImmediate) {
                    repositoryDashboard.payForInvite(invite)
                }
            }
        )
    }

    suspend fun deleteInvite(invite: Invite) {
        submitSideEffect(
            ChatListSideEffect.AlertConfirmDeleteInvite() {
                viewModelScope.launch(mainImmediate) {
                    repositoryDashboard.deleteInviteAndContact(invite.inviteString.value)
                }
            }
        )
    }

    fun toTribesDiscover() {
        viewModelScope.launch(mainImmediate) {
            val response = tribesDiscoverCoordinator.submitRequest(
                TribesDiscoverRequest()
            )

            if (response is Response.Success) {

                val code = response.value.value

                delay(200L)

                code?.toTribeJoinLink()?.let { tribeJoinLink ->
                    handleTribeJoinLink(tribeJoinLink)
                }
            }
        }
    }

    private suspend fun handleTribeJoinLink(tribeJoinLink: TribeJoinLink) {
        val chat: Chat? = try {
            chatRepository.getChatByUUID(
                ChatUUID(tribeJoinLink.tribePubkey)
            ).firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }

        if (chat != null) {
            dashboardNavigator.toChatTribe(chat.id)
        } else {
            dashboardNavigator.toJoinTribeDetail(tribeJoinLink)
        }
    }
}
