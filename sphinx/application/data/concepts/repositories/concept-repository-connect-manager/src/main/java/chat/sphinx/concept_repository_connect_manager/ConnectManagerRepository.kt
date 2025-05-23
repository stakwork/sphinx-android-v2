package chat.sphinx.concept_repository_connect_manager

import chat.sphinx.concept_repository_connect_manager.model.OwnerRegistrationState
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_connect_manager.model.RestoreProcessState
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.example.wrapper_mqtt.TransactionDto
import chat.sphinx.example.wrapper_mqtt.TribeMembersResponse
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningPaymentRequest
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.lightning.MilliSat
import chat.sphinx.wrapper_contact.NewContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The ConnectManagerRepository interface is responsible for facilitating communication
 * between the SphinxRepository and the view models. View models use the methods defined
 * in this interface, which are implemented in the SphinxRepository, to interact with
 * the ConnectManager. Additionally, the MutableStateFlow variables in this interface
 * are collected in the view models to receive values coming from the ConnectManager.
 */

interface ConnectManagerRepository {

    val connectionManagerState: MutableStateFlow<OwnerRegistrationState?>
    val networkStatus: MutableStateFlow<NetworkStatus>
    val restoreProcessState: MutableStateFlow<RestoreProcessState?>
    val connectManagerErrorState: MutableStateFlow<ConnectManagerError?>
    val transactionDtoState: MutableStateFlow<List<TransactionDto>?>
    val userStateFlow: MutableStateFlow<String?>
    val tribeMembersState: MutableStateFlow<TribeMembersResponse?>
    val restoreProgress: MutableStateFlow<Int?>
    val webViewPaymentHash: MutableStateFlow<String?>
    val processingInvoice: MutableStateFlow<Pair<String, String>?>
    val webViewPreImage: MutableStateFlow<String?>
    val restoreMinIndex: MutableStateFlow<Long?>

    fun connectAndSubscribeToMqtt(userState: String?, mixerIp: String?) {}
    fun createOwnerAccount()
    fun resetAccount()
    fun startRestoreProcess()
    fun createContact(contact: NewContact)
    fun setInviteCode(inviteString: String?)
    fun setMnemonicWords(words: List<String>?)
    fun setNetworkType(isTestEnvironment: Boolean)
    fun setOwnerDeviceId(deviceId: String)
    fun signChallenge(challenge: String): String?
    fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        serverDefaultTribe: String?,
        tribeServerIp: String?,
        mixerIp: String?
    )

    fun joinTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        tribeName: String,
        tribePicture: String?,
        isPrivate: Boolean,
        userAlias: String,
        pricePerMessage: Long,
        escrowAmount: Long,
        priceToJoin: Long,
    )

    fun getTribeMembers(
        tribeServerPubKey: String,
        tribePubKey: String
    )
    fun getTribeServerPubKey(): String?
    fun getPayments(lastMessageDate: Long, limit: Int)
    suspend fun getChatIdByEncryptedChild(child: String): Flow<ChatId?>
    fun getTagsByChatId(chatId: ChatId)
    suspend fun payContactPaymentRequest(paymentRequest: LightningPaymentRequest?)
    suspend fun payInvoice(
        paymentRequest: LightningPaymentRequest,
        endHops: String?,
        routerPubKey: String?,
        milliSatAmount: Long,
        isSphinxInvoice: Boolean = true,
        paymentHash: String? = null,
        callback: (() -> Unit)? = null
    )

    suspend fun payInvoiceFromLSP(
        paymentRequest: LightningPaymentRequest
    )

    suspend fun sendKeySend(
        pubKey: String,
        endHops: String?,
        milliSatAmount: Long,
        routerPubKey: String?,
        routeHint: String?,
        data: String? = null
    )

    suspend fun sendKeySendWithRouting(
        pubKey: LightningNodePubKey,
        routeHint: LightningRouteHint?,
        milliSatAmount: MilliSat?,
        routerUrl: String?,
        routerPubKey: String?,
        data: String? = null
    ): Boolean

    fun isRouteAvailable(pubKey: String, routeHint: String?, milliSat: Long): Boolean

    fun createInvoice(
        amount: Long,
        memo: String
    ): Pair<String, String>? // invoice, paymentHash

    fun clearWebViewPreImage()

    suspend fun updateLspAndOwner(data: String) {}
    fun requestNodes(nodeUrl: String)
    fun getInvoiceInfo(invoice: String): String?
    fun getSignedTimeStamps(): String?
    fun getSignBase64(text: String): String?
    fun getIdFromMacaroon(macaroon: String): String?
    fun attemptReconnectOnResume()

    fun reconnectMqtt()

}