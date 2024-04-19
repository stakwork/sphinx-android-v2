package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types


@JsonClass(generateAdapter = true)
data class LastReadMessages(
    val lastRead: Map<String, Long>
) {
    companion object {
        fun String.toLastReadMap(moshi: Moshi): Map<String, Long>? {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, java.lang.Long::class.java)
            val adapter: JsonAdapter<Map<String, Long>> = moshi.adapter(type)

            return try {
                adapter.fromJson(this)
            } catch (e: Exception) {
                null
            }
        }
    }
}
