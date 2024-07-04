package chat.sphinx.payment_send.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_network_query_contact.NetworkQueryContact
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_lightning.LightningRepository
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.concept_repository_message.model.SendPayment
import chat.sphinx.concept_view_model_coordinator.ViewModelCoordinator
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.exception
import chat.sphinx.payment_common.ui.PaymentSideEffect
import chat.sphinx.payment_common.ui.PaymentViewModel
import chat.sphinx.payment_common.ui.viewstate.AmountViewState
import chat.sphinx.payment_common.ui.viewstate.send.PaymentSendViewState
import chat.sphinx.payment_send.R
import chat.sphinx.payment_send.navigation.PaymentSendNavigator
import chat.sphinx.scanner_view_model_coordinator.request.ScannerRequest
import chat.sphinx.scanner_view_model_coordinator.response.ScannerResponse
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.lightning.*
import chat.sphinx.wrapper_common.message.MessageUUID
import chat.sphinx.wrapper_lightning.NodeBalance
import chat.sphinx.wrapper_message.getColorKey
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

internal inline val PaymentSendFragmentArgs.chatId: ChatId?
    get() = if (argChatId == ChatId.NULL_CHAT_ID.toLong()) {
        null
    } else {
        ChatId(argChatId)
    }

internal inline val PaymentSendFragmentArgs.contactId: ContactId?
    get() = if (argContactId == ContactId.NULL_CONTACT_ID) {
        null
    } else {
        ContactId(argContactId)
    }

internal inline val PaymentSendFragmentArgs.messageUUID: MessageUUID?
    get() = if (argMessageUUID.isEmpty()) {
        null
    } else {
        MessageUUID(argMessageUUID)
    }

@HiltViewModel
internal class PaymentSendViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    savedStateHandle: SavedStateHandle,
    private val paymentSendNavigator: PaymentSendNavigator,
    private val app: Application,
    private val contactRepository: ContactRepository,
    private val lightningRepository: LightningRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val connectManagerRepository: ConnectManagerRepository,
    private val networkQueryContact: NetworkQueryContact,
    private val scannerCoordinator: ViewModelCoordinator<ScannerRequest, ScannerResponse>
): PaymentViewModel<PaymentSendFragmentArgs, PaymentSendViewState>(
    dispatchers,
    paymentSendNavigator,
    contactRepository,
    messageRepository,
    chatRepository,
    PaymentSendViewState.Idle
)
{
    companion object {
        const val SERVER_SETTINGS_SHARED_PREFERENCES = "server_ip_settings"
        const val ROUTER_URL= "router_url"
        const val ROUTER_PUBKEY= "router_pubkey"
        private const val MAXIMUM_SEND_SAT_AMOUNT = 9_999_999
    }

    private val serverSettingsSharedPreferences: SharedPreferences =
        app.getSharedPreferences(SERVER_SETTINGS_SHARED_PREFERENCES, Context.MODE_PRIVATE)

    private val sendPaymentBuilder = SendPayment.Builder()

    override val args: PaymentSendFragmentArgs by savedStateHandle.navArgs()
    override val chatId: ChatId? = args.chatId
    override val contactId: ContactId? = args.contactId
    override val messageUUID: MessageUUID? = args.messageUUID
    override val lightningNodePubKey: LightningNodePubKey? = args.argLightningNodePubKey.toLightningNodePubKey()
    override val routeHint: LightningRouteHint? = args.argLightningRouteHint.toLightningRouteHint()

    private suspend fun getAccountBalance(): StateFlow<NodeBalance?> =
        lightningRepository.getAccountBalance()

    init {
        viewModelScope.launch(mainImmediate) {
            viewStateContainer.updateViewState(
                viewState = getContactOrNull()?.let { contact ->
                    PaymentSendViewState.ChatPayment(contact)
                } ?: getMessageOrNull()?.let { message ->
                    PaymentSendViewState.TribePayment(
                        message.senderAlias,
                        message.getColorKey(),
                        message.senderPic
                    )
                } ?: PaymentSendViewState.KeySendPayment

            )
        }
    }

    fun sendPayment(message: String? = null) {
        viewModelScope.launch {
            args.messageUUID?.let { messageUUID ->
                sendTribeDirectPayment(message, messageUUID)
            } ?: run {
                lightningNodePubKey?.value?.toLightningNodePubKey()?.let { pubKey ->
                    val contact = contactRepository.getContactByPubKey(
                        pubKey
                    ).firstOrNull()
                    val contactLspPubKey = contact?.routeHint?.getLspPubKey()
                    val ownerLsp = contactRepository.accountOwner.value?.routeHint?.getLspPubKey()

                    if (contact == null || (contactLspPubKey != null && contactLspPubKey != ownerLsp)) {
                        sendKeySend(pubKey)
                    } else {
                        sendContactPayment(message)
                    }
                }
            }
        }
    }

    private fun sendTribeDirectPayment(
        message: String? = null,
        messageUUID: MessageUUID
    ) {
        if (chatId == null) return

        viewModelScope.launch(mainImmediate) {
            val chat = getChatOrNull() ?: return@launch

            messageRepository.sendTribePayment(
                chatId = chat.id,
                amount = Sat(sendPaymentBuilder.paymentAmount),
                messageUUID = messageUUID,
                text = message ?: ""
            )

            navigator.closeDetailScreen()
        }
    }

    private suspend fun sendContactPayment(message: String? = null) {
        sendPaymentBuilder.setChatId(args.chatId)
        sendPaymentBuilder.setContactId(args.contactId)
        sendPaymentBuilder.setText(message)
        sendPaymentBuilder.setDestinationKey(lightningNodePubKey)
        sendPaymentBuilder.setRouteHint(routeHint)

        paymentSendNavigator.toPaymentTemplateDetail(
            args.contactId,
            args.chatId,
            Sat(sendPaymentBuilder.paymentAmount),
            message ?: "",
        )
    }

    private suspend fun sendKeySend(pubKey: LightningNodePubKey) {
        submitSideEffect(
            PaymentSideEffect.AlertConfirmPaymentSend(
                sendPaymentBuilder.paymentAmount,
                lightningNodePubKey?.value ?: ""
            ) {
                viewModelScope.launch {
                    val routerUrl = serverSettingsSharedPreferences.getString(ROUTER_URL, null)
                    if (routerUrl != null) {
                        networkQueryContact.getRoutingNodes(
                            routerUrl,
                            pubKey,
                            convertToMilliSat(sendPaymentBuilder.paymentAmount)
                        ).collect { response ->
                            when (response) {
                                is LoadResponse.Loading -> {}
                                is Response.Error -> {}
                                is Response.Success -> {
                                    val routerPubKey = serverSettingsSharedPreferences
                                        .getString(ROUTER_PUBKEY, null)

                                    if (isJsonResponseEmpty(response.value)) {
                                        connectManagerRepository.sendKeySend(
                                            pubKey.value,
                                            null,
                                            sendPaymentBuilder.paymentAmount,
                                            null
                                        )
                                    } else {
                                        connectManagerRepository.sendKeySend(
                                            pubKey.value,
                                            response.value,
                                            sendPaymentBuilder.paymentAmount,
                                            routerPubKey
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    private fun sendPayment() {
        viewStateContainer.updateViewState(PaymentSendViewState.ProcessingPayment)

        viewModelScope.launch(mainImmediate) {
            val sendPayment = sendPaymentBuilder.build()

            when (val response = messageRepository.sendPayment(sendPayment)) {
                is Response.Error -> {
                    submitSideEffect(
                        PaymentSideEffect.Notify(
                            String.format(
                                app.getString(R.string.error_payment_message),
                                response.exception?.message ?: response.cause.message
                            )
                        )
                    )
                    viewStateContainer.updateViewState(PaymentSendViewState.PaymentFailed)
                }
                is Response.Success -> {
                    val successMessage = app.getString(
                        R.string.payment_sent,
                        sendPayment?.amount ?: 0,
                        sendPayment?.destinationKey?.value ?: "Unknown"
                    )

                    submitSideEffect(
                        PaymentSideEffect.Notify(successMessage)
                    )

                    navigator.closeDetailScreen()
                }
            }
        }
    }

    override fun updateAmount(amountString: String) {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(PaymentSideEffect.ProduceHapticFeedback)

            getAccountBalance().firstOrNull()?.let { balance ->
                val updatedAmount: Int? = try {
                    amountString.toInt()
                } catch (e: NumberFormatException) {
                    null
                }

                when {
                    updatedAmount == null -> {
                        amountViewStateContainer.updateViewState(AmountViewState.AmountUpdated(""))
                    }
                    updatedAmount <= MAXIMUM_SEND_SAT_AMOUNT && updatedAmount <= balance.balance.value -> {
                        amountViewStateContainer.updateViewState(AmountViewState.AmountUpdated(updatedAmount.toString()))
                    }
                    else -> {
                        submitSideEffect(
                            PaymentSideEffect.Notify(
                                app.getString(
                                    if (updatedAmount > balance.balance.value) {
                                        R.string.balance_too_low
                                    } else {
                                        R.string.amount_too_high
                                    }
                                )
                            )
                        )
                        return@let
                    }
                }

                sendPaymentBuilder.setAmount(updatedAmount?.toLong() ?: 0)
            }
        }
    }

    private fun convertToMilliSat(amount: Long): Long {
        return amount * 1000
    }

    private fun isJsonResponseEmpty(response: String): Boolean {
        return try {
            val jsonArray = JSONArray(response)
            jsonArray.length() == 0
        } catch (e: Exception) {
            true
        }
    }

}
