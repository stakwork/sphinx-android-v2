package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class Payment(
    val scid: Long?,
    val amt_msat: Long?,
    val rhash: String?,
    val ts: Long?,
    val remote: Boolean?,
    val msg_idx: Long?,
    val error: String?
) {
    companion object {
        fun String.toPaymentsList(moshi: Moshi): List<Payment>? {
            val type = Types.newParameterizedType(List::class.java, Payment::class.java)
            val adapter: JsonAdapter<List<Payment>> = moshi.adapter(type)

            return try {
                adapter.fromJson(this)
            } catch (e: Exception) {
                null
            }
        }
    }
}
