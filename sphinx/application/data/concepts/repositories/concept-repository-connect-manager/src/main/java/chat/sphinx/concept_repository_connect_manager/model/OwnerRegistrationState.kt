package chat.sphinx.concept_repository_connect_manager.model

import chat.sphinx.example.wrapper_mqtt.TribeMembersResponse

sealed class OwnerRegistrationState {

    data class OwnerRegistered(
        val isRestoreAccount: Boolean,
        val mixerServerIp: String?,
        val tirbeServerHost: String?
    ) : OwnerRegistrationState()
    data class MnemonicWords(val words: String): OwnerRegistrationState()
    data class SignedChallenge(val authToken: String): OwnerRegistrationState()
    data class NewInviteCode(val inviteCode: String): OwnerRegistrationState()
}
