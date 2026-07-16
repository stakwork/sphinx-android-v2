package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthResponseDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [NetworkQueryHiveImpl]:
 *  1. Verifies the correct URL and DTO classes are forwarded to networkRelayCall.post
 *  2. Verifies Moshi (de)serialization round-trips for [HiveAuthRequestDto] / [HiveAuthResponseDto]
 */
class NetworkQueryHiveImplUnitTest {

    companion object {
        private const val EXPECTED_ENDPOINT = "https://hive.sphinx.chat/api/auth/sphinx/token"
    }

    // ------------------------------------------------------------------ //
    // Minimal fake that records what post() was called with               //
    // ------------------------------------------------------------------ //

    private data class PostCapture(
        val url: String,
        val responseJsonClass: Class<*>,
        val requestBodyJsonClass: Class<*>,
        val requestBody: Any,
    )

    private inner class FakeNetworkRelayCall :
        chat.sphinx.concept_network_relay_call.NetworkRelayCall() {

        var lastPostCapture: PostCapture? = null

        override fun <T : Any, RequestBody : Any> post(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
            accept400AsSuccess: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            lastPostCapture = PostCapture(url, responseJsonClass, requestBodyJsonClass, requestBody)
            return emptyFlow()
        }

        // Unused stubs required by the abstract base -------------------- //
        override fun <T : Any> get(url: String, responseJsonClass: Class<T>, headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean): Flow<LoadResponse<T, ResponseError>> = emptyFlow()
        override fun <T : Any> getList(url: String, responseJsonClass: Class<T>, headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean): Flow<LoadResponse<List<T>, ResponseError>> = emptyFlow()
        override fun getRawJson(url: String, headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean): Flow<LoadResponse<String, ResponseError>> = emptyFlow()
        override suspend fun getWithoutJson(url: String, headers: Map<String, String>?): Flow<LoadResponse<String, ResponseError>> = emptyFlow()
        override fun <T : Any, RequestBody : Any> put(url: String, responseJsonClass: Class<T>, requestBodyJsonClass: Class<RequestBody>?, requestBody: RequestBody?, mediaType: String?, headers: Map<String, String>?): Flow<LoadResponse<T, ResponseError>> = emptyFlow()
        override fun <T : Any, RequestBody : Any> postList(url: String, responseJsonClass: Class<T>, requestBodyJsonClass: Class<RequestBody>, requestBody: RequestBody, mediaType: String?, headers: Map<String, String>?): Flow<LoadResponse<List<T>, ResponseError>> = emptyFlow()
        override fun <T : Any, RequestBody : Any> delete(url: String, responseJsonClass: Class<T>, requestBodyJsonClass: Class<RequestBody>?, requestBody: RequestBody?, mediaType: String?, headers: Map<String, String>?): Flow<LoadResponse<T, ResponseError>> = emptyFlow()
        override suspend fun <T : Any> call(responseJsonClass: Class<T>, request: Request, useExtendedNetworkCallClient: Boolean, accept400AsSuccess: Boolean): T = throw UnsupportedOperationException()
        override suspend fun <T : Any> callList(responseJsonClass: Class<T>, request: Request, useExtendedNetworkCallClient: Boolean): List<T> = throw UnsupportedOperationException()
    }

    // ------------------------------------------------------------------ //
    // authenticate() delegation tests                                     //
    // ------------------------------------------------------------------ //

    private fun buildImpl(): Pair<FakeNetworkRelayCall, NetworkQueryHiveImpl> {
        val fake = FakeNetworkRelayCall()
        return fake to NetworkQueryHiveImpl(fake)
    }

    private fun sampleRequest() = HiveAuthRequestDto(
        token = "signed_token",
        pubkey = "03abc123",
        timestamp = "1234567890000",
    )

    @Test
    fun `authenticate posts to the correct Hive endpoint`() {
        val (fake, impl) = buildImpl()
        impl.authenticate(sampleRequest())
        assertEquals(EXPECTED_ENDPOINT, fake.lastPostCapture?.url)
    }

    @Test
    fun `authenticate passes HiveAuthResponseDto class to post`() {
        val (fake, impl) = buildImpl()
        impl.authenticate(sampleRequest())
        assertEquals(HiveAuthResponseDto::class.java, fake.lastPostCapture?.responseJsonClass)
    }

    @Test
    fun `authenticate passes HiveAuthRequestDto class to post`() {
        val (fake, impl) = buildImpl()
        impl.authenticate(sampleRequest())
        assertEquals(HiveAuthRequestDto::class.java, fake.lastPostCapture?.requestBodyJsonClass)
    }

    @Test
    fun `authenticate forwards the request body instance to post`() {
        val (fake, impl) = buildImpl()
        val req = sampleRequest()
        impl.authenticate(req)
        assertEquals(req, fake.lastPostCapture?.requestBody)
    }

    @Test
    fun `authenticate capture is non-null after call`() {
        val (fake, impl) = buildImpl()
        impl.authenticate(sampleRequest())
        assertNotNull(fake.lastPostCapture)
    }

    // ------------------------------------------------------------------ //
    // Moshi serialization round-trip tests (using generated adapters)     //
    // ------------------------------------------------------------------ //

    /** Moshi built with generated adapters from the concept module (via kapt). */
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    fun `HiveAuthRequestDto serializes token field`() {
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(HiveAuthRequestDto("tok", "pk", "ts"))
        assert(json.contains("\"token\":\"tok\"")) { "Missing token in: $json" }
    }

    @Test
    fun `HiveAuthRequestDto serializes pubkey field`() {
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(HiveAuthRequestDto("tok", "pk", "ts"))
        assert(json.contains("\"pubkey\":\"pk\"")) { "Missing pubkey in: $json" }
    }

    @Test
    fun `HiveAuthRequestDto serializes timestamp field`() {
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = adapter.toJson(HiveAuthRequestDto("tok", "pk", "ts"))
        assert(json.contains("\"timestamp\":\"ts\"")) { "Missing timestamp in: $json" }
    }

    @Test
    fun `HiveAuthRequestDto deserializes from JSON`() {
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val json = """{"token":"signed_token_value","pubkey":"pubkey_value","timestamp":"1000000000000"}"""
        val dto = adapter.fromJson(json)!!
        assertEquals("signed_token_value", dto.token)
        assertEquals("pubkey_value", dto.pubkey)
        assertEquals("1000000000000", dto.timestamp)
    }

    @Test
    fun `HiveAuthRequestDto round-trips all fields`() {
        val adapter = moshi.adapter(HiveAuthRequestDto::class.java)
        val original = HiveAuthRequestDto("signed_token_value", "pubkey_value", "1000000000000")
        val roundTripped = adapter.fromJson(adapter.toJson(original))!!
        assertEquals(original, roundTripped)
    }

    @Test
    fun `HiveAuthResponseDto deserializes token from JSON`() {
        val adapter = moshi.adapter(HiveAuthResponseDto::class.java)
        val json = """{"token":"hive_token_value"}"""
        val dto = adapter.fromJson(json)!!
        assertEquals("hive_token_value", dto.token)
    }

    @Test
    fun `HiveAuthResponseDto serializes token field`() {
        val adapter = moshi.adapter(HiveAuthResponseDto::class.java)
        val json = adapter.toJson(HiveAuthResponseDto("hive_tok"))
        assert(json.contains("\"token\":\"hive_tok\"")) { "Missing token in: $json" }
    }

    @Test
    fun `HiveAuthResponseDto round-trips token`() {
        val adapter = moshi.adapter(HiveAuthResponseDto::class.java)
        val original = HiveAuthResponseDto("hive_token_value")
        val roundTripped = adapter.fromJson(adapter.toJson(original))!!
        assertEquals(original, roundTripped)
    }
}
