package chat.sphinx.chat_tribe.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class SendSecondBrainListData(
    val type: String,
    val application: String,
    val password: String,
    val secondBrainList: List<String>,
)
@Throws(AssertionError::class)
fun SendSecondBrainListData.toJson(moshi: Moshi): String =
    moshi.adapter(SendSecondBrainListData::class.java)
        .toJson(
            SendSecondBrainListData(
                type,
                application,
                password,
                secondBrainList
            )
        )