package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendGetBudget(
    val type: String,
    val application: String,
    val password: String,
    val budget: Long,
    val success: Boolean,
)
@Throws(AssertionError::class)
fun SendGetBudget.toJson(moshi: Moshi): String =
    moshi.adapter(SendGetBudget::class.java)
        .toJson(
            SendGetBudget(
                type,
                application,
                password,
                budget,
                success
            )
        )