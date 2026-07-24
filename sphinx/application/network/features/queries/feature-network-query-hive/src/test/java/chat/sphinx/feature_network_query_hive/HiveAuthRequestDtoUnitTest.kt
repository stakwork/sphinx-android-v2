package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HiveAuthRequestDtoUnitTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(HiveAuthRequestDto::class.java)

    @Test
    fun `HiveAuthRequestDto serializes all three fields with correct json names`() {
        val dto = HiveAuthRequestDto(
            token = "signed_token_value",
            pubkey = "03abc123pubkey",
            timestamp = "1700000000000"
        )

        val json = adapter.toJson(dto)

        assert(json.contains("\"token\":\"signed_token_value\"")) {
            "Expected 'token' field but got: $json"
        }
        assert(json.contains("\"pubkey\":\"03abc123pubkey\"")) {
            "Expected 'pubkey' field but got: $json"
        }
        assert(json.contains("\"timestamp\":\"1700000000000\"")) {
            "Expected 'timestamp' field but got: $json"
        }
    }

    @Test
    fun `HiveAuthRequestDto serialized json contains no null values`() {
        val dto = HiveAuthRequestDto(
            token = "t",
            pubkey = "p",
            timestamp = "0"
        )

        val json = adapter.toJson(dto)

        assertFalse("Serialized json must not contain null values", json.contains("null"))
    }

    @Test
    fun `HiveAuthRequestDto deserializes correctly from json`() {
        val json = """{"token":"my_token","pubkey":"my_pubkey","timestamp":"12345"}"""

        val dto = adapter.fromJson(json)!!

        assertEquals("my_token", dto.token)
        assertEquals("my_pubkey", dto.pubkey)
        assertEquals("12345", dto.timestamp)
    }
}
