package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class MessageTag(
    val tag: String?,
    val ts: Long?,
    val status: String?,
    val error: String?
)

@JsonClass(generateAdapter = true)
data class TagMessageList(
    val tags: List<MessageTag>
) {
    companion object {
        fun String.toTagsList(moshi: Moshi): List<MessageTag>? {
            val type = Types.newParameterizedType(List::class.java, MessageTag::class.java)
            val adapter = moshi.adapter<List<MessageTag>>(type)
            return try {
                adapter.fromJson(this)
            } catch (e: Exception) {
                null
            }
        }
    }
}
