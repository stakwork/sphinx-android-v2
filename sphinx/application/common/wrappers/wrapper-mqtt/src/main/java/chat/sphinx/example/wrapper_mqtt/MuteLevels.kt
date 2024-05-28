package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class MuteLevels(
    val muteLevels: Map<String, Int>
) {
    companion object {
        fun String.toMuteLevelsMap(moshi: Moshi): Map<String, Int>? {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Integer::class.java)
            val adapter: JsonAdapter<Map<String, Int>> = moshi.adapter(type)

            return try {
                adapter.fromJson(this)
            } catch (e: Exception) {
                null
            }
        }
    }
}
