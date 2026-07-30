package chat.sphinx.feature_repository.mappers.hive

import chat.sphinx.concept_network_query_hive.model.WorkspaceDto
import chat.sphinx.concept_repository_dashboard.model.Workspace

fun WorkspaceDto.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    logoUrl = logoUrl,
    userRole = userRole,
    memberCount = memberCount,
)
