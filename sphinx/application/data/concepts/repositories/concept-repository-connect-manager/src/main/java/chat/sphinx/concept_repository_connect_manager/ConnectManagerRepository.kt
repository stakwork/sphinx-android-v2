package chat.sphinx.concept_repository_connect_manager

import chat.sphinx.concept_repository_connect_manager.model.ConnectionManagerState
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_connect_manager.model.RestoreProcessState
import chat.sphinx.wrapper_contact.NewContact
import kotlinx.coroutines.flow.MutableStateFlow

interface ConnectManagerRepository {

    val connectionManagerState: MutableStateFlow<ConnectionManagerState?>
    val networkStatus: MutableStateFlow<NetworkStatus>
    val restoreProcessState: MutableStateFlow<RestoreProcessState?>

    fun createOwnerAccount(lspIp: String)
    fun createContact(contact: NewContact)
    fun connectAndSubscribeToMqtt(userState: String?) {}
    fun singChallenge(challenge: String)
    fun createInvite(nickname: String, welcomeMessage: String, sats: Long, tribeServerPubKey: String?)
    fun setInviteCode(inviteString: String)
    fun setMnemonicWords(words: List<String>?)
    fun setOwnerDeviceId(deviceId: String)

    fun joinTribe(
        tribeHost: String,
        tribePubKey: String,
        tribeRouteHint: String,
        tribeName: String,
        tribePicture: String?,
        isPrivate: Boolean,
        userAlias: String,
        pricePerMessage: Long,
        escrowAmount: Long,
        priceToJoin: Long,
    )

    fun getTribeMembers(
        tribeServerPubKey: String,
        tribePubKey: String
    )

    fun getTribeServerPubKey(): String?
    suspend fun updateLspAndOwner(data: String) {}
    fun startRestoreProcess()

}