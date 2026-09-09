package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveTasksListDto(
    @Json(name = "data") val data: List<HiveTaskDto>? = null,
    @Json(name = "pagination") val pagination: HiveFeaturesPaginationDto? = null,
    @Json(name = "error") val error: String? = null,
)
