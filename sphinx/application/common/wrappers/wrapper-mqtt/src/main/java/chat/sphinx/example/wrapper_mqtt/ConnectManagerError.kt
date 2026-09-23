package chat.sphinx.example.wrapper_mqtt

sealed class ConnectManagerError {

    object GenerateXPubError: ConnectManagerError()
    object GenerateMnemonicError: ConnectManagerError()
    object InvalidMnemonicError: ConnectManagerError()
    object ProcessInviteError: ConnectManagerError()
    object SubscribeOwnerError: ConnectManagerError()
    object ParseInvite: ConnectManagerError()
    data class MqttConnectError(val error: String?): ConnectManagerError()
    object MqttClientError: ConnectManagerError()
    data class MqttInitError(val logs: String) : ConnectManagerError()
    data class SendMessageError(val provisionalId: Long? = null): ConnectManagerError()
    object DeleteMessageError: ConnectManagerError()
    object JoinTribeError: ConnectManagerError()
    object CreateTribeError: ConnectManagerError()
    object CreateInviteError: ConnectManagerError()
    object DeleteInviteError: ConnectManagerError()
    object ConcatNodesError: ConnectManagerError()
    object CreateInvoiceError: ConnectManagerError()
    object ServerPubKeyError: ConnectManagerError()
    object PayContactInvoiceError: ConnectManagerError()
    object PayInvoiceError: ConnectManagerError()
    object PaymentHashError: ConnectManagerError()
    object ListTribeMembersError: ConnectManagerError()
    object FetchMessageError: ConnectManagerError()
    object FetchFirstMessageError: ConnectManagerError()
    object MessageCountError: ConnectManagerError()
    object SetDeviceIdError: ConnectManagerError()
    object MediaTokenError: ConnectManagerError()
    object ReadMessageError: ConnectManagerError()
    object GetReadMessagesError: ConnectManagerError()
    object SignBytesError: ConnectManagerError()
    object SetMuteError: ConnectManagerError()
    object LoadTransactionsError: ConnectManagerError()
    object SendKeySendError: ConnectManagerError()
    object SendBoostError: ConnectManagerError()
    object MqttReconnectError: ConnectManagerError()
    object XPubOrSignError: ConnectManagerError()
    object RestoreConnectionError: ConnectManagerError()

    /**
     * Mixer Failed sent-status. [code] is an allowlisted enum name
     * (`CLN_UNAVAILABLE`, `CLN_TIMEOUT`, `INSUFFICIENT_BALANCE`, or `UNKNOWN`).
     * [eventId] exists only so a repeated code is a new StateFlow value.
     * Never display [eventId], [code], or a raw mixer reason.
     */
    data class MixerOperationError(
        val code: String,
        val eventId: Long
    ): ConnectManagerError()
}

const val MIXER_ERROR_CLN_UNAVAILABLE = "CLN_UNAVAILABLE"
const val MIXER_ERROR_CLN_TIMEOUT = "CLN_TIMEOUT"
const val MIXER_ERROR_INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE"
const val MIXER_ERROR_UNKNOWN = "UNKNOWN"

/**
 * Allowlisted mixer error codes. Anything else, including [MIXER_ERROR_UNKNOWN]
 * and raw reason text, maps to the generic send/pay string. Never returns the
 * raw code or a reason string.
 */
enum class MixerErrorMessageKind {
    CLN_UNAVAILABLE,
    CLN_TIMEOUT,
    INSUFFICIENT_BALANCE,
    GENERIC
}

fun mixerErrorMessageKind(code: String?): MixerErrorMessageKind {
    return when (code) {
        MIXER_ERROR_CLN_UNAVAILABLE -> MixerErrorMessageKind.CLN_UNAVAILABLE
        MIXER_ERROR_CLN_TIMEOUT -> MixerErrorMessageKind.CLN_TIMEOUT
        MIXER_ERROR_INSUFFICIENT_BALANCE -> MixerErrorMessageKind.INSUFFICIENT_BALANCE
        else -> MixerErrorMessageKind.GENERIC
    }
}

fun mixerErrorMessageKind(error: ConnectManagerError.MixerOperationError): MixerErrorMessageKind {
    return mixerErrorMessageKind(error.code)
}

