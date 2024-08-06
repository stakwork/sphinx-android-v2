package chat.sphinx.concept_repository_connect_manager.model

sealed class OwnerRegistrationState {

    data class OwnerRegistered(
        val isRestoreAccount: Boolean,
        val mixerServerIp: String?,
        val tirbeServerHost: String?,
        val isProductionEnvironment: Boolean,
        val routerUrl: String?,
        val defaultTribe: String?
    ) : OwnerRegistrationState()
    data class MnemonicWords(val words: String): OwnerRegistrationState()
    data class SignedChallenge(val authToken: String): OwnerRegistrationState()
    data class NewInviteCode(val inviteCode: String): OwnerRegistrationState()
    object GetNodes: OwnerRegistrationState()
    data class StoreRouterPubKey(val nodes: String): OwnerRegistrationState()
}
