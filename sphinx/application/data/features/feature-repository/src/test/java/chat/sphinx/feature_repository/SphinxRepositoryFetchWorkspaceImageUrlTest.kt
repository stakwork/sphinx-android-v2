package chat.sphinx.feature_repository

import chat.sphinx.concept_coredb.CoreDB
import chat.sphinx.concept_crypto_rsa.RSA
import chat.sphinx.concept_crypto_rsa.KeySize
import chat.sphinx.concept_crypto_rsa.SignatureAlgorithm
import chat.sphinx.concept_data_sync.DataSyncManager
import chat.sphinx.concept_data_sync.DataSyncManagerListener
import chat.sphinx.concept_data_sync.model.SyncStatus
import chat.sphinx.concept_meme_input_stream.MemeInputStreamHandler
import chat.sphinx.concept_meme_server.MemeServerTokenHandler
import chat.sphinx.concept_network_query_chat.NetworkQueryChat
import chat.sphinx.concept_network_query_chat.model.NewTribeDto
import chat.sphinx.concept_network_query_chat.model.feed.FeedDto
import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_network_query_contact.model.AccountConfigV2Response
import chat.sphinx.concept_network_query_discover_tribes.NetworkQueryDiscoverTribes
import chat.sphinx.concept_network_query_feed_search.NetworkQueryFeedSearch
import chat.sphinx.concept_network_query_feed_search.model.CreateProjectResponseDto
import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeDetailsDto
import chat.sphinx.concept_network_query_feed_search.model.EpisodeNodeResponseDto
import chat.sphinx.concept_network_query_feed_search.model.FeedSearchResultDto
import chat.sphinx.concept_network_query_feed_status.NetworkQueryFeedStatus
import chat.sphinx.concept_network_query_hive.NetworkQueryHive
import chat.sphinx.concept_network_query_hive.model.HiveAuthenticationTokenDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceImageDto
import chat.sphinx.concept_network_query_hive.model.WorkspacesListDto
import chat.sphinx.concept_network_query_invite.NetworkQueryInvite
import chat.sphinx.concept_network_query_invite.model.HubLowestNodePriceResponse
import chat.sphinx.concept_network_query_invite.model.HubRedeemInviteResponse
import chat.sphinx.concept_network_query_meme_server.NetworkQueryMemeServer
import chat.sphinx.concept_network_query_meme_server.model.MemeServerAuthenticationDto
import chat.sphinx.concept_network_query_meme_server.model.MemeServerAuthenticationTokenDto
import chat.sphinx.concept_network_query_meme_server.model.PaymentTemplateDto
import chat.sphinx.concept_network_query_meme_server.model.PostMemeServerUploadDto
import chat.sphinx.concept_network_query_people.NetworkQueryPeople
import chat.sphinx.concept_network_query_people.model.BadgeDto
import chat.sphinx.concept_network_query_people.model.CallTokenDto
import chat.sphinx.concept_network_query_people.model.ChatLeaderboardDto
import chat.sphinx.concept_network_query_people.model.GetExternalRequestDto
import chat.sphinx.concept_network_query_people.model.TribeMemberProfileDto
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_network_query_verify_external.model.ChallengeExternalDto
import chat.sphinx.concept_network_query_verify_external.model.PersonInfoDto
import chat.sphinx.concept_network_query_verify_external.model.RedeemSatsDto
import chat.sphinx.concept_network_query_verify_external.model.VerifyExternalInfoDto
import chat.sphinx.concept_relay.CustomException
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.conceptcoredb.SphinxDatabaseQueries
import chat.sphinx.example.concept_connect_manager.ConnectManager
import chat.sphinx.example.concept_connect_manager.ConnectManagerListener
import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.concept_connect_manager.model.RestoreState
import chat.sphinx.example.wrapper_mqtt.MsgsCounts
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.LogType
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.notification.SphinxNotificationManager
import chat.sphinx.wrapper_chat.ChatHost
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.ChapterResponseDto
import chat.sphinx.wrapper_common.contact.Blocked
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.feed.FeedType
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.datasync.DataSync
import chat.sphinx.wrapper_common.feed.FeedUrl
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.ContactFromGroup
import chat.sphinx.wrapper_contact.ContactStatus
import chat.sphinx.wrapper_contact.Owner
import chat.sphinx.wrapper_contact.PrivatePhoto
import chat.sphinx.wrapper_feed.FeedReferenceId
import chat.sphinx.wrapper_feed.FeedTitle
import chat.sphinx.wrapper_invite.InviteString
import chat.sphinx.wrapper_io_utils.InputStreamProvider
import chat.sphinx.wrapper_lightning.WalletMnemonic
import chat.sphinx.wrapper_meme_server.AuthenticationId
import chat.sphinx.wrapper_meme_server.AuthenticationSig
import chat.sphinx.wrapper_meme_server.AuthenticationToken
import chat.sphinx.wrapper_message.MessagePerson
import chat.sphinx.wrapper_message_media.FileName
import chat.sphinx.wrapper_message_media.MediaKeyDecrypted
import chat.sphinx.wrapper_message_media.MediaType
import chat.sphinx.wrapper_message_media.token.MediaHost
import chat.sphinx.wrapper_podcast.PodcastEpisode
import chat.sphinx.wrapper_relay.AuthorizationToken
import chat.sphinx.wrapper_relay.RelayUrl
import chat.sphinx.wrapper_rsa.PKCSType
import chat.sphinx.wrapper_rsa.RSAKeyPair
import chat.sphinx.wrapper_rsa.RsaPrivateKey
import chat.sphinx.wrapper_rsa.RsaPublicKey
import chat.sphinx.wrapper_rsa.RsaSignedString
import chat.sphinx.wrapper_contact.NewContact
import com.squareup.moshi.Moshi
import io.matthewnelson.concept_authentication.data.AuthenticationStorage
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_media_cache.MediaCacheHandler
import io.matthewnelson.crypto_common.clazzes.EncryptedString
import io.matthewnelson.crypto_common.clazzes.Password
import io.matthewnelson.crypto_common.clazzes.UnencryptedByteArray
import io.matthewnelson.crypto_common.clazzes.UnencryptedString
import io.matthewnelson.test_concept_coroutines.CoroutineTestHelper
import io.matthewnelson.test_feature_authentication_core.TestAuthenticationCoreManager
import io.matthewnelson.test_feature_authentication_core.TestAuthenticationCoreStorage
import io.matthewnelson.test_feature_authentication_core.TestAuthenticationManagerInitializer
import io.matthewnelson.test_feature_authentication_core.TestEncryptionKeyHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class FakeImageAuthStorage : AuthenticationStorage {
    val map: MutableMap<String, String?> = LinkedHashMap()
    var removeCallCount = 0

    fun clear() {
        map.clear()
        removeCallCount = 0
    }

    override suspend fun getString(key: String, defaultValue: String?) =
        if (map.containsKey(key)) map[key] else defaultValue

    override suspend fun putString(key: String, value: String?) {
        map[key] = value
    }

    override suspend fun removeString(key: String) {
        removeCallCount++
        map.remove(key)
    }
}

class FakeCoreDB : CoreDB() {
    override val isInitialized: Boolean = true
    override fun getSphinxDatabaseQueriesOrNull(): SphinxDatabaseQueries? = null
    override suspend fun getSphinxDatabaseQueries(): SphinxDatabaseQueries {
        throw UnsupportedOperationException("CoreDB not used in workspace image tests")
    }
}

class FakeConnectManager(
    private val signedTs: () -> String? = { "signed_ts" },
) : ConnectManager() {
    override val ownerInfoStateFlow: StateFlow<OwnerInfo?> = MutableStateFlow(null)
    override val restoreStateFlow: StateFlow<RestoreState?> = MutableStateFlow(null)
    override val msgsCountsState: MutableStateFlow<MsgsCounts?> = MutableStateFlow(null)
    override fun setOwnerInfo(ownerInfo: OwnerInfo) {}
    override fun createAccount(mnemonic: String?) {}
    override fun restoreAccount(
        defaultTribe: String?,
        tribeHost: String?,
        mixerServerIp: String?,
        routerUrl: String?,
    ) {}
    override fun restoreFailed() {}
    override fun finishRestore() {}
    override fun setInviteCode(inviteString: String?) {}
    override fun setMnemonicWords(words: List<String>?) {}
    override fun setNetworkType(isTestEnvironment: Boolean) {}
    override fun setOwnerDeviceId(deviceId: String, pushKey: String) {}
    override fun processChallengeSignature(challenge: String): String? = null
    override fun fetchFirstMessagesPerKey(lastMsgIdx: Long, totalCount: Long?) {}
    override fun fetchMessagesOnRestoreAccount(
        totalHighestIndex: Long,
        chatsTotal: Long,
        chatsPublicKeys: List<String>,
    ) {}
    override fun fetchMessagesPerContact(minIndex: Long, publicKey: String) {}
    override fun getAllMessagesCount() {}
    override fun initializeMqttAndSubscribe(
        serverUri: String,
        mnemonicWords: WalletMnemonic,
        ownerInfo: OwnerInfo,
    ) {}
    override fun reconnectWithBackOff() {}
    override fun attemptReconnectOnResume() {}
    override fun retrieveLspIp(): String? = null
    override fun resetMQTT() {}
    override fun createContact(contact: NewContact) {}
    override fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        serverDefaultTribe: String?,
        tribeServerIp: String?,
        mixerIp: String?,
    ) {}
    override fun deleteInvite(inviteString: String) {}
    override fun deleteContact(pubKey: String) {}
    override fun setReadMessage(contactPubKey: String, messageIndex: Long) {}
    override fun getReadMessages() {}
    override fun setMute(muteLevel: Int, contactPubKey: String) {}
    override fun getMutedChats() {}
    override fun addNodesFromResponse(nodesJson: String) {}
    override fun concatNodesFromResponse(nodesJson: String, routerPubKey: String, amount: Long) {}
    override fun fetchMessagesOnAppInit(lastMsgIdx: Long?, reverse: Boolean) {}
    override fun sendMessage(
        sphinxMessage: String,
        contactPubKey: String,
        provisionalId: Long,
        messageType: Int,
        amount: Long?,
        myAlias: String?,
        myPhotoUrl: String?,
        date: Long,
        isTribe: Boolean,
    ) {}
    override fun deleteMessage(
        sphinxMessage: String,
        contactPubKey: String,
        myAlias: String?,
        myPhotoUrl: String?,
        isTribe: Boolean,
    ) {}
    override fun deleteContactMessages(messageIndexList: List<Long>) {}
    override fun deletePubKeyMessages(contactPubKey: String) {}
    override fun getMessagesStatusByTags(tags: List<String>) {}
    override fun createTribe(tribeJson: String) {}
    override fun joinToTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        isPrivate: Boolean,
        userAlias: String,
        priceToJoin: Long,
    ) {}
    override fun retrieveTribeMembersList(tribeServerPubKey: String, tribePubKey: String) {}
    override fun getTribeServerPubKey(): String? = null
    override fun editTribe(tribeJson: String) {}
    override fun createInvoice(amount: Long, memo: String): Pair<String, String>? = null
    override fun sendKeySend(pubKey: String, amount: Long, routeHint: String?, data: String?) {}
    override fun processContactInvoicePayment(paymentRequest: String) {}
    override fun processInvoicePayment(paymentRequest: String, milliSatAmount: Long): String? = null
    override fun payInvoiceFromLSP(paymentRequest: String) {}
    override fun retrievePaymentHash(paymentRequest: String): String? = null
    override fun getPayments(
        lastMsgDate: Long,
        limit: Int,
        scid: Long?,
        remoteOnly: Boolean?,
        minMsat: Long?,
        reverse: Boolean?,
    ) {}
    override fun getPubKeyByEncryptedChild(child: String, pushKey: String?): String? = null
    override fun generateMediaToken(
        contactPubKey: String,
        muid: String,
        host: String,
        metaData: String?,
        amount: Long?,
    ): String? = null
    override fun getInvoiceInfo(invoice: String): String? = null
    override fun isRouteAvailable(pubKey: String, routeHint: String?, milliSat: Long): Boolean = false
    override fun getSignedTimeStamps(): String? = signedTs()
    override fun getSignBase64(text: String): String? = null
    override fun getIdFromMacaroon(macaroon: String): String? = null
    override fun addListener(listener: ConnectManagerListener): Boolean = true
    override fun removeListener(listener: ConnectManagerListener): Boolean = true
    override fun saveMessagesCounts(msgsCounts: MsgsCounts) {}
    override fun encryptDataSync(value: String): String? = null
    override fun decryptDataSync(value: String): String? = null
}

fun unusedFlow(): Nothing = error("unused")

class UnusedNetworkQueryChat : NetworkQueryChat() {
    override fun getTribeInfo(
        host: ChatHost,
        tribePubKey: LightningNodePubKey,
        isProductionEnvironment: Boolean,
    ): Flow<LoadResponse<NewTribeDto, ResponseError>> = flow { unusedFlow() }
    override fun getFeedContent(
        host: ChatHost,
        feedUrl: FeedUrl,
        chatUUID: ChatUUID?,
    ): Flow<LoadResponse<FeedDto, ResponseError>> = flow { unusedFlow() }
    override suspend fun startCallRecording(
        room: String,
        timestamp: String,
    ): Flow<LoadResponse<Any, ResponseError>> = flow { unusedFlow() }
    override suspend fun stopCallRecording(room: String): Flow<LoadResponse<Any, ResponseError>> =
        flow { unusedFlow() }
}

class UnusedNetworkQueryContact : NetworkQueryContact() {
    override fun hasAdmin(url: RelayUrl): Flow<LoadResponse<Any, ResponseError>> = flow { unusedFlow() }
    override fun getAccountConfig(isProductionEnvironment: Boolean): Flow<LoadResponse<AccountConfigV2Response, ResponseError>> =
        flow { unusedFlow() }
    override fun getNodes(routerUrl: String): Flow<LoadResponse<String, ResponseError>> = flow { unusedFlow() }
    override fun getRoutingNodes(
        routerUrl: String,
        lightningNodePubKey: LightningNodePubKey,
        milliSats: Long,
    ): Flow<LoadResponse<String, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryInvite : NetworkQueryInvite() {
    override fun getLowestNodePrice(): Flow<LoadResponse<HubLowestNodePriceResponse, ResponseError>> =
        flow { unusedFlow() }
    override fun redeemInvite(inviteString: InviteString): Flow<LoadResponse<HubRedeemInviteResponse, ResponseError>> =
        flow { unusedFlow() }
}

class UnusedNetworkQueryDiscoverTribes : NetworkQueryDiscoverTribes() {
    override fun getAllDiscoverTribes(
        page: Int,
        itemsPerPage: Int,
        searchTerm: String?,
        tags: String?,
        tribeServer: String?,
    ): Flow<LoadResponse<List<NewTribeDto>, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryMemeServer : NetworkQueryMemeServer() {
    override fun askMemeAuthentication(
        memeServerHost: MediaHost,
    ): Flow<LoadResponse<MemeServerAuthenticationDto, ResponseError>> = flow { unusedFlow() }
    override fun verifyAuthentication(
        id: AuthenticationId,
        sig: AuthenticationSig,
        ownerPubKey: LightningNodePubKey,
        memeServerHost: MediaHost,
    ): Flow<LoadResponse<MemeServerAuthenticationTokenDto, ResponseError>> = flow { unusedFlow() }
    override suspend fun getPaymentTemplates(
        authenticationToken: AuthenticationToken,
        memeServerHost: MediaHost,
        moshi: Moshi,
    ): Flow<LoadResponse<List<PaymentTemplateDto>, ResponseError>> = flow { unusedFlow() }
    override suspend fun uploadAttachmentEncrypted(
        authenticationToken: AuthenticationToken,
        mediaType: MediaType,
        file: File,
        fileName: FileName?,
        password: Password,
        memeServerHost: MediaHost,
    ): Response<PostMemeServerUploadDto, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun uploadAttachment(
        authenticationToken: AuthenticationToken,
        mediaType: MediaType,
        stream: InputStreamProvider,
        fileName: String,
        contentLength: Long?,
        memeServerHost: MediaHost,
    ): Response<PostMemeServerUploadDto, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun getDataSyncFile(
        authenticationToken: AuthenticationToken,
        memeServerHost: MediaHost,
    ): Flow<LoadResponse<String, ResponseError>> = flow { unusedFlow() }
    override suspend fun uploadDataSyncFile(
        authenticationToken: AuthenticationToken,
        memeServerHost: MediaHost,
        pubkey: String,
        data: String,
    ): Flow<LoadResponse<Boolean, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryPeople : NetworkQueryPeople() {
    override fun getExternalRequestByKey(
        host: String,
        key: String,
    ): Flow<LoadResponse<GetExternalRequestDto, ResponseError>> = flow { unusedFlow() }
    override fun getTribeMemberProfile(
        person: MessagePerson,
    ): Flow<LoadResponse<TribeMemberProfileDto, ResponseError>> = flow { unusedFlow() }
    override fun getLeaderboard(
        tribeUUID: ChatUUID,
    ): Flow<LoadResponse<List<ChatLeaderboardDto>, ResponseError>> = flow { unusedFlow() }
    override fun getKnownBadges(
        badgeIds: Array<Long>,
    ): Flow<LoadResponse<List<BadgeDto>, ResponseError>> = flow { unusedFlow() }
    override fun getBadgesByPerson(
        person: MessagePerson,
    ): Flow<LoadResponse<List<BadgeDto>, ResponseError>> = flow { unusedFlow() }
    override fun getLiveKitToken(
        room: String,
        alias: String,
        profilePictureUrl: String?,
    ): Flow<LoadResponse<CallTokenDto, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryAuthorizeExternal : NetworkQueryAuthorizeExternal() {
    override fun authorizeExternal(
        host: String,
        challenge: String,
        token: String,
        info: VerifyExternalInfoDto,
    ): Flow<LoadResponse<Any, ResponseError>> = flow { unusedFlow() }
    override fun requestNewChallenge(host: String): Flow<LoadResponse<ChallengeExternalDto, ResponseError>> =
        flow { unusedFlow() }
    override fun redeemSats(host: String, info: RedeemSatsDto): Flow<LoadResponse<Any, ResponseError>> =
        flow { unusedFlow() }
    override fun getPersonInfo(host: String, publicKey: String): Flow<LoadResponse<PersonInfoDto, ResponseError>> =
        flow { unusedFlow() }
    override fun createPeopleProfile(
        host: String,
        person: PersonInfoDto,
        token: String,
    ): Flow<LoadResponse<Any, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryFeedSearch : NetworkQueryFeedSearch() {
    override fun searchFeeds(
        searchTerm: String,
        feedType: FeedType,
    ): Flow<LoadResponse<List<FeedSearchResultDto>, ResponseError>> = flow { unusedFlow() }
    override fun checkIfEpisodeNodeExists(
        episode: PodcastEpisode?,
        feedTitle: FeedTitle?,
        youtubeVideoId: String?,
    ): Flow<LoadResponse<EpisodeNodeResponseDto, ResponseError>> = flow { unusedFlow() }
    override fun createStakworkProject(
        podcastEpisode: PodcastEpisode?,
        feedTitle: FeedTitle?,
        workflowId: Int,
        token: String,
        referenceId: FeedReferenceId,
        youtubeVideoId: String?,
    ): Flow<LoadResponse<CreateProjectResponseDto, ResponseError>> = flow { unusedFlow() }
    override fun getEpisodeNodeDetails(
        referenceId: FeedReferenceId,
    ): Flow<LoadResponse<EpisodeNodeDetailsDto, ResponseError>> = flow { unusedFlow() }
    override fun getChaptersData(
        referenceId: FeedReferenceId,
    ): Flow<LoadResponse<ChapterResponseDto, ResponseError>> = flow { unusedFlow() }
}

class UnusedNetworkQueryFeedStatus : NetworkQueryFeedStatus() {
    override suspend fun checkYoutubeVideoAvailable(videoId: String): String? = null
}

class UnusedMediaCacheHandler : MediaCacheHandler() {
    override fun createFile(mediaType: MediaType, extension: String?): File? = null
    override fun createAudioFile(extension: String): File = File("audio")
    override fun createImageFile(extension: String): File = File("image")
    override fun createVideoFile(extension: String): File = File("video")
    override fun createPdfFile(extension: String): File = File("pdf")
    override fun createPaidTextFile(extension: String): File = File("paid")
    override suspend fun copyTo(from: File, to: File): File = to
    override suspend fun copyTo(from: InputStream, to: File): File = to
    override suspend fun copyToWithCancellation(inputStream: InputStream, targetFile: File) {}
}

class UnusedMemeInputStreamHandler : MemeInputStreamHandler() {
    override suspend fun retrieveMediaInputStream(
        url: String,
        authenticationToken: AuthenticationToken?,
        mediaKeyDecrypted: MediaKeyDecrypted?,
    ): Pair<InputStream?, FileName?>? = null
}

class UnusedMemeServerTokenHandler : MemeServerTokenHandler() {
    override suspend fun retrieveAuthenticationToken(mediaHost: MediaHost): AuthenticationToken? = null
    override fun addListener(listener: ConnectManagerRepository) {}
}

class UnusedDataSyncManager : DataSyncManager() {
    override val dataSyncStateFlow: StateFlow<List<DataSync>> = MutableStateFlow(emptyList())
    override val syncStatusStateFlow: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
    override fun updateDataSyncList(dataSyncList: List<DataSync>) {}
    override suspend fun saveTipAmount(value: String) {}
    override suspend fun savePrivatePhoto(value: String) {}
    override suspend fun saveTimezoneForChat(
        chatPubkey: String,
        timezoneEnabled: Boolean,
        timezoneIdentifier: String,
    ) {}
    override suspend fun saveFeedStatus(
        feedId: String,
        chatPubkey: String,
        feedUrl: String,
        subscribed: Boolean,
        satsPerMinute: Int,
        playerSpeed: Double,
        itemId: String,
    ) {}
    override suspend fun saveFeedItemStatus(
        feedId: String,
        itemId: String,
        duration: Int,
        currentTime: Int,
    ) {}
    override suspend fun syncWithServer(pendingDataSync: DataSync?) {}
    override fun addListener(listener: DataSyncManagerListener): Boolean = true
    override fun removeListener(listener: DataSyncManagerListener): Boolean = true
    override fun setAccountOwner(owner: Contact?) {}
}

class UnusedWalletDataHandler : WalletDataHandler() {
    override suspend fun persistWalletMnemonic(mnemonic: WalletMnemonic): Boolean = false
    override suspend fun retrieveWalletMnemonic(): WalletMnemonic? = null
}

class UnusedRSA : RSA() {
    override suspend fun generateKeyPair(
        keySize: KeySize,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher?,
        pkcsType: PKCSType,
    ): Response<RSAKeyPair, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun decrypt(
        rsaPrivateKey: RsaPrivateKey,
        text: EncryptedString,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Response<UnencryptedByteArray, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun encrypt(
        rsaPublicKey: RsaPublicKey,
        text: UnencryptedString,
        formatOutput: Boolean,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Response<EncryptedString, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun sign(
        rsaPrivateKey: RsaPrivateKey,
        text: String,
        algorithm: SignatureAlgorithm,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Response<RsaSignedString, ResponseError> = Response.Error(ResponseError("unused"))
    override suspend fun verifySignature(
        rsaPublicKey: RsaPublicKey,
        signedString: RsaSignedString,
        algorithm: SignatureAlgorithm,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): Response<Boolean, ResponseError> = Response.Error(ResponseError("unused"))
}

class UnusedRelayDataHandler : RelayDataHandler() {
    override suspend fun persistAuthorizationToken(token: AuthorizationToken?): Boolean = false
    override suspend fun retrieveAuthorizationToken(): AuthorizationToken? = null
}

class UnusedSphinxNotificationManager : SphinxNotificationManager {
    override fun notify(notificationId: Int, groupId: String?, title: String, message: String) {}
    override fun clearNotification(notificationId: Int) {}
}

class UnusedLogger : SphinxLogger() {
    override fun log(tag: String, message: String, type: LogType, throwable: Throwable?) {}
}

class TestSphinxRepository(
    accountOwner: StateFlow<Contact?>,
    applicationScope: CoroutineScope,
    authenticationStorage: AuthenticationStorage,
    networkQueryHive: NetworkQueryHive,
    connectManager: ConnectManager,
    dispatchers: CoroutineDispatchers,
) : SphinxRepository(
    accountOwner,
    applicationScope,
    TestAuthenticationCoreManager(
        dispatchers as CoroutineTestHelper.TestCoroutineDispatchers,
        TestEncryptionKeyHandler(),
        TestAuthenticationCoreStorage(),
        TestAuthenticationManagerInitializer(),
    ),
    authenticationStorage,
    UnusedRelayDataHandler(),
    FakeCoreDB(),
    dispatchers,
    Moshi.Builder().build(),
    UnusedMediaCacheHandler(),
    UnusedMemeInputStreamHandler(),
    UnusedMemeServerTokenHandler(),
    UnusedNetworkQueryDiscoverTribes(),
    UnusedNetworkQueryMemeServer(),
    UnusedNetworkQueryChat(),
    UnusedNetworkQueryContact(),
    UnusedNetworkQueryInvite(),
    UnusedNetworkQueryAuthorizeExternal(),
    UnusedNetworkQueryPeople(),
    UnusedNetworkQueryFeedSearch(),
    UnusedNetworkQueryFeedStatus(),
    networkQueryHive,
    connectManager,
    UnusedDataSyncManager(),
    UnusedWalletDataHandler(),
    UnusedRSA(),
    UnusedSphinxNotificationManager(),
    UnusedLogger(),
)

class SphinxRepositoryFetchWorkspaceImageUrlTest {

    private val storage = FakeImageAuthStorage()
    private val owner = MutableStateFlow<Contact?>(makeOwner())
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineTestHelper.TestCoroutineDispatchers(
        unconfined, unconfined, unconfined, unconfined, unconfined
    )
    private val applicationScope = CoroutineScope(SupervisorJob() + unconfined)

    private var authResponse: LoadResponse<HiveAuthenticationTokenDto, ResponseError> =
        Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
    private var imageResponses: MutableList<LoadResponse<WorkspaceImageDto, ResponseError>> =
        mutableListOf()
    private val imageCallCount = AtomicInteger(0)
    private var authCallCount = 0
    private var imageGate: CompletableDeferred<Unit>? = null
    private var imageStarted: CompletableDeferred<Unit>? = null

    private val fakeHive = object : NetworkQueryHive() {
        override fun authenticateWithHive(
            token: String,
            pubkey: String,
            timestamp: Long,
        ): Flow<LoadResponse<HiveAuthenticationTokenDto, ResponseError>> = flow {
            authCallCount++
            emit(authResponse)
        }

        override fun getWorkspaces(
            authToken: String,
        ): Flow<LoadResponse<WorkspacesListDto, ResponseError>> = flow {
            emit(Response.Error(ResponseError("not used in this test")))
        }

        override fun getWorkspaceImage(
            slug: String,
            authToken: String,
        ): Flow<LoadResponse<WorkspaceImageDto, ResponseError>> = flow {
            val index = imageCallCount.getAndIncrement()
            imageStarted?.complete(Unit)
            imageGate?.await()
            val response = if (index < imageResponses.size) {
                imageResponses[index]
            } else {
                imageResponses.lastOrNull()
                    ?: Response.Error(ResponseError("no image response"))
            }
            emit(response)
        }

        override fun getFeatures(
            workspaceId: String,
            page: Int,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveFeaturesListDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun updateFeature(
            featureId: String,
            patch: chat.sphinx.concept_network_query_hive.model.HiveFeaturePatchDto,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveFeatureUpdateDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun deleteFeature(
            featureId: String,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveDeleteResponseDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun getTasks(
            workspaceId: String,
            page: Int,
            includeArchived: Boolean,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTasksListDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun startTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun retryTask(
            taskId: String,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun updateTaskStatus(
            taskId: String,
            status: String,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun setTaskArchived(
            taskId: String,
            archived: Boolean,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun updateTaskFlags(
            taskId: String,
            autoMerge: Boolean,
            runBuild: Boolean,
            runTestSuite: Boolean,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun duplicateTask(
            featureId: String,
            body: chat.sphinx.concept_network_query_hive.model.HiveTaskDuplicateDto,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }

        override fun updateTaskDependsOn(
            taskId: String,
            dependsOnTaskIds: List<String>,
            authToken: String,
        ): Flow<LoadResponse<chat.sphinx.concept_network_query_hive.model.HiveTaskMutationDto, ResponseError>> =
            flow {
                emit(Response.Error(ResponseError("not used in this test")))
            }
    }

    @Before
    fun setUp() {
        storage.clear()
        authResponse = Response.Success(HiveAuthenticationTokenDto(token = "new-jwt"))
        imageResponses = mutableListOf()
        imageCallCount.set(0)
        authCallCount = 0
        imageGate = null
        imageStarted = null
        clock = 1_000_000L
    }

    private fun makeRepo(): TestSphinxRepository {
        val repo = TestSphinxRepository(
            accountOwner = owner,
            applicationScope = applicationScope,
            authenticationStorage = storage,
            networkQueryHive = fakeHive,
            connectManager = FakeConnectManager(),
            dispatchers = dispatchers,
        )
        repo.hiveNowMillis = { clock }
        return repo
    }

    private var clock = 1_000_000L

    private suspend fun storeValidToken(jwt: String = "valid-jwt") {
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L
        storage.putString(
            SphinxRepository.HIVE_AUTHENTICATION_TOKEN,
            "$jwt${SphinxRepository.HIVE_TOKEN_DELIMITER}$expiresAt"
        )
    }

    private fun successImage(
        url: String = "https://s3.example.com/logo.png",
        expiresIn: Long = 3600L,
    ): LoadResponse<WorkspaceImageDto, ResponseError> =
        Response.Success(WorkspaceImageDto(presignedUrl = url, expiresIn = expiresIn))

    private fun error404(): LoadResponse<WorkspaceImageDto, ResponseError> =
        Response.Error(
            ResponseError(
                "Failed to convert Json to WorkspaceImageDto",
                CustomException("not found", 404),
            )
        )

    private fun errorNetwork(): LoadResponse<WorkspaceImageDto, ResponseError> =
        Response.Error(ResponseError("network error"))

    private fun Response<String, ResponseError>.assertSuccess(): String {
        return when (this) {
            is Response.Success -> this.value
            is Response.Error -> throw AssertionError(
                "Expected Response.Success but got Response.Error(${this.cause.message})"
            )
        }
    }

    @Test
    fun `cache hit returns cached url without calling getWorkspaceImage again`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/a.png"))

        val first = repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        val second = repo.fetchWorkspaceImageUrl("acme").assertSuccess()

        assertEquals("https://cdn.example/a.png", first)
        assertEquals(first, second)
        assertEquals(1, imageCallCount.get())
        assertEquals(0, authCallCount)
    }

    @Test
    fun `expired cache entry triggers a fresh fetch`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/old.png", expiresIn = 3600))
        imageResponses.add(successImage("https://cdn.example/new.png", expiresIn = 3600))

        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        clock += 3_540_000L
        val refreshed = repo.fetchWorkspaceImageUrl("acme").assertSuccess()

        assertEquals("https://cdn.example/new.png", refreshed)
        assertEquals(2, imageCallCount.get())
    }

    @Test
    fun `two concurrent callers for the same uncached slug share one GET`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/coalesce.png"))
        imageStarted = CompletableDeferred()
        imageGate = CompletableDeferred()

        val first = async { repo.fetchWorkspaceImageUrl("acme") }
        imageStarted!!.await()
        val second = async { repo.fetchWorkspaceImageUrl("acme") }
        imageGate!!.complete(Unit)

        val r1 = first.await().assertSuccess()
        val r2 = second.await().assertSuccess()
        assertEquals("https://cdn.example/coalesce.png", r1)
        assertEquals(r1, r2)
        assertEquals(1, imageCallCount.get())
    }

    @Test
    fun `404 is terminal, not retried, not cached, and does not clear hive token`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(error404())
        imageResponses.add(error404())

        val first = repo.fetchWorkspaceImageUrl("no-logo")
        assertTrue(first is Response.Error)
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
        assertEquals(1, imageCallCount.get())

        val second = repo.fetchWorkspaceImageUrl("no-logo")
        assertTrue(second is Response.Error)
        assertEquals(0, storage.removeCallCount)
        assertEquals(0, authCallCount)
        assertEquals(2, imageCallCount.get())
    }

    @Test
    fun `non-404 error triggers clearHiveToken then a single retry`() = runBlocking {
        val repo = makeRepo()
        storeValidToken("stale-jwt")
        imageResponses.add(errorNetwork())
        imageResponses.add(successImage("https://cdn.example/retry.png"))

        val result = repo.fetchWorkspaceImageUrl("acme").assertSuccess()

        assertEquals("https://cdn.example/retry.png", result)
        assertEquals(2, imageCallCount.get())
        assertEquals(1, storage.removeCallCount)
        assertEquals(1, authCallCount)
    }

    @Test
    fun `expiresIn 3600 is reusable for about 3540 seconds`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/hour.png", expiresIn = 3600))
        imageResponses.add(successImage("https://cdn.example/hour-2.png", expiresIn = 3600))

        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        clock += 3_539_999L
        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals(1, imageCallCount.get())

        clock += 1L
        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals(2, imageCallCount.get())
    }

    @Test
    fun `expiresIn 30 still caches for half the ttl`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/short.png", expiresIn = 30))
        imageResponses.add(successImage("https://cdn.example/short-2.png", expiresIn = 30))

        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        clock += 14_999L
        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals(1, imageCallCount.get())

        clock += 1L
        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals(2, imageCallCount.get())
    }

    @Test
    fun `clearDatabase evicts cached presigned urls`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/before.png"))
        imageResponses.add(successImage("https://cdn.example/after.png"))

        repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals(1, imageCallCount.get())

        try {
            repo.clearDatabase()
        } catch (_: UnsupportedOperationException) {
            // Fake CoreDB has no queries; cache is cleared before that call.
        }

        val after = repo.fetchWorkspaceImageUrl("acme").assertSuccess()
        assertEquals("https://cdn.example/after.png", after)
        assertEquals(2, imageCallCount.get())
    }

    @Test
    fun `cache evicts eldest entry when over max size`() = runBlocking {
        val repo = makeRepo()
        storeValidToken()
        imageResponses.add(successImage("https://cdn.example/first.png"))
        repeat(100) { i ->
            imageResponses.add(successImage("https://cdn.example/$i.png"))
        }
        imageResponses.add(successImage("https://cdn.example/first-again.png"))

        repo.fetchWorkspaceImageUrl("first").assertSuccess()
        repeat(100) { i ->
            repo.fetchWorkspaceImageUrl("slug-$i").assertSuccess()
        }
        assertEquals(101, imageCallCount.get())

        val again = repo.fetchWorkspaceImageUrl("first").assertSuccess()
        assertEquals("https://cdn.example/first-again.png", again)
        assertEquals(102, imageCallCount.get())
    }
}

fun makeOwner(): Contact = Contact(
    id = ContactId(1L),
    routeHint = null,
    nodePubKey = LightningNodePubKey(
        "02aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    ),
    nodeAlias = null,
    alias = null,
    photoUrl = null,
    privatePhoto = PrivatePhoto.False,
    isOwner = Owner.True,
    status = ContactStatus.AccountOwner,
    rsaPublicKey = null,
    deviceId = null,
    createdAt = DateTime(Date()),
    updatedAt = DateTime(Date()),
    fromGroup = ContactFromGroup.False,
    notificationSound = null,
    tipAmount = null,
    inviteId = null,
    inviteStatus = null,
    blocked = Blocked.False,
)
