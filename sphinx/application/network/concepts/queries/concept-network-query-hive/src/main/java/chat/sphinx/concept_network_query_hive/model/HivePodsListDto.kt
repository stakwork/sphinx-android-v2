package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HivePodsListDto(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: HivePodsListDataDto? = null,
)

@JsonClass(generateAdapter = true)
data class HivePodsListDataDto(
    @Json(name = "workspaces") val workspaces: List<HivePodDto>? = null,
)
