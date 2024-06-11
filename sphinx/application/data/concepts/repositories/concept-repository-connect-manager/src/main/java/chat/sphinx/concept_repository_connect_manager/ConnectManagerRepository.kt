package chat.sphinx.concept_repository_connect_manager

import chat.sphinx.concept_repository_connect_manager.model.OwnerRegistrationState
import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.concept_repository_connect_manager.model.RestoreProcessState
import chat.sphinx.example.wrapper_mqtt.ConnectManagerError
import chat.sphinx.example.wrapper_mqtt.TransactionDto
import chat.sphinx.example.wrapper_mqtt.TribeMembersResponse
import chat.sphinx.wrapper_common.dashboard.ChatId
import chat.sphinx.wrapper_contact.NewContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface ConnectManagerRepository {

    val connectionManagerState: MutableStateFlow<OwnerRegistrationState?>
    val networkStatus: MutableStateFlow<NetworkStatus>
    val restoreProcessState: MutableStateFlow<RestoreProcessState?>
    val connectManagerErrorState: MutableStateFlow<ConnectManagerError?>
    val transactionDtoState: MutableStateFlow<List<TransactionDto>?>
    val userStateFlow: MutableStateFlow<String?>
    val tribeMembersState: MutableStateFlow<TribeMembersResponse?>

    fun createOwnerAccount()
    fun createContact(contact: NewContact)
    fun connectAndSubscribeToMqtt(userState: String?, mixerIp: String?) {}
    fun singChallenge(challenge: String)
    fun createInvite(nickname: String, welcomeMessage: String, sats: Long, tribeServerPubKey: String?)
    fun setInviteCode(inviteString: String)
    fun setNetworkType(isTestEnvironment: Boolean)
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
    fun sendKeySend(pubKey: String, amount: Long)

    fun getTribeServerPubKey(): String?
    fun getPayments(lastMessageDate: Long, limit: Int)
    suspend fun getPubKeyByEncryptedChild(child: String): Flow<ChatId?>
    suspend fun updateLspAndOwner(data: String) {}
    fun startRestoreProcess()

}