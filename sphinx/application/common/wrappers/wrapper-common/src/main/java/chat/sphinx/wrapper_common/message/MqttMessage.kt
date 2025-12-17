package chat.sphinx.wrapper_common.message

data class MqttMessage(
    val msg: String,
    val msgSender: String,
    val msgType: Int,
    val msgUuid: String,
    val msgIndex: String,
    val msgTimestamp: Long?,
    val sentTo: String,
    val amount: Long?,
    val fromMe: Boolean?,
    val tag: String?,
    val date: Long?,
    val paymentHash: String?
)
