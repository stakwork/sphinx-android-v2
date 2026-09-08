package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspaceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "userRole") val userRole: String? = null,
    @Json(name = "memberCount") val memberCount: Int = 0,
    @Json(name = "ownerId") val ownerId: String? = null,
    @Json(name = "logoUrl") val logoUrl: String? = null,
    @Json(name = "logoKey") val logoKey: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null,
    @Json(name = "lastAccessedAt") val lastAccessedAt: String? = null,
)
