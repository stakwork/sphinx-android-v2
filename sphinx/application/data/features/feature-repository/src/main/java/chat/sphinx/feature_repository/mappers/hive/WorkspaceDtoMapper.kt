package chat.sphinx.feature_repository.mappers.hive

import chat.sphinx.concept_network_query_hive.model.HiveFeatureDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskDto
import chat.sphinx.concept_network_query_hive.model.WorkspaceDto
import chat.sphinx.concept_repository_dashboard.model.HiveFeature
import chat.sphinx.concept_repository_dashboard.model.HiveTask
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

fun HiveTaskDto.toDomain(): HiveTask = HiveTask(
    id = id,
    title = title,
    description = description,
    status = status,
    priority = priority,
    workflowStatus = workflowStatus,
    archived = archived ?: false,
    sourceType = sourceType,
    mode = mode,
    podId = podId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    featureId = featureId ?: feature?.id,
    featureTitle = feature?.title,
    assigneeId = assignee?.id,
    assigneeName = assignee?.name,
    assigneeEmail = assignee?.email,
    assigneeImage = assignee?.image,
    repositoryId = repository?.id,
    repositoryName = repository?.name,
    repositoryUrl = repository?.repositoryUrl ?: repository?.url,
    createdById = createdBy?.id,
    createdByName = createdBy?.name,
    createdByEmail = createdBy?.email,
    createdByImage = createdBy?.image,
    chatMessageCount = chatMessageCount ?: 0,
    prArtifactId = prArtifact?.id,
    prUrl = prArtifact?.content?.url ?: prArtifact?.url ?: prUrl,
    prStatus = prArtifact?.content?.status ?: prArtifact?.status ?: prStatus,
    prNumber = prArtifact?.content?.number ?: prArtifact?.number,
    stakworkProjectId = stakworkProjectId,
    deploymentStatus = deploymentStatus,
    deployedToProductionAt = deployedToProductionAt,
    systemAssigneeType = systemAssigneeType,
    autoMerge = autoMerge,
    runBuild = runBuild,
    runTestSuite = runTestSuite,
    dependsOnTaskIds = dependsOnTaskIds ?: emptyList(),
    workflowId = workflowId,
    workflowName = workflowName,
    workflowRefId = workflowRefId,
    workflowTaskType = workflowTaskType,
    workflowVersionId = workflowVersionId,
    workspaceId = workspaceId,
    phaseId = phaseId ?: phase?.id,
)
