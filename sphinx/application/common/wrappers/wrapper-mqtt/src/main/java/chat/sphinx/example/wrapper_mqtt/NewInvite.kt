package chat.sphinx.example.wrapper_mqtt

data class NewInvite(
    val nickname: String,
    val invitePrice: Long,
    val welcomeMessage: String, // May used in the future
    val tribeServerPubKey: String?, // May include on inviter UI in the future
    val inviteString: String?,
    val inviteCode: String?,
    val tag: String?
)
