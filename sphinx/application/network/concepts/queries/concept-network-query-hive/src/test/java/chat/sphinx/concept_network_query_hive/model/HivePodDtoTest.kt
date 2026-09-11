package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HivePodDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HivePodsListDto parses snake_case pod fields and string cpu memory`() {
        val json = """
            {
              "success": true,
              "data": {
                "pool_name": "workspace-pool",
                "workspaces": [
                  {
                    "id": "pod-1",
                    "subdomain": "alpha",
                    "state": "running",
                    "internal_state": "ready",
                    "usage_status": "used",
                    "resource_usage": {
                      "available": true,
                      "requests": { "cpu": "100m", "memory": "128Mi" },
                      "usage": { "cpu": "50m", "memory": "64Mi" }
                    },
                    "user_info": { "name": "ignored" },
                    "marked_at": "2026-01-01T00:00:00Z",
                    "repoName": "ignored",
                    "warning": "ignored"
                  }
                ]
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePodsListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        val pod = dto.data!!.workspaces!![0]
        assertEquals("pod-1", pod.id)
        assertEquals("alpha", pod.subdomain)
        assertEquals("running", pod.state)
        assertEquals("ready", pod.internalState)
        assertEquals("used", pod.usageStatus)
        assertEquals(true, pod.resourceUsage!!.available)
        assertEquals("100m", pod.resourceUsage!!.requests!!.cpu)
        assertEquals("128Mi", pod.resourceUsage!!.requests!!.memory)
        assertEquals("50m", pod.resourceUsage!!.usage!!.cpu)
        assertEquals("64Mi", pod.resourceUsage!!.usage!!.memory)
        assertTrue(pod.resourceUsage!!.requests!!.cpu is String)
        assertTrue(pod.resourceUsage!!.requests!!.memory is String)
    }

    @Test
    fun `HivePodsListDto incomplete pod row still parses without failing the list`() {
        val json = """
            {
              "success": true,
              "data": {
                "workspaces": [
                  { "id": "pod-ok", "subdomain": "beta", "state": "pending" },
                  { "subdomain": "missing-id", "state": "running" },
                  { "id": "pod-no-sub", "state": "failed" },
                  { "id": "pod-no-state", "subdomain": "gamma" },
                  {}
                ]
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePodsListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals(5, dto!!.data!!.workspaces!!.size)

        val incomplete = dto.data!!.workspaces!![1]
        assertNull(incomplete.id)
        assertEquals("missing-id", incomplete.subdomain)
        assertEquals("running", incomplete.state)

        val empty = dto.data!!.workspaces!![4]
        assertNull(empty.id)
        assertNull(empty.subdomain)
        assertNull(empty.state)
    }

    @Test
    fun `HivePodsListDto omits pool_name without failing`() {
        val json = """{"success":true,"data":{"workspaces":[]}}"""
        val dto = moshi.adapter(HivePodsListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertNotNull(dto.data!!.workspaces)
        assertTrue(dto.data!!.workspaces!!.isEmpty())
    }

    @Test
    fun `HivePodResourceUsageDto cpu and memory are strings not Ints`() {
        val json = """
            {
              "available": true,
              "requests": { "cpu": "100m", "memory": "128Mi" },
              "usage": { "cpu": "50m", "memory": "64Mi" }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePodResourceUsageDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("100m", dto!!.requests!!.cpu)
        assertEquals("128Mi", dto.requests!!.memory)
        assertEquals("50m", dto.usage!!.cpu)
        assertEquals("64Mi", dto.usage!!.memory)
        assertTrue(dto.requests!!.cpu is String)
        assertTrue(dto.requests!!.memory is String)
        assertTrue(dto.usage!!.cpu is String)
        assertTrue(dto.usage!!.memory is String)
    }
}
