package chat.sphinx.wrapper_common.datasync

import chat.sphinx.wrapper_common.toDateTime
import com.squareup.moshi.*

@JsonClass(generateAdapter = true)
data class ItemsResponse(
    val items: List<SettingItem>
) {
    @Throws(AssertionError::class)
    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(ItemsResponse::class.java)
        return adapter.toJson(this)
    }

    fun toOriginalFormatJson(moshi: Moshi): String? {
        return try {
            toJson(moshi)
        } catch (e: Exception) {
            null
        }
    }

    fun getItemIndex(key: String, identifier: String): Int =
        items.indexOfFirst { it.key == key && it.identifier == identifier }

    companion object {
        fun String.toItemsResponseNull(moshi: Moshi): ItemsResponse? {
            return try {
                this.toItemsResponse(moshi)
            } catch (e: Exception) {
                null
            }
        }

        @Throws(JsonDataException::class)
        fun String.toItemsResponse(moshi: Moshi): ItemsResponse {
            val adapter = moshi.adapter(ItemsResponse::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for ItemsResponse")
        }
    }
}