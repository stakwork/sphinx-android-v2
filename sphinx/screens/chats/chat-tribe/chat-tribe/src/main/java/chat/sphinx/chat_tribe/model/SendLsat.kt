package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendLsat(
    val type: String,
    val application: String,
    val password: String,
    val macaroon: String?,
    val paymentRequest: String?,
    val preimage: String?,
    val identifier: String?,
    val paths: String?,
    val status: String?,
    val success: Boolean
)


@Throws(AssertionError::class)
fun SendLsat.toJson(moshi: Moshi): String =
    moshi.adapter(SendLsat::class.java)
        .toJson(
            SendLsat(
                type,
                application,
                password,
                macaroon,
                paymentRequest,
                preimage,
                identifier,
                paths,
                status,
                success
            )
        )