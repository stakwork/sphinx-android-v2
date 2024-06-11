package chat.sphinx.example.concept_connect_manager

import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.wrapper_contact.NewContact
import chat.sphinx.wrapper_lightning.WalletMnemonic
import kotlinx.coroutines.flow.StateFlow

abstract class ConnectManager {

    abstract val ownerInfoStateFlow: StateFlow<OwnerInfo?>

    abstract fun createAccount()
    abstract fun restoreAccount(
        defaultTribe: String?,
        tribeHost: String?,
        mixerServerIp: String?
    )
    abstract fun setInviteCode(inviteString: String)
    abstract fun setMnemonicWords(words: List<String>?)
    abstract fun setNetworkType(isTestEnvironment: Boolean)
    abstract fun createContact(contact: NewContact)
    abstract fun deleteContact(pubKey: String)

    abstract fun initializeMqttAndSubscribe(
        serverUri: String,
        mnemonicWords: WalletMnemonic,
        ownerInfo: OwnerInfo
    )
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

    abstract fun joinToTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        isPrivate: Boolean,
        userAlias: String,
        priceToJoin: Long
    )

    abstract fun createTribe(
        tribeJson: String
    )

    abstract fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        tribeServerPubKey: String?
    )

    abstract fun createInvoice(
        amount: Long,
        memo: String
    ): Pair<String, String>? // invoice, paymentHash

    abstract fun sendKeySend(pubKey: String, amount: Long)

    abstract fun getTribeServerPubKey(): String?

    abstract fun processInvoicePayment(paymentRequest: String)

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

    abstract fun retrieveTribeMembersList(
        tribeServerPubKey: String,
        tribePubKey: String
    )

    abstract fun generateMediaToken(
        contactPubKey: String,
        muid: String,
        host: String,
        metaData: String?,
        amount: Long?
    ): String?

    abstract fun readMessage(
        contactPubKey: String,
        messageIndex: Long
    )

    abstract fun getReadMessages()
    abstract fun getMutedChats()
    abstract fun retrieveLspIp(): String?
    abstract fun addListener(listener: ConnectManagerListener): Boolean
    abstract fun removeListener(listener: ConnectManagerListener): Boolean
    abstract fun processChallengeSignature(challenge: String)
    abstract fun fetchMessagesOnRestoreAccount(totalHighestIndex: Long?)
    abstract fun fetchFirstMessagesPerKey(lastMsgIdx: Long)
    abstract fun getAllMessagesCount()
    abstract fun reconnectWithBackoff()
    abstract fun setOwnerDeviceId(deviceId: String)
    abstract fun setMute(muteLevel: Int, contactPubKey: String)
}

interface ConnectManagerListener {

    fun onMnemonicWords(words: String)
    fun onOwnerRegistered(
        okKey: String,
        routeHint: String,
        isRestoreAccount: Boolean,
        mixerServerIp: String?,
        tribeServerHost: String?,
        isProductionEnvironment: Boolean
    )

    fun onMessage(
        msg: String,
        msgSender: String,
        msgType: Int,
        msgUuid: String,
        msgIndex: String,
        msgTimestamp: Long?,
        sentTo: String,
        amount: Long?,
        fromMe: Boolean?
    )

    fun onRestoreContacts(contacts: List<String?>)
    fun onRestoreTribes(tribes: List<Pair<String?, Boolean?>>, isProductionEnvironment: Boolean) // Sender, FromMe
    fun onRestoreNextPageMessages(highestIndex: Long, limit: Int)
    fun onNewTribeCreated(newTribe: String)
    fun onTribeMembersList(tribeMembers: String)
    fun onMessageTagAndUuid(tag: String?, msgUUID: String, provisionalId: Long)
    fun onUpdateUserState(userState: String)
    fun onSignedChallenge(sign: String)
    fun onNewBalance(balance: Long)
    fun onPayments(payments: String)
    fun onNetworkStatusChange(isConnected: Boolean)
    fun listenToOwnerCreation(callback: () -> Unit)
    fun onRestoreAccount(isProductionEnvironment: Boolean)

    fun onNewInviteCreated(
        nickname: String,
        inviteString: String,
        inviteCode: String,
        sats: Long
    )

    fun onSentStatus(sentStatus: String)

    fun onLastReadMessages(lastReadMessages: String)
    fun onUpdateMutes(mutes: String)
    fun onMessagesCounts(msgsCounts: String)
    fun onInitialTribe(tribe: String, isProductionEnvironment: Boolean)
    fun onConnectManagerError(error: ConnectManagerError)

}


