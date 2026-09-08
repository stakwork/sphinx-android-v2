package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `WorkspaceDto deserializes camelCase JSON correctly`() {
        val json = """{"id":"ws-1","name":"Acme Workspace","description":"desc","slug":"acme","ownerId":"user-1","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-02T00:00:00Z","userRole":"OWNER","memberCount":5,"logoKey":"key123","logoUrl":"https://example.com/logo.png","lastAccessedAt":"2026-01-03T00:00:00Z"}"""
        val adapter = moshi.adapter(WorkspaceDto::class.java)
        val dto = adapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("ws-1", dto!!.id)
        assertEquals("Acme Workspace", dto.name)
        assertEquals("OWNER", dto.userRole)
        assertEquals(5, dto.memberCount)
        assertEquals("https://example.com/logo.png", dto.logoUrl)

        // Explicitly guard against silently falling back to defaults/null
        // when the API uses camelCase keys instead of snake_case.
        assertNotNull(dto.userRole)
        assertNotEquals(0, dto.memberCount)
    }
}
