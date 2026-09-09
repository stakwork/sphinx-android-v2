package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiveFeatureDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HiveFeatureDto deserializes required-only id and title`() {
        val json = """{"id":"feat-1","title":"Ship Hive features"}"""
        val dto = moshi.adapter(HiveFeatureDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("feat-1", dto!!.id)
        assertEquals("Ship Hive features", dto.title)
        assertNull(dto.status)
        assertNull(dto.priority)
    }

    @Test
    fun `HiveFeatureDto ignores unknown nested keys`() {
        val json = """
            {
              "id":"feat-2",
              "title":"Full feature",
              "status":"IN_PROGRESS",
              "priority":"HIGH",
              "assignee":{"id":"user-1"},
              "createdBy":{"id":"user-2"},
              "userStories":[{"id":"us-1"}],
              "phases":[{"id":"ph-1"}],
              "tasks":[{"id":"t-1"}],
              "_count":{"tasks":3}
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveFeatureDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals("feat-2", dto!!.id)
        assertEquals("Full feature", dto.title)
        assertEquals("IN_PROGRESS", dto.status)
        assertEquals("HIGH", dto.priority)
    }

    @Test
    fun `HiveFeaturesListDto success false is not an empty page`() {
        val json = """{"success":false,"data":[]}"""
        val dto = moshi.adapter(HiveFeaturesListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertFalse(dto!!.success)
        assertNotNull(dto.data)
        assertTrue(dto.data!!.isEmpty())
    }

    @Test
    fun `HiveFeaturesListDto missing success fails to parse`() {
        val json = """{"data":[]}"""
        val thrown = try {
            moshi.adapter(HiveFeaturesListDto::class.java).fromJson(json)
            null
        } catch (e: Exception) {
            e
        }
        // Generated adapters throw; reflective adapters may return null. Either is a failed list.
        if (thrown == null) {
            val dto = moshi.adapter(HiveFeaturesListDto::class.java).fromJson(json)
            assertTrue(dto == null)
        }
    }

    @Test
    fun `pagination defaults are applied only after success true`() {
        val json = """{"success":true,"data":[{"id":"feat-1","title":"One"}]}"""
        val dto = moshi.adapter(HiveFeaturesListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertEquals(1, dto.data!!.size)
        assertNull(dto.pagination)

        val page = dto.pagination?.page ?: 1
        val totalPages = dto.pagination?.totalPages ?: 1
        val totalCount = dto.pagination?.totalCount ?: 0
        val hasMore = dto.pagination?.hasMore ?: false
        assertEquals(1, page)
        assertEquals(1, totalPages)
        assertEquals(0, totalCount)
        assertFalse(hasMore)
    }

    @Test
    fun `pagination fields deserialize when present`() {
        val json = """
            {
              "success":true,
              "data":[{"id":"feat-1","title":"One"}],
              "pagination":{"page":2,"totalPages":4,"totalCount":40,"hasMore":true}
            }
        """.trimIndent()
        val dto = moshi.adapter(HiveFeaturesListDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals(2, dto!!.pagination!!.page)
        assertEquals(4, dto.pagination!!.totalPages)
        assertEquals(40, dto.pagination!!.totalCount)
        assertTrue(dto.pagination!!.hasMore!!)
    }

    @Test
    fun `HiveDeleteResponseDto empty object has null success`() {
        val dto = moshi.adapter(HiveDeleteResponseDto::class.java).fromJson("{}")
        assertNotNull(dto)
        assertNull(dto!!.success)
    }

    @Test
    fun `HiveDeleteResponseDto success false is explicit failure`() {
        val dto = moshi.adapter(HiveDeleteResponseDto::class.java).fromJson("""{"success":false}""")
        assertNotNull(dto)
        assertEquals(false, dto!!.success)
    }

    @Test
    fun `HiveFeatureUpdateDto with error set`() {
        val json = """{"error":"cannot update","data":null}"""
        val dto = moshi.adapter(HiveFeatureUpdateDto::class.java).fromJson(json)
        assertNotNull(dto)
        assertEquals("cannot update", dto!!.error)
        assertNull(dto.data)
    }

    @Test
    fun `HiveFeatureUpdateDto unparseable data fails to parse`() {
        val json = """{"data":"not-an-object"}"""
        val thrown = try {
            moshi.adapter(HiveFeatureUpdateDto::class.java).fromJson(json)
            null
        } catch (e: Exception) {
            e
        }
        if (thrown == null) {
            val dto = moshi.adapter(HiveFeatureUpdateDto::class.java).fromJson(json)
            assertTrue(dto == null)
        }
    }

    @Test
    fun `HiveFeaturePatchDto omits null fields`() {
        val json = moshi.adapter(HiveFeaturePatchDto::class.java).toJson(
            HiveFeaturePatchDto(status = "PLANNED", priority = null)
        )
        assertEquals("""{"status":"PLANNED"}""", json)
    }
}
