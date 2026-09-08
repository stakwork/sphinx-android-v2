package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspaceImageDto(
    @Json(name = "presignedUrl") val presignedUrl: String,
    @Json(name = "expiresIn") val expiresIn: Long,
)
