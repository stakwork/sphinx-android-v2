package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.model.HivePodDto
import chat.sphinx.concept_network_query_hive.model.HivePodResourceQuantityDto
import chat.sphinx.concept_network_query_hive.model.HivePodResourceUsageDto
import chat.sphinx.feature_repository.mappers.hive.toDomain
import chat.sphinx.feature_repository.mappers.hive.toDomainOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HivePodDtoMapperTest {

    @Test
    fun `complete pod maps all fields including string cpu memory`() {
        val domain = HivePodDto(
            id = "pod-1",
            subdomain = "alpha",
            state = "running",
            internalState = "ready",
            usageStatus = "used",
            resourceUsage = HivePodResourceUsageDto(
                available = true,
                requests = HivePodResourceQuantityDto(cpu = "100m", memory = "128Mi"),
                usage = HivePodResourceQuantityDto(cpu = "50m", memory = "64Mi"),
            ),
        ).toDomainOrNull()

        assertNotNull(domain)
        assertEquals("pod-1", domain!!.id)
        assertEquals("alpha", domain.subdomain)
        assertEquals("running", domain.state)
        assertEquals("ready", domain.internalState)
        assertEquals("used", domain.usageStatus)
        assertTrue(domain.resourceUsage!!.available)
        assertEquals("100m", domain.resourceUsage!!.requestsCpu)
        assertEquals("128Mi", domain.resourceUsage!!.requestsMemory)
        assertEquals("50m", domain.resourceUsage!!.usageCpu)
        assertEquals("64Mi", domain.resourceUsage!!.usageMemory)
    }

    @Test
    fun `blank or null identity fields drop the pod`() {
        assertNull(
            HivePodDto(id = null, subdomain = "alpha", state = "running").toDomainOrNull()
        )
        assertNull(
            HivePodDto(id = " ", subdomain = "alpha", state = "running").toDomainOrNull()
        )
        assertNull(
            HivePodDto(id = "pod-1", subdomain = null, state = "running").toDomainOrNull()
        )
        assertNull(
            HivePodDto(id = "pod-1", subdomain = " ", state = "running").toDomainOrNull()
        )
        assertNull(
            HivePodDto(id = "pod-1", subdomain = "alpha", state = null).toDomainOrNull()
        )
        assertNull(
            HivePodDto(id = "pod-1", subdomain = "alpha", state = " ").toDomainOrNull()
        )
    }

    @Test
    fun `resource usage available defaults to false when null`() {
        val domain = HivePodResourceUsageDto(available = null).toDomain()
        assertEquals(false, domain.available)
        assertNull(domain.requestsCpu)
        assertNull(domain.requestsMemory)
        assertNull(domain.usageCpu)
        assertNull(domain.usageMemory)
    }
}
