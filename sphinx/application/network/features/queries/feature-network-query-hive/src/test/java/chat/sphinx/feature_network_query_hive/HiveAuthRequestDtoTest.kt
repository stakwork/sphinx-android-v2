package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HiveAuthRequestDtoTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `HiveAuthRequestDto serializes to correct JSON with no null fields`() {
        val dto = HiveAuthRequestDto(
            token = "signed_token_value",
            pubkey = "03abc123",
            timestamp = "1690000000000"
        )

        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(dto)

        assertEquals(
            """{"token":"signed_token_value","pubkey":"03abc123","timestamp":"1690000000000"}""",
            json
        )
        assertFalse(json.contains("null"))
    }

    @Test
    fun `HiveAuthRequestDto deserializes from JSON correctly`() {
        val json = """{"token":"tok","pubkey":"pubk","timestamp":"12345"}"""
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val dto = adapter.fromJson(json)!!

        assertEquals("tok", dto.token)
        assertEquals("pubk", dto.pubkey)
        assertEquals("12345", dto.timestamp)
    }
}
