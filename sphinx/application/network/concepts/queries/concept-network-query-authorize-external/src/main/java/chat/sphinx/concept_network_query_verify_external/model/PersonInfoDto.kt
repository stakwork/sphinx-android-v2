package chat.sphinx.concept_network_query_verify_external.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PersonInfoDto(
    val id: Int? = null,
    val owner_pubkey: String,
    val owner_alias: String,
    val owner_route_hint: String?,
    val img: String?,
    val description: String? = null,
    val price_to_meet: Long? = null
)