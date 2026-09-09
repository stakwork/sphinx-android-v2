package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveFeatureUpdateDto(
    @Json(name = "data") val data: HiveFeatureDto? = null,
    @Json(name = "error") val error: String? = null,
)
