package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveFeaturePatchDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "priority") val priority: String? = null,
)
