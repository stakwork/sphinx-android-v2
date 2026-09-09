package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveTaskDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HiveTaskDto deserializes required-only id and title without defaulting status or flags`() {
        val json = """{"id":"task-1","title":"Ship Hive tasks"}"""
        val dto = moshi.adapter(HiveTaskDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("task-1", dto!!.id)
        assertEquals("Ship Hive tasks", dto.title)
        assertNull(dto.status)
        assertNull(dto.priority)
        assertNull(dto.archived)
        assertNull(dto.autoMerge)
        assertNull(dto.runBuild)
        assertNull(dto.runTestSuite)
        assertNull(dto.dependsOnTaskIds)
        assertNull(dto.featureId)
        assertNull(dto.workflowStatus)
    }

    @Test
    fun `HiveTaskDto ignores unknown nested keys`() {
        val json = """
            {
              "id":"task-2",
              "title":"Full task",
              "status":"IN_PROGRESS",
              "priority":"HIGH",
              "unknownTop":true,
              "assignee":{"id":"user-1","role":"owner"},
              "createdBy":{"id":"user-2","extra":1},
              "feature":{"id":"feat-1","title":"Parent","unknown":"x"},
              "phase":{"id":"ph-1","name":"Build"},
              "comments":[{"id":"c-1"}]
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveTaskDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("task-2", dto!!.id)
        assertEquals("Full task", dto.title)
        assertEquals("IN_PROGRESS", dto.status)
        assertEquals("HIGH", dto.priority)
        assertEquals("user-1", dto.assignee!!.id)
        assertEquals("feat-1", dto.feature!!.id)
        assertEquals("ph-1", dto.phase!!.id)
    }

    @Test
    fun `HiveTasksListDto error envelope has no success flag`() {
        val json = """{"error":"cannot list","data":null}"""
        val dto = moshi.adapter(HiveTasksListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("cannot list", dto!!.error)
        assertNull(dto.data)
        assertNull(dto.pagination)
    }

    @Test
    fun `HiveTasksListDto success payload has data and pagination`() {
        val json = """
            {
              "data":[{"id":"task-1","title":"One"}],
              "pagination":{"page":2,"totalPages":4,"totalCount":40,"hasMore":true}
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveTasksListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertNull(dto!!.error)
        assertEquals(1, dto.data!!.size)
        assertEquals("task-1", dto.data!![0].id)
        assertEquals(2, dto.pagination!!.page)
        assertEquals(4, dto.pagination!!.totalPages)
        assertEquals(40, dto.pagination!!.totalCount)
        assertTrue(dto.pagination!!.hasMore!!)
    }

    @Test
    fun `HiveTasksListDto missing data and error is a sparse success envelope`() {
        val json = """{"pagination":{"page":1}}"""
        val dto = moshi.adapter(HiveTasksListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertNull(dto!!.error)
        assertNull(dto.data)
        assertEquals(1, dto.pagination!!.page)
    }

    @Test
    fun `HiveTaskMutationDto success plus data envelope`() {
        val json = """{"success":true,"data":{"id":"task-9","title":"Updated"}}"""
        val dto = moshi.adapter(HiveTaskMutationDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertEquals("task-9", dto.data!!.id)
        assertEquals("Updated", dto.data!!.title)
    }

    @Test
    fun `HiveTaskMutationDto success without data`() {
        val json = """{"success":true}"""
        val dto = moshi.adapter(HiveTaskMutationDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertNull(dto.data)
    }

    @Test
    fun `HiveTaskMutationDto success false is explicit failure`() {
        val json = """{"success":false,"data":null}"""
        val dto = moshi.adapter(HiveTaskMutationDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertFalse(dto!!.success)
        assertNull(dto.data)
    }

    @Test
    fun `repository prefers repositoryUrl over url`() {
        val json = """
            {
              "id":"task-3",
              "title":"Repo quirk",
              "repository":{
                "id":"repo-1",
                "name":"sphinx",
                "url":"https://example.com/fallback.git",
                "repositoryUrl":"https://example.com/preferred.git"
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveTaskDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("https://example.com/preferred.git", dto!!.repository!!.repositoryUrl)
        assertEquals("https://example.com/fallback.git", dto.repository!!.url)
    }

    @Test
    fun `repository falls back to url when repositoryUrl is absent`() {
        val json = """
            {
              "id":"task-4",
              "title":"Repo fallback",
              "repository":{"id":"repo-2","url":"https://example.com/only-url.git"}
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveTaskDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertNull(dto!!.repository!!.repositoryUrl)
        assertEquals("https://example.com/only-url.git", dto.repository!!.url)
    }

    @Test
    fun `prArtifact nested content and top-level pr fields are both parsed`() {
        val json = """
            {
              "id":"task-5",
              "title":"PR fields",
              "prUrl":"https://github.com/org/repo/pull/1",
              "prStatus":"OPEN",
              "prArtifact":{
                "id":"art-1",
                "content":{"url":"https://github.com/org/repo/pull/9","status":"MERGED","number":9}
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveTaskDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("https://github.com/org/repo/pull/1", dto!!.prUrl)
        assertEquals("OPEN", dto.prStatus)
        assertEquals("art-1", dto.prArtifact!!.id)
        assertEquals("https://github.com/org/repo/pull/9", dto.prArtifact!!.content!!.url)
        assertEquals("MERGED", dto.prArtifact!!.content!!.status)
        assertEquals(9, dto.prArtifact!!.content!!.number)
    }

    @Test
    fun `HiveTaskPatchDto omits null fields`() {
        val json = moshi.adapter(HiveTaskPatchDto::class.java).toJson(
            HiveTaskPatchDto(startWorkflow = true)
        )
        assertEquals("""{"startWorkflow":true}""", json)
    }

    @Test
    fun `HiveTaskPatchDto serializes only status when other fields are null`() {
        val json = moshi.adapter(HiveTaskPatchDto::class.java).toJson(
            HiveTaskPatchDto(status = "DONE")
        )
        assertEquals("""{"status":"DONE"}""", json)
        assertFalse(json.contains("startWorkflow"))
        assertFalse(json.contains("retryWorkflow"))
        assertFalse(json.contains("archived"))
        assertFalse(json.contains("autoMerge"))
    }

    @Test
    fun `HiveTaskDuplicateDto always includes title status autoMerge and priority`() {
        val json = moshi.adapter(HiveTaskDuplicateDto::class.java).toJson(
            HiveTaskDuplicateDto(title = "Copy", priority = "LOW")
        )
        assertTrue(json.contains(""""title":"Copy""""))
        assertTrue(json.contains(""""status":"TODO""""))
        assertTrue(json.contains(""""autoMerge":false"""))
        assertTrue(json.contains(""""priority":"LOW""""))
        assertFalse(json.contains("description"))
        assertFalse(json.contains("phaseId"))
        assertFalse(json.contains("repositoryId"))
        assertFalse(json.contains("dependsOnTaskIds"))
        assertFalse(json.contains("workflowId"))
        assertFalse(json.contains("workflowName"))
        assertFalse(json.contains("workflowRefId"))
        assertFalse(json.contains("workflowTaskType"))
        assertFalse(json.contains("workflowVersionId"))
    }

    @Test
    fun `HiveTaskDuplicateDto includes optional fields when present`() {
        val json = moshi.adapter(HiveTaskDuplicateDto::class.java).toJson(
            HiveTaskDuplicateDto(
                title = "Copy",
                priority = "HIGH",
                description = "Details",
                phaseId = "ph-1",
                repositoryId = "repo-1",
                dependsOnTaskIds = listOf("task-a"),
                workflowId = "wf-1",
                workflowName = "Build",
                workflowRefId = "ref-1",
                workflowTaskType = "TICKET",
                workflowVersionId = "v-1",
            )
        )
        assertTrue(json.contains(""""description":"Details""""))
        assertTrue(json.contains(""""phaseId":"ph-1""""))
        assertTrue(json.contains(""""repositoryId":"repo-1""""))
        assertTrue(json.contains(""""dependsOnTaskIds":["task-a"]"""))
        assertTrue(json.contains(""""workflowId":"wf-1""""))
        assertTrue(json.contains(""""workflowName":"Build""""))
        assertTrue(json.contains(""""workflowRefId":"ref-1""""))
        assertTrue(json.contains(""""workflowTaskType":"TICKET""""))
        assertTrue(json.contains(""""workflowVersionId":"v-1""""))
        assertTrue(json.contains(""""status":"TODO""""))
        assertTrue(json.contains(""""autoMerge":false"""))
        assertTrue(json.contains(""""priority":"HIGH""""))
    }

    @Test
    fun `HiveTaskDependsOnPatchDto serializes dependsOnTaskIds`() {
        val json = moshi.adapter(HiveTaskDependsOnPatchDto::class.java).toJson(
            HiveTaskDependsOnPatchDto(dependsOnTaskIds = listOf("a", "b"))
        )
        assertEquals("""{"dependsOnTaskIds":["a","b"]}""", json)
    }
}
