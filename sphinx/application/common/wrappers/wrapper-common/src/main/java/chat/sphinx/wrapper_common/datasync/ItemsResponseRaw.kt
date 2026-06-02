package chat.sphinx.wrapper_common.datasync

import com.squareup.moshi.*

@JsonClass(generateAdapter = true)
data class ItemsResponseRaw(
    val items: List<SettingItemRaw>
)

data class SettingItemRaw(
    val key: String,
    val identifier: String,
    val date: String,
    val value: Any
)
