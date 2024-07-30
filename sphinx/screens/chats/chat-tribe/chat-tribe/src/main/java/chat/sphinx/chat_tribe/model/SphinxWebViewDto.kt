package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SphinxWebViewDto(
    val application: String,
    val type: String,
    val challenge: String?,
    val paymentRequest: String?,
    val macaroon: String?,
    val issuer: String?,
    val dest: String?,
    val amt: Int?,
    val message: String?
) {
    companion object {
        const val APPLICATION_NAME = "Sphinx"

        const val TYPE_AUTHORIZE = "AUTHORIZE"
        const val TYPE_GET_LSAT = "GETLSAT"
        const val TYPE_KEYSEND = "KEYSEND"
        const val TYPE_SET_BUDGET = "SETBUDGET"
        const val TYPE_LSAT = "LSAT"
        const val TYPE_UPDATE_LSAT = "UPDATELSAT"
        const val TYPE_GET_BUDGET = "GETBUDGET"
        const val TYPE_PAYMENT = "PAYMENT"
        const val TYPE_SIGN = "SIGN"
        const val TYPE_GET_PERSON_DATA = "GETPERSONDATA"
    }
}