package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi


@JsonClass(generateAdapter = true)
data class SendBudget(
    val type: String,
    val application: String,
    val password: String,
    val pubkey: String,
    val signature: String,
    val budget: Long,
)
@Throws(AssertionError::class)
fun SendBudget.toJson(moshi: Moshi): String =
    moshi.adapter(SendBudget::class.java)
        .toJson(
            SendBudget(
                type,
                application,
                password,
                pubkey,
                signature,
                budget
            )
        )