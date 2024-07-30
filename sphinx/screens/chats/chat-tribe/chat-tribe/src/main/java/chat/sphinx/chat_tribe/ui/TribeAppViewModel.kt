package chat.sphinx.chat_tribe.ui

import android.app.Application
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.lifecycle.viewModelScope
import chat.sphinx.chat_tribe.R
import chat.sphinx.chat_tribe.model.*
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.APPLICATION_NAME
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_AUTHORIZE
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_KEYSEND
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_GET_LSAT
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SET_BUDGET
import chat.sphinx.chat_tribe.model.SphinxWebViewDto.Companion.TYPE_SIGN
import chat.sphinx.chat_tribe.ui.viewstate.WebViewLayoutScreenViewState
import chat.sphinx.chat_tribe.ui.viewstate.TribeFeedViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebAppViewState
import chat.sphinx.chat_tribe.ui.viewstate.WebViewViewState
import chat.sphinx.concept_network_query_verify_external.NetworkQueryAuthorizeExternal
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.wrapper_common.lightning.Bolt11
import chat.sphinx.wrapper_common.lightning.Sat
import chat.sphinx.wrapper_common.lightning.toLightningPaymentRequestOrNull
import chat.sphinx.wrapper_common.lightning.toSat
import chat.sphinx.wrapper_common.lsat.toLsatIssuer
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
                        _budgetStateFlow.value = Sat(0)
                        webViewViewStateContainer.updateViewState(WebViewViewState.RequestAuthorization)
                    }
                    TYPE_GET_LSAT -> {
                        processGetLsat()
//                        sphinxWebViewDtoStateFlow.value?.paymentRequest?.let {
//                            decodePaymentRequest(it)
//                        }
                    }
                    TYPE_SET_BUDGET -> {
                        webViewViewStateContainer.updateViewState(WebViewViewState.SetBudget)
                    }
                    TYPE_SIGN -> {
                        processSign()
                    }
                    TYPE_KEYSEND -> {
                        sendKeySend()
                    }
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
                password = password!!,
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
                password = password!!,
                macaroon = lastLsat.macaroon.value,
                paymentRequest = lastLsat.paymentRequest?.value,
                preimage = lastLsat.preimage?.value,
                identifier = lastLsat.id.value,
                paths = lastLsat.paths?.value,
                status = lastLsat.status.value.toString(),
                success = true
            ).toJson(moshi)
        } else {
            SendLsat(
                type = webViewDto?.type ?: "",
                application = webViewDto?.application ?: "",
                password = password!!,
                macaroon = null,
                paymentRequest = null,
                preimage = null,
                identifier = null,
                paths = null,
                status = null,
                success = false
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
            password = password!!,
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
            password = password!!,
            signature = signature ?: "",
            success = signature != null
        ).toJson(moshi)

        sendWebAppMessage(sendSign)
    }

    private fun sendWebAppMessage(message: String) {
        webViewViewStateContainer.updateViewState(
            WebViewViewState.SendMessage(
                "window.sphinxMessage('$message')",
                null
            )
        )
    }

    private fun sendKeySend(){
        sphinxWebViewDtoStateFlow.value?.amt?.let { amount ->
            sphinxWebViewDtoStateFlow.value?.dest?.let { destination ->
                if (budgetStateFlow.value.value >= amount) {
                    viewModelScope.launch(mainImmediate) {}
                    return
                }
            }
        }

        sendMessage(
            type = TYPE_KEYSEND,
            success = false,
            error = app.getString(R.string.side_effect_insufficient_budget)
        )
    }

    private fun decodePaymentRequest(paymentRequest: String) {
        paymentRequest.toLightningPaymentRequestOrNull()?.let { lightningPaymentRequest ->
            try {
                val bolt11 = Bolt11.decode(lightningPaymentRequest)
                val amount = bolt11.getSatsAmount()

                amount?.let { nnAmount ->
                    if (budgetStateFlow.value.value >= (nnAmount.value)) {
                        viewModelScope.launch(mainImmediate) {}
                    } else {
                        sendMessage(
                            type = TYPE_GET_LSAT,
                            success = false,
                            lsat = null,
                            error = app.getString(R.string.side_effect_insufficient_budget)
                        )
                    }
                }

            } catch (e: Exception) {
                sendMessage(
                    type = TYPE_GET_LSAT,
                    success = false,
                    lsat = null,
                    error = app.getString(R.string.side_effect_error_pay_lsat)
                )
            }
        }
    }

    private fun sendMessage(
        type: String,
        success: Boolean,
        lsat: String? = null,
        error: String? = null
    ) {
        val password = generatePassword()

        val message = when (type) {
            TYPE_GET_LSAT -> {
//                SendLsat(
//                    password = password,
//                    budget = budgetStateFlow.value.value.toString(),
//                    type = TYPE_LSAT,
//                    application = APPLICATION_NAME,
//                    lsat = lsat,
//                    success = success
//                ).toJson(moshi)
            }
            TYPE_KEYSEND -> {
                SendKeySend(
                    password = password,
                    type = TYPE_KEYSEND,
                    application = APPLICATION_NAME,
                    success = success
                ).toJson(moshi)
            }
            else -> {
                null
            }
        }

        message?.let { nnMessage ->
            webViewViewStateContainer.updateViewState(
                WebViewViewState.SendMessage(
                    "window.sphinxMessage('$nnMessage')",
                    error
                )
            )
        }
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
}