package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.model.HiveTaskDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskFeatureDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPersonDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPhaseDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPrArtifactContentDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskPrArtifactDto
import chat.sphinx.concept_network_query_hive.model.HiveTaskRepositoryDto
import chat.sphinx.feature_repository.mappers.hive.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveTaskDtoMapperTest {

    @Test
    fun `sparse DTO does not default status or flags`() {
        val domain = HiveTaskDto(id = "task-1", title = "Bare").toDomain()

        assertEquals("task-1", domain.id)
        assertEquals("Bare", domain.title)
        assertNull(domain.status)
        assertNull(domain.priority)
        assertNull(domain.autoMerge)
        assertNull(domain.runBuild)
        assertNull(domain.runTestSuite)
        assertFalse(domain.archived)
        assertTrue(domain.dependsOnTaskIds.isEmpty())
        assertEquals(0, domain.chatMessageCount)
        assertNull(domain.featureId)
        assertNull(domain.phaseId)
        assertNull(domain.prUrl)
        assertNull(domain.prStatus)
    }

    @Test
    fun `featureId prefers top-level then nested feature id`() {
        val nested = HiveTaskDto(
            id = "task-2",
            title = "Nested",
            feature = HiveTaskFeatureDto(id = "feat-nested", title = "Parent"),
        ).toDomain()
        assertEquals("feat-nested", nested.featureId)
        assertEquals("Parent", nested.featureTitle)

        val topLevel = HiveTaskDto(
            id = "task-3",
            title = "Top",
            featureId = "feat-top",
            feature = HiveTaskFeatureDto(id = "feat-nested", title = "Parent"),
        ).toDomain()
        assertEquals("feat-top", topLevel.featureId)
        assertEquals("Parent", topLevel.featureTitle)
    }

    @Test
    fun `phaseId prefers top-level then nested phase id`() {
        val nested = HiveTaskDto(
            id = "task-4",
            title = "Phase nested",
            phase = HiveTaskPhaseDto(id = "ph-nested"),
        ).toDomain()
        assertEquals("ph-nested", nested.phaseId)

        val topLevel = HiveTaskDto(
            id = "task-5",
            title = "Phase top",
            phaseId = "ph-top",
            phase = HiveTaskPhaseDto(id = "ph-nested"),
        ).toDomain()
        assertEquals("ph-top", topLevel.phaseId)
    }

    @Test
    fun `repositoryUrl prefers repositoryUrl then url`() {
        val preferred = HiveTaskDto(
            id = "task-6",
            title = "Repo preferred",
            repository = HiveTaskRepositoryDto(
                id = "repo-1",
                name = "sphinx",
                url = "https://example.com/fallback.git",
                repositoryUrl = "https://example.com/preferred.git",
            ),
        ).toDomain()
        assertEquals("repo-1", preferred.repositoryId)
        assertEquals("sphinx", preferred.repositoryName)
        assertEquals("https://example.com/preferred.git", preferred.repositoryUrl)

        val fallback = HiveTaskDto(
            id = "task-7",
            title = "Repo fallback",
            repository = HiveTaskRepositoryDto(
                id = "repo-2",
                url = "https://example.com/only-url.git",
            ),
        ).toDomain()
        assertEquals("https://example.com/only-url.git", fallback.repositoryUrl)
    }

    @Test
    fun `prUrl and prStatus prefer prArtifact content then artifact then top-level`() {
        val fromContent = HiveTaskDto(
            id = "task-8",
            title = "PR content",
            prUrl = "https://github.com/org/repo/pull/1",
            prStatus = "OPEN",
            prArtifact = HiveTaskPrArtifactDto(
                id = "art-1",
                url = "https://github.com/org/repo/pull/2",
                status = "DRAFT",
                number = 2,
                content = HiveTaskPrArtifactContentDto(
                    url = "https://github.com/org/repo/pull/9",
                    status = "MERGED",
                    number = 9,
                ),
            ),
        ).toDomain()
        assertEquals("art-1", fromContent.prArtifactId)
        assertEquals("https://github.com/org/repo/pull/9", fromContent.prUrl)
        assertEquals("MERGED", fromContent.prStatus)
        assertEquals(9, fromContent.prNumber)

        val fromArtifact = HiveTaskDto(
            id = "task-9",
            title = "PR artifact",
            prUrl = "https://github.com/org/repo/pull/1",
            prStatus = "OPEN",
            prArtifact = HiveTaskPrArtifactDto(
                id = "art-2",
                url = "https://github.com/org/repo/pull/2",
                status = "DRAFT",
                number = 2,
            ),
        ).toDomain()
        assertEquals("https://github.com/org/repo/pull/2", fromArtifact.prUrl)
        assertEquals("DRAFT", fromArtifact.prStatus)
        assertEquals(2, fromArtifact.prNumber)

        val fromTopLevel = HiveTaskDto(
            id = "task-10",
            title = "PR top-level",
            prUrl = "https://github.com/org/repo/pull/1",
            prStatus = "OPEN",
        ).toDomain()
        assertEquals("https://github.com/org/repo/pull/1", fromTopLevel.prUrl)
        assertEquals("OPEN", fromTopLevel.prStatus)
        assertNull(fromTopLevel.prNumber)
    }

    @Test
    fun `maps nested people and workflow fields without inventing defaults`() {
        val domain = HiveTaskDto(
            id = "task-11",
            title = "Full",
            description = "Details",
            status = "TODO",
            priority = "HIGH",
            workflowStatus = "HALTED",
            archived = true,
            assignee = HiveTaskPersonDto(
                id = "user-1",
                name = "Ada",
                email = "ada@example.com",
                image = "https://img/ada.png",
            ),
            createdBy = HiveTaskPersonDto(
                id = "user-2",
                name = "Bob",
                email = "bob@example.com",
                image = "https://img/bob.png",
            ),
            dependsOnTaskIds = listOf("dep-1"),
            workflowId = "wf-1",
            workflowName = "Build",
            workflowRefId = "ref-1",
            workflowTaskType = "TICKET",
            workflowVersionId = "v-1",
            workspaceId = "ws-1",
            autoMerge = false,
            runBuild = true,
            runTestSuite = false,
        ).toDomain()

        assertEquals("TODO", domain.status)
        assertEquals("HIGH", domain.priority)
        assertEquals("HALTED", domain.workflowStatus)
        assertTrue(domain.archived)
        assertEquals("user-1", domain.assigneeId)
        assertEquals("Ada", domain.assigneeName)
        assertEquals("ada@example.com", domain.assigneeEmail)
        assertEquals("https://img/ada.png", domain.assigneeImage)
        assertEquals("user-2", domain.createdById)
        assertEquals("Bob", domain.createdByName)
        assertEquals(listOf("dep-1"), domain.dependsOnTaskIds)
        assertEquals("wf-1", domain.workflowId)
        assertEquals("Build", domain.workflowName)
        assertEquals("ref-1", domain.workflowRefId)
        assertEquals("TICKET", domain.workflowTaskType)
        assertEquals("v-1", domain.workflowVersionId)
        assertEquals("ws-1", domain.workspaceId)
        assertEquals(false, domain.autoMerge)
        assertEquals(true, domain.runBuild)
        assertEquals(false, domain.runTestSuite)
    }
}
