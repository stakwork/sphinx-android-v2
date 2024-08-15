package chat.sphinx.example.concept_connect_manager

import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.concept_connect_manager.model.RestoreState
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.wrapper_contact.NewContact
import chat.sphinx.wrapper_lightning.WalletMnemonic
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

    // Account Management Methods
    abstract fun createAccount()
    abstract fun restoreAccount(
        defaultTribe: String?,
        tribeHost: String?,
        mixerServerIp: String?,
        routerUrl: String?
    )
    abstract fun setInviteCode(inviteString: String)
    abstract fun setMnemonicWords(words: List<String>?)
    abstract fun setNetworkType(isTestEnvironment: Boolean)
    abstract fun setOwnerDeviceId(deviceId: String)
    abstract fun processChallengeSignature(challenge: String): String?
    abstract fun fetchFirstMessagesPerKey(lastMsgIdx: Long, firstForEachScid: Long?)
    abstract fun fetchMessagesOnRestoreAccount(totalHighestIndex: Long?)
    abstract fun getAllMessagesCount()
    abstract fun initializeMqttAndSubscribe(
        serverUri: String,
        mnemonicWords: WalletMnemonic,
        ownerInfo: OwnerInfo
    )
    abstract fun reconnectWithBackOff()
    abstract fun attemptReconnectOnResume()
    abstract fun retrieveLspIp(): String?

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
    abstract fun sendKeySend(pubKey: String, amount: Long, routeHint: String?)
    abstract fun processContactInvoicePayment(paymentRequest: String)
    abstract fun processInvoicePayment(
        paymentRequest: String,
        milliSatAmount: Long,
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
    abstract fun getPubKeyFromChildIndex(childIndex: Long): String?
    abstract fun getPubKeyByEncryptedChild(child: String): String?
    abstract fun generateMediaToken(
        contactPubKey: String,
        muid: String,
        host: String,
        metaData: String?,
        amount: Long?
    ): String?
    abstract fun getInvoiceInfo(invoice: String): String?

    // Utility Methods
    abstract fun getSignedTimeStamps(): String?
    abstract fun getSignBase64(text: String): String?
    abstract fun getIdFromMacaroon(macaroon: String): String?
    // Listener Methods
    abstract fun addListener(listener: ConnectManagerListener): Boolean
    abstract fun removeListener(listener: ConnectManagerListener): Boolean
}

/**
 * The ConnectManagerListener interface defines callbacks for handling events and data updates.
 * These callbacks are used to transmit data to the SphinxRepository, ensuring that values
 * are stored in the database or passed to the ViewModels as needed.
 */

interface ConnectManagerListener {

    // Account Management Callbacks
    fun onUpdateUserState(userState: String)
    fun onMnemonicWords(words: String)
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
    fun onRestoreContacts(contacts: List<String?>)
    fun onRestoreMessages()
    fun onRestoreTribes(tribes: List<Pair<String?, Boolean?>>, isProductionEnvironment: Boolean) // Sender, FromMe
//    fun onRestoreNextPageMessages(highestIndex: Long, limit: Int)
    fun onNewBalance(balance: Long)
    fun onSignedChallenge(sign: String)
    fun onInitialTribe(tribe: String, isProductionEnvironment: Boolean)
    fun onLastReadMessages(lastReadMessages: String)
    fun onUpdateMutes(mutes: String)
    fun onGetNodes()
    fun onConnectManagerError(error: ConnectManagerError)
    fun onRestoreProgress(progress: Int)

    // Messaging Callbacks
    fun onMessage(
        msg: String,
        msgSender: String,
        msgType: Int,
        msgUuid: String,
        msgIndex: String,
        msgTimestamp: Long?,
        sentTo: String,
        amount: Long?,
        fromMe: Boolean?,
        tag: String?
    )
    fun onMessageTagAndUuid(tag: String?, msgUUID: String, provisionalId: Long)
    fun onMessagesCounts(msgsCounts: String)
    fun onSentStatus(sentStatus: String)
    fun onMessageTagList(tags: String)

    // Tribe Management Callbacks
    fun onNewTribeCreated(newTribe: String)
    fun onTribeMembersList(tribeMembers: String)

    // Invoice and Payment Management Callbacks
    fun onPayments(payments: String)
    fun onNetworkStatusChange(isConnected: Boolean)
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


