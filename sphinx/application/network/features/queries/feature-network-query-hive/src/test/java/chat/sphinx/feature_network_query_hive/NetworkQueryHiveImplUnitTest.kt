package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import io.matthewnelson.test_concept_coroutines.CoroutineTestHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkQueryHiveImplUnitTest : CoroutineTestHelper() {

    // ── captured call arguments ─────────────────────────────────────────────
    private var capturedUrl: String? = null
    private var capturedBody: Any? = null
    private var capturedResponseClass: Class<*>? = null

    /** Minimal fake that records the post() invocation then returns an empty Flow. */
    private inner class FakeNetworkRelayCall : NetworkRelayCall() {

        override fun <T : Any, RequestBody : Any> post(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
            accept400AsSuccess: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> {
            capturedUrl = url
            capturedBody = requestBody
            capturedResponseClass = responseJsonClass
            @Suppress("UNCHECKED_CAST")
            return emptyFlow()
        }

        // ── required stubs (unused in this test) ───────────────────────────
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

    private lateinit var networkQueryHive: NetworkQueryHiveImpl

    @Before
    fun setUp() {
        setupCoroutineTestHelper()
        capturedUrl = null
        capturedBody = null
        capturedResponseClass = null
        networkQueryHive = NetworkQueryHiveImpl(dispatchers, FakeNetworkRelayCall())
    }

    @After
    fun tearDown() {
        tearDownCoroutineTestHelper()
    }

    @Test
    fun `authenticate posts to exact HTTPS hive endpoint`() = testDispatcher.runBlockingTest {
        networkQueryHive.authenticate("tok", "pk", "ts")

        assertEquals(
            "https://hive.sphinx.chat/api/auth/sphinx/token",
            capturedUrl,
        )
        assertTrue(
            "URL must start with https://",
            capturedUrl!!.startsWith("https://"),
        )
    }

    @Test
    fun `authenticate sends token pubkey and timestamp in request body`() = testDispatcher.runBlockingTest {
        networkQueryHive.authenticate("my-token", "my-pubkey", "my-timestamp")

        val body = capturedBody as? Map<*, *>
        assertNotNull("Request body must not be null", body)
        assertEquals("my-token",     body!!["token"])
        assertEquals("my-pubkey",    body["pubkey"])
        assertEquals("my-timestamp", body["timestamp"])
    }

    @Test
    fun `authenticate deserialises response into HiveAuthTokenDto`() = testDispatcher.runBlockingTest {
        networkQueryHive.authenticate("tok", "pk", "ts")

        assertEquals(HiveAuthTokenDto::class.java, capturedResponseClass)
    }
}
