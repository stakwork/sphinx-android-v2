package chat.sphinx.concept_network_query_hive.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HivePoolStatusDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HivePoolStatusDto parses camelCase queuedCount and unusedVms`() {
        val json = """
            {
              "success": true,
              "data": {
                "status": {
                  "runningVms": 1,
                  "pendingVms": 2,
                  "failedVms": 3,
                  "usedVms": 4,
                  "unusedVms": 5,
                  "lastCheck": "2026-01-01T00:00:00Z",
                  "queuedCount": 6
                }
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePoolStatusDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertEquals(6, dto.data!!.status!!.queuedCount)
        assertEquals(5, dto.data!!.status!!.unusedVms)
    }

    @Test
    fun `HivePoolStatusDto ignores undeclared sibling keys on status`() {
        val json = """
            {
              "success": true,
              "data": {
                "status": {
                  "runningVms": 9,
                  "pendingVms": 8,
                  "failedVms": 7,
                  "usedVms": 6,
                  "unusedVms": 1,
                  "lastCheck": "ignored",
                  "queuedCount": 2,
                  "extraField": true
                }
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePoolStatusDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertEquals(2, dto!!.data!!.status!!.queuedCount)
        assertEquals(1, dto.data!!.status!!.unusedVms)
    }

    @Test
    fun `HivePoolStatusDto missing queuedCount and unusedVms stays null`() {
        val json = """
            {
              "success": true,
              "data": {
                "status": {
                  "runningVms": 0,
                  "lastCheck": "..."
                }
              }
            }
        """.trimIndent()
        val dto = moshi.adapter(HivePoolStatusDto::class.java).fromJson(json)

        assertNotNull(dto)
        assertTrue(dto!!.success)
        assertNull(dto.data!!.status!!.queuedCount)
        assertNull(dto.data!!.status!!.unusedVms)
    }
}
