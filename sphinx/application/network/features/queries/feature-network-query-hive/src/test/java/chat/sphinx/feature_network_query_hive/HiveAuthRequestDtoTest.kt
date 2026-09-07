package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class HiveAuthRequestDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HiveAuthRequestDto serializes to correct JSON with no null fields`() {
        val dto = HiveAuthRequestDto(
            token = "signed-token-123",
            pubkey = "pubkey-abc",
            timestamp = 1700000000000L
        )
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(dto)

        assertNotNull(json)
        // All three fields must be present
        assert(json.contains("\"token\""))
        assert(json.contains("\"pubkey\""))
        assert(json.contains("\"timestamp\""))
        // Values must be correct
        assert(json.contains("signed-token-123"))
        assert(json.contains("pubkey-abc"))
        // timestamp must be serialized as a bare JSON number, not a quoted string
        assert(json.contains(":1700000000000"))
        assertFalse(json.contains("\"1700000000000\""))
        // No null values
        assertFalse(json.contains("null"))
    }

    @Test
    fun `HiveAuthRequestDto serializes to exact expected JSON`() {
        val dto = HiveAuthRequestDto(
            token = "t",
            pubkey = "p",
            timestamp = 123L
        )
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(dto)
        val parsed = adapter.fromJson(json)

        assertNotNull(parsed)
        assertEquals("t", parsed!!.token)
        assertEquals("p", parsed.pubkey)
        assertEquals(123L, parsed.timestamp)
        // timestamp must be a bare JSON number, not a quoted string
        assert(json.contains(":123"))
        assertFalse(json.contains("\"123\""))
    }

    @Test
    fun `HiveAuthRequestDto deserializes from JSON correctly`() {
        val json = """{"token":"signed-token","pubkey":"mypubkey","timestamp":9999999}"""
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val dto = adapter.fromJson(json)

        assertNotNull(dto)
        assertEquals("signed-token", dto!!.token)
        assertEquals("mypubkey", dto.pubkey)
        assertEquals(9999999L, dto.timestamp)
    }
}
