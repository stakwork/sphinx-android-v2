package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class TimezoneSetting(
    @Json(name = "timezone_enabled") val timezoneEnabled: Boolean,
    @Json(name = "timezone_identifier") val timezoneIdentifier: String
) {
    @Throws(AssertionError::class)
    fun toJson(moshi: Moshi): String {
        val adapter = moshi.adapter(TimezoneSetting::class.java)
        return adapter.toJson(this)
    }

    companion object {
        fun String.toTimezoneSettingNull(moshi: Moshi): TimezoneSetting? {
            return try {
                this.toTimezoneSetting(moshi)
            } catch (e: Exception) {
                null
            }
        }

        @Throws(JsonDataException::class)
        fun String.toTimezoneSetting(moshi: Moshi): TimezoneSetting {
            val adapter = moshi.adapter(TimezoneSetting::class.java)
            return adapter.fromJson(this) ?: throw IllegalArgumentException("Invalid JSON for TimezoneSetting")
        }
    }
}