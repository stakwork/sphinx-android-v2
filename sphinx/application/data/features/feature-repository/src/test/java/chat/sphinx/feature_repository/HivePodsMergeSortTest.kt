package chat.sphinx.feature_repository

import chat.sphinx.concept_repository_dashboard.model.HivePod
import chat.sphinx.concept_repository_dashboard.model.HivePodResourceUsage
import chat.sphinx.concept_repository_dashboard.model.mergeHivePods
import chat.sphinx.concept_repository_dashboard.model.sortHivePods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HivePodsMergeSortTest {

    private fun pod(
        id: String,
        subdomain: String = id,
        state: String,
        internalState: String? = null,
        usageStatus: String? = null,
        resourceUsage: HivePodResourceUsage? = null,
    ) = HivePod(
        id = id,
        subdomain = subdomain,
        state = state,
        internalState = internalState,
        usageStatus = usageStatus,
        resourceUsage = resourceUsage,
    )

    private fun usage() = HivePodResourceUsage(
        available = true,
        requestsCpu = "100m",
        requestsMemory = "128Mi",
        usageCpu = "50m",
        usageMemory = "64Mi",
    )

    @Test
    fun `merge overlays state internalState and resourceUsage from full while keeping basic identity`() {
        val basic = listOf(
            pod(
                id = "pod-1",
                subdomain = "basic-sub",
                state = "pending",
                internalState = "stale",
                usageStatus = "used",
            )
        )
        val full = listOf(
            pod(
                id = "pod-1",
                subdomain = "full-sub",
                state = "running",
                internalState = "ready",
                usageStatus = "unused",
                resourceUsage = usage(),
            )
        )

        val merged = mergeHivePods(basic, full).single()

        assertEquals("pod-1", merged.id)
        assertEquals("basic-sub", merged.subdomain)
        assertEquals("used", merged.usageStatus)
        assertEquals("running", merged.state)
        assertEquals("ready", merged.internalState)
        assertEquals("100m", merged.resourceUsage!!.requestsCpu)
    }

    @Test
    fun `merge keeps basic-only rows and appends full-only rows`() {
        val basic = listOf(
            pod(id = "basic-only", state = "pending", usageStatus = "unused"),
            pod(id = "shared", subdomain = "shared-basic", state = "pending", usageStatus = "used"),
        )
        val full = listOf(
            pod(
                id = "shared",
                subdomain = "shared-full",
                state = "running",
                internalState = "ready",
                usageStatus = "unused",
                resourceUsage = usage(),
            ),
            pod(id = "full-only", subdomain = "full-only", state = "failed", usageStatus = "unused"),
        )

        val merged = mergeHivePods(basic, full)

        assertEquals(listOf("basic-only", "shared", "full-only"), merged.map { it.id })
        assertEquals("pending", merged[0].state)
        assertNull(merged[0].resourceUsage)
        assertEquals("shared-basic", merged[1].subdomain)
        assertEquals("used", merged[1].usageStatus)
        assertEquals("running", merged[1].state)
        assertEquals("full-only", merged[2].id)
        assertEquals("failed", merged[2].state)
    }

    @Test
    fun `sort buckets actively used then pending then idle then failed then unknown`() {
        val unknown = pod(id = "unknown", state = "unknown", usageStatus = "unused")
        val failed = pod(id = "failed", state = "FAILED", usageStatus = "unused")
        val idle = pod(id = "idle", state = "Running", usageStatus = "unused")
        val pending = pod(id = "pending", state = "PENDING", usageStatus = "used")
        val used = pod(id = "used", state = "RUNNING", usageStatus = "USED")
        val starting = pod(id = "starting", state = "STARTING", usageStatus = "unused")
        val runningNoUsage = pod(id = "running-null", state = "running", usageStatus = null)

        val sorted = sortHivePods(
            listOf(unknown, failed, idle, pending, used, starting, runningNoUsage)
        )

        assertEquals(
            listOf("used", "pending", "idle", "running-null", "failed", "unknown", "starting"),
            sorted.map { it.id }
        )
    }

    @Test
    fun `unknown is never folded into idle even when usage is unused`() {
        val unknown = pod(id = "unknown", state = "unknown", usageStatus = "unused")
        val idle = pod(id = "idle", state = "running", usageStatus = "unused")

        val sorted = sortHivePods(listOf(unknown, idle))

        assertEquals(listOf("idle", "unknown"), sorted.map { it.id })
    }
}
