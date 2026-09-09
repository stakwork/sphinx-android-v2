package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveFeatureDto(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "status") val status: String? = null,
    @Json(name = "priority") val priority: String? = null,
)
