package chat.sphinx.payment_receive.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import chat.sphinx.concept_repository_chat.ChatRepository
import chat.sphinx.concept_repository_connect_manager.ConnectManagerRepository
import chat.sphinx.concept_repository_contact.ContactRepository
import chat.sphinx.concept_repository_message.MessageRepository
import chat.sphinx.concept_repository_message.model.SendPaymentRequest
import chat.sphinx.kotlin_response.*
import chat.sphinx.payment_common.ui.PaymentSideEffect
import chat.sphinx.payment_common.ui.PaymentViewModel
import chat.sphinx.payment_common.ui.viewstate.AmountViewState
import chat.sphinx.payment_common.ui.viewstate.receive.PaymentReceiveViewState
import chat.sphinx.payment_receive.R
import chat.sphinx.resources.R as R_common
import chat.sphinx.payment_receive.navigation.PaymentReceiveNavigator
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_common.dashboard.ContactId
import chat.sphinx.wrapper_common.lightning.LightningNodePubKey
import chat.sphinx.wrapper_common.lightning.LightningRouteHint
import chat.sphinx.wrapper_common.message.MessageUUID
import dagger.hilt.android.lifecycle.HiltViewModel
import io.matthewnelson.android_feature_navigation.util.navArgs
import io.matthewnelson.android_feature_viewmodel.submitSideEffect
import io.matthewnelson.android_feature_viewmodel.updateViewState
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.annotation.meta.Exhaustive
import javax.inject.Inject

internal inline val PaymentReceiveFragmentArgs.chatId: ChatId?
    get() = if (argChatId == ChatId.NULL_CHAT_ID.toLong()) {
        null
    } else {
        ChatId(argChatId)
    }

internal inline val PaymentReceiveFragmentArgs.contactId: ContactId?
    get() = if (argContactId == ContactId.NULL_CONTACT_ID) {
        null
    } else {
        ContactId(argContactId)
    }

@HiltViewModel
internal class PaymentReceiveViewModel @Inject constructor(
    dispatchers: CoroutineDispatchers,
    savedStateHandle: SavedStateHandle,
    private val paymentReceiveNavigator: PaymentReceiveNavigator,
    private val app: Application,
    private val contactRepository: ContactRepository,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val connectManagerRepository: ConnectManagerRepository
): PaymentViewModel<PaymentReceiveFragmentArgs, PaymentReceiveViewState>(
    dispatchers,
    paymentReceiveNavigator,
    contactRepository,
    messageRepository,
    chatRepository,
    PaymentReceiveViewState.Idle
)
{
    override val args: PaymentReceiveFragmentArgs by savedStateHandle.navArgs()
    override val chatId: ChatId? = args.chatId
    override val contactId: ContactId? = args.contactId
    override val messageUUID: MessageUUID? = null
    override val lightningNodePubKey: LightningNodePubKey? = null
    override val routeHint: LightningRouteHint? = null

    private val sendPaymentRequestBuilder = SendPaymentRequest.Builder()

    init {
        viewModelScope.launch(mainImmediate) {
            refreshViewState()
        }
    }

    private suspend fun refreshViewState() {
        val contact = getContactOrNull()
        viewStateContainer.updateViewState(
            if (contact != null) {
                PaymentReceiveViewState.ChatPaymentRequest(contact)
            } else {
                PaymentReceiveViewState.RequestLightningPayment
            }
        )
    }

    fun requestPayment(message: String? = null) {
        sendPaymentRequestBuilder.setChatId(args.chatId)
        sendPaymentRequestBuilder.setContactId(args.contactId)
        sendPaymentRequestBuilder.setMemo(message)

        if (sendPaymentRequestBuilder.isContactRequest) {
            sendPaymentRequest()
            return
        }
        viewModelScope.launch(mainImmediate) {
            val requestPayment = sendPaymentRequestBuilder.build()

            if (requestPayment != null) {
                updateViewState(PaymentReceiveViewState.ProcessingRequest)

                val invoiceAndHash = connectManagerRepository.createInvoice(requestPayment.amount, requestPayment.memo ?: "")

                if (invoiceAndHash != null) {
                    paymentReceiveNavigator.toQRCodeDetail(
                        invoiceAndHash.first,
                        app.getString(R_common.string.payment_request),
                        app.getString(R_common.string.amount_n_sats, requestPayment.amount),
                        false
                    )
                    refreshViewState()
                    delay(100L)
                    updateAmount("")
                } else {
                    submitSideEffect(
                        PaymentSideEffect.Notify(app.getString(R_common.string.failed_to_request_payment))
                    )
                    refreshViewState()

                }
            } else {
                submitSideEffect(PaymentSideEffect.Notify("Failed to request payment"))
            }
        }
    }

    fun sendPaymentRequest() {
        viewModelScope.launch(mainImmediate) {
            val requestPayment = sendPaymentRequestBuilder.build()

            if (requestPayment != null) {

                messageRepository.sendNewPaymentRequest(requestPayment)
                navigator.closeDetailScreen()
            } else {
                submitSideEffect(PaymentSideEffect.Notify("Failed to request payment"))
            }
        }
    }

    override fun updateAmount(amountString: String) {
        viewModelScope.launch(mainImmediate) {
            submitSideEffect(PaymentSideEffect.ProduceHapticFeedback)

            val updatedAmount: Int? = try {
                amountString.toInt()
            } catch (e: NumberFormatException) {
                null
            }

            sendPaymentRequestBuilder.setAmount(updatedAmount?.toLong() ?: 0)

            when {
                updatedAmount == null -> {
                    amountViewStateContainer.updateViewState(AmountViewState.AmountUpdated(""))
                }
                updatedAmount <= MAXIMUM_RECEIVE_SAT_AMOUNT -> {
                    amountViewStateContainer.updateViewState(AmountViewState.AmountUpdated(updatedAmount.toString()))
                }
                else -> {
                    submitSideEffect(
                        PaymentSideEffect.Notify(app.getString(R.string.requested_amount_too_high))
                    )
                }
            }
        }
    }

    companion object {
        private const val MAXIMUM_RECEIVE_SAT_AMOUNT = 9_999_999
    }
}
