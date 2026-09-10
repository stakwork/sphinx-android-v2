package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HivePoolStatusDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: HivePoolStatusDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class HivePoolStatusDataDto(
    @Json(name = "status") val status: HivePoolStatusCountsDto? = null,
)

@JsonClass(generateAdapter = true)
data class HivePoolStatusCountsDto(
    @Json(name = "queuedCount") val queuedCount: Int? = null,
    @Json(name = "unusedVms") val unusedVms: Int? = null,
)
