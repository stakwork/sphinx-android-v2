package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HiveDtoSerializationTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    // ── HiveAuthRequestDto ──────────────────────────────────────────────────

    @Test
    fun `HiveAuthRequestDto serializes all fields to JSON`() {
        val dto = HiveAuthRequestDto(
            token = "signed-token-value",
            pubkey = "pubkey-value",
            timestamp = "1234567890000"
        )
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(dto)
        assertEquals(true, json.contains("\"token\":\"signed-token-value\""))
        assertEquals(true, json.contains("\"pubkey\":\"pubkey-value\""))
        assertEquals(true, json.contains("\"timestamp\":\"1234567890000\""))
    }

    @Test
    fun `HiveAuthRequestDto deserializes from JSON`() {
        val json = """{"token":"signed-token","pubkey":"mypubkey","timestamp":"9999"}"""
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val dto = adapter.fromJson(json)!!
        assertEquals("signed-token", dto.token)
        assertEquals("mypubkey", dto.pubkey)
        assertEquals("9999", dto.timestamp)
    }

    // ── HiveAuthTokenDto ────────────────────────────────────────────────────

    @Test
    fun `HiveAuthTokenDto deserializes token field when present`() {
        val json = """{"token":"hive-jwt-token"}"""
        val adapter = moshi.adapter(HiveAuthTokenDto::class.java)
        val dto = adapter.fromJson(json)!!
        assertEquals("hive-jwt-token", dto.token)
    }

    @Test
    fun `HiveAuthTokenDto token is null when field is missing from JSON`() {
        val json = """{}"""
        val adapter = moshi.adapter(HiveAuthTokenDto::class.java)
        val dto = adapter.fromJson(json)!!
        assertNull(dto.token)
    }

    @Test
    fun `HiveAuthTokenDto token is null when field is explicitly null`() {
        val json = """{"token":null}"""
        val adapter = moshi.adapter(HiveAuthTokenDto::class.java)
        val dto = adapter.fromJson(json)!!
        assertNull(dto.token)
    }

    @Test
    fun `HiveAuthTokenDto does not throw on extra unknown fields`() {
        val json = """{"token":"abc","extra_field":"ignored"}"""
        val adapter = moshi.adapter(HiveAuthTokenDto::class.java)
        val dto = adapter.fromJson(json)!!
        assertEquals("abc", dto.token)
    }
}
