package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendSign(
    val type: String,
    val application: String,
    val password: String,
    val signature: String,
    val success: Boolean
)

@Throws(AssertionError::class)
fun SendSign.toJson(moshi: Moshi): String =
    moshi.adapter(SendSign::class.java)
        .toJson(
            SendSign(
                type,
                application,
                password,
                signature,
                success
            )
        )