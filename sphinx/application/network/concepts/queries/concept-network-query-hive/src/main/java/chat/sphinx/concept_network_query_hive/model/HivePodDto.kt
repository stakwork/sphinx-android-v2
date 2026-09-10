package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HivePodDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "subdomain") val subdomain: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "internal_state") val internalState: String? = null,
    @Json(name = "usage_status") val usageStatus: String? = null,
    @Json(name = "resource_usage") val resourceUsage: HivePodResourceUsageDto? = null,
)
