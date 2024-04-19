package chat.sphinx.concept_repository_connect_manager.model

import chat.sphinx.example.wrapper_mqtt.MsgsCounts

sealed class RestoreProcessState{
    data class MessagesCounts(val msgsCounts: MsgsCounts) : RestoreProcessState()
    object RestoreMessages : RestoreProcessState()

}
