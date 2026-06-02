package chat.sphinx.wrapper_common.datasync

import chat.sphinx.wrapper_common.DateTime
import chat.sphinx.wrapper_common.toDateTime
import com.squareup.moshi.JsonClass

data class SettingItem(
    val key: String,
    val identifier: String,
    val date: String,
    val value: DataSyncJson
) {
    val dateTime: DateTime
        get() = (date.toDoubleOrNull()?.times(1000)?.toLong() ?: System.currentTimeMillis()).toDateTime()
}