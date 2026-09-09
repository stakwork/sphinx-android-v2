package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HiveFeaturesListDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: List<HiveFeatureDto>? = null,
    @Json(name = "pagination") val pagination: HiveFeaturesPaginationDto? = null,
)

@JsonClass(generateAdapter = true)
data class HiveFeaturesPaginationDto(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "totalPages") val totalPages: Int? = null,
    @Json(name = "totalCount") val totalCount: Int? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null,
)
