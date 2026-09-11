package chat.sphinx.dashboard.ui.adapter

import chat.sphinx.concept_repository_dashboard.model.HivePod
import chat.sphinx.concept_repository_dashboard.model.HivePodResourceUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors [HivePodAdapter.HivePodViewHolder.bind] without inflating Android views.
 * View-only: no click callback is exposed.
 */
private class TestableHivePodRow {
    var subdomain: String? = null
    var status: String? = null
    var capacity: String? = null
    var capacityVisible: Boolean = false

    fun bind(pod: HivePod) {
        subdomain = pod.subdomain
        status = if (pod.usageStatus.isNullOrBlank()) {
            pod.state
        } else {
            "${pod.state} · ${pod.usageStatus}"
        }
        val usage = pod.resourceUsage
        if (usage?.available == true) {
            capacityVisible = true
            capacity = "CPU ${usage.usageCpu.orEmpty()} / ${usage.requestsCpu.orEmpty()} · Mem ${usage.usageMemory.orEmpty()} / ${usage.requestsMemory.orEmpty()}"
        } else {
            capacityVisible = false
            capacity = null
        }
    }
}

class HivePodAdapterTest {

    private fun pod(
        id: String = "pod-1",
        subdomain: String = "alpha",
        state: String = "running",
        usageStatus: String? = "used",
        resourceUsage: HivePodResourceUsage? = null,
    ) = HivePod(
        id = id,
        subdomain = subdomain,
        state = state,
        internalState = null,
        usageStatus = usageStatus,
        resourceUsage = resourceUsage,
    )

    @Test
    fun `binding exposes subdomain and status usage`() {
        val row = TestableHivePodRow()

        row.bind(pod(subdomain = "beta", state = "pending", usageStatus = "unused"))

        assertEquals("beta", row.subdomain)
        assertEquals("pending · unused", row.status)
        assertFalse(row.capacityVisible)
        assertNull(row.capacity)
    }

    @Test
    fun `capacity is shown when resourceUsage available is true`() {
        val row = TestableHivePodRow()

        row.bind(
            pod(
                resourceUsage = HivePodResourceUsage(
                    available = true,
                    requestsCpu = "100m",
                    requestsMemory = "128Mi",
                    usageCpu = "50m",
                    usageMemory = "64Mi",
                )
            )
        )

        assertTrue(row.capacityVisible)
        assertEquals("CPU 50m / 100m · Mem 64Mi / 128Mi", row.capacity)
    }

    @Test
    fun `capacity is omitted when resourceUsage is missing or unavailable`() {
        val missing = TestableHivePodRow()
        missing.bind(pod(resourceUsage = null))
        assertFalse(missing.capacityVisible)
        assertNull(missing.capacity)

        val unavailable = TestableHivePodRow()
        unavailable.bind(
            pod(
                resourceUsage = HivePodResourceUsage(
                    available = false,
                    requestsCpu = "100m",
                    requestsMemory = "128Mi",
                    usageCpu = "50m",
                    usageMemory = "64Mi",
                )
            )
        )
        assertFalse(unavailable.capacityVisible)
        assertNull(unavailable.capacity)
    }
}
