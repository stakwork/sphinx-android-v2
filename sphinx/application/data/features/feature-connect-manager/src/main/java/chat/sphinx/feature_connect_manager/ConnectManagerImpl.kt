package chat.sphinx.feature_connect_manager

import android.util.Base64
import android.util.Log
import chat.sphinx.example.concept_connect_manager.ConnectManager
import chat.sphinx.example.concept_connect_manager.ConnectManagerListener
import chat.sphinx.example.concept_connect_manager.model.OwnerInfo
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.example.wrapper_mqtt.NewInvite
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningRouteHint
import chat.sphinx.wrapper_contact.NewContact
import chat.sphinx.wrapper_lightning.WalletMnemonic
import chat.sphinx.wrapper_lightning.toWalletMnemonic
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPack
import com.ensarsarajcic.kotlinx.serialization.msgpack.MsgPackDynamicSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.base64.encodeBase64
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONException
import org.json.JSONObject
import uniffi.sphinxrs.ParsedInvite
import uniffi.sphinxrs.RunReturn
import uniffi.sphinxrs.addContact
import uniffi.sphinxrs.codeFromInvite
import uniffi.sphinxrs.fetchMsgsBatch
import uniffi.sphinxrs.getDefaultTribeServer
import uniffi.sphinxrs.getMsgsCounts
import uniffi.sphinxrs.getMutes
import uniffi.sphinxrs.getReads
import uniffi.sphinxrs.getTribeManagementTopic
import uniffi.sphinxrs.handle
import uniffi.sphinxrs.initialSetup
import uniffi.sphinxrs.joinTribe
import uniffi.sphinxrs.listContacts
import uniffi.sphinxrs.listTribeMembers
import uniffi.sphinxrs.makeInvite
import uniffi.sphinxrs.makeMediaToken
import uniffi.sphinxrs.makeMediaTokenWithMeta
import uniffi.sphinxrs.makeMediaTokenWithPrice
import uniffi.sphinxrs.mnemonicFromEntropy
import uniffi.sphinxrs.mnemonicToSeed
import uniffi.sphinxrs.mute
import uniffi.sphinxrs.parseInvite
import uniffi.sphinxrs.paymentHashFromInvoice
import uniffi.sphinxrs.processInvite
import uniffi.sphinxrs.read
import uniffi.sphinxrs.rootSignMs
import uniffi.sphinxrs.send
import uniffi.sphinxrs.setNetwork
import uniffi.sphinxrs.setPushToken
import uniffi.sphinxrs.signBytes
import uniffi.sphinxrs.xpubFromSeed
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.math.min

class ConnectManagerImpl: ConnectManager()
{
    private var _mixerIp: String? = null
    private var walletMnemonic: WalletMnemonic? = null
    private var mqttClient: MqttAsyncClient? = null
    private var network = ""
    private var ownerSeed: String? = null
    private var inviteCode: String? = null
    private var inviteInitialTribe: String? = null
    private var currentInvite: NewInvite? = null
    private var restoreMnemonicWords: List<String>? = emptyList()
    private var inviterContact: NewContact? = null
    private var hasAttemptedReconnect = false
    private var tribeServer: String? = null

    companion object {
        const val TEST_V2_SERVER_IP = "34.229.52.200:1883"
        const val TEST_V2_TRIBES_SERVER = "34.229.52.200:8801"
        const val REGTEST_NETWORK = "regtest"
        const val MAINNET_NETWORK = "bitcoin"
        const val TEST_SERVER_PORT =  1883
        const val PROD_SERVER_PORT = 8883
    }

    private val _ownerInfoStateFlow: MutableStateFlow<OwnerInfo> by lazy {
        MutableStateFlow(OwnerInfo(
            null,
            null,
            null,
            null
        ))
    }
    override val ownerInfoStateFlow: StateFlow<OwnerInfo>
        get() = _ownerInfoStateFlow.asStateFlow()

    private var mixerIp: String?
        get() = _mixerIp?.let {
            if (isProductionEnvironment()) {
                if (!it.startsWith("ssl://")) "ssl://$it" else it
            } else {
                if (!it.startsWith("tcp://")) "tcp://$it" else it
            }
        }
        set(value) {
            _mixerIp = value?.replace("tcp://", "")?.replace("ssl://", "")
        }
    private fun getRawMixerIp(): String? {
        return _mixerIp
    }

    // Key Generation and Management
    override fun createAccount() {
        if (!restoreMnemonicWords.isNullOrEmpty()) {
            notifyListeners {
                onRestoreAccount(isProductionEnvironment())
            }
        } else {
            val seed = generateMnemonicAndSeed(null)
            val now = getTimestampInMilliseconds()

            seed?.let { firstSeed ->
                var invite: ParsedInvite? = null
                ownerSeed = firstSeed

                if (inviteCode != null) {
                    invite = parseInvite(inviteCode!!)

                    val hostAndPubKey = invite.initialTribe?.let { extractTribeServerAndPubkey(it) }
                    val parts = invite.inviterContactInfo?.split("_")
                    val okKey = parts?.getOrNull(0)?.toLightningNodePubKey()
                    val routeHint =
                        "${parts?.getOrNull(1)}_${parts?.getOrNull(2)}".toLightningRouteHint()

                    // add contact alias
                    inviterContact = NewContact(
                        null,
                        okKey,
                        routeHint,
                        null,
                        false,
                        null,
                        invite.code,
                        null,
                        null,
                    )
                    _mixerIp = invite.lspHost
                    tribeServer = hostAndPubKey?.first ?: TEST_V2_TRIBES_SERVER
                    inviteInitialTribe = invite.initialTribe

                    network = if (isProductionServer()) {
                        MAINNET_NETWORK
                    } else {
                        REGTEST_NETWORK
                    }
                }

                val xPub = generateXPub(firstSeed, now, network)
                val sig = signMs(firstSeed, now, network)

                if (xPub != null) {
                    connectToMQTT(mixerIp!!, xPub, now, sig)
                }
//                notifyListeners {
//                    onConnectManagerError(ConnectManagerError.GenerateXPubError)
//                }
            }
        }
    }

    override fun restoreAccount(defaultTribe: String?, tribeHost: String?, mixerServerIp: String?) {
        val restoreMnemonic = restoreMnemonicWords?.joinToString(" ")
        val seed = generateMnemonicAndSeed(restoreMnemonic)
        val now = getTimestampInMilliseconds()

        seed?.let { firstSeed ->
            ownerSeed = firstSeed
            _mixerIp = mixerServerIp ?: TEST_V2_SERVER_IP
            tribeServer = tribeHost ?: TEST_V2_TRIBES_SERVER

            network = if (isProductionServer()) {
                MAINNET_NETWORK
            } else {
                REGTEST_NETWORK
            }

            val xPub = generateXPub(firstSeed, now, network)
            val sig = signMs(firstSeed, now, network)

            if (xPub != null) {
                connectToMQTT(mixerIp!!, xPub, now, sig)
            }
        }
    }

    override fun setInviteCode(inviteString: String) {
        this.inviteCode = inviteString
    }

    override fun setMnemonicWords(words: List<String>?) {
        this.restoreMnemonicWords = words
    }

    override fun setNetworkType(isTestEnvironment: Boolean) {
        if (isTestEnvironment) {
            this.network = REGTEST_NETWORK
        } else {
            this.network = MAINNET_NETWORK
        }
    }

    private fun isProductionServer(): Boolean {
        val ip = mixerIp ?: return false
        val port = ip.substringAfterLast(":").toIntOrNull() ?: return false
        return port != 1883
    }

    private fun isProductionEnvironment(): Boolean {
        return network != REGTEST_NETWORK
    }


    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateMnemonicAndSeed(restoreMnemonic: String?): String? {
        var seed: String? = null

        // Check if is account restoration
        val mnemonic = if (!restoreMnemonic.isNullOrEmpty()) {
            restoreMnemonicWords!!.joinToString(" ").toWalletMnemonic()
        } else {
            try {
                val randomBytes = generateRandomBytes(16)
                val randomBytesString =
                    randomBytes.joinToString("") { it.toString(16).padStart(2, '0') }
                val words = mnemonicFromEntropy(randomBytesString)

                words.toWalletMnemonic()
            } catch (e: Exception) {
                notifyListeners {
                    onConnectManagerError(ConnectManagerError.GenerateMnemonicError)
                }
                null
            }
        }

        mnemonic?.value?.let { words ->
            try {
                seed = mnemonicToSeed(words)

                notifyListeners {
                    onMnemonicWords(words)
                }
            } catch (e: Exception) {
            }
        }

        return seed
    }

    private fun generateXPub(seed: String, time: String, network: String): String? {
        return try {
            xpubFromSeed(seed, time, network)
        } catch (e: Exception) {
            null
        }
    }

    private fun signMs(seed: String, time: String, network: String): String {
        return try {
            rootSignMs(seed, time, network)
        } catch (e: Exception) {
            ""
        }
    }

    private fun processNewInvite(
        seed: String,
        uniqueTime: String,
        state: ByteArray,
        inviteQr: String
    ): RunReturn? {
        return try {
            processInvite(seed, uniqueTime, state, inviteQr)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.ProcessInviteError)
            }
            null
        }
    }

    private fun parseNewInvite(inviteQr: String): ParsedInvite? {
        return try {
            parseInvite(inviteQr)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.ProcessInviteError)
            }
            null
        }
    }

    override fun createContact(
        contact: NewContact
    ) {
        val now = getTimestampInMilliseconds()

        try {
            val runReturn = addContact(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                contact.lightningNodePubKey?.value!!,
                contact.lightningRouteHint?.value!!,
                ownerInfoStateFlow.value.alias ?: "",
                ownerInfoStateFlow.value?.picture ?: "",
                3000.toULong(),
                contact.inviteCode,
                contact.contactAlias?.value
            )

            handleRunReturn(
                runReturn,
                mqttClient
            )
        } catch (e: Exception) {
            Log.e("MQTT_MESSAGES", "add contact excp $e")
        }
    }

    override fun deleteContact(pubKey: String) {
        removeKeysFromUserState(listOf(pubKey))
    }

    // MQTT Connection Management

    override fun initializeMqttAndSubscribe(
        serverUri: String,
        mnemonicWords: WalletMnemonic,
        ownerInfo: OwnerInfo
    ) {
        _mixerIp = serverUri
        walletMnemonic = mnemonicWords
        network = if (isProductionServer()) MAINNET_NETWORK else REGTEST_NETWORK

        if (isConnected()) {
            _ownerInfoStateFlow.value = OwnerInfo(
                ownerInfo.alias,
                ownerInfo.picture,
                ownerInfoStateFlow.value.userState,
                ownerInfo.messageLastIndex
            )

            // It's called when invitee first init the dashboard
            if (inviterContact != null) {
                createContact(inviterContact!!)
                inviterContact = null
            }

            if (inviteInitialTribe != null) {
                notifyListeners {
                    onInitialTribe(inviteInitialTribe!!, isProductionEnvironment())
                }
            }

            // return always that the mqtt is connected
            return
        }
        _ownerInfoStateFlow.value = ownerInfo

        val seed = try {
            mnemonicToSeed(mnemonicWords.value)
        } catch (e: Exception) {
            null
        }

        val xPub = seed?.let {
            generateXPub(
                it,
                getTimestampInMilliseconds(),
                network
            )
        }

        val now = getTimestampInMilliseconds()

        val sig = seed?.let {
            rootSignMs(
                it,
                now,
                network
            )
        }

        if (xPub != null && sig != null) {
            ownerSeed = seed

            connectToMQTT(
                mixerIp!!,
                xPub,
                now,
                sig,
            )
        }
    }

    private fun connectToMQTT(
        serverURI: String,
        clientId: String,
        key: String,
        password: String,
    ) {
        try {
            mqttClient = MqttAsyncClient(serverURI, clientId, null)

            val sslContext: SSLContext? = if (isProductionEnvironment()) {
                SSLContext.getInstance("TLS").apply {
                    val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {}
                        override fun checkServerTrusted(
                            chain: Array<X509Certificate>,
                            authType: String
                        ) {}
                    })
                    init(null, trustAllCerts, SecureRandom())
                }
            } else {
                null
            }

            val options = MqttConnectOptions().apply {
                this.userName = key
                this.password = password.toCharArray()
                this.socketFactory = sslContext?.socketFactory
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT_MESSAGES", "MQTT CONNECTED!")
                    hasAttemptedReconnect = false

                    subscribeOwnerMQTT()

                    notifyListeners {
                        onNetworkStatusChange(true)
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d("MQTT_MESSAGES", "Failed to connect to MQTT: ${exception?.message}")

                    // If it's the first time trying to connect, try to reconnect otherwise
                    // prevent infinite loop
                    if (!hasAttemptedReconnect) {
                        hasAttemptedReconnect = true
                        reconnectWithBackoff()
                    }
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d("MQTT_MESSAGES", "MQTT DISCONNECTED! $cause ${cause?.message}")

                    reconnectWithBackoff()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    // Handle incoming messages here
                    Log.d("MQTT_MESSAGES", "toppicArrived: $topic")

                    if (topic?.contains("/ping") == true) {

                        notifyListeners {
                            listenToOwnerCreation {
                                Log.d("MQTT_MESSAGES", "OWNER EXIST!")
                                handleMessageArrived(topic, message)
                            }
                        }

                    } else {
                        handleMessageArrived(topic, message)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Handle message delivery confirmation here
                }
            })
        } catch (e: MqttException) {
            Log.d("MQTT_MESSAGES", "MQTT DISCONNECTED! exception ${e.printStackTrace()}")

            notifyListeners {
                onConnectManagerError(ConnectManagerError.MqttConnectError)
            }

            reconnectWithBackoff()
        }
    }

    private fun handleMessageArrived(topic: String?, message: MqttMessage?) {
        if (topic != null && message?.payload != null) {
            try {
                val runReturn = handle(
                    topic,
                    message.payload,
                    ownerSeed ?: "",
                    getTimestampInMilliseconds(),
                    getCurrentUserState(),
                    ownerInfoStateFlow.value?.alias ?: "",
                    ownerInfoStateFlow.value?.picture ?: ""
                )

                mqttClient?.let { client ->
                    handleRunReturn(runReturn, client)
                }

                Log.d("MQTT_MESSAGES", " this is handle ${runReturn}")

                runReturn.msgs.forEach {
                    Log.d("RESTORE_MESSAGES", " ${it}")
                }

            } catch (e: Exception) {
                Log.e("MQTT_MESSAGES", "handleMessageArrived ${e.message}")
            }
        }
    }

    private fun subscribeOwnerMQTT() {
        try {
            mqttClient?.let { client ->
                // Network setup and handling
                val networkSetup = setNetwork(network)
                handleRunReturn(networkSetup, client)

                // Initial setup and handling
                val setUp = initialSetup(
                    ownerSeed!!,
                    getTimestampInMilliseconds(),
                    getCurrentUserState(),
                    UUID.randomUUID().toString(),
                    inviterContact?.inviteCode
                )
                handleRunReturn(setUp, client)

                val qos = IntArray(1) { 1 }

                val tribeSubtopic = getTribeManagementTopic(
                    ownerSeed!!,
                    getTimestampInMilliseconds(),
                    getCurrentUserState()
                )
                client.subscribe(arrayOf(tribeSubtopic), qos)

                if (ownerInfoStateFlow.value.messageLastIndex != null && restoreMnemonicWords?.isEmpty() == true) {

                    val fetchMessages = fetchMsgsBatch(
                        ownerSeed!!,
                        getTimestampInMilliseconds(),
                        getCurrentUserState(),
                        ownerInfoStateFlow.value.messageLastIndex?.plus(1)?.toULong() ?: 0.toULong(),
                        null,
                        false
                    )
                    handleRunReturn(fetchMessages, mqttClient)

                    getReadMessages()
                    getMutedChats()
                }
            }
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SubscribeOwnerError)
            }
            Log.e("MQTT_MESSAGES", "${e.message}")
        }
    }

    override fun sendMessage(
        sphinxMessage: String,
        contactPubKey: String,
        provisionalId: Long,
        messageType: Int,
        amount: Long?,
        isTribe: Boolean
    ) {
        val now = getTimestampInMilliseconds()

        // Have to include al least 1 sat for tribe messages
        val nnAmount = when {
            isTribe && (amount == null || amount == 0L) -> 1L
            isTribe -> amount ?: 1L
            else -> amount ?: 0L
        }
        try {
            val message = send(
                ownerSeed!!,
                now,
                contactPubKey,
                messageType.toUByte(),
                sphinxMessage,
                getCurrentUserState(),
                ownerInfoStateFlow.value?.alias ?: "",
                ownerInfoStateFlow.value?.picture ?: "",
                convertSatsToMillisats(nnAmount),
                isTribe
            )
            handleRunReturn(message, mqttClient)

            message.msgs.firstOrNull()?.let { sentMessage ->
                sentMessage.uuid?.let { msgUuid ->
                    notifyListeners {
                        onMessageTagAndUuid(sentMessage.tag, msgUuid, provisionalId)
                    }
                }
            }

        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SendMessageError)
            }
            Log.e("MQTT_MESSAGES", "send ${e.message}")
        }
    }

    override fun deleteMessage(
        sphinxMessage: String,
        contactPubKey: String,
        isTribe: Boolean
    ) {
        val now = getTimestampInMilliseconds()

        // Have to include al least 1 sat for tribe messages
        val nnAmount = if (isTribe) 1L else 0L

        try {
            val message = send(
                ownerSeed!!,
                now,
                contactPubKey,
                17.toUByte(), // Fix this hardcoded value
                sphinxMessage,
                getCurrentUserState(),
                ownerInfoStateFlow.value?.alias ?: "",
                ownerInfoStateFlow.value?.picture ?: "",
                convertSatsToMillisats(nnAmount),
                isTribe
            )
            handleRunReturn(message, mqttClient)

        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.DeleteMessageError)
            }
            Log.e("MQTT_MESSAGES", "send ${e.message}")
        }
    }

    override fun joinToTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        isPrivate: Boolean,
        userAlias: String,
        priceToJoin: Long
    ) {
        val now = getTimestampInMilliseconds()
        val amount = if (priceToJoin == 0L) 1L else priceToJoin

        try {
            val joinTribeMessage = joinTribe(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                tribePubKey,
                tribeRouteHint,
                userAlias,
                convertSatsToMillisats(amount),
                isPrivate
            )
            handleRunReturn(joinTribeMessage, mqttClient)

        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.JoinTribeError)
            }
            Log.e("MQTT_MESSAGES", "joinTribe ${e.message}")
        }
    }

    override fun createTribe(tribeJson: String) {
        val now = getTimestampInMilliseconds()

        try {
            val tribeServerPubKey = getTribeServerPubKey()
            val createTribe = tribeServerPubKey?.let { tribePubKey ->
                uniffi.sphinxrs.createTribe(
                    ownerSeed!!,
                    now,
                    getCurrentUserState(),
                    tribePubKey,
                    tribeJson
                )
            }
            if (createTribe != null) {
                handleRunReturn(createTribe, mqttClient)
            }
        }
        catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.CreateTribeError)
            }
            Log.e("MQTT_MESSAGES", "createTribe ${e.message}")
        }
    }

    override fun createInvite(
        nickname: String,
        welcomeMessage: String,
        sats: Long,
        tribeServerPubKey: String?
    ) {
        val now = getTimestampInMilliseconds()

        // Initial tribe is hardcoded for now
        try {
            val createInvite = makeInvite(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                _mixerIp!!,
                convertSatsToMillisats(sats),
                ownerInfoStateFlow.value?.alias ?: "",
                "34.229.52.200:8801",
                "02792ee5b9162f9a00686aaa5d5274e91fd42a141113007797b5c1872d43f78e07"
            )

            if (createInvite.newInvite != null) {
                val invite = createInvite.newInvite ?: return
                val code = codeFromInvite(invite)
                val tag = createInvite.msgs.getOrNull(0)?.tag

                currentInvite = NewInvite(
                    nickname,
                    sats,
                    welcomeMessage,
                    tribeServerPubKey,
                    invite,
                    code,
                    tag
                )

                handleRunReturn(createInvite, mqttClient)
            }
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.CreateInviteError)
            }
            Log.e("MQTT_MESSAGES", "createInvite ${e.message}")
        }
    }

    override fun createInvoice(amount: Long, memo: String): Pair<String, String>? {
        val now = getTimestampInMilliseconds()

        try {
            val makeInvoice = uniffi.sphinxrs.makeInvoice(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                convertSatsToMillisats(amount),
                memo
            )
            handleRunReturn(makeInvoice, mqttClient)

            val invoice = makeInvoice.invoice

            if (invoice != null) {
                val paymentHash = paymentHashFromInvoice(invoice)
                return Pair(invoice, paymentHash)
            }

        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.CreateInvoiceError)
            }
            Log.e("MQTT_MESSAGES", "makeInvoice ${e.message}")
        }
        return null
    }

    override fun sendKeySend(pubKey: String, amount: Long) {
        val now = getTimestampInMilliseconds()
        try {
            val keySend = uniffi.sphinxrs.keysend(
                ownerSeed!!,
                now,
                pubKey,
                getCurrentUserState(),
                convertSatsToMillisats(amount),
                null
            )
            handleRunReturn(keySend, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SendKeySendError)
            }
            Log.e("MQTT_MESSAGES", "sendKeySend ${e.message}")
        }
    }

    override fun getTribeServerPubKey(): String? {
        return try {
            val defaultTribe = getDefaultTribeServer(
                getCurrentUserState()
            )
            Log.d("MQTT_MESSAGES", "getDefaultTribeServer $defaultTribe")
            defaultTribe
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.ServerPubKeyError)
            }
            null
        }
    }

    override fun processInvoicePayment(paymentRequest: String) {
        val now = getTimestampInMilliseconds()

        try {
            val processInvoice = uniffi.sphinxrs.payContactInvoice(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                paymentRequest,
                ownerInfoStateFlow.value?.alias ?: "",
                ownerInfoStateFlow.value?.picture ?: "",
                false // not implemented on tribes yet
            )
            handleRunReturn(processInvoice, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.PayContactInvoiceError)
            }
            Log.e("MQTT_MESSAGES", "processInvoicePayment ${e.message}")
        }
    }

    override fun retrievePaymentHash(paymentRequest: String): String? {
        return try {
            paymentHashFromInvoice(paymentRequest)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.PaymentHashError)
            }
            null
        }
    }

    override fun getPayments(
        lastMsgDate: Long,
        limit: Int,
        scid: Long?,
        remoteOnly: Boolean?,
        minMsat: Long?,
        reverse: Boolean?
    ) {
        val now = getTimestampInMilliseconds()

        try {
            val payments = uniffi.sphinxrs.fetchPayments(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                lastMsgDate.plus(1000).toULong(),
                limit.toUInt(),
                scid?.toULong(),
                remoteOnly ?: false,
                minMsat?.toULong(),
                true
            )
            handleRunReturn(payments, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.LoadTransactionsError)
            }
            Log.e("MQTT_MESSAGES", "getPayments ${e.message}")
        }
    }

    override fun getPubKeyFromChildIndex(childIndex: Long): String? {
        return try {
            uniffi.sphinxrs.contactPubkeyByChildIndex(
                getCurrentUserState(),
                childIndex.toULong()
            )
        } catch (e: Exception) {
//            notifyListeners {
//                onConnectManagerError(ConnectManagerError.PubKeyFromChildIndexError)
//            }
            null
        }
    }

    override fun getPubKeyByEncryptedChild(child: String): String? {
        return try {
            uniffi.sphinxrs.contactPubkeyByEncryptedChild(
                ownerSeed!!,
                getCurrentUserState(),
                child
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun retrieveTribeMembersList(tribeServerPubKey: String, tribePubKey: String) {
        val now = getTimestampInMilliseconds()

        try {
            val tribeMembers = listTribeMembers(
                ownerSeed!!,
                now,
                getCurrentUserState(),
                tribeServerPubKey,
                tribePubKey
            )
            handleRunReturn(tribeMembers, mqttClient)
        }
        catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.ListTribeMembersError)
            }
            Log.e("MQTT_MESSAGES", "tribeMembers ${e.message}")
        }
    }

    override fun fetchMessagesOnRestoreAccount(totalHighestIndex: Long?) {
        try {
            val limit = 250
            val fetchMessages = uniffi.sphinxrs.fetchMsgsBatch(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState(),
                totalHighestIndex?.toULong() ?: 0.toULong(),
                limit.toUInt(),
                true,
            )
            handleRunReturn(fetchMessages, mqttClient)

            notifyListeners {
                onRestoreNextPageMessages(totalHighestIndex ?: 0, limit)
            }
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.FetchMessageError)
            }
            Log.e("MQTT_MESSAGES", "fetchMessagesOnRestoreAccount ${e.message}")
        }
    }

    override fun fetchFirstMessagesPerKey() {
        try {
            val fetchFirstMsg = uniffi.sphinxrs.fetchFirstMsgsPerKey(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState(),
                0.toULong(),
                null,
                false
            )
            handleRunReturn(fetchFirstMsg, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.FetchFirstMessageError)
            }
            Log.e("MQTT_MESSAGES", "fetchFirstMessagesPerKey ${e.message}")
        }
    }

    override fun getAllMessagesCount() {
        try {
            val messageAmount = getMsgsCounts(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState()
            )
            handleRunReturn(messageAmount, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.MessageCountError)
            }
            Log.e("MQTT_MESSAGES", "getAllMessagesCount ${e.message}")
        }
    }

    override fun reconnectWithBackoff() {
        if (!isConnected()) {
            resetMQTT()

            notifyListeners {
                onNetworkStatusChange(false)
            }

            if (mixerIp != null && walletMnemonic != null && ownerInfoStateFlow.value != null) {

                initializeMqttAndSubscribe(
                    mixerIp!!,
                    walletMnemonic!!,
                    ownerInfoStateFlow.value,
                )
            }

            Log.d("MQTT_MESSAGES", "onReconnectMqtt")
        }
    }

    override fun setOwnerDeviceId(deviceId: String) {
        try {
            val token = setPushToken(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState(),
                deviceId
            )
            handleRunReturn(token, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SetDeviceIdError)
            }
            Log.e("MQTT_MESSAGES", "setOwnerDeviceId ${e.message}")
        }
    }

    override fun setMute(muteLevel: Int, contactPubKey: String) {
        try {
            val mute = mute(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState(),
                contactPubKey,
                muteLevel.toUByte()
            )
            handleRunReturn(mute, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SetMuteError)
            }
        }
    }

    override fun generateMediaToken(
        contactPubKey: String,
        muid: String,
        host: String,
        metaData: String?,
        amount: Long?
    ): String? {
        val now = getTimestampInMilliseconds()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.YEAR, 1)

        val yearFromNow = try {
            (calendar.timeInMillis / 1000).toUInt()
        } catch (e: Exception) {
            null
        }

        return try {
            if (amount != null && amount > 0) {
                makeMediaTokenWithPrice(
                    ownerSeed!!,
                    now,
                    getCurrentUserState(),
                    host,
                    muid,
                    contactPubKey,
                    yearFromNow!!,
                    amount.toULong(),
                )
            } else {
                if (metaData != null) {
                    makeMediaTokenWithMeta(
                        ownerSeed!!,
                        now,
                        getCurrentUserState(),
                        host,
                        muid,
                        contactPubKey,
                        yearFromNow!!,
                        metaData
                    )
                } else {
                    makeMediaToken(
                        ownerSeed!!,
                        now,
                        getCurrentUserState(),
                        host,
                        muid,
                        contactPubKey,
                        yearFromNow!!
                    )
                }
            }
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.MediaTokenError)
            }
            Log.d("MQTT_MESSAGES", "Error to generate media token $e")
            null
        }
    }

    override fun readMessage(contactPubKey: String, messageIndex: Long) {
        try {
            val contacts = listContacts(getCurrentUserState())
            Log.d("MQTT_MESSAGES", "readMessage contacts ${contacts}")

            val readMessage = read(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState(),
                contactPubKey,
                messageIndex.toULong()
            )
            handleRunReturn(readMessage, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.ReadMessageError)
            }
            Log.e("MQTT_MESSAGES", "readMessage ${e.message}")
        }
    }

    override fun getReadMessages() {
        try {
            val readMessages = getReads(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState()
            )
            handleRunReturn(readMessages, mqttClient)
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.GetReadMessagesError)
            }
            Log.e("MQTT_MESSAGES", "getReadMessages ${e.message}")
        }
    }

    override fun getMutedChats() {
        try {
            val mutedChats = getMutes(
                ownerSeed!!,
                getTimestampInMilliseconds(),
                getCurrentUserState()
            )
            handleRunReturn(mutedChats, mqttClient)
        } catch (e: Exception) {
            Log.e("MQTT_MESSAGES", "getMutedChats ${e.message}")
        }
    }

    private fun publishTopicsSequentially(topics: Array<String>, messages: Array<String>?, index: Int) {
        if (index < topics.size) {
            val topic = topics[index]
            val mqttMessage = messages?.getOrNull(index)

            val message = if (mqttMessage?.isNotEmpty() == true) {
                MqttMessage(mqttMessage.toByteArray())
            } else {
                MqttMessage()
            }

            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    // Recursively call the function with the next index
                    publishTopicsSequentially(topics, messages, index + 1)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d("MQTT_MESSAGES", "Failed to publish to $topic: ${exception?.message}")
                }
            })
        }
    }

    private fun handleRunReturn(rr: RunReturn, client: MqttAsyncClient?) {
        if (client != null) {
            // Set updated state into db
            rr.stateMp?.let {
                storeUserState(it)
                Log.d("MQTT_MESSAGES", "=> stateMp $it")
            }

            rr.stateToDelete.let {
                removeKeysFromUserState(it)
                Log.d("MQTT_MESSAGES", "=> stateToDelete $it")
            }

            rr.subscriptionTopics.forEach { topic ->
                val qos = IntArray(1) { 1 }
                client.subscribe(arrayOf(topic), qos)
                Log.d("MQTT_MESSAGES", "=> subscribed to $topic")
            }

            // Publish to topics based on the new array structure
            rr.topics.forEachIndexed { index, topic ->
                val payload = rr.payloads.getOrElse(index) { ByteArray(0) }
                client.publish(topic, MqttMessage(payload))
                Log.d("MQTT_MESSAGES", "=> published to $topic")
            }

            // Set your balance
            rr.newBalance?.let { newBalance ->
                convertMillisatsToSats(newBalance)?.let { balance ->
                    notifyListeners {
                        onNewBalance(balance)
                    }
                }
                Log.d("MQTT_MESSAGES", "===> BALANCE ${newBalance.toLong()}")
            }

            // Process each message in the new msgs array
            if (rr.msgs.isNotEmpty()) {

                if (restoreMnemonicWords?.isNotEmpty() == true) {

                    val contactsToRestore = rr.msgs.filter {
                        it.type?.toInt() == 33 || it.type?.toInt() == 11 || it.type?.toInt() == 10
                    }.map { it.sender }.distinct()

                    if (contactsToRestore.isNotEmpty()) {
                        notifyListeners {
                            onRestoreContacts(contactsToRestore)
                        }
                    }

                    val tribesToRestore = rr.msgs.filter {
                        it.type?.toInt() == 20 || it.type?.toInt() == 14
                    }.map {
                        Pair(it.sender, it.fromMe)
                    }

                    if (tribesToRestore.isNotEmpty()) {
                        notifyListeners {
                            onRestoreTribes(tribesToRestore, isProductionEnvironment())
                        }
                    }
                }

                rr.msgs.forEach { msg ->
                    notifyListeners {
                        onMessage(
                            msg.message.orEmpty(),
                            msg.sender.orEmpty(),
                            msg.type?.toInt() ?: 0,
                            msg.uuid.orEmpty(),
                            msg.index.orEmpty(),
                            msg.timestamp?.toLong(),
                            msg.sentTo.orEmpty(),
                            msg.msat?.let { convertMillisatsToSats(it) },
                            msg.fromMe
                        )
                    }
                }
            }

            // Handling new tribe and tribe members
            rr.newTribe?.let { newTribe ->
                notifyListeners {
                    onNewTribeCreated(newTribe)
                }
                Log.d("MQTT_MESSAGES", "===> newTribe $newTribe")
            }

            rr.tribeMembers?.let { tribeMembers ->
                notifyListeners {
                    onTribeMembersList(tribeMembers)
                }
                Log.d("MQTT_MESSAGES", "=> tribeMembers $tribeMembers")
            }

            // Handling my contact info
            rr.myContactInfo?.let { myContactInfo ->
                val parts = myContactInfo.split("_", limit = 2)
                val okKey = parts.getOrNull(0)
                val routeHint = parts.getOrNull(1)
                val isRestoreAccount = restoreMnemonicWords?.isNotEmpty() == true

                if (okKey != null && routeHint != null) {
                    notifyListeners {
                        onOwnerRegistered(
                            okKey,
                            routeHint,
                            isRestoreAccount,
                            getRawMixerIp(),
                            tribeServer,
                            isProductionEnvironment()
                        )
                    }
                }
                Log.d("MQTT_MESSAGES", "=> my_contact_info $myContactInfo")
                Log.d("MQTT_MESSAGES", "=> my_contact_info mixerIp $mixerIp")
                Log.d("MQTT_MESSAGES", "=> my_contact_info tribeServer $tribeServer")
            }

            // Handling new invite created
            rr.newInvite?.let { invite ->
                Log.d("MQTT_MESSAGES", "=> new_invite $invite")
            }

            rr.inviterContactInfo?.let { inviterInfo ->
                val parts = inviterInfo.split("_")
                val okKey = parts.getOrNull(0)?.toLightningNodePubKey()
                val routeHint = "${parts.getOrNull(1)}_${parts.getOrNull(2)}".toLightningRouteHint()

                val code = codeFromInvite(inviteCode!!)

                inviterContact = NewContact(
                    null,
                    okKey,
                    routeHint,
                    null,
                    false,
                    null,
                    code,
                    null,
                    null,
                )

                subscribeOwnerMQTT()

                Log.d("MQTT_MESSAGES", "=> inviterInfo $inviterInfo")
            }

            rr.msgsCounts?.let { msgsCounts ->
                notifyListeners {
                    onMessagesCounts(msgsCounts)
                }
                Log.d("MQTT_MESSAGES", "=> msgsCounts $msgsCounts")
            }

            rr.msgsTotal?.let { msgsTotal ->
                Log.d("MQTT_MESSAGES", "=> msgsTotal $msgsTotal")
            }

            rr.lastRead?.let { lastRead ->
                notifyListeners {
                    onLastReadMessages(lastRead)
                }
                Log.d("MQTT_MESSAGES", "=> lastRead $lastRead")
            }

            rr.initialTribe?.let { initialTribe ->
                // Call joinTribe with the url that comes on initialTribe
                inviteInitialTribe = initialTribe
                Log.d("MQTT_MESSAGES", "=> initialTribe $initialTribe")
            }

            // Handling other properties like sentStatus, settledStatus, error, etc.
            rr.error?.let { error ->
                Log.d("MQTT_MESSAGES", "=> error $error")
            }

            // Sent
            rr.sentStatus?.let { sentStatus ->
                val tagAndStatus = extractTagAndStatus(sentStatus)

                if (tagAndStatus?.first == currentInvite?.tag) {
                    if (tagAndStatus?.second == true) {

                        notifyListeners {
                            onNewInviteCreated(
                                currentInvite?.nickname.orEmpty(),
                                currentInvite?.inviteString ?: "",
                                currentInvite?.inviteCode ?: "",
                                currentInvite?.invitePrice ?: 0L,
                            )
                        }
                        currentInvite = null
                    }
                } else {
                    notifyListeners {
                        onSentStatus(sentStatus)
                    }
                }

                Log.d("MQTT_MESSAGES", "=> sent_status $sentStatus")
            }

            // Settled
            rr.settledStatus?.let { settledStatus ->
                Log.d("MQTT_MESSAGES", "=> settled_status $settledStatus")
            }

            rr.lspHost?.let { lspHost ->
                mixerIp = lspHost
            }

            rr.muteLevels?.let { muteLevels ->
                notifyListeners {
                    onUpdateMutes(muteLevels)
                }
                Log.d("MQTT_MESSAGES", "=> muteLevels $muteLevels")
            }
            rr.payments?.let { payments ->
                notifyListeners {
                    onPayments(payments)
                }
                logLongString("PAYMENTS_MESSAGES", payments)
            }
            rr.paymentsTotal?.let { paymentsTotal ->
                Log.d("MQTT_MESSAGES", "=> paymentsTotal $paymentsTotal")
            }
        } else {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.MqttClientError)
            }
        }
    }

     private fun logLongString(tag: String, str: String) {
        val maxLogSize = 4000 // You can set it to 4000 or any suitable chunk size
        for (i in 0..str.length / maxLogSize) {
            val start = i * maxLogSize
            val end = (i + 1) * maxLogSize
            Log.d(tag, str.substring(start, min(end, str.length)))
        }
    }

    override fun retrieveLspIp(): String? {
        return mixerIp
    }

    override fun processChallengeSignature(challenge: String) {

        val signedChallenge = try {
            signBytes(
                ownerSeed!!,
                0.toULong(),
                getTimestampInMilliseconds(),
                network,
                challenge.toByteArray()
            )
        } catch (e: Exception) {
            notifyListeners {
                onConnectManagerError(ConnectManagerError.SignBytesError)
            }
            null
        }

        if (signedChallenge != null) {

            val sign = ByteArray(signedChallenge.length / 2) { index ->
                val start = index * 2
                val end = start + 2
                val byteValue = signedChallenge.substring(start, end).toInt(16)
                byteValue.toByte()
            }.encodeBase64()
                .replace("/", "_")
                .replace("+", "-")

            notifyListeners {
                onSignedChallenge(sign)
            }
        }
    }

    // Utility Methods

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun generateRandomBytes(size: Int): UByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        val uByteArray = UByteArray(size)

        for (i in bytes.indices) {
            uByteArray[i] = bytes[i].toUByte()
        }

        return uByteArray
    }

    private fun getTimestampInMilliseconds(): String =
        System.currentTimeMillis().toString()

    private fun resetMQTT() {
        if (mqttClient?.isConnected == true) {
            mqttClient?.disconnect()
        }
        mqttClient = null
    }


    private val synchronizedListeners = SynchronizedListenerHolder()

    override fun addListener(listener: ConnectManagerListener): Boolean {
        return synchronizedListeners.addListener(listener)
    }

    override fun removeListener(listener: ConnectManagerListener): Boolean {
        return synchronizedListeners.removeListener(listener)
    }

    private fun notifyListeners(action: ConnectManagerListener.() -> Unit) {
        synchronizedListeners.forEachListener { listener ->
            action(listener)
        }
    }

    private inner class SynchronizedListenerHolder {
        private val listeners: LinkedHashSet<ConnectManagerListener> = LinkedHashSet()

        fun addListener(listener: ConnectManagerListener): Boolean = synchronized(this) {
            listeners.add(listener).also {
                if (it) {
                    // Log listener registration
                }
            }
        }

        fun removeListener(listener: ConnectManagerListener): Boolean = synchronized(this) {
            listeners.remove(listener).also {
                if (it) {
                    // Log listener removal
                }
            }
        }

        fun forEachListener(action: (ConnectManagerListener) -> Unit) {
            synchronized(this) {
                listeners.forEach(action)
            }
        }
    }

    private fun storeUserState(state: ByteArray) {
        try {
            val decoded = MsgPack.decodeFromByteArray(MsgPackDynamicSerializer, state)
            (decoded as? MutableMap<String, ByteArray>)?.let {
                storeUserStateOnSharedPreferences(it)
            }

        } catch (e: Exception) { }
    }

    private fun storeUserStateOnSharedPreferences(newUserState: MutableMap<String, ByteArray>) {
        val existingUserState = retrieveUserStateMap(ownerInfoStateFlow.value.userState)
        existingUserState.putAll(newUserState)

        val encodedString = encodeMapToBase64(existingUserState)

        // Update class var
        _ownerInfoStateFlow.value = ownerInfoStateFlow.value.copy(
            userState = encodedString
        )

        // Update SharedPreferences
        notifyListeners {
            onUpdateUserState(encodedString)
        }
    }

    private fun removeKeysFromUserState(keys: List<String>) {
        val existingUserState = retrieveUserStateMap(ownerInfoStateFlow.value.userState)

        for (key in keys) {
            existingUserState.remove(key)
        }

        val encodedString = encodeMapToBase64(existingUserState)

        // Update class var
        _ownerInfoStateFlow.value = ownerInfoStateFlow.value.copy(
            userState = encodedString
        )

        // Update SharedPreferences
        notifyListeners {
            onUpdateUserState(encodedString)
        }
    }

    private fun retrieveUserStateMap(encodedString: String?): MutableMap<String, ByteArray> {
        val result = encodedString?.let {
            decodeBase64ToMap(it)
        } ?: mutableMapOf()

        return result
    }

    private fun getCurrentUserState(): ByteArray {
        val userStateMap = retrieveUserStateMap(ownerInfoStateFlow.value.userState)
        Log.d("MQTT_MESSAGES", "getCurrentUserState $userStateMap")
        return MsgPack.encodeToByteArray(MsgPackDynamicSerializer, userStateMap)
    }

    private fun encodeMapToBase64(map: MutableMap<String, ByteArray>): String {
        val encodedMap = mutableMapOf<String, String>()

        for ((key, value) in map) {
            encodedMap[key] = Base64.encodeToString(value, Base64.NO_WRAP)
        }

        val result = (encodedMap as Map<*, *>?)?.let { JSONObject(it).toString() } ?: ""


        return result
    }

    private fun extractTagAndStatus(sentStatusJson: String?): Pair<String, Boolean>? {
        if (sentStatusJson == null) return null

        try {
            val jsonObject = JSONObject(sentStatusJson)
            val tag = jsonObject.getString("tag")
            val status = jsonObject.getString("status") == "COMPLETE"

            return Pair(tag, status)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun decodeBase64ToMap(encodedString: String): MutableMap<String, ByteArray> {
        if (encodedString.isEmpty()) {
            return mutableMapOf()
        }

        val decodedMap = mutableMapOf<String, ByteArray>()

        try {
            val jsonObject = JSONObject(encodedString)
            val keys = jsonObject.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val encodedValue = jsonObject.getString(key)
                val decodedValue = Base64.decode(encodedValue, Base64.NO_WRAP)
                decodedMap[key] = decodedValue
            }
        } catch (e: JSONException) { }

        return decodedMap
    }

    private fun convertSatsToMillisats(sats: Long): ULong {
        return (sats * 1_000).toULong()
    }

    fun convertMillisatsToSats(millisats: ULong): Long? {
        try {
            return (millisats / 1_000uL).toLong()
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractTribeServerAndPubkey(url: String): Pair<String, String>? {
        val regex = Regex("https://([^/]+)/tribes/([^/]+)")
        return regex.find(url)?.groupValues?.takeIf { it.size == 3 }?.let {
            it[1] to it[2]
        }
    }
    private fun isConnected(): Boolean {
        return mqttClient?.isConnected ?: false
    }

}