package chat.sphinx.onboard_connecting.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_crypto_rsa.RSA
import chat.sphinx.concept_network_query_contact.model.GenerateTokenResponse
import chat.sphinx.concept_network_query_invite.NetworkQueryInvite
import chat.sphinx.concept_network_query_invite.model.RedeemInviteDto
import chat.sphinx.concept_network_tor.TorManager
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_connect_manager.model.ConnectionManagerState
import chat.sphinx.concept_signer_manager.CheckAdminCallback
import chat.sphinx.concept_signer_manager.SignerManager
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.key_restore.KeyRestore
import chat.sphinx.key_restore.KeyRestoreResponse
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.onboard_common.OnBoardStepHandler
import chat.sphinx.onboard_common.model.OnBoardInviterData
import chat.sphinx.onboard_common.model.OnBoardStep
import chat.sphinx.onboard_common.model.RedemptionCode
import chat.sphinx.onboard_connecting.R
import chat.sphinx.onboard_connecting.navigation.OnBoardConnectingNavigator
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_invite.InviteString
import chat.sphinx.wrapper_invite.toValidInviteStringOrNull
import chat.sphinx.wrapper_relay.*
import chat.sphinx.wrapper_rsa.RsaPrivateKey
import chat.sphinx.wrapper_rsa.RsaPublicKey
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.MotionLayoutViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import io.matthewnelson.crypto_common.annotations.UnencryptedDataAccess
import io.matthewnelson.crypto_common.clazzes.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.annotation.meta.Exhaustive
import javax.inject.Inject

internal inline val OnBoardConnectingFragmentArgs.restoreCode: RedemptionCode.AccountRestoration?
    get() {
        argCode?.let {
            val redemptionCode = RedemptionCode.decode(it)

            if (redemptionCode is RedemptionCode.AccountRestoration) {
                return redemptionCode
            }
        }
        return null
    }

internal inline val OnBoardConnectingFragmentArgs.connectionCode: RedemptionCode.NodeInvite?
    get() {
        argCode?.let {
            val redemptionCode = RedemptionCode.decode(it)

            if (redemptionCode is RedemptionCode.NodeInvite) {
                return redemptionCode
            }
        }
        return null
    }

internal inline val OnBoardConnectingFragmentArgs.swarmConnect: RedemptionCode.SwarmConnect?
    get() {
        argCode?.let {
            val redemptionCode = RedemptionCode.decode(it)

            if (redemptionCode is RedemptionCode.SwarmConnect) {
                return redemptionCode
            }
        }
        return null
    }

internal inline val OnBoardConnectingFragmentArgs.swarmClaim: RedemptionCode.SwarmClaim?
    get() {
        argCode?.let {
            val redemptionCode = RedemptionCode.decode(it)

            if (redemptionCode is RedemptionCode.SwarmClaim) {
                return redemptionCode
            }
        }
        return null
    }

internal inline val OnBoardConnectingFragmentArgs.inviteCode: InviteString?
    get() {
        argCode?.let {
            return it.toValidInviteStringOrNull()
        }
        return null
    }

@HiltViewModel
internal class OnBoardConnectingViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    handle: SavedStateHandle,
    val navigator: OnBoardConnectingNavigator,
    private val keyRestore: KeyRestore,
    private val walletDataHandler: WalletDataHandler,
    private val relayDataHandler: RelayDataHandler,
    private val torManager: TorManager,
    private val networkQueryInvite: NetworkQueryInvite,
    private val onBoardStepHandler: OnBoardStepHandler,
    private val connectManagerRepository: ConnectManagerRepository,
    val moshi: Moshi,
    private val rsa: RSA,
    private val app: Application
    ): MotionLayoutViewModel<
        Any,
        Context,
        OnBoardConnectingSideEffect,
        OnBoardConnectingViewState
        >(dispatchers, OnBoardConnectingViewState.Connecting),
    CheckAdminCallback
{

    private val args: OnBoardConnectingFragmentArgs by handle.navArgs()
    private lateinit var signerManager: SignerManager

    private val userStateSharedPreferences: SharedPreferences =
        app.getSharedPreferences(USER_STATE_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    companion object {
        const val USER_STATE_SHARED_PREFERENCES = "user_state_settings"
        const val ONION_STATE_KEY = "onion_state"
    }

    init {
        viewModelScope.launch(mainImmediate) {
            delay(500L)
            processCode()
        }
        collectConnectionState()
        collectConnectManagerErrorState()
    }

    fun setSignerManager(signerManager: SignerManager) {
        signerManager.setWalletDataHandler(walletDataHandler)
        signerManager.setMoshi(moshi)
//        signerManager.setNetworkQueryContact(networkQueryContact)

        this.signerManager = signerManager
    }

    private fun processCode() {
        viewModelScope.launch(mainImmediate) {
            args.restoreCode?.let { restoreCode ->
                updateViewState(
                    OnBoardConnectingViewState.Transition_Set2_DecryptKeys(restoreCode)
                )
            } ?: args.inviteCode?.let { inviteCode ->
                redeemInvite(inviteCode)
            } ?: run {
                if (signerManager.isPhoneSignerSettingUp()) {
                    continuePhoneSignerSetup()
                } else {
                    connectManagerRepository.createOwnerAccount("34.229.52.200:1883")

//                    submitSideEffect(OnBoardConnectingSideEffect.InvalidCode)
//                    navigator.popBackStack()
                }
            }
        }
    }

    private fun storeUserState(state: String) {
        val editor = userStateSharedPreferences.edit()

        editor.putString(ONION_STATE_KEY, state)
        editor.apply()
    }


    private var decryptionJob: Job? = null
    @OptIn(RawPasswordAccess::class)
    fun decryptInput(viewState: OnBoardConnectingViewState.Set2_DecryptKeys) {
        // TODO: Replace with automatic launching upon entering the 6th PIN character
        //  when Authentication View's Layout gets incorporated
        if (viewState.pinWriter.size() != 6 /*TODO: https://github.com/stakwork/sphinx-kotlin/issues/9*/) {
            viewModelScope.launch(mainImmediate) {
                submitSideEffect(OnBoardConnectingSideEffect.InvalidPinLength)
            }
        }

        if (decryptionJob?.isActive == true) {
            return
        }

        var decryptionJobException: Exception? = null
        decryptionJob = viewModelScope.launch(default) {
            try {
                val pin = viewState.pinWriter.toCharArray()

                val decryptedCode: RedemptionCode.AccountRestoration.DecryptedRestorationCode =
                    viewState.restoreCode.decrypt(pin, dispatchers)

                // TODO: Ask to use Tor before any network calls go out.
                // TODO: Hit relayUrl to verify creds work

                val relayUrl = relayDataHandler.formatRelayUrl(decryptedCode.relayUrl)
                torManager.setTorRequired(relayUrl.isOnionAddress)

                var transportKey: RsaPublicKey? = null

//                networkQueryRelayKeys.getRelayTransportKey(relayUrl).collect { loadResponse ->
//                    @Exhaustive
//                    when (loadResponse) {
//                        is LoadResponse.Loading -> {}
//                        is Response.Error -> {}
//
//                        is Response.Success -> {
//                            transportKey = RsaPublicKey(loadResponse.value.transport_key.toCharArray())
//
//                            relayDataHandler.persistRelayTransportKey(transportKey)
//                        }
//                    }
//                }

                var ownerPrivateKey = RsaPrivateKey(
                    Password(decryptedCode.privateKey.value.copyOf()).value
                )

                var success: KeyRestoreResponse.Success? = null
                keyRestore.restoreKeys(
                    privateKey = decryptedCode.privateKey,
                    publicKey = decryptedCode.publicKey,
                    userPin = pin,
                    relayUrl = decryptedCode.relayUrl,
                    authorizationToken = decryptedCode.authorizationToken,
                    transportKey = transportKey
                ).collect { flowResponse ->
                    // TODO: Implement in Authentication View when it get's built/refactored
                    if (flowResponse is KeyRestoreResponse.Success) {
                        success = flowResponse
                    }
                }

                success?.let { _ ->
                    // Overwrite PIN
                    viewState.pinWriter.reset()
                    repeat(6) {
                        viewState.pinWriter.append('0')
                    }

                } ?: updateViewState(
                    OnBoardConnectingViewState.Set2_DecryptKeys(viewState.restoreCode)
                ).also {
                    submitSideEffect(OnBoardConnectingSideEffect.InvalidPin)
                }

            } catch (e: Exception) {
                decryptionJobException = e
            }
        }

        viewModelScope.launch(mainImmediate) {
            decryptionJob?.join()
            decryptionJobException?.let { exception ->
                updateViewState(
                    // reset view state
                    OnBoardConnectingViewState.Set2_DecryptKeys(viewState.restoreCode)
                )
                exception.printStackTrace()
                submitSideEffect(OnBoardConnectingSideEffect.DecryptionFailure)
            }
        }
    }

    private suspend fun redeemInvite(input: InviteString) {
        networkQueryInvite.redeemInvite(input).collect { loadResponse ->
            @Exhaustive
            when (loadResponse) {
                is LoadResponse.Loading -> {}
                is Response.Error -> {
                    submitSideEffect(OnBoardConnectingSideEffect.InvalidInvite)
                    navigator.popBackStack()
                }
                is Response.Success -> {
                    val inviteResponse = loadResponse.value.response
                }
            }
        }
    }

    private suspend fun continueWithToken(
        token: AuthorizationToken,
        relayUrl: RelayUrl,
        transportKey: RsaPublicKey? = null,
        redeemInviteDto: RedeemInviteDto?
    ) {
        val inviterData: OnBoardInviterData? = redeemInviteDto?.let { dto ->
            OnBoardInviterData(
                dto.nickname,
                dto.pubkey?.toLightningNodePubKey(),
                dto.route_hint,
                dto.message,
                dto.action,
                dto.pin
            )
        }
    }

    private var tokenRetries = 0
    private suspend fun registerTokenAndStartOnBoard(
        ip: RelayUrl,
        nodePubKey: String?,
        password: String?,
        redeemInviteDto: RedeemInviteDto?,
        token: AuthorizationToken? = null,
        transportKey: RsaPublicKey? = null,
        transportToken: TransportToken? = null
    ) {

        @OptIn(RawPasswordAccess::class)
        val authToken = token ?: AuthorizationToken(
            PasswordGenerator(passwordLength = 20).password.value.joinToString("")
        )

        val relayUrl = relayDataHandler.formatRelayUrl(ip)
        torManager.setTorRequired(relayUrl.isOnionAddress)

        val inviterData: OnBoardInviterData? = redeemInviteDto?.let { dto ->
            OnBoardInviterData(
                dto.nickname,
                dto.pubkey?.toLightningNodePubKey(),
                dto.route_hint,
                dto.message,
                dto.action,
                dto.pin
            )
        }

        val relayTransportToken = transportToken ?: transportKey?.let { transportKey ->
            relayDataHandler.retrieveRelayTransportToken(
                authToken,
                transportKey
            )
        } ?: null

        var generateTokenResponse: LoadResponse<GenerateTokenResponse, ResponseError> = Response.Error(
            ResponseError("generateToken endpoint failed")
        )

//        if (relayTransportToken != null) {
//            networkQueryContact.generateToken(
//                password,
//                nodePubKey,
//                Triple(Pair(authToken, relayTransportToken), null, relayUrl)
//            ).collect { loadResponse ->
//                generateTokenResponse = loadResponse
//            }
//        } else {
//            networkQueryContact.generateToken(
//                relayUrl,
//                authToken,
//                password,
//                nodePubKey
//            ).collect { loadResponse ->
//                generateTokenResponse = loadResponse
//            }
//        }

//        @Exhaustive
//        when (generateTokenResponse) {
//            is LoadResponse.Loading -> {}
//            is Response.Error -> {
//                if (tokenRetries < 3) {
//                    tokenRetries += 1
//
//                    registerTokenAndStartOnBoard(
//                        ip,
//                        nodePubKey,
//                        password,
//                        redeemInviteDto,
//                        authToken,
//                        transportKey,
//                        relayTransportToken
//                    )
//                } else {
//                    submitSideEffect(OnBoardConnectingSideEffect.GenerateTokenFailed)
//                    navigator.popBackStack()
//                }
//            }
//            is Response.Success -> {
//
//                val hMacKey = createHMacKey(
//                    relayData = Triple(Pair(authToken, relayTransportToken), null, relayUrl),
//                    transportKey = transportKey
//                )
//
//                val step1Message: OnBoardStep.Step1_WelcomeMessage? =
//                    onBoardStepHandler.persistOnBoardStep1Data(
//                        relayUrl,
//                        authToken,
//                        transportKey,
//                        hMacKey,
//                        inviterData
//                    )
//
//                if (step1Message == null) {
//                    submitSideEffect(OnBoardConnectingSideEffect.GenerateTokenFailed)
//                    navigator.popBackStack()
//                } else {
//                    navigator.toOnBoardMessageScreen(step1Message)
//                }
//            }
//        }
    }

    private suspend fun goToConnectedScreen(
        ownerPrivateKey: RsaPrivateKey,
        transportKey: RsaPublicKey?
    ) {
        navigator.toOnBoardConnectedScreen()
    }

    override suspend fun onMotionSceneCompletion(value: Any) {
        return
    }

    private fun continuePhoneSignerSetup() {
        viewModelScope.launch(mainImmediate) {
            signerManager.checkHasAdmin(this@OnBoardConnectingViewModel)
        }
    }

    override fun checkAdminSucceeded() {
        viewModelScope.launch(mainImmediate) {
            signerManager.getPublicKeyAndRelayUrl()?.let { publicKeyAndRelayUrl ->
                publicKeyAndRelayUrl.second.toRelayUrl()?.let {
//                    getTransportKey(
//                        ip = it,
//                        publicKeyAndRelayUrl.first,
//                        null,
//                        null,
//                        token = null
//                    )
                } ?: run {
                    checkAdminFailed()
                }
            } ?: run {
                checkAdminFailed()
            }
        }
    }

    override fun checkAdminFailed() {
        viewModelScope.launch(mainImmediate) {
            signerManager.reset()
            submitSideEffect(OnBoardConnectingSideEffect.CheckAdminFailed)
            navigator.popBackStack()
        }
    }

    private fun collectConnectionState() {
        viewModelScope.launch(mainImmediate) {
            connectManagerRepository.connectionManagerState.collect { connectionState ->
                when (connectionState) {
                    is ConnectionManagerState.MnemonicWords -> {
                        submitSideEffect(OnBoardConnectingSideEffect.ShowMnemonicToUser(connectionState.words) {})
                    }
                    is ConnectionManagerState.OwnerRegistered -> {
                        if (connectionState.isRestoreAccount) {
                            navigator.toDashboardScreen()
                        } else {
                            navigator.toOnBoardNameScreen()
                        }
                    }
                    is ConnectionManagerState.UserState -> {
                        storeUserState(connectionState.userState)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun collectConnectManagerErrorState(){
        viewModelScope.launch(mainImmediate) {
            connectManagerRepository.connectManagerErrorState.collect { connectManagerError ->
                when (connectManagerError) {
                    is ConnectManagerError.GenerateXPubError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_generate_xpub_error))
                        )
                    }
                    is ConnectManagerError.GenerateMnemonicError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_generate_mnemonic_error))
                        )
                    }
                    is ConnectManagerError.ProcessInviteError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_process_invite_error))
                        )
                    }
                    is ConnectManagerError.MqttConnectError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_connect_error))
                        )
                    }
                    is ConnectManagerError.SubscribeOwnerError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_subscribe_owner_error))
                        )
                    }
                    is ConnectManagerError.MqttClientError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_client_error))
                        )
                    }
                    is ConnectManagerError.MqttInitError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_init_error))
                        )
                    }
                    is ConnectManagerError.FetchMessageError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_fetch_message_error))
                        )
                    }
                    is ConnectManagerError.FetchFirstMessageError -> {
                        submitSideEffect(OnBoardConnectingSideEffect.Notify(
                            app.getString(R.string.connect_manager_fetch_first_message_error))
                        )
                    }
                }
            }
        }
    }

}