package chat.sphinx.feature_repository.util

import chat.sphinx.concept_network_query_chat.model.ChatDto
import chat.sphinx.concept_network_query_chat.model.NewTribeDto
import chat.sphinx.concept_network_query_chat.model.TribeDto
import chat.sphinx.concept_network_query_chat.model.feed.FeedDto
import chat.sphinx.concept_network_query_contact.model.ContactDto
import chat.sphinx.concept_network_query_invite.model.InviteDto
import chat.sphinx.conceptcoredb.SphinxDatabaseQueries
import chat.sphinx.example.wrapper_mqtt.MessageDto
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.ChatMuted
import chat.sphinx.wrapper_chat.NotificationLevel
import chat.sphinx.wrapper_chat.isConversation
import chat.sphinx.wrapper_chat.isTribe
import chat.sphinx.wrapper_chat.toChatAlias
import chat.sphinx.wrapper_chat.toChatGroupKey
import chat.sphinx.wrapper_chat.toChatHost
import chat.sphinx.wrapper_chat.toChatMuted
import chat.sphinx.wrapper_chat.toChatName
import chat.sphinx.wrapper_chat.toChatPrivate
import chat.sphinx.wrapper_chat.toChatStatus
import chat.sphinx.wrapper_chat.toChatType
import chat.sphinx.wrapper_chat.toChatUnlisted
import chat.sphinx.wrapper_chat.toNotificationLevel
import chat.sphinx.wrapper_chat.toSecondBrainUrl
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.PhotoUrl
import chat.sphinx.wrapper_common.Push
import chat.sphinx.wrapper_common.Seen
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.contact.toBlocked
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.dashboard.InviteId
import chat.sphinx.wrapper_common.feed.FeedId
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.feed.Subscribed
import chat.sphinx.wrapper_common.feed.toFeedType
import chat.sphinx.wrapper_common.feed.toFeedUrl
import chat.sphinx.wrapper_common.invite.InviteStatus
import chat.sphinx.wrapper_common.invite.isPaymentPending
import chat.sphinx.wrapper_common.invite.isProcessingPayment
import chat.sphinx.wrapper_common.invite.toInviteStatus
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningPaymentHash
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.toLightningNodeAlias
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningPaymentHash
import chat.sphinx.wrapper_common.lightning.toLightningPaymentRequestOrNull
import chat.sphinx.wrapper_common.lightning.toLightningRouteHint
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.lsat.Lsat
import chat.sphinx.wrapper_common.message.MessageId
import chat.sphinx.wrapper_common.message.toMessageUUID
import chat.sphinx.wrapper_common.secondsToDateTime
import chat.sphinx.wrapper_common.subscription.SubscriptionId
import chat.sphinx.wrapper_common.time
import chat.sphinx.wrapper_common.toDateTime
import chat.sphinx.wrapper_common.toPhotoUrl
import chat.sphinx.wrapper_common.toPush
import chat.sphinx.wrapper_common.toSeen
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.isTrue
import chat.sphinx.wrapper_contact.toContactAlias
import chat.sphinx.wrapper_contact.toContactStatus
import chat.sphinx.wrapper_contact.toDeviceId
import chat.sphinx.wrapper_contact.toNotificationSound
import chat.sphinx.wrapper_contact.toOwner
import chat.sphinx.wrapper_contact.toPrivatePhoto
import chat.sphinx.wrapper_feed.FeedDescription
import chat.sphinx.wrapper_feed.FeedDestinationAddress
import chat.sphinx.wrapper_feed.FeedDestinationSplit
import chat.sphinx.wrapper_feed.FeedDestinationType
import chat.sphinx.wrapper_feed.FeedItemsCount
import chat.sphinx.wrapper_feed.FeedModelSuggested
import chat.sphinx.wrapper_feed.FeedModelType
import chat.sphinx.wrapper_feed.FeedTitle
import chat.sphinx.wrapper_feed.toFeedAuthor
import chat.sphinx.wrapper_feed.toFeedContentType
import chat.sphinx.wrapper_feed.toFeedDescription
import chat.sphinx.wrapper_feed.toFeedDestinationAddress
import chat.sphinx.wrapper_feed.toFeedEnclosureLength
import chat.sphinx.wrapper_feed.toFeedEnclosureType
import chat.sphinx.wrapper_feed.toFeedGenerator
import chat.sphinx.wrapper_feed.toFeedItemDuration
import chat.sphinx.wrapper_feed.toFeedLanguage
import chat.sphinx.wrapper_invite.Invite
import chat.sphinx.wrapper_invite.InviteString
import chat.sphinx.wrapper_lightning.LightningServiceProvider
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_message.Message
import chat.sphinx.wrapper_message.MessageStatus
import chat.sphinx.wrapper_message.MessageType
import chat.sphinx.wrapper_message.NewMessage
import chat.sphinx.wrapper_message.isInvoicePayment
import chat.sphinx.wrapper_message.toErrorMessage
import chat.sphinx.wrapper_message.toFlagged
import chat.sphinx.wrapper_message.toMessageContent
import chat.sphinx.wrapper_message.toMessageContentDecrypted
import chat.sphinx.wrapper_message.toMessageMUID
import chat.sphinx.wrapper_message.toMessagePerson
import chat.sphinx.wrapper_message.toMessageStatus
import chat.sphinx.wrapper_message.toMessageType
import chat.sphinx.wrapper_message.toRecipientAlias
import chat.sphinx.wrapper_message.toReplyUUID
import chat.sphinx.wrapper_message.toSenderAlias
import chat.sphinx.wrapper_message.toTagMessage
import chat.sphinx.wrapper_message.toThreadUUID
import chat.sphinx.wrapper_message_media.FileName
import chat.sphinx.wrapper_message_media.MediaToken
import chat.sphinx.wrapper_message_media.getMUIDFromMediaToken
import chat.sphinx.wrapper_message_media.toMediaKey
import chat.sphinx.wrapper_message_media.toMediaKeyDecrypted
import chat.sphinx.wrapper_message_media.toMediaToken
import chat.sphinx.wrapper_message_media.toMediaType
import chat.sphinx.wrapper_rsa.RsaPublicKey
import com.squareup.moshi.Moshi
import com.squareup.sqldelight.TransactionCallbacks

@Suppress("NOTHING_TO_INLINE")
inline fun String.toNodeBalance(): NodeBalance? {
    return try {
        NodeBalance(Sat(this.toLong()))
    } catch (e: NumberFormatException) {
        null
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun Long.toNodeBalance(): NodeBalance? {
    return try {
        NodeBalance(Sat(this.toLong()))
    } catch (e: NumberFormatException) {
        null
    }
}

inline val MessageDto.updateChatDboLatestMessage: Boolean
    get() = type.toMessageType().show           &&
            type != MessageType.BOT_RES         &&
            status != MessageStatus.DELETED


inline val Message.updateChatNewLatestMessage: Boolean
    get() = type.show                          &&
            type != MessageType.BotRes         &&
            status != MessageStatus.Deleted


@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateServerDbo(
    lsp: LightningServiceProvider,
    queries: SphinxDatabaseQueries
) {
    queries.serverUpsert(lsp.pubKey, lsp.ip)

}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatDboLatestMessage(
    messageDto: MessageDto,
    chatId: ChatId,
    latestMessageUpdatedTimeMap: MutableMap<ChatId, DateTime>,
    queries: SphinxDatabaseQueries,
) {
    val dateTime = messageDto.created_at.toDateTime()

    if (
        messageDto.updateChatDboLatestMessage &&
        (latestMessageUpdatedTimeMap[chatId]?.time ?: 0L) <= dateTime.time
    ){
        queries.chatUpdateLatestMessage(
            MessageId(messageDto.id),
            chatId,
        )
        queries.dashboardUpdateLatestMessage(
            dateTime,
            MessageId(messageDto.id),
            chatId,
        )
        latestMessageUpdatedTimeMap[chatId] = dateTime
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatNewLatestMessage(
    message: Message,
    chatId: ChatId,
    latestMessageUpdatedTimeMap: MutableMap<ChatId, DateTime>,
    queries: SphinxDatabaseQueries,
) {
    val dateTime = message.date

    if (
        message.updateChatNewLatestMessage &&
        (latestMessageUpdatedTimeMap[chatId]?.time ?: 0L) <= dateTime.time
    ){
        queries.chatUpdateLatestMessage(
            message.id,
            chatId,
        )
        queries.dashboardUpdateLatestMessage(
            dateTime,
            message.id,
            chatId,
        )
        latestMessageUpdatedTimeMap[chatId] = dateTime
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatDboLatestMessage(
    messageDto: MessageDto,
    chatId: ChatId,
    latestMessageUpdatedTimeMap: SynchronizedMap<ChatId, DateTime>,
    queries: SphinxDatabaseQueries,
) {
    latestMessageUpdatedTimeMap.withLock { map ->
        updateChatDboLatestMessage(messageDto, chatId, map, queries)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatNewLatestMessage(
    message: Message,
    chatId: ChatId,
    latestMessageUpdatedTimeMap: SynchronizedMap<ChatId, DateTime>,
    queries: SphinxDatabaseQueries,
) {
    latestMessageUpdatedTimeMap.withLock { map ->
        updateChatNewLatestMessage(message, chatId, map, queries)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatMuted(
    chatId: ChatId,
    muted: ChatMuted,
    queries: SphinxDatabaseQueries
) {
    queries.chatUpdateMuted(muted, chatId)
    queries.dashboardUpdateMuted(muted, chatId)
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateChatNotificationLevel(
    chatId: ChatId,
    notificationLevel: NotificationLevel?,
    queries: SphinxDatabaseQueries
) {
    queries.chatUpdateNotificationLevel(notificationLevel, chatId)
}

@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.updateNewChatTribeData(
    tribe: NewTribeDto,
    chatId: ChatId,
    queries: SphinxDatabaseQueries,
) {
    // Needs to implement the rest of args

    val pricePerMessage = tribe.getPricePerMessageInSats().toSat()
    val escrowAmount = tribe.getEscrowAmountInSats().toSat()
    val name = tribe.name.toChatName()
    val photoUrl = tribe.img?.toPhotoUrl()
    val pinMessage = tribe.pin?.toMessageUUID()
    val secondBrainUrl = tribe.second_brain_url?.toSecondBrainUrl()

    queries.chatUpdateTribeData(
        pricePerMessage,
        escrowAmount,
        name,
        photoUrl,
        pinMessage,
        secondBrainUrl,
        chatId,
    )

    queries.dashboardUpdateTribe(
        name?.value ?: "",
        photoUrl,
        chatId
    )
}

@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.updateChatTribeData(
    tribe: TribeDto,
    chatId: ChatId,
    queries: SphinxDatabaseQueries,
) {
    val pricePerMessage = tribe.price_per_message.toSat()
    val escrowAmount = tribe.escrow_amount.toSat()
    val name = tribe.name.toChatName()
    val photoUrl = tribe.img?.toPhotoUrl()
    val pinMessage = tribe.pin?.toMessageUUID()
    val secondBrainUrl = tribe.second_brain_url?.toSecondBrainUrl()

    queries.chatUpdateTribeData(
        pricePerMessage,
        escrowAmount,
        name,
        photoUrl,
        pinMessage,
        secondBrainUrl,
        chatId,
    )

    queries.dashboardUpdateTribe(
        name?.value ?: "",
        photoUrl,
        chatId
    )
}

inline fun TransactionCallbacks.upsertNewChat(
    chat: Chat, // Replaced ChatDto with Chat
    moshi: Moshi,
    chatSeenMap: SynchronizedMap<ChatId, Seen>,
    queries: SphinxDatabaseQueries,
    contact: Contact? = null, // Replaced ContactDto with Contact
    ownerPubKey: LightningNodePubKey? = null
) {
    val seen = chat.seen
    val chatId = chat.id
    val chatType = chat.type
    val createdAt = chat.createdAt
    val contactIds = chat.contactIds
    val muted = chat.isMuted
    val chatPhotoUrl = chat.photoUrl
    val pricePerMessage = chat.pricePerMessage
    val escrowAmount = chat.escrowAmount
    val chatName = chat.name
    val adminPubKey = chat.ownerPubKey
    val pinedMessage = chat.pinedMessage

    queries.chatUpsert(
        chatName,
        chatPhotoUrl,
        chat.status,
        contactIds,
        muted,
        chat.groupKey,
        chat.host,
        chat.unlisted,
        chat.privateTribe,
        chat.ownerPubKey,
        seen,
        null,
        chat.myPhotoUrl,
        chat.myAlias,
        chat.pendingContactIds,
        chat.notify,
        pinedMessage,
        chatId,
        chat.uuid,
        chatType,
        createdAt,
        pricePerMessage,
        escrowAmount,
    )

    if (
        chatType.isTribe() &&
        (ownerPubKey == adminPubKey) &&
        (pricePerMessage != null || escrowAmount != null || pinedMessage != null)
    ) {
        queries.chatUpdateTribeData(
            pricePerMessage,
            escrowAmount,
            chatName,
            chatPhotoUrl,
            pinedMessage,
            null,
            chatId,
        )
    }

    val conversationContactId: ContactId? = if (chatType.isConversation()) {
        contactIds.elementAtOrNull(1)?.let { contactId ->
            queries.dashboardUpdateIncludeInReturn(false, contactId)
            contactId
        }
    } else {
        null
    }

    queries.dashboardUpsert(
        if (conversationContactId != null && contact != null) {
            contact.alias?.value
        } else {
            contact?.alias?.value ?: " "
        },
        muted,
        seen,
        if (conversationContactId != null && contact != null) {
            contact.photoUrl
        } else {
            chatPhotoUrl
        },
        chatId,
        conversationContactId,
        createdAt
    )

    chatSeenMap.withLock { it[ChatId(chat.id.value)] = seen }
}

@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.upsertChat(
    dto: ChatDto,
    moshi: Moshi,
    chatSeenMap: SynchronizedMap<ChatId, Seen>,
    queries: SphinxDatabaseQueries,
    contactDto: ContactDto? = null,
    ownerPubKey: LightningNodePubKey? = null
) {
    val seen = dto.seenActual.toSeen()
    val chatId = ChatId(dto.id)
    val chatType = dto.type.toChatType()
    val createdAt = dto.created_at.toDateTime()
    val contactIds = dto.contact_ids.map { ContactId(it) }
    val muted = dto.isMutedActual.toChatMuted()
    val chatPhotoUrl = dto.photo_url?.toPhotoUrl()
    val pricePerMessage = dto.price_per_message?.toSat()
    val escrowAmount = dto.escrow_amount?.toSat()
    val chatName = dto.name?.toChatName()
    val adminPubKey = dto.owner_pub_key?.toLightningNodePubKey()
    val pinedMessage = dto.pin?.toMessageUUID()

    queries.chatUpsert(
        chatName,
        chatPhotoUrl,
        dto.status.toChatStatus(),
        contactIds,
        muted,
        dto.group_key?.toChatGroupKey(),
        dto.host?.toChatHost(),
        dto.unlistedActual.toChatUnlisted(),
        dto.privateActual.toChatPrivate(),
        dto.owner_pub_key?.toLightningNodePubKey(),
        seen,
        null,
        dto.my_photo_url?.toPhotoUrl(),
        dto.my_alias?.toChatAlias(),
        dto.pending_contact_ids?.map { ContactId(it) },
        dto.notify?.toNotificationLevel(),
        pinedMessage,
        chatId,
        ChatUUID(dto.uuid),
        chatType,
        createdAt,
        pricePerMessage,
        escrowAmount
    )

    if (
        chatType.isTribe() &&
        (ownerPubKey == adminPubKey) &&
        (pricePerMessage != null || escrowAmount != null || pinedMessage != null)
    ) {
        queries.chatUpdateTribeData(
            pricePerMessage,
            escrowAmount,
            chatName,
            chatPhotoUrl,
            pinedMessage,
            null,
            chatId,
        )
    }

    val conversationContactId: ContactId? = if (chatType.isConversation()) {
        contactIds.elementAtOrNull(1)?.let { contactId ->
            queries.dashboardUpdateIncludeInReturn(false, contactId)
            contactId
        }
    } else {
        null
    }

    queries.dashboardUpsert(
        if (conversationContactId != null && contactDto != null) {
            contactDto.alias
        } else {
            dto.name ?: " "
        },
        muted,
        seen,
        if (conversationContactId != null && contactDto != null) {
            contactDto.photo_url?.toPhotoUrl()
        } else {
            chatPhotoUrl
        },
        chatId,
        conversationContactId,
        createdAt
    )

    chatSeenMap.withLock { it[ChatId(dto.id)] = seen }
}


@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.upsertNewContact(contact: Contact, queries: SphinxDatabaseQueries) {

    if (contact.fromGroup.isTrue()) {
        return
    }

    val routeHint = contact.routeHint
    val nodePubKey = contact.nodePubKey
    val nodeAlias = contact.nodeAlias
    val alias = contact.alias
    val photoUrl = contact.photoUrl
    val privatePhoto = contact.privatePhoto
    val status = contact.status
    val rsaPublicKey = contact.rsaPublicKey
    val deviceId = contact.deviceId
    val updatedAt = contact.updatedAt
    val notificationSound = contact.notificationSound
    val tipAmount = contact.tipAmount
    val blocked = contact.blocked

    // Perform the upsert operation
    queries.contactUpsert(
        routeHint,
        nodePubKey,
        nodeAlias,
        alias,
        photoUrl,
        privatePhoto,
        status,
        rsaPublicKey,
        deviceId,
        updatedAt,
        notificationSound,
        tipAmount,
        blocked,
        contact.id,
        contact.isOwner,
        contact.createdAt
    )

    if (!contact.isOwner.isTrue()) {
        queries.dashboardUpsert(
            contact.alias?.value,
            ChatMuted.False,
            Seen.True,
            contact.photoUrl,
            contact.id,
            null,
            contact.createdAt
        )
        queries.dashboardUpdateConversation(
            contact.alias?.value,
            contact.photoUrl,
            contact.id
        )
    }
}


@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.upsertContact(dto: ContactDto, queries: SphinxDatabaseQueries) {

    if (dto.fromGroupActual) {
        return
    }

    val contactId = ContactId(dto.id)
    val createdAt = dto.created_at.toDateTime()
    val isOwner = dto.isOwnerActual.toOwner()
    val photoUrl = dto.photo_url?.toPhotoUrl()

    queries.contactUpsert(
        dto.route_hint?.toLightningRouteHint(),
        dto.public_key?.toLightningNodePubKey(),
        dto.node_alias?.toLightningNodeAlias(),
        dto.alias?.toContactAlias(),
        photoUrl,
        dto.privatePhotoActual.toPrivatePhoto(),
        dto.status.toContactStatus(),
        dto.contact_key?.let { RsaPublicKey(it.toCharArray()) },
        dto.device_id?.toDeviceId(),
        dto.updated_at.toDateTime(),
        dto.notification_sound?.toNotificationSound(),
        dto.tip_amount?.toSat(),
        dto.blockedActual.toBlocked(),
        contactId,
        isOwner,
        createdAt
    )

    dto.invite?.let { inviteDto ->
        upsertInvite(inviteDto, queries)
    }

    if (!isOwner.isTrue()) {
        queries.dashboardUpsert(
            dto.alias,
            ChatMuted.False,
            Seen.True,
            photoUrl,
            contactId,
            null,
            createdAt,
        )
        queries.dashboardUpdateConversation(
            dto.alias,
            photoUrl,
            contactId
        )
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.upsertNewInvite(invite: Invite, queries: SphinxDatabaseQueries) {

    queries.inviteUpsert(
        invite.inviteString,
        invite.inviteCode,
        invite.paymentRequest,
        invite.status,
        invite.price,
        invite.id,
        invite.contactId,
        invite.createdAt,
    )

    queries.contactUpdateInvite(
        invite.status,
        invite.id,
        invite.contactId
    )
}


@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.upsertInvite(dto: InviteDto, queries: SphinxDatabaseQueries) {

    val inviteStatus = dto.status.toInviteStatus().let { dtoStatus ->
        if (dtoStatus.isPaymentPending()) {

            val invite = queries.inviteGetById(InviteId(dto.id)).executeAsOneOrNull()

            if (invite?.status?.isProcessingPayment() == true) {
                InviteStatus.ProcessingPayment
            } else {
                dtoStatus
            }

        } else {
            dtoStatus
        }
    }

    queries.inviteUpsert(
        InviteString(dto.invite_string),
        null,
        dto.invoice?.toLightningPaymentRequestOrNull(),
        inviteStatus,
        dto.price?.toSat(),
        InviteId(dto.id),
        ContactId(dto.contact_id),
        dto.created_at.toDateTime(),
    )

    queries.contactUpdateInvite(
        inviteStatus,
        InviteId(dto.id),
        ContactId(dto.contact_id)
    )

// TODO: Work out what status needs to be included to be shown on the dashboard

//        when (inviteStatus) {
//            is InviteStatus.Complete -> TODO()
//            is InviteStatus.Delivered -> TODO()
//            is InviteStatus.Expired -> TODO()
//            is InviteStatus.InProgress -> TODO()
//            is InviteStatus.PaymentPending -> TODO()
//            is InviteStatus.Pending -> TODO()
//            is InviteStatus.Ready -> TODO()
//            is InviteStatus.Unknown -> TODO()
//        }
//        queries.dashboardInsert(
//            InviteId(it.id),
//            DateTime.nowUTC().toDateTime(),
//        )
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.updateInviteStatus(
    inviteId: InviteId,
    inviteStatus: InviteStatus,
    queries: SphinxDatabaseQueries,
) {
    queries.inviteUpdateStatus(inviteStatus, inviteId)
}

fun TransactionCallbacks.upsertNewMessage(
    message: NewMessage,
    queries: SphinxDatabaseQueries,
    fileName: FileName? = null
) {
    val chatId: ChatId = message.chatId

    message.messageMedia?.mediaToken?.let { mediaToken ->

        if (mediaToken.value.isEmpty()) return

        queries.messageMediaUpsert(
            (message.messageMedia?.mediaKey?.value ?: "").toMediaKey(),
            (message.messageMedia?.mediaType?.value ?: "").toMediaType(),
            MediaToken(mediaToken.value),
            MessageId(message.id.value),
            chatId,
            (message.messageMedia?.mediaKeyDecrypted?.value ?: "").toMediaKeyDecrypted(),
            message.messageMedia?.localFile,
            fileName
        )
    }

    queries.messageUpsert(
        message.status,
        message.seen,
        message.senderAlias,
        message.senderPic,
        message.originalMUID,
        message.replyUUID,
        message.type,
        message.recipientAlias,
        message.recipientPic,
        Push.False,
        message.person,
        message.threadUUID,
        message.errorMessage,
        message.tagMessage,
        MessageId(message.id.value),
        message.uuid,
        chatId,
        message.sender,
        message.receiver?.let { ContactId(it.value) },
        message.amount,
        message.paymentHash,
        message.paymentRequest,
        message.date,
        message.expirationDate,
        message.messageContent,
        message.messageContentDecrypted,
        message.messageMedia?.mediaToken?.getMUIDFromMediaToken()?.value?.toMessageMUID(),
        message.flagged.value.toFlagged()
    )

    if (message.type.isInvoicePayment()) {
        message.paymentHash?.let {
            queries.messageUpdateInvoiceAsPaidByPaymentHash(LightningPaymentHash(it.value))
        }
    }
}


@Suppress("SpellCheckingInspection")
fun TransactionCallbacks.upsertMessage(
    dto: MessageDto,
    queries: SphinxDatabaseQueries,
    fileName: FileName? = null
) {

    val chatId: ChatId = dto.chat_id?.let {
        ChatId(it)
    } ?: dto.chat?.let {
        ChatId(it)
    } ?: ChatId(ChatId.NULL_CHAT_ID.toLong())

    dto.media_token?.let { mediaToken ->

        if (mediaToken.isEmpty()) return

        queries.messageMediaUpsert(
            (dto.media_key ?: "").toMediaKey(),
            (dto.media_type ?: "").toMediaType(),
            MediaToken(mediaToken),
            MessageId(dto.id),
            chatId,
            dto.mediaKeyDecrypted?.toMediaKeyDecrypted(),
            dto.mediaLocalFile,
            fileName
        )
    }

    queries.messageUpsert(
        dto.status.toMessageStatus(),
        dto.seenActual.toSeen(),
        dto.sender_alias?.toSenderAlias(),
        dto.sender_pic?.toPhotoUrl(),
        dto.original_muid?.toMessageMUID(),
        dto.reply_uuid?.toReplyUUID(),
        dto.type.toMessageType(),
        dto.recipient_alias?.toRecipientAlias(),
        dto.recipient_pic?.toPhotoUrl(),
        dto.pushActual.toPush(),
        dto.person?.toMessagePerson(),
        dto.thread_uuid?.toThreadUUID(),
        dto.error_message?.toErrorMessage(),
        dto.tag_message?.toTagMessage(),
        MessageId(dto.id),
        dto.uuid?.toMessageUUID(),
        chatId,
        ContactId(dto.sender),
        dto.receiver?.let { ContactId(it) },
        Sat(dto.amount),
        dto.payment_hash?.toLightningPaymentHash(),
        dto.payment_request?.toLightningPaymentRequestOrNull(),
        dto.date.toDateTime(),
        dto.expiration_date?.toDateTime(),
        dto.message_content?.toMessageContent(),
        dto.messageContentDecrypted?.toMessageContentDecrypted(),
        dto.media_token?.toMediaToken()?.getMUIDFromMediaToken()?.value?.toMessageMUID(),
        false.toFlagged()
    )

    if (dto.type.toMessageType()?.isInvoicePayment() == true) {
        dto.payment_hash?.toLightningPaymentHash()?.let {
            queries.messageUpdateInvoiceAsPaidByPaymentHash(it)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun SphinxDatabaseQueries.updateSeen(chatId: ChatId) {
    transaction {
        chatUpdateSeen(Seen.True, chatId)
        messageUpdateSeenByChatId(Seen.True, chatId)
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.deleteChatById(
    chatId: ChatId?,
    queries: SphinxDatabaseQueries,
    latestMessageUpdatedTimeMap: SynchronizedMap<ChatId, DateTime>?,
) {
    queries.messageDeleteByChatId(chatId ?: return)
    queries.messageMediaDeleteByChatId(chatId)
    queries.chatDeleteById(chatId)
    queries.dashboardDeleteById(chatId)
    latestMessageUpdatedTimeMap?.withLock { it.remove(chatId) }
    deleteFeedsByChatId(chatId, queries)
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.deleteContactById(
    contactId: ContactId,
    queries: SphinxDatabaseQueries
) {
    queries.contactDeleteById(contactId)
    queries.inviteDeleteByContactId(contactId)
    queries.dashboardDeleteById(contactId)
    queries.subscriptionDeleteByContactId(contactId)
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.deleteMessageById(
    messageId: MessageId,
    queries: SphinxDatabaseQueries
) {
    queries.messageDeleteById(messageId)
    queries.messageMediaDeleteById(messageId)
}

//@Suppress("NOTHING_TO_INLINE")
//inline fun TransactionCallbacks.upsertSubscription(subscriptionDto: SubscriptionDto, queries: SphinxDatabaseQueries) {
//    queries.subscriptionUpsert(
//        id = SubscriptionId(subscriptionDto.id),
//        amount = Sat(subscriptionDto.amount),
//        contact_id = ContactId(subscriptionDto.contact_id),
//        chat_id = ChatId(subscriptionDto.chat_id),
//        count = SubscriptionCount(subscriptionDto.count.toLong()),
//        cron = Cron(subscriptionDto.cron),
//        end_date = subscriptionDto.end_date?.toDateTime(),
//        end_number = subscriptionDto.end_number?.let { EndNumber(it.toLong()) },
//        created_at = subscriptionDto.created_at.toDateTime(),
//        updated_at = subscriptionDto.updated_at.toDateTime(),
//        ended = subscriptionDto.endedActual,
//        paused = subscriptionDto.pausedActual,
//    )
//}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.deleteSubscriptionById(
    subscriptionId: SubscriptionId,
    queries: SphinxDatabaseQueries
) {
    queries.subscriptionDeleteById(subscriptionId)
}

fun TransactionCallbacks.upsertFeed(
    feedDto: FeedDto,
    feedUrl: FeedUrl,
    searchResultDescription: FeedDescription? = null,
    searchResultImageUrl: PhotoUrl? = null,
    chatId: ChatId,
    subscribed: Subscribed,
    currentItemId: FeedId? = null,
    queries: SphinxDatabaseQueries
) {

    if (feedDto.items.isEmpty()) {
        return
    }

    if (chatId.value != ChatId.NULL_CHAT_ID.toLong()) {
        queries.feedGetAllByChatId(chatId).executeAsList()?.forEach { feedDbo ->
            //Deleting old feed associated with chat
            if (feedDbo.feed_url.value != feedUrl.value) {
                deleteFeedById(
                    feedDbo.id,
                    queries
                )
            }
        }
    }

    val feedId = FeedId(feedDto.fixedId)

    feedDto.value?.let { feedValueDto ->
        queries.feedModelUpsert(
            type = FeedModelType(feedValueDto.model.type),
            suggested = FeedModelSuggested(feedValueDto.model.suggested),
            id = feedId
        )
    }

    val itemIds: MutableList<FeedId> = mutableListOf()

    for (item in feedDto.items) {
        val itemId = FeedId(item.id)

        itemIds.add(itemId)

        queries.feedItemUpsert(
            title = FeedTitle(item.title),
            description = item.description?.toFeedDescription(),
            date_published = item.datePublished?.secondsToDateTime(),
            date_updated = item.dateUpdated?.secondsToDateTime(),
            author = item.author?.toFeedAuthor(),
            content_type = item.contentType?.toFeedContentType(),
            enclosure_length = item.enclosureLength?.toFeedEnclosureLength(),
            enclosure_url = FeedUrl(item.enclosureUrl),
            enclosure_type = item.enclosureType?.toFeedEnclosureType(),
            image_url = item.imageUrl?.toPhotoUrl(),
            thumbnail_url = item.thumbnailUrl?.toPhotoUrl(),
            link = item.link?.toFeedUrl(),
            feed_id = feedId,
            id = itemId,
            duration = item.duration?.toFeedItemDuration(),
        )
    }

    queries.feedItemsDeleteOldByFeedId(feedId, itemIds)

    for (destination in feedDto.value?.destinations ?: listOf()) {
        if (destination.address.toFeedDestinationAddress() != null) {
            queries.feedDestinationUpsert(
                address = FeedDestinationAddress(destination.address),
                split = FeedDestinationSplit(destination.split.toDouble()),
                type = FeedDestinationType(destination.type),
                feed_id = feedId
            )
        }
    }

    val description = searchResultDescription
        ?: if (feedDto.description?.toFeedDescription() != null) {
            feedDto.description?.toFeedDescription()
        } else {
            null
        }

    val imageUrl = searchResultImageUrl
        ?: if (feedDto.imageUrl?.toPhotoUrl() != null) {
            feedDto.imageUrl?.toPhotoUrl()
        } else {
            null
        }

    queries.feedUpsert(
        feed_type = feedDto.feedType.toInt().toFeedType(),
        title = FeedTitle(feedDto.title),
        description = description,
        feed_url = feedUrl,
        author = feedDto.author?.toFeedAuthor(),
        image_url = imageUrl,
        owner_url = feedDto.ownerUrl?.toFeedUrl(),
        link = feedDto.link?.toFeedUrl(),
        date_published = feedDto.datePublished?.secondsToDateTime(),
        date_updated = feedDto.dateUpdated?.secondsToDateTime(),
        content_type = feedDto.contentType?.toFeedContentType(),
        language = feedDto.language?.toFeedLanguage(),
        items_count = FeedItemsCount(feedDto.items.count().toLong()),
        chat_id = chatId,
        subscribed = subscribed,
        id = feedId,
        generator = feedDto.generator?.toFeedGenerator(),
        current_item_id = currentItemId
    )
}

fun TransactionCallbacks.upsertFeedItems(
    feedDto: FeedDto,
    queries: SphinxDatabaseQueries
) {
    val feedId = FeedId(feedDto.fixedId)

    val itemIds: MutableList<FeedId> = mutableListOf()

    for (item in feedDto.items) {
        val itemId = FeedId(item.id)

        itemIds.add(itemId)

        queries.feedItemUpsert(
            title = FeedTitle(item.title),
            description = item.description?.toFeedDescription(),
            date_published = item.datePublished?.secondsToDateTime(),
            date_updated = item.dateUpdated?.secondsToDateTime(),
            author = item.author?.toFeedAuthor(),
            content_type = item.contentType?.toFeedContentType(),
            enclosure_length = item.enclosureLength?.toFeedEnclosureLength(),
            enclosure_url = FeedUrl(item.enclosureUrl),
            enclosure_type = item.enclosureType?.toFeedEnclosureType(),
            image_url = item.imageUrl?.toPhotoUrl(),
            thumbnail_url = item.thumbnailUrl?.toPhotoUrl(),
            link = item.link?.toFeedUrl(),
            feed_id = feedId,
            id = itemId,
            duration = item.duration?.toFeedItemDuration(),
        )
    }

    queries.feedItemsDeleteOldByFeedId(feedId, itemIds)
}


fun TransactionCallbacks.deleteFeedsByChatId(
    chatId: ChatId,
    queries: SphinxDatabaseQueries
) {
    queries.feedGetAllByChatId(chatId).executeAsList()?.forEach { feedDbo ->
        queries.feedItemsDeleteByFeedId(feedDbo.id)
        queries.feedModelDeleteById(feedDbo.id)
        queries.feedDestinationDeleteByFeedId(feedDbo.id)
        queries.feedDeleteById(feedDbo.id)
    }
}

fun TransactionCallbacks.deleteFeedById(
    feedId: FeedId,
    queries: SphinxDatabaseQueries
) {
    queries.feedGetById(feedId).executeAsOneOrNull()?.let { feedDbo ->
        queries.feedItemsDeleteByFeedId(feedDbo.id)
        queries.feedModelDeleteById(feedDbo.id)
        queries.feedDestinationDeleteByFeedId(feedDbo.id)
        queries.feedDeleteById(feedDbo.id)
    }
}

@Suppress("NOTHING_TO_INLINE", "SpellCheckingInspection")
inline fun TransactionCallbacks.updateSubscriptionStatus(
    queries: SphinxDatabaseQueries,
    subscribed: Subscribed,
    feedId: FeedId
) {
    queries.feedUpdateSubscribe(subscribed, feedId)
    queries.contentFeedStatusUpdateSubscriptionStatus(subscribed, feedId)
}

@Suppress("NOTHING_TO_INLINE")
inline fun TransactionCallbacks.upsertLsat(
    lsat: Lsat,
    queries: SphinxDatabaseQueries
) {
    queries.lsatUpsert(
        lsat.macaroon,
        lsat.paymentRequest,
        lsat.issuer,
        lsat.metaData,
        lsat.paths,
        lsat.preimage,
        lsat.status,
        lsat.createdAt,
        lsat.id
    )
}


fun TransactionCallbacks.clearDatabase(
    queries: SphinxDatabaseQueries
) {
    queries.chatDeleteAll()
    queries.contactDeleteAll()
    queries.inviteDeleteAll()
    queries.dashboardDeleteAll()
    queries.messageDeleteAll()
    queries.messageMediaDeleteAll()
    queries.subscriptionDeleteAll()
    queries.feedDeleteAll()
    queries.feedItemDeleteAll()
    queries.feedModelDeleteAll()
    queries.feedDestinationDeleteAll()
    queries.actionTrackDeleteAll()
    queries.contentFeedStatusDeleteAll()
    queries.contentEpisodeStatusDeleteAll()
}
