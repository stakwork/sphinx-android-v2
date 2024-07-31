package chat.sphinx.chat_tribe.ui

import android.app.Application
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.viewModelScope
import chat.sphinx.chat_tribe.model.*
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.APPLICATION_NAME
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_AUTHORIZE
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_KEYSEND
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_GET_LSAT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_LSAT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_PAYMENT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SET_BUDGET
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SIGN
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_UPDATE_LSAT
import chat.sphinx.chat_tribe.ui.viewstate.WebViewLayoutScreenViewState
import chat.sphinx.chat_tribe.ui.viewstate.TribeFeedViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebAppViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebViewViewState
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.example.wrapper_mqtt.InvoiceBolt11.Companion.toInvoiceBolt11
import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.Sat
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
    private val connectManagerRepository: ConnectManagerRepository
    ) : BaseViewModel<TribeFeedViewState>(dispatchers, TribeFeedViewState.Idle) {

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
        when(webAppViewStateContainer.value) {
            is WebAppViewState.AppAvailable.WebViewClosed -> {
                (webAppViewStateContainer.value as? WebAppViewState.AppAvailable.WebViewClosed)?.let {
                    webAppViewStateContainer.updateViewState(
                        WebAppViewState.AppAvailable.WebViewOpen.Loading(it.appUrl)
                    )
                    webViewLayoutScreenViewStateContainer.updateViewState(WebViewLayoutScreenViewState.Open)
                }
            }
            is WebAppViewState.AppAvailable.WebViewOpen -> {
                (webAppViewStateContainer.value as? WebAppViewState.AppAvailable.WebViewOpen)?.let {
                    webAppViewStateContainer.updateViewState(
                        WebAppViewState.AppAvailable.WebViewClosed(it.appUrl)
                    )
                    webViewLayoutScreenViewStateContainer.updateViewState(WebViewLayoutScreenViewState.Closed)
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
        val areRequiredFieldsPresent = webViewDto?.paymentRequest != null && macaroon != null && issuer != null

        if (isAmountValid && isBudgetSufficient && areRequiredFieldsPresent) {
            val identifier = macaroon?.let { connectManagerRepository.getIdFromMacaroon(it) }?.toLsatIdentifier()

            identifier?.let { lspIdentifier ->
                val identifierDbRecord = chatRepository.getLsatByIdentifier(lspIdentifier).firstOrNull()

                if (identifierDbRecord == null) {
                    val invoice = connectManagerRepository.getInvoiceInfo(webViewDto.paymentRequest ?: "")?.toInvoiceBolt11(moshi)
                    val invoiceAmount = invoice?.getSatsAmount()?.value
                    val paymentHash = invoice?.payment_hash

                    if (paymentHash != null && invoiceAmount != null && invoiceAmount <= budget) {

                        connectManagerRepository.payWebAppInvoice(
                            paymentRequest = webViewDto.paymentRequest ?: "",
                            paymentHash = paymentHash,
                            milliSatAmount = invoiceAmount
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
                                    lsat = retrieveLsatString(macaroon, preimage),
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

        if (webViewDto?.status == LsatStatus.EXPIRED) {
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

            connectManagerRepository.sendKeySend(
                pubKey = dest,
                endHops = null,
                milliSatAmount = amt.toLong(),
                routerPubKey = null,
                routeHint = null
            )

            // Check for KeySend success to send the message

        } else {
            // Failure
            val sendKeySend = SendKeySend(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password ?: "",
                success = false
            ).toJson(moshi)

            sendWebAppMessage(sendKeySend)
        }
    }

    private suspend fun processPayment() {
        val webViewDto = sphinxWebViewDtoStateFlow.value
        val budget = budgetStateFlow.value.value
        val paymentRequest = decodePaymentRequest(webViewDto?.paymentRequest ?: "")
        val paymentAmount = paymentRequest?.getSatsAmount()
        val isAmountValid = paymentAmount != null
        val isBudgetSufficient = budget >= (paymentAmount?.value ?: 0)
        val lightningPaymentRequest = webViewDto?.paymentRequest?.toLightningPaymentRequestOrNull()

        if (isAmountValid && isBudgetSufficient && lightningPaymentRequest != null) {
            connectManagerRepository.payInvoice(
                lightningPaymentRequest,
                endHops = null,
                routerPubKey = null,
                paymentAmount?.value ?: 0
            )

            // Check for Payment success to send the message

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
            _sphinxWebViewDtoStateFlow.value =
                moshi.adapter(SphinxWebViewDto::class.java).fromJson(data)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun generatePassword(): String {
        @OptIn(RawPasswordAccess::class)
        return PasswordGenerator(passwordLength = 16).password.value.joinToString("")
    }

    private fun retrieveLsatString(macaroon: String?, preimage: String?): String {
        return "LSAT $macaroon:$preimage"
    }
}