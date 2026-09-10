package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HivePodResourceUsageDto(
    @Json(name = "available") val available: Boolean? = null,
    @Json(name = "requests") val requests: HivePodResourceQuantityDto? = null,
    @Json(name = "usage") val usage: HivePodResourceQuantityDto? = null,
)

@JsonClass(generateAdapter = true)
data class HivePodResourceQuantityDto(
    @Json(name = "cpu") val cpu: String? = null,
    @Json(name = "memory") val memory: String? = null,
)
