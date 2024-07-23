package chat.sphinx.concept_network_query_verify_external.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VerifyExternalInfoDto(
    val pubkey: String?,
    val alias: String?,
    val photo_url: String?,
    val route_hint: String?,
    val price_to_meet: Long?,
    val verification_signature: String?,
) {
}