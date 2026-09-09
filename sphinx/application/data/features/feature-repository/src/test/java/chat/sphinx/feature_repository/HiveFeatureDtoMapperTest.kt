package chat.sphinx.feature_repository

import chat.sphinx.concept_network_query_hive.model.HiveFeatureDto
import chat.sphinx.feature_repository.mappers.hive.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiveFeatureDtoMapperTest {

    @Test
    fun `HiveFeatureDto maps all fields to domain`() {
        val domain = HiveFeatureDto(
            id = "feat-1",
            title = "Title",
            status = "BACKLOG",
            priority = "LOW",
        ).toDomain()

        assertEquals("feat-1", domain.id)
        assertEquals("Title", domain.title)
        assertEquals("BACKLOG", domain.status)
        assertEquals("LOW", domain.priority)
    }

    @Test
    fun `HiveFeatureDto maps null optionals`() {
        val domain = HiveFeatureDto(id = "feat-2", title = "Bare").toDomain()
        assertEquals("feat-2", domain.id)
        assertEquals("Bare", domain.title)
        assertNull(domain.status)
        assertNull(domain.priority)
    }
}
