package chat.sphinx.feature_coredb

import chat.sphinx.concept_coredb.CoreDB
import chat.sphinx.concept_coredb.SphinxDatabase
import chat.sphinx.conceptcoredb.ActionTrackDbo
import chat.sphinx.conceptcoredb.ChatDbo
import chat.sphinx.conceptcoredb.ContactDbo
import chat.sphinx.conceptcoredb.ContentEpisodeStatusDbo
import chat.sphinx.conceptcoredb.ContentFeedStatusDbo
import chat.sphinx.conceptcoredb.DashboardDbo
import chat.sphinx.conceptcoredb.FeedDbo
import chat.sphinx.conceptcoredb.FeedDestinationDbo
import chat.sphinx.conceptcoredb.FeedItemDbo
import chat.sphinx.conceptcoredb.FeedModelDbo
import chat.sphinx.conceptcoredb.InviteDbo
import chat.sphinx.conceptcoredb.LsatDbo
import chat.sphinx.conceptcoredb.MessageDbo
import chat.sphinx.conceptcoredb.MessageMediaDbo
import chat.sphinx.conceptcoredb.ServerDbo
import chat.sphinx.conceptcoredb.SphinxDatabaseQueries
import chat.sphinx.conceptcoredb.SubscriptionDbo
import chat.sphinx.feature_coredb.adapters.action_track.ActionTrackIdAdapter
import chat.sphinx.feature_coredb.adapters.action_track.ActionTrackMetaDataAdapter
import chat.sphinx.feature_coredb.adapters.action_track.ActionTrackTypeAdapter
import chat.sphinx.feature_coredb.adapters.action_track.ActionTrackUploadedAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatAliasAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatGroupKeyAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatHostAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatMetaDataAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatMutedAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatNameAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatPrivateAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatStatusAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatTypeAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatUUIDAdapter
import chat.sphinx.feature_coredb.adapters.chat.ChatUnlistedAdapter
import chat.sphinx.feature_coredb.adapters.chat.NotifyAdapter
import chat.sphinx.feature_coredb.adapters.chat.RemoteTimezoneIdentifierAdapter
import chat.sphinx.feature_coredb.adapters.chat.SecondBrainUrlAdapter
import chat.sphinx.feature_coredb.adapters.chat.TimezoneEnabledAdapter
import chat.sphinx.feature_coredb.adapters.chat.TimezoneIdentifierAdapter
import chat.sphinx.feature_coredb.adapters.chat.TimezoneUpdatedAdapter
import chat.sphinx.feature_coredb.adapters.common.ChatIdAdapter
import chat.sphinx.feature_coredb.adapters.common.ContactIdAdapter
import chat.sphinx.feature_coredb.adapters.common.ContactIdsAdapter
import chat.sphinx.feature_coredb.adapters.common.DashboardIdAdapter
import chat.sphinx.feature_coredb.adapters.common.DateTimeAdapter
import chat.sphinx.feature_coredb.adapters.common.FileAdapter
import chat.sphinx.feature_coredb.adapters.common.InviteIdAdapter
import chat.sphinx.feature_coredb.adapters.common.InviteStatusAdapter
import chat.sphinx.feature_coredb.adapters.common.LightningNodePubKeyAdapter
import chat.sphinx.feature_coredb.adapters.common.LightningPaymentHashAdapter
import chat.sphinx.feature_coredb.adapters.common.LightningPaymentRequestAdapter
import chat.sphinx.feature_coredb.adapters.common.MessageIdAdapter
import chat.sphinx.feature_coredb.adapters.common.PhotoUrlAdapter
import chat.sphinx.feature_coredb.adapters.common.PinMessageAdapter
import chat.sphinx.feature_coredb.adapters.common.SatAdapter
import chat.sphinx.feature_coredb.adapters.common.SeenAdapter
import chat.sphinx.feature_coredb.adapters.common.SubscriptionIdAdapter
import chat.sphinx.feature_coredb.adapters.contact.BlockedAdapter
import chat.sphinx.feature_coredb.adapters.contact.ContactAliasAdapter
import chat.sphinx.feature_coredb.adapters.contact.ContactOwnerAdapter
import chat.sphinx.feature_coredb.adapters.contact.ContactPublicKeyAdapter
import chat.sphinx.feature_coredb.adapters.contact.ContactStatusAdapter
import chat.sphinx.feature_coredb.adapters.contact.DeviceIdAdapter
import chat.sphinx.feature_coredb.adapters.contact.LightningNodeAliasAdapter
import chat.sphinx.feature_coredb.adapters.contact.LightningRouteHintAdapter
import chat.sphinx.feature_coredb.adapters.contact.NotificationSoundAdapter
import chat.sphinx.feature_coredb.adapters.contact.PrivatePhotoAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedAuthorAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedChapterDataAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedContentTypeAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedDescriptionAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedDestinationAddressAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedDestinationSplitAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedDestinationTypeAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedEnclosureLengthAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedEnclosureTypeAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedGeneratorAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedIdAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedItemDurationAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedItemsCountAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedLanguageAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedModelSuggestedAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedModelTypeAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedReferenceIdAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedTitleAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedTypeAdapter
import chat.sphinx.feature_coredb.adapters.feed.FeedUrlAdapter
import chat.sphinx.feature_coredb.adapters.feed.PlayerSpeedAdapter
import chat.sphinx.feature_coredb.adapters.feed.SubscribedAdapter
import chat.sphinx.feature_coredb.adapters.invite.InviteCodeAdapter
import chat.sphinx.feature_coredb.adapters.invite.InviteStringAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatIdentifierAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatIssuerAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatMetaDataAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatPathsAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatPreImageAdapter
import chat.sphinx.feature_coredb.adapters.lsp.LsatStatusAdapter
import chat.sphinx.feature_coredb.adapters.lsp.MacaroonAdapter
import chat.sphinx.feature_coredb.adapters.media.FileNameAdapter
import chat.sphinx.feature_coredb.adapters.media.MediaKeyAdapter
import chat.sphinx.feature_coredb.adapters.media.MediaKeyDecryptedAdapter
import chat.sphinx.feature_coredb.adapters.media.MediaTokenAdapter
import chat.sphinx.feature_coredb.adapters.media.MediaTypeAdapter
import chat.sphinx.feature_coredb.adapters.message.ErrorMessageAdapter
import chat.sphinx.feature_coredb.adapters.message.FlaggedAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageContentAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageContentDecryptedAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageMUIDAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageStatusAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageTypeAdapter
import chat.sphinx.feature_coredb.adapters.message.MessageUUIDAdapter
import chat.sphinx.feature_coredb.adapters.message.PersonAdapter
import chat.sphinx.feature_coredb.adapters.message.PushAdapter
import chat.sphinx.feature_coredb.adapters.message.RecipientAliasAdapter
import chat.sphinx.feature_coredb.adapters.message.ReplyUUIDAdapter
import chat.sphinx.feature_coredb.adapters.message.SenderAliasAdapter
import chat.sphinx.feature_coredb.adapters.message.TagMessageAdapter
import chat.sphinx.feature_coredb.adapters.message.ThreadUUIDAdapter
import chat.sphinx.feature_coredb.adapters.server.IpAdapter
import chat.sphinx.feature_coredb.adapters.subscription.CronAdapter
import chat.sphinx.feature_coredb.adapters.subscription.EndNumberAdapter
import chat.sphinx.feature_coredb.adapters.subscription.SubscriptionCountAdapter
import com.squareup.moshi.Moshi
import com.squareup.sqldelight.db.SqlDriver
import io.matthewnelson.concept_encryption_key.EncryptionKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

abstract class CoreDBImpl(private val moshi: Moshi): CoreDB() {

    companion object {
        const val DB_NAME = "sphinx.db"
    }

    private val sphinxDatabaseQueriesStateFlow: MutableStateFlow<SphinxDatabaseQueries?> =
        MutableStateFlow(null)

    override val isInitialized: Boolean
        get() = sphinxDatabaseQueriesStateFlow.value != null

    override fun getSphinxDatabaseQueriesOrNull(): SphinxDatabaseQueries? {
        return sphinxDatabaseQueriesStateFlow.value
    }

    protected abstract fun getSqlDriver(encryptionKey: EncryptionKey): SqlDriver

    private val initializationLock = Object()

    fun initializeDatabase(encryptionKey: EncryptionKey) {
        if (isInitialized) {
            return
        }

        synchronized(initializationLock) {

            if (isInitialized) {
                return
            }

            sphinxDatabaseQueriesStateFlow.value = SphinxDatabase(
                driver = getSqlDriver(encryptionKey),
                chatDboAdapter = ChatDbo.Adapter(
                    idAdapter = ChatIdAdapter.getInstance(),
                    uuidAdapter = ChatUUIDAdapter(),
                    nameAdapter = ChatNameAdapter(),
                    photo_urlAdapter = PhotoUrlAdapter.getInstance(),
                    typeAdapter = ChatTypeAdapter(),
                    statusAdapter = ChatStatusAdapter(),
                    contact_idsAdapter = ContactIdsAdapter.getInstance(),
                    is_mutedAdapter = ChatMutedAdapter.getInstance(),
                    created_atAdapter = DateTimeAdapter.getInstance(),
                    group_keyAdapter = ChatGroupKeyAdapter(),
                    hostAdapter = ChatHostAdapter(),
                    price_per_messageAdapter = SatAdapter.getInstance(),
                    escrow_amountAdapter = SatAdapter.getInstance(),
                    unlistedAdapter = ChatUnlistedAdapter(),
                    private_tribeAdapter = ChatPrivateAdapter(),
                    owner_pub_keyAdapter = LightningNodePubKeyAdapter.getInstance(),
                    seenAdapter = SeenAdapter.getInstance(),
                    meta_dataAdapter = ChatMetaDataAdapter(moshi),
                    my_photo_urlAdapter = PhotoUrlAdapter.getInstance(),
                    my_aliasAdapter = ChatAliasAdapter(),
                    pending_contact_idsAdapter = ContactIdsAdapter.getInstance(),
                    latest_message_idAdapter = MessageIdAdapter.getInstance(),
                    content_seen_atAdapter = DateTimeAdapter.getInstance(),
                    notifyAdapter = NotifyAdapter(),
                    pin_messageAdapter = PinMessageAdapter.getInstance(),
                    second_brain_urlAdapter = SecondBrainUrlAdapter(),
                    timezone_enabledAdapter = TimezoneEnabledAdapter(),
                    timezone_updatedAdapter = TimezoneUpdatedAdapter(),
                    remote_timezone_identifierAdapter = RemoteTimezoneIdentifierAdapter(),
                    timezone_identifierAdapter = TimezoneIdentifierAdapter()
                ),
                contactDboAdapter = ContactDbo.Adapter(
                    idAdapter = ContactIdAdapter.getInstance(),
                    route_hintAdapter = LightningRouteHintAdapter(),
                    node_pub_keyAdapter = LightningNodePubKeyAdapter.getInstance(),
                    node_aliasAdapter = LightningNodeAliasAdapter(),
                    aliasAdapter = ContactAliasAdapter(),
                    photo_urlAdapter = PhotoUrlAdapter.getInstance(),
                    private_photoAdapter = PrivatePhotoAdapter(),
                    ownerAdapter = ContactOwnerAdapter(),
                    statusAdapter = ContactStatusAdapter(),
                    public_keyAdapter = ContactPublicKeyAdapter(),
                    device_idAdapter = DeviceIdAdapter(),
                    created_atAdapter = DateTimeAdapter.getInstance(),
                    updated_atAdapter = DateTimeAdapter.getInstance(),
                    notification_soundAdapter = NotificationSoundAdapter(),
                    tip_amountAdapter = SatAdapter.getInstance(),
                    invite_idAdapter = InviteIdAdapter.getInstance(),
                    invite_statusAdapter = InviteStatusAdapter.getInstance(),
                    blockedAdapter = BlockedAdapter.getInstance(),
                ),
                inviteDboAdapter = InviteDbo.Adapter(
                    idAdapter = InviteIdAdapter.getInstance(),
                    invite_stringAdapter = InviteStringAdapter(),
                    invite_codeAdapter = InviteCodeAdapter(),
                    invoiceAdapter = LightningPaymentRequestAdapter.getInstance(),
                    contact_idAdapter = ContactIdAdapter.getInstance(),
                    statusAdapter = InviteStatusAdapter.getInstance(),
                    priceAdapter = SatAdapter.getInstance(),
                    created_atAdapter = DateTimeAdapter.getInstance(),
                ),
                dashboardDboAdapter = DashboardDbo.Adapter(
                    idAdapter = DashboardIdAdapter(),
                    contact_idAdapter = ContactIdAdapter.getInstance(),
                    dateAdapter = DateTimeAdapter.getInstance(),
                    mutedAdapter = ChatMutedAdapter.getInstance(),
                    seenAdapter = SeenAdapter.getInstance(),
                    photo_urlAdapter = PhotoUrlAdapter.getInstance(),
                    latest_message_idAdapter = MessageIdAdapter.getInstance()
                ),
                messageDboAdapter = MessageDbo.Adapter(
                    idAdapter = MessageIdAdapter.getInstance(),
                    uuidAdapter = MessageUUIDAdapter(),
                    chat_idAdapter = ChatIdAdapter.getInstance(),
                    typeAdapter = MessageTypeAdapter(),
                    senderAdapter = ContactIdAdapter.getInstance(),
                    receiver_Adapter = ContactIdAdapter.getInstance(),
                    amountAdapter = SatAdapter.getInstance(),
                    payment_hashAdapter = LightningPaymentHashAdapter.getInstance(),
                    payment_requestAdapter = LightningPaymentRequestAdapter.getInstance(),
                    dateAdapter = DateTimeAdapter.getInstance(),
                    expiration_dateAdapter = DateTimeAdapter.getInstance(),
                    message_contentAdapter = MessageContentAdapter(),
                    message_content_decryptedAdapter = MessageContentDecryptedAdapter(),
                    statusAdapter = MessageStatusAdapter(),
                    seenAdapter = SeenAdapter.getInstance(),
                    sender_aliasAdapter = SenderAliasAdapter(),
                    sender_picAdapter = PhotoUrlAdapter.getInstance(),
                    original_muidAdapter = MessageMUIDAdapter(),
                    reply_uuidAdapter = ReplyUUIDAdapter(),
                    muidAdapter = MessageMUIDAdapter(),
                    flaggedAdapter = FlaggedAdapter.getInstance(),
                    recipient_aliasAdapter = RecipientAliasAdapter(),
                    recipient_picAdapter = PhotoUrlAdapter.getInstance(),
                    pushAdapter = PushAdapter(),
                    personAdapter = PersonAdapter(),
                    thread_uuidAdapter = ThreadUUIDAdapter(),
                    error_messageAdapter = ErrorMessageAdapter(),
                    tag_messageAdapter = TagMessageAdapter(),
                    remote_timezone_identifierAdapter = RemoteTimezoneIdentifierAdapter()
                ),
                messageMediaDboAdapter = MessageMediaDbo.Adapter(
                    idAdapter = MessageIdAdapter.getInstance(),
                    chat_idAdapter = ChatIdAdapter.getInstance(),
                    media_keyAdapter = MediaKeyAdapter(),
                    media_key_decryptedAdapter = MediaKeyDecryptedAdapter(),
                    media_typeAdapter = MediaTypeAdapter(),
                    media_tokenAdapter = MediaTokenAdapter(),
                    local_fileAdapter = FileAdapter.getInstance(),
                    file_nameAdapter = FileNameAdapter(),
                ),
                subscriptionDboAdapter = SubscriptionDbo.Adapter(
                    idAdapter = SubscriptionIdAdapter.getInstance(),
                    cronAdapter = CronAdapter(),
                    amountAdapter = SatAdapter.getInstance(),
                    end_numberAdapter = EndNumberAdapter(),
                    countAdapter = SubscriptionCountAdapter(),
                    end_dateAdapter = DateTimeAdapter.getInstance(),
                    created_atAdapter = DateTimeAdapter.getInstance(),
                    updated_atAdapter = DateTimeAdapter.getInstance(),
                    chat_idAdapter = ChatIdAdapter.getInstance(),
                    contact_idAdapter = ContactIdAdapter.getInstance()
                ),
                feedDboAdapter = FeedDbo.Adapter(
                    idAdapter = FeedIdAdapter(),
                    feed_typeAdapter = FeedTypeAdapter(),
                    titleAdapter = FeedTitleAdapter(),
                    descriptionAdapter = FeedDescriptionAdapter(),
                    feed_urlAdapter = FeedUrlAdapter.getInstance(),
                    authorAdapter = FeedAuthorAdapter(),
                    generatorAdapter = FeedGeneratorAdapter(),
                    image_urlAdapter = PhotoUrlAdapter.getInstance(),
                    owner_urlAdapter = FeedUrlAdapter.getInstance(),
                    linkAdapter = FeedUrlAdapter.getInstance(),
                    date_publishedAdapter = DateTimeAdapter.getInstance(),
                    date_updatedAdapter = DateTimeAdapter.getInstance(),
                    content_typeAdapter = FeedContentTypeAdapter(),
                    languageAdapter = FeedLanguageAdapter(),
                    items_countAdapter = FeedItemsCountAdapter(),
                    current_item_idAdapter = FeedIdAdapter(),
                    chat_idAdapter = ChatIdAdapter.getInstance(),
                    subscribedAdapter = SubscribedAdapter.getInstance(),
                    last_playedAdapter = DateTimeAdapter.getInstance()
                ),
                feedItemDboAdapter = FeedItemDbo.Adapter(
                    idAdapter = FeedIdAdapter(),
                    titleAdapter = FeedTitleAdapter(),
                    descriptionAdapter = FeedDescriptionAdapter(),
                    date_publishedAdapter = DateTimeAdapter.getInstance(),
                    date_updatedAdapter = DateTimeAdapter.getInstance(),
                    authorAdapter = FeedAuthorAdapter(),
                    content_typeAdapter = FeedContentTypeAdapter(),
                    enclosure_lengthAdapter = FeedEnclosureLengthAdapter(),
                    enclosure_urlAdapter = FeedUrlAdapter.getInstance(),
                    enclosure_typeAdapter = FeedEnclosureTypeAdapter(),
                    image_urlAdapter = PhotoUrlAdapter.getInstance(),
                    thumbnail_urlAdapter = PhotoUrlAdapter.getInstance(),
                    linkAdapter = FeedUrlAdapter.getInstance(),
                    feed_idAdapter = FeedIdAdapter(),
                    durationAdapter = FeedItemDurationAdapter(),
                    local_fileAdapter = FileAdapter.getInstance(),
                    reference_idAdapter = FeedReferenceIdAdapter(),
                    chapters_dataAdapter = FeedChapterDataAdapter(),
                ),
                feedModelDboAdapter = FeedModelDbo.Adapter(
                    idAdapter = FeedIdAdapter(),
                    typeAdapter = FeedModelTypeAdapter(),
                    suggestedAdapter = FeedModelSuggestedAdapter()
                ),
                feedDestinationDboAdapter = FeedDestinationDbo.Adapter(
                    addressAdapter = FeedDestinationAddressAdapter(),
                    splitAdapter = FeedDestinationSplitAdapter(),
                    typeAdapter = FeedDestinationTypeAdapter(),
                    feed_idAdapter = FeedIdAdapter()
                ),
                actionTrackDboAdapter = ActionTrackDbo.Adapter(
                    idAdapter = ActionTrackIdAdapter(),
                    typeAdapter = ActionTrackTypeAdapter(),
                    meta_dataAdapter = ActionTrackMetaDataAdapter(),
                    uploadedAdapter = ActionTrackUploadedAdapter()
                ),
                contentFeedStatusDboAdapter = ContentFeedStatusDbo.Adapter(
                    feed_idAdapter = FeedIdAdapter(),
                    feed_urlAdapter = FeedUrlAdapter.getInstance(),
                    subscription_statusAdapter = SubscribedAdapter.getInstance(),
                    chat_idAdapter = ChatIdAdapter.getInstance(),
                    item_idAdapter = FeedIdAdapter(),
                    sats_per_minuteAdapter = SatAdapter.getInstance(),
                    player_speedAdapter = PlayerSpeedAdapter()
                ),
                contentEpisodeStatusDboAdapter = ContentEpisodeStatusDbo.Adapter(
                    feed_idAdapter = FeedIdAdapter(),
                    item_idAdapter = FeedIdAdapter(),
                    durationAdapter = FeedItemDurationAdapter(),
                    current_timeAdapter = FeedItemDurationAdapter()
                ),
                serverDboAdapter = ServerDbo.Adapter(
                    ipAdapter = IpAdapter(),
                    pub_keyAdapter = LightningNodePubKeyAdapter.getInstance()
                ),
                lsatDboAdapter = LsatDbo.Adapter(
                    idAdapter = LsatIdentifierAdapter(),
                    macaroonAdapter = MacaroonAdapter(),
                    payment_requestAdapter = LightningPaymentRequestAdapter.getInstance(),
                    issuerAdapter = LsatIssuerAdapter(),
                    meta_dataAdapter = LsatMetaDataAdapter(),
                    pathsAdapter = LsatPathsAdapter(),
                    preimageAdapter = LsatPreImageAdapter(),
                    statusAdapter = LsatStatusAdapter(),
                    created_atAdapter = DateTimeAdapter.getInstance()
                )
            ).sphinxDatabaseQueries
        }
    }

    private class Hackery(val hack: SphinxDatabaseQueries): Exception()

    override suspend fun getSphinxDatabaseQueries(): SphinxDatabaseQueries {
        sphinxDatabaseQueriesStateFlow.value?.let { queries ->
            return queries
        }

        var queries: SphinxDatabaseQueries? = null

        try {
            sphinxDatabaseQueriesStateFlow.collect { queriesState ->
                if (queriesState != null) {
                    queries = queriesState
                    throw Hackery(queriesState)
                }
            }
        } catch (e: Hackery) {
            return e.hack
        }

        // Will never make it here, but to please the IDE just in case...
        delay(25L)
        return queries!!
    }
}
