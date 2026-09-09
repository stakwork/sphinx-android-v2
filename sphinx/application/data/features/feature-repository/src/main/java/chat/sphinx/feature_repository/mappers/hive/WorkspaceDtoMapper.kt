package chat.sphinx.feature_repository.mappers.hive

import chat.sphinx.concept_network_query_hive.model.HiveFeatureDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceDto
import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import chat.sphinx.concept_repository_dashboard.model.Workspace

fun WorkspaceDto.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    logoUrl = null,
    userRole = userRole,
    memberCount = memberCount,
    slug = slug,
)

fun HiveFeatureDto.toDomain(): HiveFeature = HiveFeature(
    id = id,
    title = title,
    status = status,
    priority = priority,
)
