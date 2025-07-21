package chat.sphinx.concept_repository_chat

import chat.sphinx.concept_network_query_chat.model.NewTribeDto
import chat.sphinx.concept_repository_chat.model.AddMember
import chat.sphinx.concept_repository_chat.model.CreateTribe
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.NotificationLevel
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.lsat.Lsat
import chat.sphinx.wrapper_common.lsat.LsatIdentifier
import chat.sphinx.wrapper_common.lsat.LsatIssuer
import chat.sphinx.wrapper_common.lsat.LsatStatus
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_common.message.RemoteTimezoneIdentifier
import chat.sphinx.wrapper_chat.TimezoneEnabled
import chat.sphinx.wrapper_chat.TimezoneIdentifier
import chat.sphinx.wrapper_chat.TimezoneUpdated
import chat.sphinx.wrapper_podcast.Podcast
import kotlinx.coroutines.flow.Flow

/**
 * All [Chat]s are cached to the DB such that a network refresh will update
 * them, and thus proc any [Flow] being collected
 * */
interface ChatRepository {

    val getAllChats: Flow<List<Chat>>
    val getAllTribeChats: Flow<List<Chat>>
    fun getChatById(chatId: ChatId): Flow<Chat?>
    fun getChatByUUID(chatUUID: ChatUUID): Flow<Chat?>
    fun getPodcastByChatId(chatId: ChatId): Flow<Podcast?>

    /**
     * Returns a [chat.sphinx.wrapper_chat.ChatType.Conversation] or `null`
     * for the provided [contactId]
     * */
    fun getConversationByContactId(contactId: ContactId): Flow<Chat?>

    /**
     * Throws [NoSuchElementException] on collection if [Chat.contactIds]
     * is empty.
     * */
    fun getUnseenMessagesByChatId(chatId: ChatId): Flow<Long?>

    suspend fun getAllChatsByIds(chatIds: List<ChatId>): List<Chat>

    /**
     * Returns `true` if the user has muted the chat and there is a need
     * to notify them that they won't receive messages anymore.
     *
     * Returns `false` if the user has _un_ muted the chat and there is no
     * need to notify.
     *
     * Returns error if something went wrong (networking)
     * */
    suspend fun setNotificationLevel(
        chat: Chat,
        level: NotificationLevel
    ): Response<Boolean, ResponseError>

    suspend fun updateChatContentSeenAt(chatId: ChatId)


    fun getAllDiscoverTribes(
        page: Int,
        itemsPerPage: Int,
        searchTerm: String? = null,
        tags: String? = null,
        tribeServer: String?
    ): Flow<List<NewTribeDto>>

    fun getSecondBrainTribes(): Flow<List<Chat?>>

    suspend fun updateTribeInfo(chat: Chat, isProductionEnvironment: Boolean): NewTribeDto?
    suspend fun storeTribe(createTribe: CreateTribe, chatId: ChatId?)
    suspend fun updateTribe(chatId: ChatId, createTribe: CreateTribe)
    suspend fun exitAndDeleteTribe(tribe: Chat)

    suspend fun pinMessage(
        chatId: ChatId,
        message: Message,
        isProductionEnvironment: Boolean
    ): Response<Any, ResponseError>

    suspend fun unPinMessage(
        chatId: ChatId,
        message: Message,
        isProductionEnvironment: Boolean
    ): Response<Any, ResponseError>

    suspend fun addTribeMember(addMember: AddMember): Response<Any, ResponseError>

    // LSAT related
    suspend fun getLastLsatByIssuer(issuer: LsatIssuer): Flow<Lsat?>
    suspend fun getLastLsatActive(): Flow<Lsat?>

    suspend fun getLsatByIdentifier(identifier: LsatIdentifier): Flow<Lsat?>
    suspend fun upsertLsat(lsat: Lsat)
    suspend fun updateLsatStatus(identifier: LsatIdentifier, status: LsatStatus)

    suspend fun updateTimezoneEnabledStatus(
        isTimezoneEnabled: TimezoneEnabled,
        chatId: ChatId
    )

    suspend fun updateTimezoneIdentifier(
        timezoneIdentifier: TimezoneIdentifier?,
        chatId: ChatId
    )

    suspend fun updateTimezoneUpdated(
        timezoneUpdated: TimezoneUpdated,
        chatId: ChatId
    )

    suspend fun updateTimezoneUpdatedOnSystemChange()
}
