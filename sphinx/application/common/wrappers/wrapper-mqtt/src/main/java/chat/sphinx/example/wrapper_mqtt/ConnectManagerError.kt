package chat.sphinx.example.wrapper_mqtt

sealed class ConnectManagerError {

    object GenerateXPubError: ConnectManagerError()
    object GenerateMnemonicError: ConnectManagerError()
    object ProcessInviteError: ConnectManagerError()
    object SubscribeOwnerError: ConnectManagerError()
    object MqttConnectError: ConnectManagerError()
    object MqttClientError: ConnectManagerError()
    object MqttInitError: ConnectManagerError()
    object SendMessageError: ConnectManagerError()
    object DeleteMessageError: ConnectManagerError()
    object JoinTribeError: ConnectManagerError()
    object CreateTribeError: ConnectManagerError()
    object CreateInviteError: ConnectManagerError()
    object CreateInvoiceError: ConnectManagerError()
    object ServerPubKeyError: ConnectManagerError()
    object PayContactInvoiceError: ConnectManagerError()
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
}
