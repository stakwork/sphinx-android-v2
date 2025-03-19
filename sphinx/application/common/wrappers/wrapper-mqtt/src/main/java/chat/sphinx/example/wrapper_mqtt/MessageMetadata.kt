package chat.sphinx.example.wrapper_mqtt

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class MessageMetadata(
    val tz: String
) {
    @Throws(AssertionError::class)
    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(MessageMetadata::class.java)
        return adapter.toJson(this)
    }

    companion object {
        @Throws(AssertionError::class)
        fun String.toMessageMetadata(moshi: Moshi): MessageMetadata? {
            return try {
                this.toMetadata(moshi)
            } catch (e: Exception) {
                null
            }
        }

        @Throws(JsonDataException::class)
        private fun String.toMetadata(moshi: Moshi): MessageMetadata {
            val adapter = moshi.adapter(MessageMetadata::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for Message")
        }
    }
}
