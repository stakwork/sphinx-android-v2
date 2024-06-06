package chat.sphinx.concept_network_query_contact.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccountConfigV2(
    val tribe: String,
    val tribe_host: String,
    val router: String,
    val default_lsp: String
)