package chat.sphinx.example.concept_connect_manager

import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.concept_connect_manager.model.RestoreState
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.example.wrapper_mqtt.MsgsCounts
import chat.sphinx.wrapper_common.message.MqttMessage
import chat.sphinx.wrapper_contact.NewContact
import chat.sphinx.wrapper_lightning.WalletMnemonic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The ConnectManager abstract class defines the structure for managing the account,
 * contacts, messages, tribes, and payments for the Sphinx V2 communication system.
 * It facilitates the interaction between the SphinxRepository, which handles the actual
 * data coming from the view models to then communicates with the bindings for key generation,
 * publishing, and receiving MQTT messages.
 */

abstract class ConnectManager {
    abstract val ownerInfoStateFlow: StateFlow<OwnerInfo?>
    abstract val restoreStateFlow: StateFlow<RestoreState?>
    abstract val msgsCountsState: MutableStateFlow<MsgsCounts?>

    abstract fun setOwnerInfo(ownerInfo: OwnerInfo)

    // Account Management Methods
    abstract fun createAccount(mnemonic: String?)
    abstract fun restoreAccount(
        defaultTribe: String?,
        tribeHost: String?,
        mixerServerIp: String?,
        routerUrl: String?
    )
    abstract fun restoreFailed()
    abstract fun finishRestore()
    abstract fun setInviteCode(inviteString: String?)
    abstract fun setMnemonicWords(words: List<String>?)
    abstract fun setNetworkType(isTestEnvironment: Boolean)
    abstract fun setOwnerDeviceId(deviceId: String, pushKey: String)
    abstract fun processChallengeSignature(challenge: String): String?
    abstract fun fetchFirstMessagesPerKey(lastMsgIdx: Long, totalCount: Long?)
    abstract fun fetchMessagesOnRestoreAccount(totalHighestIndex: Long?, totalMsgsCount: Long?)
    abstract fun getAllMessagesCount()
    abstract fun initializeMqttAndSubscribe(
        serverUri: String,
        mnemonicWords: WalletMnemonic,
        ownerInfo: OwnerInfo
    )
    abstract fun reconnectWithBackOff()
    abstract fun attemptReconnectOnResume()
    abstract fun retrieveLspIp(): String?
    abstract fun resetMQTT()

    // Contact Management Methods
    abstract fun createContact(contact: NewContact)
    abstract fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        serverDefaultTribe: String?,
        tribeServerIp: String?,
        mixerIp: String?,
    )

    abstract fun deleteInvite(inviteString: String)
    abstract fun deleteContact(pubKey: String)
    abstract fun setReadMessage(contactPubKey: String, messageIndex: Long)
    abstract fun getReadMessages()
    abstract fun setMute(muteLevel: Int, contactPubKey: String)
    abstract fun getMutedChats()
    abstract fun addNodesFromResponse(nodesJson: String)
    abstract fun concatNodesFromResponse(
        nodesJson: String,
        routerPubKey: String,
        amount: Long
    )
    abstract fun fetchMessagesOnAppInit(
        lastMsgIdx: Long?,
        reverse: Boolean
    )

    // Messaging Methods
    abstract fun sendMessage(
        sphinxMessage: String,
        contactPubKey: String,
        provisionalId: Long,
        messageType: Int,
        amount: Long?,
        date: Long,
        isTribe: Boolean = false
    )
    abstract fun deleteMessage(
        sphinxMessage: String,
        contactPubKey: String,
        isTribe: Boolean
    )
    abstract fun deleteContactMessages(
        messageIndexList: List<Long>,
    )
    abstract fun deletePubKeyMessages(
        contactPubKey: String
    )
    abstract fun getMessagesStatusByTags(tags: List<String>)

    // Tribe Management Methods
    abstract fun createTribe(tribeJson: String)
    abstract fun joinToTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        isPrivate: Boolean,
        userAlias: String,
        priceToJoin: Long
    )
    abstract fun retrieveTribeMembersList(
        tribeServerPubKey: String,
        tribePubKey: String
    )
    abstract fun getTribeServerPubKey(): String?
    abstract fun editTribe(
        tribeJson: String
    )

    // Invoice and Payment Methods
    abstract fun createInvoice(
        amount: Long,
        memo: String
    ): Pair<String, String>? // invoice, paymentHash
    abstract fun sendKeySend(
        pubKey: String,
        amount: Long,
        routeHint: String?,
        data: String? = null
    )
    abstract fun processContactInvoicePayment(paymentRequest: String)
    abstract fun processInvoicePayment(
        paymentRequest: String,
        milliSatAmount: Long,
    ): String?

    abstract fun payInvoiceFromLSP(
        paymentRequest: String
    )
    abstract fun retrievePaymentHash(paymentRequest: String): String?
    abstract fun getPayments(
        lastMsgDate: Long,
        limit: Int,
        scid: Long?, // fetch payments for a single child key
        remoteOnly: Boolean?, // only payments routed through other LSPs
        minMsat: Long?, // include only payments above this amount
        reverse: Boolean?
    )
    abstract fun getPubKeyByEncryptedChild(child: String, pushKey: String?): String?
    abstract fun generateMediaToken(
        contactPubKey: String,
        muid: String,
        host: String,
        metaData: String?,
        amount: Long?
    ): String?
    abstract fun getInvoiceInfo(invoice: String): String?

    abstract fun isRouteAvailable(pubKey: String, routeHint: String?, milliSat: Long): Boolean

    // Utility Methods
    abstract fun getSignedTimeStamps(): String?
    abstract fun getSignBase64(text: String): String?
    abstract fun getIdFromMacaroon(macaroon: String): String?
    // Listener Methods
    abstract fun addListener(listener: ConnectManagerListener): Boolean
    abstract fun removeListener(listener: ConnectManagerListener): Boolean

    abstract fun saveMessagesCounts(msgsCounts: MsgsCounts)
}

/**
 * The ConnectManagerListener interface defines callbacks for handling events and data updates.
 * These callbacks are used to transmit data to the SphinxRepository, ensuring that values
 * are stored in the database or passed to the ViewModels as needed.
 */

interface ConnectManagerListener {

    // Account Management Callbacks
    fun onUpdateUserState(userState: String)
    fun onMnemonicWords(words: String, isRestore: Boolean)
    fun showMnemonic(isRestore: Boolean)
    fun onOwnerRegistered(
        okKey: String,
        routeHint: String,
        isRestoreAccount: Boolean,
        mixerServerIp: String?,
        tribeServerHost: String?,
        isProductionEnvironment: Boolean,
        routerUrl: String?,
        defaultTribe: String?
    )
    fun onRestoreAccount(isProductionEnvironment: Boolean)
    fun onUpsertContacts(
        contacts: List<Pair<String?, Long?>>,
        isRestore: Boolean = false,
        callback: (() -> Unit)? = null
    )
    fun onRestoreMessages()
    fun onUpsertTribes(
        tribes: List<Pair<String?, Boolean?>>,
        isProductionEnvironment: Boolean,
        callback: (() -> Unit)? = null
    ) // Sender, FromMe
//    fun onRestoreNextPageMessages(highestIndex: Long, limit: Int)
    fun onNewBalance(balance: Long)
    fun onSignedChallenge(sign: String)
    fun onInitialTribe(tribe: String, isProductionEnvironment: Boolean)
    fun onLastReadMessages(lastReadMessages: String)
    fun onUpdateMutes(mutes: String)
    fun onGetNodes()
    fun onConnectManagerError(error: ConnectManagerError)
    fun onRestoreProgress(progress: Int)
    fun onRestoreFinished()
    fun updatePaidInvoices()

    fun onMessages(messages: List<MqttMessage>, isRestore: Boolean)

    fun onMessageTagAndUuid(tag: String?, msgUUID: String, provisionalId: Long)
    fun onMessagePaymentHash(paymentHash: String, provisionalId: Long)
    fun onMessagesCounts(msgsCounts: String)
    fun onSentStatus(sentStatus: String)
    fun onMessageTagList(tags: String)

    fun onRestoreMinIndex(minIndex: Long)

    // Tribe Management Callbacks
    fun onNewTribeCreated(newTribe: String)
    fun onTribeMembersList(tribeMembers: String)

    // Invoice and Payment Management Callbacks
    fun onPayments(payments: String)
    fun onNetworkStatusChange(
        isConnected: Boolean,
        isLoading: Boolean = false
    )
    fun onNewInviteCreated(
        nickname: String,
        inviteString: String,
        inviteCode: String,
        sats: Long
    )
    // Utility

    fun onPerformDelay(delay: Long, callback: () -> Unit)
    fun listenToOwnerCreation(callback: () -> Unit)
}


