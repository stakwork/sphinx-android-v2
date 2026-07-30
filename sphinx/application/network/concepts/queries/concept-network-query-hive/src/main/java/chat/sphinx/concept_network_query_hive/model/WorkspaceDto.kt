package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkspaceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "user_role") val userRole: String? = null,
    @Json(name = "member_count") val memberCount: Int = 0,
    @Json(name = "owner_id") val ownerId: String? = null,
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "logo_key") val logoKey: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "last_accessed_at") val lastAccessedAt: String? = null,
)
