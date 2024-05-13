package chat.sphinx.dashboard.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.exhaustive.Exhaustive
import chat.sphinx.concept_background_login.BackgroundLoginHandler
import chat.sphinx.concept_network_query_crypter.NetworkQueryCrypter
import chat.sphinx.concept_network_query_people.NetworkQueryPeople
import chat.sphinx.concept_network_query_people.model.isClaimOnLiquidPath
import chat.sphinx.concept_network_query_people.model.isDeleteMethod
import chat.sphinx.concept_network_query_people.model.isProfilePath
import chat.sphinx.concept_network_query_people.model.isSaveMethod
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_repository_actions.ActionsRepository
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_connect_manager.model.ConnectionManagerState
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_dashboard_android.RepositoryDashboardAndroid
import chat.sphinx.concept_repository_feed.FeedRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_service_media.MediaPlayerServiceController
import chat.sphinx.concept_service_notification.PushNotificationRegistrar
import chat.sphinx.concept_signer_manager.SignerManager
import chat.sphinx.concept_signer_manager.SignerPhoneCallback
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.concept_wallet.WalletDataHandler
import chat.sphinx.dashboard.R
import chat.sphinx.dashboard.navigation.DashboardBottomNavBarNavigator
import chat.sphinx.dashboard.navigation.DashboardNavDrawerNavigator
import chat.sphinx.dashboard.navigation.DashboardNavigator
import chat.sphinx.dashboard.ui.viewstates.ChatListFooterButtonsViewState
import chat.sphinx.dashboard.ui.viewstates.DashboardMotionViewState
import chat.sphinx.dashboard.ui.viewstates.DashboardTabsViewState
import chat.sphinx.dashboard.ui.viewstates.DeepLinkPopupViewState
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.menu_bottom.ui.MenuBottomViewState
import chat.sphinx.menu_bottom_scanner.ScannerMenuHandler
import chat.sphinx.menu_bottom_scanner.ScannerMenuViewModel
import chat.sphinx.scanner_view_model_coordinator.request.ScannerFilter
import chat.sphinx.scanner_view_model_coordinator.request.ScannerRequest
import chat.sphinx.scanner_view_model_coordinator.response.ScannerResponse
import chat.sphinx.wrapper_chat.Chat
import chat.sphinx.wrapper_chat.isConversation
import chat.sphinx.wrapper_common.CreateInvoiceLink
import chat.sphinx.wrapper_common.ExternalAuthorizeLink
import chat.sphinx.wrapper_common.ExternalRequestLink
import chat.sphinx.wrapper_common.FeedRecommendationsToggle
import chat.sphinx.wrapper_common.HideBalance
import chat.sphinx.wrapper_common.PeopleConnectLink
import chat.sphinx.wrapper_common.RedeemSatsLink
import chat.sphinx.wrapper_common.StakworkAuthorizeLink
import chat.sphinx.wrapper_common.chat.ChatUUID
import chat.sphinx.wrapper_common.chat.PushNotificationLink
import chat.sphinx.wrapper_common.chat.toPushNotificationLink
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.RestoreProgressViewState
import chat.sphinx.wrapper_common.dashboard.toChatId
import chat.sphinx.wrapper_common.feed.FeedItemLink
import chat.sphinx.wrapper_common.feed.toFeedItemLink
import chat.sphinx.wrapper_common.isValidExternalAuthorizeLink
import chat.sphinx.wrapper_common.isValidExternalRequestLink
import chat.sphinx.wrapper_common.isValidPeopleConnectLink
import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningPaymentRequest
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.isValidLightningNodeLink
import chat.sphinx.wrapper_common.lightning.isValidLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.isValidLightningPaymentRequest
import chat.sphinx.wrapper_common.lightning.isValidVirtualNodeAddress
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningPaymentRequestOrNull
import chat.sphinx.wrapper_common.lightning.toLightningRouteHint
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.message.SphinxCallLink
import chat.sphinx.wrapper_common.message.toSphinxCallLink
import chat.sphinx.wrapper_common.toCreateInvoiceLink
import chat.sphinx.wrapper_common.toExternalAuthorizeLink
import chat.sphinx.wrapper_common.toExternalRequestLink
import chat.sphinx.wrapper_common.toPeopleConnectLink
import chat.sphinx.wrapper_common.toPhotoUrl
import chat.sphinx.wrapper_common.toRedeemSatsLink
import chat.sphinx.wrapper_common.toStakworkAuthorizeLink
import chat.sphinx.wrapper_common.tribe.TribeJoinLink
import chat.sphinx.wrapper_common.tribe.isValidTribeJoinLink
import chat.sphinx.wrapper_common.tribe.toTribeJoinLink
import chat.sphinx.wrapper_contact.Contact
import chat.sphinx.wrapper_contact.ContactAlias
import chat.sphinx.wrapper_contact.avatarUrl
import chat.sphinx.wrapper_contact.toContactAlias
import chat.sphinx.wrapper_contact.toContactKey
import chat.sphinx.wrapper_feed.Feed
import chat.sphinx.wrapper_feed.isNewsletter
import chat.sphinx.wrapper_feed.isPodcast
import chat.sphinx.wrapper_feed.isVideo
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_relay.RelayUrl
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.MotionLayoutViewModel
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.build_config.BuildConfigVersionCode
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jitsi.meet.sdk.JitsiMeetActivity
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions
import org.jitsi.meet.sdk.JitsiMeetUserInfo
import javax.inject.Inject


data class BalanceState(val nodeBalance: NodeBalance?, val hideBalanceState: Int)

@HiltViewModel
internal class DashboardViewModel @Inject constructor(
    private val app: Application,
    private val backgroundLoginHandler: BackgroundLoginHandler,
    handler: SavedStateHandle,

    private val accountOwner: StateFlow<Contact?>,

    val dashboardNavigator: DashboardNavigator,
    val navBarNavigator: DashboardBottomNavBarNavigator,
    val navDrawerNavigator: DashboardNavDrawerNavigator,

    private val buildConfigVersionCode: BuildConfigVersionCode,
    dispatchers: CoroutineDispatchers,

    private val repositoryDashboard: RepositoryDashboardAndroid<Any>,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val feedRepository: FeedRepository,
    private val actionsRepository: ActionsRepository,
    private val lightningRepository: LightningRepository,

    private val networkQueryAuthorizeExternal: NetworkQueryAuthorizeExternal,
    private val networkQueryPeople: NetworkQueryPeople,
    private val pushNotificationRegistrar: PushNotificationRegistrar,
    private val networkQueryCrypter: NetworkQueryCrypter,

    private val walletDataHandler: WalletDataHandler,

    private val mediaPlayerServiceController: MediaPlayerServiceController,

    private val scannerCoordinator: ViewModelCoordinator<ScannerRequest, ScannerResponse>,
    private val moshi: Moshi,
    private val connectManagerRepository: ConnectManagerRepository,

    private val LOG: SphinxLogger,
) : MotionLayoutViewModel<
        Any,
        Context,
        ChatListSideEffect,
        DashboardMotionViewState
        >(dispatchers, DashboardMotionViewState.DrawerCloseNavBarVisible),
    ScannerMenuViewModel,
    SignerPhoneCallback
{

    private val args: DashboardFragmentArgs by handler.navArgs()

    val newVersionAvailable: MutableStateFlow<Boolean> by lazy(LazyThreadSafetyMode.NONE) {
        MutableStateFlow(false)
    }

    val currentVersion: MutableStateFlow<String> by lazy(LazyThreadSafetyMode.NONE) {
        MutableStateFlow("-")
    }

    private val scannedNodeAddress: MutableStateFlow<Pair<LightningNodePubKey, LightningRouteHint?>?> by lazy(LazyThreadSafetyMode.NONE) {
        MutableStateFlow(null)
    }

    companion object {
        const val USER_STATE_SHARED_PREFERENCES = "user_state_settings"
        const val ONION_STATE_KEY = "onion_state"
    }

    private val _hideBalanceStateFlow: MutableStateFlow<Int> by lazy {
        MutableStateFlow(HideBalance.DISABLED)
    }

    val hideBalanceStateFlow: StateFlow<Int>
        get() = _hideBalanceStateFlow.asStateFlow()

    private lateinit var signerManager: SignerManager

    private val userStateSharedPreferences: SharedPreferences =
        app.getSharedPreferences(USER_STATE_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    init {
        if (args.updateBackgroundLoginTime) {
            viewModelScope.launch(default) {
                backgroundLoginHandler.updateLoginTime()
            }
        }
        connectManagerRepository.connectAndSubscribeToMqtt(getUserState())
        setDeviceId()
        collectConnectionState()
        collectConnectManagerErrorState()

        getHideBalanceState()

        syncFeedRecommendationsState()

        handleDeepLink(args.argDeepLink)

        actionsRepository.syncActions()
        feedRepository.restoreContentFeedStatuses()

        networkRefresh(true)
    }

    private fun setDeviceId() {
        viewModelScope.launch(mainImmediate) {
            val register = pushNotificationRegistrar.register()

            when (register) {
                is Response.Error -> {}
                is Response.Success -> {
                    networkStatusStateFlow.collect { networkStatus ->
                        if (networkStatus is NetworkStatus.Connected) {
                            connectManagerRepository.setOwnerDeviceId(register.value.toString())
                            return@collect
                        }
                    }
                }
            }
        }
    }

    private fun collectConnectionState() {
        viewModelScope.launch(mainImmediate) {
            connectManagerRepository.connectionManagerState.collect { connectionState ->
                when (connectionState) {
                    is ConnectionManagerState.UserState -> {
                        storeUserState(connectionState.userState)
                    }
                    is ConnectionManagerState.DeleteUserState -> {
                        deleteUserState(connectionState.userState)
                    }
                    is ConnectionManagerState.NewInviteCode -> {
                        delay(500L)
                        dashboardNavigator.toQRCodeDetail(
                            connectionState.inviteCode,
                            app.getString(R.string.dashboard_invite_code_screen),
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    private fun collectConnectManagerErrorState() {
        viewModelScope.launch(mainImmediate) {
            connectManagerRepository.connectManagerErrorState.collect { connectManagerError ->
                when (connectManagerError) {
                    is ConnectManagerError.GenerateXPubError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_generate_xpub_error))
                        )
                    }
                    is ConnectManagerError.MqttConnectError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_connect_error))
                        )
                    }
                    is ConnectManagerError.MqttClientError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_client_error))
                        )
                    }
                    is ConnectManagerError.MqttInitError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_mqtt_init_error))
                        )
                    }
                    is ConnectManagerError.JoinTribeError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_join_tribe_error))
                        )
                    }
                    is ConnectManagerError.CreateTribeError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_create_tribe_error))
                        )
                    }
                    is ConnectManagerError.CreateInviteError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_create_invite_error))
                        )
                    }
                    is ConnectManagerError.CreateInvoiceError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_create_invoice_error))
                        )
                    }
                    is ConnectManagerError.GetReadMessagesError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_get_read_messages_error))
                        )
                    }
                    is ConnectManagerError.SignBytesError -> {
                        submitSideEffect(ChatListSideEffect.Notify(
                            app.getString(R.string.connect_manager_sign_bytes_error))
                        )
                    }
                }
            }
        }
    }


    private fun storeUserState(state: String) {
        val editor = userStateSharedPreferences.edit()

        editor.putString(ONION_STATE_KEY, state)
        editor.apply()
    }

    private fun getUserState(): String? {
        return userStateSharedPreferences.getString(ONION_STATE_KEY, null)
    }

    private fun deleteUserState(userStates: List<String>) {
        val editor = userStateSharedPreferences.edit()

        for (state in userStates) {
            editor.remove(state)
        }
        editor.apply()
    }

    private fun getHideBalanceState() {
        viewModelScope.launch(mainImmediate) {
            val appContext: Context = app.applicationContext
            val hideSharedPreferences = appContext.getSharedPreferences(HideBalance.HIDE_BALANCE_SHARED_PREFERENCES, Context.MODE_PRIVATE)

            val shouldHide = hideSharedPreferences.getInt(HideBalance.HIDE_BALANCE_ENABLED_KEY, HideBalance.DISABLED)
            _hideBalanceStateFlow.value = shouldHide
        }
    }

    fun toggleHideBalanceState(){
        viewModelScope.launch(mainImmediate) {
            val newState = if(_hideBalanceStateFlow.value == HideBalance.DISABLED){
                HideBalance.ENABLED
            } else {
                HideBalance.DISABLED
            }
            _hideBalanceStateFlow.value = newState

            delay(50L)

            val appContext: Context = app.applicationContext
            val hideSharedPreferences = appContext.getSharedPreferences(HideBalance.HIDE_BALANCE_SHARED_PREFERENCES, Context.MODE_PRIVATE)

            withContext(dispatchers.io) {
                hideSharedPreferences.edit()
                    .putInt(HideBalance.HIDE_BALANCE_ENABLED_KEY, newState)
                    .let { editor ->
                        if (!editor.commit()) {
                            editor.apply()
                        }
                    }
            }
        }
    }

    fun handleDeepLink(deepLink: String?) {
        viewModelScope.launch(mainImmediate) {
            delay(100L)

            deepLink?.toTribeJoinLink()?.let { tribeJoinLink ->
                handleTribeJoinLink(tribeJoinLink)
            } ?: deepLink?.toExternalAuthorizeLink()?.let { externalAuthorizeLink ->
                handleExternalAuthorizeLink(externalAuthorizeLink)
            } ?: deepLink?.toPeopleConnectLink()?.let { peopleConnectLink ->
                handlePeopleConnectLink(peopleConnectLink)
            } ?: deepLink?.toExternalRequestLink()?.let { externalRequestLink ->
                handleExternalRequestLink(externalRequestLink)
            } ?: deepLink?.toStakworkAuthorizeLink()?.let { stakworkAuthorizeLink ->
                handleStakworkAuthorizeLink(stakworkAuthorizeLink)
            } ?: deepLink?.toRedeemSatsLink()?.let { redeemSatsLink ->
                handleRedeemSatsLink(redeemSatsLink)
            } ?: deepLink?.toFeedItemLink()?.let { feedItemLink ->
                handleFeedItemLink(feedItemLink)
            } ?: deepLink?.toPushNotificationLink()?.let { pushNotificationLink ->
                handlePushNotification(pushNotificationLink)
            } ?: deepLink?.toSphinxCallLink()?.let { sphinxCallLink ->
                joinCall(sphinxCallLink, sphinxCallLink.startAudioOnly)
            }
        }
    }

    private fun joinCall(link: SphinxCallLink, audioOnly: Boolean) {
        link.callServerUrl?.let { nnCallUrl ->

            viewModelScope.launch(mainImmediate) {

                val owner = getOwner()

                val userInfo = JitsiMeetUserInfo()
                userInfo.displayName = owner.alias?.value ?: ""

                owner.avatarUrl?.let { nnAvatarUrl ->
                    userInfo.avatar = nnAvatarUrl
                }

                val options = JitsiMeetConferenceOptions.Builder()
                    .setServerURL(nnCallUrl)
                    .setRoom(link.callRoom)
                    .setAudioMuted(false)
                    .setVideoMuted(false)
                    .setFeatureFlag("welcomepage.enabled", false)
                    .setAudioOnly(audioOnly)
                    .setUserInfo(userInfo)
                    .build()

                JitsiMeetActivity.launch(app, options)
            }
        }
    }

    private fun handlePushNotification(pushNotificationLink: PushNotificationLink) {
        viewModelScope.launch(mainImmediate) {
            pushNotificationLink.chatId?.toChatId()?.let { nnChatId ->
                chatRepository.getChatById(nnChatId).firstOrNull()?.let { chat ->
                    if (chat.isConversation()) {
                        chat.contactIds.elementAtOrNull(1)?.let { contactId ->
                            dashboardNavigator.toChatContact(nnChatId, contactId)
                        }
                    } else {
                        dashboardNavigator.toChatTribe(nnChatId)
                    }
                }
            }
        }
    }

    suspend fun getPubKeyByEncryptedChild(child: String): ChatId? {
        return connectManagerRepository.getPubKeyByEncryptedChild(child).firstOrNull()
    }

    private fun syncFeedRecommendationsState() {
        val appContext: Context = app.applicationContext
        val sharedPreferences = appContext.getSharedPreferences(FeedRecommendationsToggle.FEED_RECOMMENDATIONS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

        val feedRecommendationsToggle = sharedPreferences.getBoolean(
            FeedRecommendationsToggle.FEED_RECOMMENDATIONS_ENABLED_KEY, false
        )
        feedRepository.setRecommendationsToggle(feedRecommendationsToggle)
    }

    fun setSignerManager(signerManager: SignerManager) {
        signerManager.setWalletDataHandler(walletDataHandler)
        signerManager.setMoshi(moshi)

        this.signerManager = signerManager

        reconnectMQTT()
    }

    private fun reconnectMQTT(){
        signerManager.reconnectMQTT(this)
    }

    override fun showMnemonicToUser(message: String, callback: (Boolean) -> Unit) {
        return
    }

    override fun phoneSignerSuccessfullySet() {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(
                ChatListSideEffect.Notify(
                    app.getString(R.string.signer_phone_connected_to_mqtt)
                )
            )

        }
    }

    override fun phoneSignerSetupError() {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(
                ChatListSideEffect.Notify(
                    app.getString(R.string.signer_phone_error_mqtt)
                )
            )
        }
    }

    fun toScanner(isPayment: Boolean) {
        viewModelScope.launch(mainImmediate) {
            val response = scannerCoordinator.submitRequest(
                ScannerRequest(
                    filter = object : ScannerFilter() {
                        override suspend fun checkData(data: String): Response<Any, String> {
                            return when {
                                data.isValidTribeJoinLink ||
                                data.isValidExternalAuthorizeLink ||
                                data.isValidPeopleConnectLink ||
                                data.isValidLightningPaymentRequest ||
                                data.isValidLightningNodePubKey ||
                                data.isValidVirtualNodeAddress ||
                                data.isValidLightningNodeLink ||
                                data.isValidExternalRequestLink ->
                                {
                                    Response.Success(Any())
                                }
                                else -> {
                                    Response.Error(app.getString(R.string.not_valid_invoice_or_tribe_link))
                                }
                            }
                        }
                    },
                    showBottomView = true,
                    scannerModeLabel = if (isPayment) app.getString(R.string.paste_invoice_or_public_key) else " "
                )
            )

            if (response is Response.Success) {

                val code = response.value.value

                code.toTribeJoinLink()?.let { tribeJoinLink ->

                    handleTribeJoinLink(tribeJoinLink)

                } ?: code.toExternalAuthorizeLink()?.let { externalAuthorizeLink ->

                    handleExternalAuthorizeLink(externalAuthorizeLink)

                } ?: code.toExternalRequestLink()?.let { externalRequestLink ->

                    handleExternalRequestLink(externalRequestLink)

                } ?: code.toPeopleConnectLink()?.let { peopleConnectLink ->

                    handlePeopleConnectLink(peopleConnectLink)

                } ?: run {
                    val contactInfo = code.split("_")
                    val pubKey = contactInfo.getOrNull(0)?.toLightningNodePubKey()
                    val contactRouteHint = "${contactInfo.getOrNull(1)}_${contactInfo.getOrNull(2)}".toLightningRouteHint()

                    if (pubKey != null) {
                        handleContactLink(pubKey, contactRouteHint)
                    } else {
                        null
                    }
                } ?: code.toLightningPaymentRequestOrNull()?.let { lightningPaymentRequest ->
                    try {
                        val bolt11 = Bolt11.decode(lightningPaymentRequest)
                        val amount = bolt11.getSatsAmount()

                        if (amount != null) {
                            submitSideEffect(
                                ChatListSideEffect.AlertConfirmPayLightningPaymentRequest(
                                    amount.value,
                                    bolt11.getMemo()
                                ) {
                                    payLightningPaymentRequest(lightningPaymentRequest)
                                }
                            )
                        } else {
                            submitSideEffect(
                                ChatListSideEffect.Notify(
                                    app.getString(R.string.payment_request_missing_amount),
                                    true
                                )
                            )
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    override val scannerMenuHandler: ScannerMenuHandler by lazy {
        ScannerMenuHandler()
    }

    override fun createContact() {
        viewModelScope.launch(default) {
            scannedNodeAddress.value?.let { address ->
                dashboardNavigator.toAddContactDetail(address.first, address.second)
            }
            scannerMenuDismiss()
        }
    }

    override fun sendDirectPayment() {
        viewModelScope.launch(default) {
            scannedNodeAddress.value?.let { address ->
                navBarNavigator.toPaymentSendDetail(address.first, address.second, null)
            }
            scannerMenuDismiss()
        }
    }

    override fun scannerMenuDismiss() {
        viewModelScope.launch(default) {
            scannerMenuHandler.viewStateContainer.updateViewState(MenuBottomViewState.Closed)
            scannedNodeAddress.value = null
        }
    }

    private suspend fun handleTribeJoinLink(tribeJoinLink: TribeJoinLink) {
        val chat: Chat? = try {
            chatRepository.getChatByUUID(
                ChatUUID(tribeJoinLink.tribePubkey)
            ).firstOrNull()
        } catch (e: IllegalArgumentException) {
            null
        }

        if (chat != null) {
            dashboardNavigator.toChatTribe(chat.id)
        } else {
            dashboardNavigator.toJoinTribeDetail(tribeJoinLink)
        }
    }

    private suspend fun handleContactLink(pubKey: LightningNodePubKey, routeHint: LightningRouteHint?) {
        scannedNodeAddress.value = Pair(pubKey, routeHint)

        contactRepository.getContactByPubKey(pubKey).firstOrNull()?.let { _ ->
            navBarNavigator.toPaymentSendDetail(pubKey, routeHint, null)
        } ?: scannerMenuHandler.viewStateContainer.updateViewState(MenuBottomViewState.Open)
    }

    private suspend fun goToContactChat(contact: Contact) {
        chatRepository.getConversationByContactId(contact.id).firstOrNull()?.let { chat ->

            dashboardNavigator.toChatContact(chat.id, contact.id)

        } ?: dashboardNavigator.toChatContact(null, contact.id)
    }

    private fun handleExternalAuthorizeLink(link: ExternalAuthorizeLink) {
        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.ExternalAuthorizePopup(link)
        )
    }

    private fun handleStakworkAuthorizeLink(link: StakworkAuthorizeLink) {
        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.StakworkAuthorizePopup(link)
        )
    }

    private fun handleRedeemSatsLink(link: RedeemSatsLink) {
        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.RedeemSatsPopup(link)
        )
    }

    private suspend fun handleExternalRequestLink(link: ExternalRequestLink) {
        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.LoadingExternalRequestPopup
        )

        networkQueryPeople.getExternalRequestByKey(
            link.host,
            link.key
        ).collect { loadResponse ->

            when(loadResponse){
                is LoadResponse.Loading -> {}

                is Response.Error -> {
                    deepLinkPopupViewStateContainer.updateViewState(
                        DeepLinkPopupViewState.PopupDismissed
                    )

                    submitSideEffect(
                        ChatListSideEffect.Notify(
                            app.getString(R.string.dashboard_save_profile_generic_error)
                        )
                    )
                }

                is Response.Success -> {
                    if (loadResponse.value.isProfilePath()) {
                        if (loadResponse.value.isDeleteMethod()) {
                            deepLinkPopupViewStateContainer.updateViewState(
                                DeepLinkPopupViewState.DeletePeopleProfilePopup(
                                    link.host,
                                    loadResponse.value.body
                                )
                            )
                        } else if (loadResponse.value.isSaveMethod()) {
                            deepLinkPopupViewStateContainer.updateViewState(
                                DeepLinkPopupViewState.SaveProfilePopup(
                                    link.host,
                                    loadResponse.value.body,
                                )
                            )
                        }
                    } else if (loadResponse.value.isClaimOnLiquidPath()){
                        if(loadResponse.value.isSaveMethod()){
                            deepLinkPopupViewStateContainer.updateViewState(
                                DeepLinkPopupViewState.RedeemTokensPopup(
                                    link.host,
                                    loadResponse.value.body,
                                )
                            )
                        }
                    }
                }
            }

        }
    }

    private suspend fun handlePeopleConnectLink(link: PeopleConnectLink) {
        link.publicKey.toLightningNodePubKey()?.let { lightningNodePubKey ->
            contactRepository.getContactByPubKey(lightningNodePubKey).firstOrNull()?.let { contact ->

                goToContactChat(contact)

            } ?: loadPeopleConnectPopup(link)
        }
    }

    private suspend fun handleFeedItemLink(link: FeedItemLink) {
        feedRepository.getFeedForLink(link).firstOrNull()?.let { feed ->
            goToFeedDetailView(feed)
        }
    }

    private suspend fun goToFeedDetailView(feed: Feed) {
        when {
            feed.isPodcast -> {
                dashboardNavigator.toPodcastPlayerScreen(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.id,
                    feed.feedUrl
                )
            }
            feed.isVideo -> {
                dashboardNavigator.toVideoWatchScreen(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.id,
                    feed.feedUrl
                )
            }
            feed.isNewsletter -> {
                dashboardNavigator.toNewsletterDetail(
                    feed.chat?.id ?: ChatId(ChatId.NULL_CHAT_ID.toLong()),
                    feed.feedUrl
                )
            }
        }
    }

    private suspend fun loadPeopleConnectPopup(link: PeopleConnectLink) {
        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.PeopleConnectPopupLoadingPersonInfo
        )

        networkQueryAuthorizeExternal.getPersonInfo(link.host, link.publicKey).collect { loadResponse ->
            @Exhaustive
            when (loadResponse) {
                is LoadResponse.Loading -> {}

                is Response.Error -> {

                    deepLinkPopupViewStateContainer.updateViewState(
                        DeepLinkPopupViewState.PopupDismissed
                    )

                    submitSideEffect(
                        ChatListSideEffect.Notify(
                            app.getString(R.string.dashboard_connect_retrieve_person_data_error)
                        )
                    )

                }
                is Response.Success -> {
                    deepLinkPopupViewStateContainer.updateViewState(
                        DeepLinkPopupViewState.PeopleConnectPopup(
                            loadResponse.value.owner_alias,
                            loadResponse.value.description ?: app.getString(R.string.dashboard_connect_description_missing),
                            loadResponse.value.price_to_meet ?: 0,
                            loadResponse.value.img,
                            loadResponse.value
                        )
                    )
                }
            }
        }
    }

    fun connectToContact(message: String?) {
        val viewState = deepLinkPopupViewStateContainer.viewStateFlow.value

        viewModelScope.launch(mainImmediate) {

            if (message.isNullOrEmpty()) {
                submitSideEffect(
                    ChatListSideEffect.Notify(
                        app.getString(R.string.dashboard_connect_message_empty)
                    )
                )

                return@launch
            }

            deepLinkPopupViewStateContainer.updateViewState(
                DeepLinkPopupViewState.PeopleConnectPopupProcessing
            )

            var errorMessage = app.getString(R.string.dashboard_connect_generic_error)

            if (viewState is DeepLinkPopupViewState.PeopleConnectPopup) {
                val alias = viewState.personInfoDto.owner_alias.toContactAlias() ?: ContactAlias(app.getString(R.string.unknown))
                val priceToMeet = viewState.personInfoDto.price_to_meet?.toSat() ?: Sat(0)
                val routeHint = viewState.personInfoDto.owner_route_hint?.toLightningRouteHint()
                val photoUrl = viewState.personInfoDto.img?.toPhotoUrl()

                viewState.personInfoDto.owner_pubkey.toLightningNodePubKey()?.let { pubKey ->
                    viewState.personInfoDto.owner_contact_key.toContactKey()?.let { contactKey ->
                        val response = contactRepository.connectToContact(
                            alias,
                            pubKey,
                            routeHint,
                            contactKey,
                            message,
                            photoUrl,
                            priceToMeet
                        )

                        when (response) {
                            is Response.Error -> {
                                errorMessage = response.cause.message
                            }
                            is Response.Success -> {
                                response.value?.let { contactId ->
                                    dashboardNavigator.toChatContact(null, contactId)
                                }

                                deepLinkPopupViewStateContainer.updateViewState(
                                    DeepLinkPopupViewState.PopupDismissed
                                )

                                return@launch
                            }
                        }
                    }
                }
            }

            submitSideEffect(
                ChatListSideEffect.Notify(errorMessage)
            )

            deepLinkPopupViewStateContainer.updateViewState(
                DeepLinkPopupViewState.PopupDismissed
            )
        }
    }

    fun authorizeExternal() {
        val viewState = deepLinkPopupViewStateContainer.viewStateFlow.value

        viewModelScope.launch(mainImmediate) {

            if (viewState is DeepLinkPopupViewState.ExternalAuthorizePopup) {

                deepLinkPopupViewStateContainer.updateViewState(
                    DeepLinkPopupViewState.ExternalAuthorizePopupProcessing
                )


//                when (response) {
//                    is Response.Error -> {
//                        submitSideEffect(
//                            ChatListSideEffect.Notify(response.cause.message)
//                        )
//                    }
//                    is Response.Success -> {
//                        val i = Intent(Intent.ACTION_VIEW)
//                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                        i.data = Uri.parse(
//                            "https://${viewState.link.host}?challenge=${viewState.link.challenge}"
//                        )
//                        app.startActivity(i)
//                    }
//                }
            } else if (viewState is DeepLinkPopupViewState.StakworkAuthorizePopup) {
                deepLinkPopupViewStateContainer.updateViewState(
                    DeepLinkPopupViewState.ExternalAuthorizePopupProcessing
                )

                val response = repositoryDashboard.authorizeStakwork(
                    viewState.link.host,
                    viewState.link.id,
                    viewState.link.challenge
                )

                when (response) {
                    is Response.Error -> {
                        submitSideEffect(
                            ChatListSideEffect.Notify(response.cause.message)
                        )
                    }
                    is Response.Success -> {
                        val i = Intent(Intent.ACTION_VIEW)
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        i.data = Uri.parse(response.value)
                        app.startActivity(i)
                    }
                }
            } else {
                submitSideEffect(
                    ChatListSideEffect.Notify(
                        app.getString(R.string.dashboard_authorize_generic_error)
                    )
                )
            }

            deepLinkPopupViewStateContainer.updateViewState(
                DeepLinkPopupViewState.PopupDismissed
            )
        }
    }

    fun redeemSats() {
        val viewState = deepLinkPopupViewStateContainer.viewStateFlow.value

        viewModelScope.launch(mainImmediate) {

            if (viewState is DeepLinkPopupViewState.RedeemSatsPopup) {
                deepLinkPopupViewStateContainer.updateViewState(
                    DeepLinkPopupViewState.RedeemSatsPopupProcessing
                )

                val response = repositoryDashboard.redeemSats(
                    viewState.link.host,
                    viewState.link.token,

                )

                when (response) {
                    is Response.Error -> {
                        submitSideEffect(
                            ChatListSideEffect.Notify(response.cause.message)
                        )
                    }
                    is Response.Success -> {
//                        networkRefresh(false)
                    }
                }
            }

            deepLinkPopupViewStateContainer.updateViewState(
                DeepLinkPopupViewState.PopupDismissed
            )
        }
    }

    suspend fun updatePeopleProfile() {
        val viewState = deepLinkPopupViewStateContainer.viewStateFlow.value

        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.ExternalRequestPopupProcessing
        )

        when (viewState) {
            is DeepLinkPopupViewState.RedeemTokensPopup -> {}
            is DeepLinkPopupViewState.SaveProfilePopup -> {
                savePeopleProfile(viewState.body)
            }
            is DeepLinkPopupViewState.DeletePeopleProfilePopup -> {
                deletePeopleProfile(viewState.body)
            }
            else -> {}
        }
    }

    private suspend fun deletePeopleProfile(body: String){
        viewModelScope.launch(mainImmediate) {
            when (repositoryDashboard.deletePeopleProfile(body)) {
                is Response.Error -> {
                    submitSideEffect(
                        ChatListSideEffect.Notify(
                            app.getString(R.string.dashboard_delete_profile_generic_error)
                        )
                    )
                }
                is Response.Success -> {
                    submitSideEffect(
                        ChatListSideEffect.Notify(
                            app.getString(R.string.dashboard_delete_profile_success)
                        )
                    )
                }
            }
        }.join()

        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.PopupDismissed
        )
    }

    private suspend fun savePeopleProfile(body: String) {
//        viewModelScope.launch(mainImmediate) {
//            val response = repositoryDashboard.savePeopleProfile(
//                body
//            )
//
//            when (response) {
//                is Response.Error -> {
//                    submitSideEffect(
//                        ChatListSideEffect.Notify(
//                            app.getString(R.string.dashboard_save_profile_generic_error)
//                        )
//                    )
//                }
//                is Response.Success -> {
//                    submitSideEffect(
//                        ChatListSideEffect.Notify(
//                            app.getString(R.string.dashboard_save_profile_success)
//                        )
//                    )
//                }
//            }
//        }.join()

        deepLinkPopupViewStateContainer.updateViewState(
            DeepLinkPopupViewState.PopupDismissed
        )
    }

    val deepLinkPopupViewStateContainer: ViewStateContainer<DeepLinkPopupViewState> by lazy {
        ViewStateContainer(DeepLinkPopupViewState.PopupDismissed)
    }

    val chatListFooterButtonsViewStateContainer: ViewStateContainer<ChatListFooterButtonsViewState> by lazy {
        ViewStateContainer(ChatListFooterButtonsViewState.Idle)
    }

    val tabsViewStateContainer: ViewStateContainer<DashboardTabsViewState> by lazy {
        ViewStateContainer(DashboardTabsViewState.Idle)
    }

    private val _accountOwnerStateFlow: MutableStateFlow<Contact?> by lazy {
        MutableStateFlow(null)
    }

    val accountOwnerStateFlow: StateFlow<Contact?>
        get() = _accountOwnerStateFlow.asStateFlow()

    suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        repositoryDashboard.getAccountBalance()

    private var messagesCountJob: Job? = null
    fun screenInit() {
        messagesCountJob?.cancel()
        messagesCountJob = viewModelScope.launch(mainImmediate) {
            repositoryDashboard.getUnseenActiveConversationMessagesCount()
                .collect { unseenConversationMessagesCount ->
                    updateTabsState(
                        friendsBadgeVisible = (unseenConversationMessagesCount ?: 0) > 0
                    )
                }
        }
    }

    init {
        viewModelScope.launch(mainImmediate) {
            repositoryDashboard.getUnseenTribeMessagesCount()
                .collect { unseenTribeMessagesCount ->
                    updateTabsState(
                        tribesBadgeVisible = (unseenTribeMessagesCount ?: 0) > 0
                    )
                }
        }

        viewModelScope.launch(mainImmediate) {

            chatListFooterButtonsViewStateContainer.updateViewState(
                ChatListFooterButtonsViewState.ButtonsVisibility(
                    addFriendVisible = true,
                    createTribeVisible = true,
                    discoverTribesVisible = false
                )
            )
        }
    }

    fun initOwner() {
        viewModelScope.launch(mainImmediate) {
            getOwner()
        }
    }

    fun updateTabsState(
        feedActive: Boolean? = null,
        friendsActive: Boolean? = null,
        tribesActive: Boolean? = null,
        friendsBadgeVisible: Boolean? = null,
        tribesBadgeVisible: Boolean? = null
    ) {
        val currentState = tabsViewStateContainer.viewStateFlow.value

        tabsViewStateContainer.updateViewState(
            if (currentState is DashboardTabsViewState.TabsState) {
                DashboardTabsViewState.TabsState(
                    feedActive = feedActive ?: currentState.feedActive,
                    friendsActive = friendsActive ?: currentState.friendsActive,
                    tribesActive = tribesActive ?: currentState.tribesActive,
                    friendsBadgeVisible = friendsBadgeVisible ?: currentState.friendsBadgeVisible,
                    tribesBadgeVisible = tribesBadgeVisible ?: currentState.tribesBadgeVisible
                )
            } else {
                DashboardTabsViewState.TabsState(
                    feedActive = feedActive ?: false,
                    friendsActive = friendsActive ?: true,
                    tribesActive = tribesActive ?: false,
                    friendsBadgeVisible = friendsBadgeVisible ?: false,
                    tribesBadgeVisible = tribesBadgeVisible ?: false
                )
            }
        )
    }

    fun getCurrentPagePosition() : Int {
        val currentState = tabsViewStateContainer.viewStateFlow.value
        if (currentState is DashboardTabsViewState.TabsState) {
            return when {
                currentState.feedActive -> {
                    DashboardFragmentsAdapter.FEED_TAB_POSITION
                }
                currentState.friendsActive -> {
                    DashboardFragmentsAdapter.FRIENDS_TAB_POSITION
                }
                currentState.tribesActive -> {
                    DashboardFragmentsAdapter.TRIBES_TAB_POSITION
                }
                else -> DashboardFragmentsAdapter.FRIENDS_TAB_POSITION
            }
        }
        return DashboardFragmentsAdapter.FIRST_INIT
    }

    private suspend fun getOwner(): Contact {
        val owner = accountOwner.value.let { contact ->
            if (contact != null) {
                contact
            } else {
                var resolvedOwner: Contact? = null
                try {
                    accountOwner.collect { ownerContact ->
                        if (ownerContact != null) {
                            resolvedOwner = ownerContact
                            throw Exception()
                        }
                    }
                } catch (e: Exception) {
                }
                delay(25L)

                resolvedOwner!!
            }
        }
        _accountOwnerStateFlow.value = owner

        return owner
    }

    private fun payLightningPaymentRequest(lightningPaymentRequest: LightningPaymentRequest) {
        viewModelScope.launch(mainImmediate) {}
    }

    private val _restoreProgressStateFlow: MutableStateFlow<RestoreProgressViewState?> by lazy {
        MutableStateFlow(null)
    }

//    val networkStateFlow: StateFlow<Pair<LoadResponse<Boolean, ResponseError>, Boolean>>
//        get() = _networkStateFlow.asStateFlow()

    val networkStatusStateFlow: StateFlow<NetworkStatus>
        get() = connectManagerRepository.networkStatus.asStateFlow()

    val restoreProgressStateFlow: StateFlow<RestoreProgressViewState?>
        get() = _restoreProgressStateFlow.asStateFlow()

    private var jobNetworkRefresh: Job? = null
    private var jobPushNotificationRegistration: Job? = null

    fun networkRefresh(
        screenStart: Boolean
    ) {
        if (jobNetworkRefresh?.isActive == true) {
            return
        }


//        jobNetworkRefresh = viewModelScope.launch(mainImmediate) {
//            repositoryDashboard.networkRefreshBalance.collect { response ->
//                @Exhaustive
//                when (response) {
//                    is LoadResponse.Loading,
//                    is Response.Error -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                    is Response.Success -> {}
//                }
//            }
//
//            if (_networkStateFlow.value.first is Response.Error) {
//                jobNetworkRefresh?.cancel()
//            }
//
//            repositoryDashboard.networkRefreshLatestContacts.collect { response ->
//                @Exhaustive
//                when (response) {
//                    is LoadResponse.Loading -> {}
//                    is Response.Error -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                    is Response.Success -> {
//                        val restoreProgress = response.value
//
//                        if (restoreProgress.restoring) {
//
//                            _restoreProgressStateFlow.value = RestoreProgressViewState(
//                                response.value.progress,
//                                R.string.dashboard_restore_progress_contacts,
//                                false
//                            )
//                        }
//                    }
//                }
//            }
//
//            repositoryDashboard.networkRefreshFeedContent.collect { response ->
//                @Exhaustive
//                when (response) {
//                    is Response.Success -> {
//                        val restoreProgress = response.value
//
//                        if (restoreProgress.restoring && restoreProgress.progress < 100) {
//                            _restoreProgressStateFlow.value = RestoreProgressViewState(
//                                response.value.progress,
//                                R.string.dashboard_restore_progress_feeds,
//                                false
//                            )
//                        } else {
//                            _restoreProgressStateFlow.value = null
//
//                            _networkStateFlow.value = Pair(Response.Success(true), screenStart)
//                        }
//                    }
//                    is Response.Error -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                    is LoadResponse.Loading -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                }
//            }
//
//
//            if (_networkStateFlow.value.first is Response.Error) {
//                jobNetworkRefresh?.cancel()
//            }
//
//            // must occur after contacts have been retrieved such that
//            // an account owner is available, otherwise it just suspends
//            // until it is.
//            if (jobPushNotificationRegistration == null) {
//                jobPushNotificationRegistration = launch(mainImmediate) {
//                    pushNotificationRegistrar
//                    pushNotificationRegistrar.register().let { response ->
//                        @Exhaustive
//                        when (response) {
//                            is Response.Error -> {
//                                // TODO: Handle on the UI
//                            }
//                            is Response.Success -> {}
//                        }
//                    }
//                }
//            }
//
//            repositoryDashboard.networkRefreshMessages.collect { response ->
//                @Exhaustive
//                when (response) {
//                    is Response.Success -> {
//                        val restoreProgress = response.value
//
//                        if (restoreProgress.restoring && restoreProgress.progress < 100) {
//                            _restoreProgressStateFlow.value = RestoreProgressViewState(
//                                response.value.progress,
//                                R.string.dashboard_restore_progress_messages,
//                                true
//                            )
//                        } else {
//                            _restoreProgressStateFlow.value = null
//
//                            _networkStateFlow.value = Pair(Response.Success(true), screenStart)
//                        }
//                    }
//                    is Response.Error -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                    is LoadResponse.Loading -> {
//                        _networkStateFlow.value = Pair(response, screenStart)
//                    }
//                }
//            }
//        }
    }

    fun cancelRestore() {
        jobNetworkRefresh?.cancel()

        viewModelScope.launch(mainImmediate) {

//            _networkStateFlow.value = Pair(Response.Success(true), true)
            _restoreProgressStateFlow.value = null

            repositoryDashboard.didCancelRestore()
        }
    }

    fun goToAppUpgrade() {
        val i = Intent(Intent.ACTION_VIEW)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        i.data = Uri.parse("https://github.com/stakwork/sphinx-kotlin/releases")
        app.startActivity(i)
    }

    override suspend fun onMotionSceneCompletion(value: Any) {
        // Unused
    }

    fun toastIfNetworkConnected(){
        viewModelScope.launch(mainImmediate){
            submitSideEffect(
                ChatListSideEffect.Notify(
                    app.getString(
                        if (networkStatusStateFlow.value is NetworkStatus.Connected) {
                            R.string.dashboard_network_connected_mqtt_toast
                        } else {
                            R.string.dashboard_network_disconnected_mqtt_toast
                        }
                    )
                )
            )
        }
    }

    fun sendAppLog(appLog: String) {
        actionsRepository.setAppLog(appLog)
    }
}

