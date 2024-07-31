package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendGetPersonData(
    val type: String,
    val application: String,
    val password: String,
    val alias: String,
    val photoUrl: String,
    val publicKey: String
)
@Throws(AssertionError::class)
fun SendGetPersonData.toJson(moshi: Moshi): String =
    moshi.adapter(SendGetPersonData::class.java)
        .toJson(
            SendGetPersonData(
                type,
                application,
                password,
                alias,
                photoUrl,
                publicKey
            )
        )