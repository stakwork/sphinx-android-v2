package chat.sphinx.concept_network_query_people.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CallTokenDto(
    val serverUrl: String,
    val roomName: String,
    val participantToken: String,
    val participantName: String,
)