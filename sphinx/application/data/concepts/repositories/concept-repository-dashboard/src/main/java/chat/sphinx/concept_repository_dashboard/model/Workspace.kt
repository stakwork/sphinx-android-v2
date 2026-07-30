package chat.sphinx.concept_repository_dashboard.model

data class Workspace(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val userRole: String?,
    val memberCount: Int,
)
