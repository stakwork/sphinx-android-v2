package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendAuthorization(
    val type: String,
    val application: String,
    val password: String,
    val pubkey: String,
)
@Throws(AssertionError::class)
fun SendAuthorization.toJson(moshi: Moshi): String =
    moshi.adapter(SendAuthorization::class.java)
        .toJson(
            SendAuthorization(
                type,
                application,
                password,
                pubkey
            )
        )