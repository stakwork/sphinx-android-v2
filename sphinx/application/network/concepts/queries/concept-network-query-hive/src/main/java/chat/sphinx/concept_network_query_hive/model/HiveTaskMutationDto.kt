package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveTaskMutationDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: HiveTaskDto? = null,
)
