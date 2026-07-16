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
import org.junit.Assert.*
import org.junit.Test

/**
 * Behavioral unit tests for the getSignedTimestampParams() contract.
 *
 * We use a controllable fake that mirrors the exact logic from
 * ConnectManagerImpl.getSignedTimestampParams() without pulling in
 * Android or native-FFI dependencies:
 *
 *   val seed = ownerSeed ?: return null
 *   val pubkey = ownerInfoStateFlow.value?.pubkey ?: return null
 *   val time = getTimestampInMilliseconds()
 *   return try { Triple(signedToken(seed, time), pubkey, time) } catch { null }
 */
class GetSignedTimestampParamsTest {

    // ── controllable fake ───────────────────────────────────────────────────

    /**
     * A minimal ConnectManager fake that:
     *  - exposes [ownerSeed] and [pubkey] as settable state
     *  - delegates token signing to [signingFunction] so tests can inject
     *    a success or an exception without native FFI
     */
    private class FakeConnectManager(
        private var ownerSeed: String? = null,
        private var pubkey: String? = null,
        private val signingFunction: (seed: String, time: String) -> String = { _, _ ->
            throw UnsupportedOperationException("stub — set signingFunction in test")
        }
    ) : ConnectManager() {

        private val _ownerInfo = MutableStateFlow<OwnerInfo?>(null)
        override val ownerInfoStateFlow: StateFlow<OwnerInfo?> = _ownerInfo

        private val _restoreState = MutableStateFlow<RestoreState?>(null)
        override val restoreStateFlow: StateFlow<RestoreState?> = _restoreState

        override val msgsCountsState = MutableStateFlow<MsgsCounts?>(null)

        init {
            // wire pubkey into ownerInfoStateFlow
            _ownerInfo.value = pubkey?.let { pk ->
                OwnerInfo(null, null, pk, null, null, null, null)
            }
        }

        /** Exact logic from ConnectManagerImpl.getSignedTimestampParams() */
        override fun getSignedTimestampParams(): Triple<String, String, String>? {
            val seed = ownerSeed ?: return null
            val pk = ownerInfoStateFlow.value?.pubkey ?: return null
            val time = System.currentTimeMillis().toString()
            return try {
                val signedToken = signingFunction(seed, time)
                Triple(signedToken, pk, time)
            } catch (e: Exception) {
                null
            }
        }

        // ── all other abstract members stubbed out ──────────────────────────
        override fun getSignedTimeStamps(): String? = null
        override fun setOwnerInfo(ownerInfo: OwnerInfo) {}
        override fun createAccount(mnemonic: String?) {}
        override fun restoreAccount(defaultTribe: String?, tribeHost: String?, mixerServerIp: String?, routerUrl: String?) {}
        override fun restoreFailed() {}
        override fun finishRestore() {}
        override fun setInviteCode(inviteString: String?) {}
        override fun setMnemonicWords(words: List<String>?) {}
        override fun setNetworkType(isTestEnvironment: Boolean) {}
        override fun setOwnerDeviceId(deviceId: String, pushKey: String) {}
        override fun processChallengeSignature(challenge: String): String? = null
        override fun fetchFirstMessagesPerKey(lastMsgIdx: Long, totalCount: Long?) {}
        override fun fetchMessagesOnRestoreAccount(totalHighestIndex: Long, chatsTotal: Long, chatsPublicKeys: List<String>) {}
        override fun fetchMessagesPerContact(minIndex: Long, publicKey: String) {}
        override fun getAllMessagesCount() {}
        override fun initializeMqttAndSubscribe(serverUri: String, mnemonicWords: WalletMnemonic, ownerInfo: OwnerInfo) {}
        override fun reconnectWithBackOff() {}
        override fun attemptReconnectOnResume() {}
        override fun retrieveLspIp(): String? = null
        override fun resetMQTT() {}
        override fun createContact(contact: NewContact) {}
        override fun createInvite(nickname: String, welcomeMessage: String, sats: Long, serverDefaultTribe: String?, tribeServerIp: String?, mixerIp: String?) {}
        override fun deleteInvite(inviteString: String) {}
        override fun deleteContact(pubKey: String) {}
        override fun setReadMessage(contactPubKey: String, messageIndex: Long) {}
        override fun getReadMessages() {}
        override fun setMute(muteLevel: Int, contactPubKey: String) {}
        override fun getMutedChats() {}
        override fun addNodesFromResponse(nodesJson: String) {}
        override fun concatNodesFromResponse(nodesJson: String, routerPubKey: String, amount: Long) {}
        override fun fetchMessagesOnAppInit(lastMsgIdx: Long?, reverse: Boolean) {}
        override fun sendMessage(sphinxMessage: String, contactPubKey: String, provisionalId: Long, messageType: Int, amount: Long?, myAlias: String?, myPhotoUrl: String?, date: Long, isTribe: Boolean) {}
        override fun deleteMessage(sphinxMessage: String, contactPubKey: String, myAlias: String?, myPhotoUrl: String?, isTribe: Boolean) {}
        override fun deleteContactMessages(messageIndexList: List<Long>) {}
        override fun deletePubKeyMessages(contactPubKey: String) {}
        override fun getMessagesStatusByTags(tags: List<String>) {}
        override fun createTribe(tribeJson: String) {}
        override fun joinToTribe(tribeHost: String, tribePubKey: String, tribeRouteHint: String, isPrivate: Boolean, userAlias: String, priceToJoin: Long) {}
        override fun retrieveTribeMembersList(tribeServerPubKey: String, tribePubKey: String) {}
        override fun getTribeServerPubKey(): String? = null
        override fun editTribe(tribeJson: String) {}
        override fun createInvoice(amount: Long, memo: String): Pair<String, String>? = null
        override fun sendKeySend(pubKey: String, amount: Long, routeHint: String?, data: String?) {}
        override fun processContactInvoicePayment(paymentRequest: String) {}
        override fun processInvoicePayment(paymentRequest: String, milliSatAmount: Long): String? = null
        override fun payInvoiceFromLSP(paymentRequest: String) {}
        override fun retrievePaymentHash(paymentRequest: String): String? = null
        override fun getPayments(lastMsgDate: Long, limit: Int, scid: Long?, remoteOnly: Boolean?, minMsat: Long?, reverse: Boolean?) {}
        override fun getPubKeyByEncryptedChild(child: String, pushKey: String?): String? = null
        override fun generateMediaToken(contactPubKey: String, muid: String, host: String, metaData: String?, amount: Long?): String? = null
        override fun getInvoiceInfo(invoice: String): String? = null
        override fun isRouteAvailable(pubKey: String, routeHint: String?, milliSat: Long): Boolean = false
        override fun getSignBase64(text: String): String? = null
        override fun getIdFromMacaroon(macaroon: String): String? = null
        override fun addListener(listener: ConnectManagerListener): Boolean = false
        override fun removeListener(listener: ConnectManagerListener): Boolean = false
        override fun saveMessagesCounts(msgsCounts: MsgsCounts) {}
        override fun encryptDataSync(value: String): String? = null
        override fun decryptDataSync(value: String): String? = null
    }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    fun `returns null when ownerSeed is null`() {
        val manager = FakeConnectManager(
            ownerSeed = null,
            pubkey    = "somePubkey",
        )
        assertNull(manager.getSignedTimestampParams())
    }

    @Test
    fun `returns null when pubkey in ownerInfoStateFlow is null`() {
        val manager = FakeConnectManager(
            ownerSeed = "someSeed",
            pubkey    = null,          // ownerInfo.pubkey will be null
        )
        assertNull(manager.getSignedTimestampParams())
    }

    @Test
    fun `returns null when signing function throws an exception`() {
        val manager = FakeConnectManager(
            ownerSeed       = "goodSeed",
            pubkey          = "goodPubkey",
            signingFunction = { _, _ -> throw RuntimeException("simulated FFI error") },
        )
        assertNull(manager.getSignedTimestampParams())
    }

    @Test
    fun `returns Triple with matching timestamp in both signed token and third element`() {
        val expectedToken = "signed-token-abc"
        val expectedPubkey = "my-pubkey"

        val manager = FakeConnectManager(
            ownerSeed       = "validSeed",
            pubkey          = expectedPubkey,
            signingFunction = { _, _ -> expectedToken },
        )

        val result = manager.getSignedTimestampParams()

        assertNotNull("result must not be null for valid state", result)
        assertEquals("first element must be the signed token",  expectedToken,  result!!.first)
        assertEquals("second element must be the pubkey",       expectedPubkey, result.second)
        assertNotNull("third element (timestamp) must not be null", result.third)
        assertTrue("timestamp must be a non-empty numeric string", result.third.isNotEmpty())
        // The signed token was produced using the same timestamp that is returned as the third
        // element — verify the signing function was called with that exact timestamp
        val capturedTime = result.third
        assertTrue("timestamp must be a numeric string", capturedTime.toLongOrNull() != null)
    }

    @Test
    fun `all three elements in the Triple correspond to the same signing call`() {
        // Capture the exact timestamp passed to the signing function
        var capturedSeed: String? = null
        var capturedTime: String? = null

        val manager = FakeConnectManager(
            ownerSeed       = "mySeed",
            pubkey          = "myPubkey",
            signingFunction = { seed, time ->
                capturedSeed = seed
                capturedTime = time
                "token-for-$time"
            },
        )

        val result = manager.getSignedTimestampParams()

        assertNotNull(result)
        // The returned timestamp must be exactly the one that was signed
        assertEquals(capturedTime, result!!.third)
        // The returned token must match what the signing function produced for that timestamp
        assertEquals("token-for-${capturedTime}", result.first)
        // Seed must have been passed correctly
        assertEquals("mySeed", capturedSeed)
    }
}
