package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.lang.IllegalArgumentException

@JsonClass(generateAdapter = true)
data class NewSentStatus(
    val tag: String?,
    val status: String?,
    val preimage: String?,
    val payment_hash: String?,
    val message: String?
) {


    companion object {
        @Throws(JsonDataException::class, IllegalArgumentException::class)
        fun String.toNewSentStatus(moshi: Moshi): NewSentStatus {
            val adapter = moshi.adapter(NewSentStatus::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for NewSentStatus")
        }
    }

    fun isFailedMessage(): Boolean {
        return !(status?.contains("COMPLETE") == true || status?.contains("PENDING") == true)
    }

}
