package chat.sphinx.concept_repository_dashboard.model

data class HiveFeature(
    val id: String,
    val title: String,
    val status: String?,
    val priority: String?,
)

data class HiveFeaturesPage(
    val features: List<HiveFeature>,
    val page: Int,
    val hasMore: Boolean,
    val totalPages: Int,
    val totalCount: Int,
)
