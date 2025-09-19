package chat.sphinx.transactions.ui

import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.example.wrapper_mqtt.TransactionDto
import chat.sphinx.transactions.navigation.TransactionsNavigator
import chat.sphinx.transactions.ui.viewstate.TransactionHolderViewState
import chat.sphinx.wrapper_chat.isConversation
import chat.sphinx.wrapper_chat.isTrue
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.message.MessageUUID
import chat.sphinx.wrapper_common.message.toMessageUUID
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_message.SenderAlias
import chat.sphinx.wrapper_message.toSenderAlias
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.BaseViewModel
import io.matthewnelson.android_feature_viewmodel.currentViewState
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
internal class TransactionsViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    val navigator: TransactionsNavigator,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val connectManagerRepository: ConnectManagerRepository,
): BaseViewModel<TransactionsViewState>(
    dispatchers,
    TransactionsViewState.ListMode(
        listOf(),
        loading = true,
        firstPage = true
    )
)
{
    private var page: Int = 0
    private var loading: Boolean = false
    private val itemsPerPage: Int = 50
    private var loadedItems: Int = 0
    private var lastMessageDate: Long = System.currentTimeMillis()

    private suspend fun getOwnerContact(): Contact {
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

    init {
        viewModelScope.launch(mainImmediate) {
            loadTransactions(
                System.currentTimeMillis()
            )
        }
        collectTransactions()
    }

    fun loadMoreTransactions() {
        if (loading) {
            return
        }
        loading = true
        page += 1

        loadTransactions(
            lastMessageDate.minus(1)
        )
    }

    private fun loadTransactions(
        lastMessageDate: Long
    ) {
        connectManagerRepository.getPayments(lastMessageDate, itemsPerPage)
    }

    private fun collectTransactions(){
        viewModelScope.launch(mainImmediate) {

            connectManagerRepository.transactionDtoState.collect { transactionsDto ->

                val transactionsToShow = (transactionsDto ?: mutableListOf())

                if (transactionsToShow.isNotEmpty()) {
                    val firstPage = (page == 0)
                    val lastItemDate =  transactionsToShow.lastOrNull()?.ts ?: lastMessageDate

                    if (lastMessageDate == lastItemDate) {
                        loading = false
                        updateViewState(TransactionsViewState.LastItem)
                        return@collect
                    }

                    lastMessageDate = lastItemDate
                    loadedItems = loadedItems.plus(transactionsToShow.size)

                    updateViewState(
                        TransactionsViewState.ListMode(
                            processTransactions(transactionsToShow.distinct(), getOwnerContact()),
                            false,
                            firstPage
                        )
                    )
                    loading = false
                }
            }
        }
    }

    private suspend fun processTransactions(
        transactions: List<TransactionDto>,
        owner: Contact
    ): List<TransactionHolderViewState> {

        var chatsIdsMap: MutableMap<ChatId, ArrayList<Long>> = LinkedHashMap(transactions.size)
        var originalMessageUUIDsMap: MutableMap<MessageUUID, Long> = LinkedHashMap(transactions.size)

        var contactIdsMap: MutableMap<Long, ContactId> = LinkedHashMap(transactions.size)
        var contactAliasMap: MutableMap<Long, SenderAlias> = LinkedHashMap(transactions.size)

        for (transaction in transactions) {
            when {
                transaction.isIncomingWithSender(owner.id) -> {
                    transaction.getSenderId()?.let { senderId ->
                        contactIdsMap[transaction.id] = senderId
                    }
                    transaction.getSenderAlias()?.toSenderAlias()?.let { senderAlias ->
                        contactAliasMap[transaction.id] = senderAlias
                    }
                }
                transaction.isOutgoingWithReceiver(owner.id) -> {
                    transaction.getReceiverId()?.let { receiverId ->
                        contactIdsMap[transaction.id] = receiverId
                    }
                }
                transaction.isOutgoingMessageBoost(owner.id) -> {
                    transaction.reply_uuid?.toMessageUUID()?.let { originalMessageUUID ->
                        originalMessageUUIDsMap[originalMessageUUID] = transaction.id
                    }
                }
                transaction.isPaymentInChat() -> {
                    transaction.getChatId()?.let { chatId ->
                        if (chatsIdsMap[chatId] == null) {
                            chatsIdsMap[chatId] = ArrayList(0)
                        }
                        chatsIdsMap[chatId]?.add(transaction.id)
                    }
                }
            }
        }

        val chatIds = chatsIdsMap.keys.map { it }
        chatRepository.getAllChatsByIds(chatIds).let { response ->
            response.forEach { chat ->
                if (
                    (chat.ownedTribe?.isTrue() == true || chat.isConversation()) &&
                    chat.contactIds.size == 2
                ) {
                    chatsIdsMap[chat.id]?.let { transactionIds ->
                        for (transactionId in transactionIds) {
                            contactIdsMap[transactionId] = chat.contactIds[1]
                        }
                    }
                }
            }
        }

        val originalMessageUUIDs = originalMessageUUIDsMap.keys.map { it }
        messageRepository.getAllMessagesByUUID(originalMessageUUIDs).let { response ->
            response.forEach { message ->
                originalMessageUUIDsMap[message.uuid]?.let { transactionId ->
                    contactIdsMap[transactionId] = message.sender

                    message.senderAlias?.let { senderAlias ->
                        contactAliasMap[transactionId] = senderAlias
                    }
                }
            }
        }

        val contactsMap: MutableMap<Long, Contact> = LinkedHashMap(transactions.size)
        val contactIds = contactIdsMap.values.map { it }

        contactRepository.getAllContactsByIds(contactIds).let { response ->
            response.forEach { contact ->
                contactsMap[contact.id.value] = contact
            }
        }

        val transactionsHVSs: MutableList<TransactionHolderViewState> = currentViewState.list.toMutableList()

        if (transactionsHVSs.lastOrNull() is TransactionHolderViewState.Loader) {
            transactionsHVSs.removeAt(transactionsHVSs.lastIndex)
        }

        for (transaction in transactions) {
            val senderId = contactIdsMap[transaction.id]
            val senderAlias: String? = contactAliasMap[transaction.id]?.value ?: contactsMap[senderId?.value]?.alias?.value

            if (transaction.isBountyPayment()) {
                transactionsHVSs.add(
                    TransactionHolderViewState.Incoming(
                        transaction,
                        null,
                        withContext(Dispatchers.IO) {
                            URLDecoder.decode(
                                transaction.message_content,
                                StandardCharsets.UTF_8.toString()
                            )
                        } ?: "-",
                        true
                    )
                )
            } else {
                if (transaction.isOutgoingPayment(owner.id)) {
                    transactionsHVSs.add(
                        TransactionHolderViewState.Outgoing(
                            transaction,
                            null,
                            senderAlias ?: "-",
                        )
                    )
                }
                if (transaction.isIncomingPayment(owner.id)) {
                    transactionsHVSs.add(
                        TransactionHolderViewState.Incoming(
                            transaction,
                            null,
                            senderAlias ?: "-",
                            false
                        )
                    )
                }
                if (transaction.isFailedPayment()) {
                    transactionsHVSs.add(
                        TransactionHolderViewState.Failed.Closed(
                            transaction,
                            null,
                            senderAlias ?: "-",
                        )
                    )
                }
            }
        }

        if (loadedItems >= itemsPerPage) {
            transactionsHVSs.add(
                TransactionHolderViewState.Loader()
            )
            loadedItems = 0
        }

        return transactionsHVSs
    }

    fun toggleFailed(
        transactionViewHolder: TransactionHolderViewState,
        position: Int
    ) {
        (currentViewState as? TransactionsViewState.ListMode)?.let { currentVS ->
            var list: MutableList<TransactionHolderViewState> = currentVS.list.toMutableList()

            transactionViewHolder.transaction?.let { nnTransaction ->

                val toggledViewHolder = when (transactionViewHolder) {
                    is TransactionHolderViewState.Failed.Open -> {
                        TransactionHolderViewState.Failed.Closed(
                            nnTransaction,
                            transactionViewHolder.invoice,
                            transactionViewHolder.messageSenderName
                        )
                    }
                    is TransactionHolderViewState.Failed.Closed -> {
                        TransactionHolderViewState.Failed.Open(
                            nnTransaction,
                            transactionViewHolder.invoice,
                            transactionViewHolder.messageSenderName
                        )
                    }
                    else -> {
                        null
                    }
                }

                toggledViewHolder?.let {
                    list[position] = it

                    updateViewState(
                        TransactionsViewState.ListMode(
                            list,
                            currentVS.loading,
                            currentVS.firstPage
                        )
                    )
                }
            }
        }
    }
}
