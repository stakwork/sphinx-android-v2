package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class MsgsCounts(
    val total: Long?,
    val ok_key: Long?,
    val first_for_each_scid: Long?,
    val total_highest_index: Long?,
    val ok_key_highest_index: Long?,
    val first_for_each_scid_highest_index: Long?
) {
    companion object {
        fun String.toMsgsCounts(moshi: Moshi): MsgsCounts? {
            return try {
                val adapter = moshi.adapter(MsgsCounts::class.java)
                adapter.fromJson(this)
            } catch (e: Exception) {
                null
            }
        }
    }
}