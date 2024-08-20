package chat.sphinx.chat_tribe.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.viewModelScope
import chat.sphinx.chat_tribe.model.*
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_AUTHORIZE
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_GET_BUDGET
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_KEYSEND
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_GET_LSAT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_GET_PERSON_DATA
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_LSAT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_PAYMENT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SET_BUDGET
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SIGN
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_UPDATE_LSAT
import chat.sphinx.chat_tribe.ui.viewstate.WebViewLayoutScreenViewState
import chat.sphinx.chat_tribe.ui.viewstate.TribeFeedViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebAppViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebViewViewState
import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.example.wrapper_mqtt.InvoiceBolt11.Companion.toInvoiceBolt11
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.getLspPubKey
import chat.sphinx.wrapper_common.lightning.toLightningNodePubKey
import chat.sphinx.wrapper_common.lightning.toLightningPaymentRequestOrNull
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.lsat.Lsat
import chat.sphinx.wrapper_common.lsat.LsatStatus
import chat.sphinx.wrapper_common.lsat.toLsatIdentifier
import chat.sphinx.wrapper_common.lsat.toLsatIssuer
import chat.sphinx.wrapper_common.lsat.toLsatPreImage
import chat.sphinx.wrapper_common.lsat.toMacaroon
import chat.sphinx.wrapper_common.toDateTime
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_viewmodel.BaseViewModel
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.concept_views.viewstate.ViewStateContainer
import io.matthewnelson.concept_views.viewstate.value
import io.matthewnelson.crypto_common.annotations.RawPasswordAccess
import io.matthewnelson.crypto_common.clazzes.PasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class TribeAppViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    private val app: Application,
    private val contactRepository: ContactRepository,
    private val chatRepository: ChatRepository,
    private val moshi: Moshi,
    private val networkQueryAuthorizeExternal: NetworkQueryAuthorizeExternal,
    private val networkQueryContact: NetworkQueryContact,
    private val connectManagerRepository: ConnectManagerRepository
    ) : BaseViewModel<TribeFeedViewState>(dispatchers, TribeFeedViewState.Idle) {

    companion object {
        const val SERVER_SETTINGS_SHARED_PREFERENCES = "server_ip_settings"
        const val ROUTER_URL = "router_url"
        const val ROUTER_PUBKEY = "router_pubkey"
    }

    private val _sphinxWebViewDtoStateFlow: MutableStateFlow<SphinxWebViewDto?> by lazy {
        MutableStateFlow(null)
    }

    private val sphinxWebViewDtoStateFlow: StateFlow<SphinxWebViewDto?>
        get() = _sphinxWebViewDtoStateFlow.asStateFlow()

    val webViewViewStateContainer: ViewStateContainer<WebViewViewState> by lazy {
        ViewStateContainer(WebViewViewState.Idle)
    }

    val webViewLayoutScreenViewStateContainer: ViewStateContainer<WebViewLayoutScreenViewState> by lazy {
        ViewStateContainer(WebViewLayoutScreenViewState.Closed)
    }

    val webAppViewStateContainer: ViewStateContainer<WebAppViewState> by lazy {
        ViewStateContainer(WebAppViewState.NoApp)
    }

    private val _budgetStateFlow: MutableStateFlow<Sat> by lazy {
        MutableStateFlow(Sat(0))
    }

    val budgetStateFlow: StateFlow<Sat>
        get() = _budgetStateFlow.asStateFlow()

    private var password: String? = null

    private val serverSettingsSharedPreferences: SharedPreferences =
        app.getSharedPreferences(SERVER_SETTINGS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    init {
        handleWebAppJson()
    }

    fun init(url: TribeFeedData.Result) {
        (url as? TribeFeedData.Result.FeedData)?.appUrl?.let { url ->
            webAppViewStateContainer.updateViewState(
                WebAppViewState.AppAvailable.WebViewClosed(url)
            )
        }
    }

    fun toggleWebAppView() {
        when (webAppViewStateContainer.value) {
            is WebAppViewState.AppAvailable.WebViewClosed -> {
                (webAppViewStateContainer.value as? WebAppViewState.AppAvailable.WebViewClosed)?.let {
                    webAppViewStateContainer.updateViewState(
                        WebAppViewState.AppAvailable.WebViewOpen.Loading(it.appUrl)
                    )
                    webViewLayoutScreenViewStateContainer.updateViewState(
                        WebViewLayoutScreenViewState.Open
                    )
                }
            }

            is WebAppViewState.AppAvailable.WebViewOpen -> {
                (webAppViewStateContainer.value as? WebAppViewState.AppAvailable.WebViewOpen)?.let {
                    webAppViewStateContainer.updateViewState(
                        WebAppViewState.AppAvailable.WebViewClosed(it.appUrl)
                    )
                    webViewLayoutScreenViewStateContainer.updateViewState(
                        WebViewLayoutScreenViewState.Closed
                    )
                }
            }

            is WebAppViewState.NoApp -> {}
        }
    }

    fun didFinishLoadingWebView() {
        (webAppViewStateContainer.value as? WebAppViewState.AppAvailable.WebViewOpen.Loading)?.let {
            webAppViewStateContainer.updateViewState(
                WebAppViewState.AppAvailable.WebViewOpen.Loaded(it.appUrl)
            )
        }
    }

    private fun handleWebAppJson() {
        viewModelScope.launch(mainImmediate) {
            sphinxWebViewDtoStateFlow.collect { dto ->
                Log.d("SphinxWebView", "Collecting DTO: $dto")
                when (dto?.type) {
                    TYPE_AUTHORIZE -> {
                        webViewViewStateContainer.updateViewState(WebViewViewState.RequestAuthorization)
                    }

                    TYPE_GET_LSAT -> {
                        processGetLsat()
                    }

                    TYPE_SET_BUDGET -> {
                        webViewViewStateContainer.updateViewState(WebViewViewState.SetBudget)
                    }

                    TYPE_SIGN -> {
                        processSign()
                    }

                    TYPE_LSAT -> {
                        processLsat()
                    }

                    TYPE_KEYSEND -> {
                        sendKeySend()
                    }

                    TYPE_PAYMENT -> {
                        processPayment()
                    }

                    TYPE_UPDATE_LSAT -> {
                        processUpdateLsat()
                    }

                    TYPE_GET_PERSON_DATA -> {
                        processGetPersonData()
                    }
                    TYPE_GET_BUDGET -> {
                        processGetBudget()
                    }
                    else -> {}
                }
            }
        }
    }

    fun hideAuthorizePopup() {
        webViewViewStateContainer.updateViewState(WebViewViewState.Idle)
    }

//

    fun processAuthorize() {
        hideAuthorizePopup()
        contactRepository.accountOwner.value?.nodePubKey?.let { pubKey ->
            val webViewDto = sphinxWebViewDtoStateFlow.value

            val type = webViewDto?.type
            val application = webViewDto?.application
            password = generatePassword()

            if (type == null || application == null) {
                // handle error
                return
            }

            val sendAuthorization = SendAuthorization(
                type = type,
                application = application,
                password = password ?: "",
                pubkey = pubKey.value
            ).toJson(moshi)

            sendAuthorization(sendAuthorization)

        }
    }

    private fun sendAuthorization(authorization: String) {
        webViewViewStateContainer.updateViewState(
            WebViewViewState.SendAuthorization("window.sphinxMessage('$authorization')")
        )
    }

    private suspend fun processGetLsat() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val issuer = webViewDto?.issuer?.toLsatIssuer()

        val lastLsat = if (issuer != null) {
            chatRepository.getLastLsatByIssuer(issuer).firstOrNull()
        } else {
            chatRepository.getLastLsatActive().firstOrNull()
        }

        val sendGetLsat = if (lastLsat != null) {
            SendLsat(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                macaroon = lastLsat.macaroon.value,
                paymentRequest = lastLsat.paymentRequest?.value,
                preimage = lastLsat.preimage?.value,
                identifier = lastLsat.id.value,
                paths = lastLsat.paths?.value,
                status = lastLsat.status.value.toString(),
                success = true,
                budget = null,
                lsat = null

            ).toJson(moshi)
        } else {
            SendLsat(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                macaroon = null,
                paymentRequest = null,
                preimage = null,
                identifier = null,
                paths = null,
                status = null,
                success = false,
                budget = null,
                lsat = null
            ).toJson(moshi)
        }

        sendWebAppMessage(sendGetLsat)
    }

    fun processSetBudget(amount: Long) {
        hideAuthorizePopup()
        _budgetStateFlow.value = amount.toSat() ?: Sat(0)

        val webViewDto = sphinxWebViewDtoStateFlow.value
        val challenge = webViewDto?.challenge
        val signature = challenge?.let { connectManagerRepository.signChallenge(it) }

        val sendBudget = SendBudget(
            type = webViewDto?.type ?: "",
            application = webViewDto?.application ?: "",
            password = password ?: "",
            pubkey = contactRepository.accountOwner.value?.nodePubKey?.value ?: "",
            signature = signature ?: "",
            budget = amount
        ).toJson(moshi)

        sendWebAppMessage(sendBudget)
    }

    private fun processSign() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val message = webViewDto?.message
        val signature = message?.let { connectManagerRepository.signChallenge(it) }

        val sendSign = SendSign(
            type = webViewDto?.type ?: "",
            application = webViewDto?.application ?: "",
            password = password ?: "",
            signature = signature ?: "",
            success = signature != null
        ).toJson(moshi)

        sendWebAppMessage(sendSign)
    }

    private suspend fun processLsat() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val macaroon = webViewDto?.macaroon
        val issuer = webViewDto?.issuer

        val paymentRequest = decodePaymentRequest(webViewDto?.paymentRequest ?: "")
        val paymentAmount = paymentRequest?.getSatsAmount()
        val budget = budgetStateFlow.value.value

        val isAmountValid = paymentAmount != null
        val isBudgetSufficient = budget >= (paymentAmount?.value ?: 0)
        val areRequiredFieldsPresent =
            webViewDto?.paymentRequest != null && macaroon != null && issuer != null

        if (isAmountValid && isBudgetSufficient && areRequiredFieldsPresent) {
            val identifier =
                macaroon?.let { connectManagerRepository.getIdFromMacaroon(it) }?.toLsatIdentifier()

            identifier?.let { lspIdentifier ->
                val identifierDbRecord =
                    chatRepository.getLsatByIdentifier(lspIdentifier).firstOrNull()

                if (identifierDbRecord == null) {
                    val invoice =
                        connectManagerRepository.getInvoiceInfo(webViewDto.paymentRequest ?: "")
                            ?.toInvoiceBolt11(moshi)
                    val invoiceAmount = invoice?.getSatsAmount()?.value
                    val invoicePubKey = invoice?.getPubKey()
                    val paymentHash = invoice?.payment_hash

                    if (invoicePubKey != null && paymentHash != null && invoiceAmount != null && invoiceAmount <= budget) {
                        val routerUrl = serverSettingsSharedPreferences.getString(ROUTER_URL, null)

                        if (routerUrl != null) {
                            viewModelScope.launch {
                                if (invoice.retrieveLspPubKey() == contactRepository.accountOwner.value?.routeHint?.getLspPubKey()) {
                                    val nnPaymentRequest =
                                        webViewDto.paymentRequest?.toLightningPaymentRequestOrNull()
                                            ?: return@launch

                                    connectManagerRepository.payInvoice(
                                        paymentRequest = nnPaymentRequest,
                                        null,
                                        null,
                                        milliSatAmount = convertToMilliSat(invoiceAmount),
                                        paymentHash = paymentHash
                                    )
                                    connectManagerRepository.webViewPreImage.collect { preimage ->
                                        if (preimage?.isNotEmpty() == true) {

                                            val lsatToSave = Lsat(
                                                paymentRequest = webViewDto.paymentRequest?.toLightningPaymentRequestOrNull(),
                                                macaroon = macaroon.toMacaroon()!!,
                                                issuer = issuer?.toLsatIssuer()!!,
                                                id = lspIdentifier,
                                                preimage = preimage.toLsatPreImage(),
                                                status = LsatStatus.Active,
                                                createdAt = DateTime.nowUTC().toDateTime(),
                                                paths = null,
                                                metaData = null
                                            )

                                            val sendLsat = SendLsat(
                                                type = webViewDto.type ?: "",
                                                application = webViewDto.application ?: "",
                                                password = password ?: "",
                                                lsat = retrieveLsatString(
                                                    macaroon,
                                                    preimage
                                                ),
                                                budget = budget,
                                                success = true,
                                                macaroon = null,
                                                paymentRequest = null,
                                                preimage = null,
                                                identifier = null,
                                                paths = null,
                                                status = null
                                            ).toJson(moshi)

                                            chatRepository.upsertLsat(lsatToSave)
                                            connectManagerRepository.clearWebViewPreImage()

                                            sendWebAppMessage(sendLsat)
                                        }
                                    }

                                } else {
                                    networkQueryContact.getRoutingNodes(
                                        routerUrl,
                                        invoicePubKey,
                                        convertToMilliSat(invoiceAmount)
                                    ).collect { response ->
                                        when (response) {
                                            is LoadResponse.Loading -> {}
                                            is Response.Error -> {}
                                            is Response.Success -> {
                                                try {
                                                    val routerPubKey =
                                                        serverSettingsSharedPreferences
                                                            .getString(ROUTER_PUBKEY, null)
                                                            ?: "true"

                                                    val nnPaymentRequest =
                                                        webViewDto.paymentRequest?.toLightningPaymentRequestOrNull()
                                                            ?: return@collect

                                                    connectManagerRepository.payInvoice(
                                                        paymentRequest = nnPaymentRequest,
                                                        response.value,
                                                        routerPubKey,
                                                        milliSatAmount = convertToMilliSat(
                                                            invoiceAmount
                                                        ),
                                                        paymentHash = paymentHash
                                                    )
                                                    connectManagerRepository.webViewPreImage.collect { preimage ->
                                                        if (preimage?.isNotEmpty() == true) {

                                                            val lsatToSave = Lsat(
                                                                paymentRequest = webViewDto.paymentRequest?.toLightningPaymentRequestOrNull(),
                                                                macaroon = macaroon.toMacaroon()!!,
                                                                issuer = issuer?.toLsatIssuer()!!,
                                                                id = lspIdentifier,
                                                                preimage = preimage.toLsatPreImage(),
                                                                status = LsatStatus.Active,
                                                                createdAt = DateTime.nowUTC()
                                                                    .toDateTime(),
                                                                paths = null,
                                                                metaData = null
                                                            )

                                                            val sendLsat = SendLsat(
                                                                type = webViewDto.type ?: "",
                                                                application = webViewDto.application
                                                                    ?: "",
                                                                password = password ?: "",
                                                                lsat = retrieveLsatString(
                                                                    macaroon,
                                                                    preimage
                                                                ),
                                                                budget = budget,
                                                                success = true,
                                                                macaroon = null,
                                                                paymentRequest = null,
                                                                preimage = null,
                                                                identifier = null,
                                                                paths = null,
                                                                status = null
                                                            ).toJson(moshi)

                                                            chatRepository.upsertLsat(lsatToSave)
                                                            connectManagerRepository.clearWebViewPreImage()

                                                            sendWebAppMessage(sendLsat)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    // Handle exception
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                        } else {
                            sendLsatFailure(webViewDto, paymentAmount)
                        }
                    } else {
                        sendLsatFailure(webViewDto, paymentAmount)
                    }
                } else {
                    sendLsatFailure(webViewDto, paymentAmount)
                }
            } ?: sendLsatFailure(webViewDto, paymentAmount)
        } else {
            sendLsatFailure(webViewDto, paymentAmount)
        }
    }

    private suspend fun processUpdateLsat() {
        val webViewDto = sphinxWebViewDtoStateFlow.value

        if (webViewDto?.status == LsatStatus.EXPIRED_STRING) {
            val identifier = webViewDto.identifier?.toLsatIdentifier()
            val lsatOnDb = identifier?.let { chatRepository.getLsatByIdentifier(it).firstOrNull() }

            if (lsatOnDb != null) {
                chatRepository.updateLsatStatus(identifier, LsatStatus.Expired)

                val updateLsat = SendLsat(
                    type = webViewDto.type ?: "",
                    application = webViewDto.application ?: "",
                    password = password ?: "",
                    lsat = retrieveLsatString(lsatOnDb.macaroon.value, lsatOnDb.preimage?.value),
                    success = true,
                    macaroon = null,
                    paymentRequest = null,
                    preimage = null,
                    identifier = null,
                    paths = null,
                    status = null,
                    budget = null
                ).toJson(moshi)

                sendWebAppMessage(updateLsat)
            }
        }
    }

    private fun sendLsatFailure(webViewDto: SphinxWebViewDto?, amount: Sat?) {
        val sendLsat = SendLsat(
            type = webViewDto?.type ?: "",
            application = webViewDto?.application ?: "",
            password = password ?: "",
            macaroon = null,
            paymentRequest = null,
            preimage = null,
            identifier = null,
            paths = null,
            status = null,
            success = false,
            budget = amount?.value,
            lsat = null
        ).toJson(moshi)

        sendWebAppMessage(sendLsat)
    }

    private fun sendWebAppMessage(message: String) {
        webViewViewStateContainer.updateViewState(
            WebViewViewState.SendMessage(
                "window.sphinxMessage('$message')",
                null
            )
        )
    }

    private suspend fun sendKeySend() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val dest = webViewDto?.dest
        val amt = webViewDto?.amt
        val budget = budgetStateFlow.value.value

        if (dest != null && amt != null && amt <= budget) {
            val routerUrl = serverSettingsSharedPreferences.getString(ROUTER_URL, null)
            val routerPubKey = serverSettingsSharedPreferences.getString(ROUTER_PUBKEY, null)

            viewModelScope.launch {
                val success = connectManagerRepository.sendKeySendWithRouting(
                    pubKey = dest.toLightningNodePubKey()!!,
                    routeHint = null,
                    milliSatAmount = amt.toLong(),
                    routerUrl = routerUrl,
                    routerPubKey = routerPubKey
                )

                val sendKeySend = SendKeySend(
                    type = webViewDto.type,
                    application = webViewDto.application,
                    password = password ?: "",
                    success = success
                ).toJson(moshi)

                sendWebAppMessage(sendKeySend)
            }
        } else {
            val sendKeySend = SendKeySend(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                success = false
            ).toJson(moshi)

            sendWebAppMessage(sendKeySend)
        }

//        if (dest != null && amt != null && amt <= budget) {
//
//            val keySendPubKey = dest.toLightningNodePubKey()?.let {
//                contactRepository.getContactByPubKey(
//                    it
//                ).firstOrNull()
//            }
//            val keySendLspPubKey = keySendPubKey?.routeHint?.getLspPubKey()
//            val ownerLsp = contactRepository.accountOwner.value?.routeHint?.getLspPubKey()
//
//            if (ownerLsp == keySendLspPubKey) {
//
//                connectManagerRepository.sendKeySend(
//                    pubKey = dest,
//                    endHops = null,
//                    milliSatAmount = amt.toLong(),
//                    routerPubKey = null,
//                    routeHint = null
//                )
//
//                val sendKeySend = SendKeySend(
//                    type = webViewDto.type,
//                    application = webViewDto.application,
//                    password = password ?: "",
//                    success = true
//                ).toJson(moshi)
//
//                sendWebAppMessage(sendKeySend)
//
//            } else {
//                val routerUrl = serverSettingsSharedPreferences.getString(ROUTER_URL, null)
//                if (routerUrl != null) {
//                    networkQueryContact.getRoutingNodes(
//                        routerUrl,
//                        dest.toLightningNodePubKey()!!,
//                        amt.toLong()
//                    ).collect { response ->
//                        when (response) {
//                            is LoadResponse.Loading -> {}
//                            is Response.Error -> {}
//                            is Response.Success -> {
//                                try {
//                                    val routerPubKey = serverSettingsSharedPreferences
//                                        .getString(ROUTER_PUBKEY, null)
//                                        ?: "true"
//
//                                    connectManagerRepository.sendKeySend(
//                                        pubKey = dest,
//                                        endHops = response.value,
//                                        milliSatAmount = amt.toLong(),
//                                        routerPubKey = routerPubKey,
//                                        routeHint = null
//                                    )
//
//                                    val sendKeySend = SendKeySend(
//                                        type = webViewDto.type,
//                                        application = webViewDto.application,
//                                        password = password ?: "",
//                                        success = true
//                                    ).toJson(moshi)
//
//                                    sendWebAppMessage(sendKeySend)
//
//                                } catch (e: Exception) {
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            // Failure
//            val sendKeySend = SendKeySend(
//                type = webViewDto?.type ?: "",
//                application = webViewDto?.application ?: "",
//                password = password ?: "",
//                success = false
//            ).toJson(moshi)
//
//            sendWebAppMessage(sendKeySend)
//        }
    }

    private suspend fun processPayment() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val budget = budgetStateFlow.value.value

        val invoice = connectManagerRepository.getInvoiceInfo(webViewDto?.paymentRequest ?: "")
            ?.toInvoiceBolt11(moshi)
        val paymentAmount = invoice?.getSatsAmount()?.value
        val invoicePubKey = invoice?.getPubKey()

        val isAmountValid = paymentAmount != null
        val isBudgetSufficient = budget >= (paymentAmount ?: 0)
        val lightningPaymentRequest = webViewDto?.paymentRequest?.toLightningPaymentRequestOrNull()

        if (isAmountValid && isBudgetSufficient && lightningPaymentRequest != null) {

            if (invoice?.retrieveLspPubKey() == contactRepository.accountOwner.value?.routeHint?.getLspPubKey()) {
                connectManagerRepository.payInvoice(
                    lightningPaymentRequest,
                    endHops = null,
                    routerPubKey = null,
                    paymentAmount ?: 0
                )

                val sendPayment = SendKeySend(
                    type = webViewDto.type,
                    application = webViewDto.application,
                    password = password ?: "",
                    success = true
                ).toJson(moshi)

                sendWebAppMessage(sendPayment)

            } else {
                val routerUrl = serverSettingsSharedPreferences.getString(ROUTER_URL, null)
                if (invoicePubKey != null && routerUrl != null) {
                    networkQueryContact.getRoutingNodes(
                        routerUrl,
                        invoicePubKey,
                        paymentAmount ?: 0
                    ).collect { response ->
                        when (response) {
                            is LoadResponse.Loading -> {}
                            is Response.Error -> {}
                            is Response.Success -> {
                                try {
                                    val routerPubKey = serverSettingsSharedPreferences
                                        .getString(ROUTER_PUBKEY, null)
                                        ?: "true"

                                    val nnPaymentRequest =
                                        webViewDto.paymentRequest.toLightningPaymentRequestOrNull()
                                            ?: return@collect

                                    connectManagerRepository.payInvoice(
                                        paymentRequest = nnPaymentRequest,
                                        response.value,
                                        routerPubKey,
                                        milliSatAmount = paymentAmount ?: 0,
                                    )

                                    val sendPayment = SendKeySend(
                                        type = webViewDto.type,
                                        application = webViewDto.application,
                                        password = password ?: "",
                                        success = true
                                    ).toJson(moshi)

                                    sendWebAppMessage(sendPayment)

                                } catch (e: Exception) { }
                            }
                        }
                    }
                }
            }
        } else {
            val sendPayment = SendKeySend(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                success = false
            ).toJson(moshi)

            sendWebAppMessage(sendPayment)
        }
    }


        private fun processGetPersonData() {
            val webViewDto = sphinxWebViewDtoStateFlow.value

            contactRepository.accountOwner.value?.let { owner ->
                val sendPersonData = SendGetPersonData(
                    type = webViewDto?.type ?: "",
                    application = webViewDto?.application ?: "",
                    password = password ?: "",
                    alias = owner.alias?.value ?: "",
                    photoUrl = owner.photoUrl?.value ?: "",
                    publicKey = owner.nodePubKey?.value ?: ""
                ).toJson(moshi)

                sendWebAppMessage(sendPersonData)
            }
        }

        private fun processGetBudget() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
            val sendGetBudget = SendGetBudget(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                budget = budgetStateFlow.value.value,
                success = true
            ).toJson(moshi)

            sendWebAppMessage(sendGetBudget)
        }

        private fun decodePaymentRequest(paymentRequest: String): Bolt11? {
            paymentRequest.toLightningPaymentRequestOrNull()?.let { lightningPaymentRequest ->
                try {
                    return Bolt11.decode(lightningPaymentRequest)
                } catch (e: Exception) {
                    return null
                }
            }
            return null
        }

        @JavascriptInterface
        fun receiveMessage(data: String) {
            Log.d("SphinxWebView", "receiveMessage: $data")
            try {
                val dto = moshi.adapter(SphinxWebViewDto::class.java).fromJson(data)
                Log.d("SphinxWebView", "Parsed DTO: $dto")
                _sphinxWebViewDtoStateFlow.value = dto
            } catch (e: java.lang.Exception) {
                Log.d("SphinxWebView", "Error parsing JSON", e)
                e.printStackTrace()
            }
        }

        private fun generatePassword(): String {
            @OptIn(RawPasswordAccess::class)
            return PasswordGenerator(passwordLength = 16).password.value.joinToString("")
        }

        private fun convertToMilliSat(amount: Long): Long {
            return amount * 1000
        }

        private fun retrieveLsatString(macaroon: String?, preimage: String?): String {
            return "LSAT $macaroon:$preimage"
        }
    }