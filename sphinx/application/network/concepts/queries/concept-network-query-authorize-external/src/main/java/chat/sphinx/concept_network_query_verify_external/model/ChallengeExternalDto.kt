package chat.sphinx.concept_network_query_verify_external.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChallengeExternalDto(
    val challenge: String?,
    val ts: Long?,
)