package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@Suppress("DEPRECATION")
class NetworkQueryHiveImplTest {

    private var capturedUrl: String? = null
    private var capturedResponseJsonClass: Class<*>? = null
    private var capturedRequestBodyJsonClass: Class<*>? = null
    private var capturedRequestBody: Any? = null

    /**
     * Minimal fake NetworkRelayCall that records arguments passed to post().
     * NetworkRelayCall extends NetworkCall — all abstract NetworkCall members
     * must be implemented; NetworkRelayCall itself adds no additional abstract members.
     */
    private val fakeNetworkRelayCall = object : NetworkRelayCall() {

        @Suppress("UNCHECKED_CAST")
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
            capturedResponseJsonClass = responseJsonClass
            capturedRequestBodyJsonClass = requestBodyJsonClass
            capturedRequestBody = requestBody
            return flowOf(Response.Success(HiveAuthTokenDto("fake-token") as T))
        }

        // ── Stubs for remaining NetworkCall abstract members ──────────────────────

        override fun <T : Any> get(
            url: String, responseJsonClass: Class<T>,
            headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any> getList(
            url: String, responseJsonClass: Class<T>,
            headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<List<T>, ResponseError>> = throw UnsupportedOperationException()

        override fun getRawJson(
            url: String, headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<String, ResponseError>> = throw UnsupportedOperationException()

        override suspend fun getWithoutJson(
            url: String, headers: Map<String, String>?,
        ): Flow<LoadResponse<String, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> put(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?, mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> postList(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody, mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<List<T>, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> delete(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?, mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override suspend fun <T : Any> call(
            responseJsonClass: Class<T>, request: Request,
            useExtendedNetworkCallClient: Boolean, accept400AsSuccess: Boolean,
        ): T = throw UnsupportedOperationException()

        override suspend fun <T : Any> callList(
            responseJsonClass: Class<T>, request: Request,
            useExtendedNetworkCallClient: Boolean,
        ): List<T> = throw UnsupportedOperationException()
    }

    private val networkQueryHiveImpl = NetworkQueryHiveImpl(fakeNetworkRelayCall)

    @Test
    fun `post is invoked with HiveAuthRequestDto class as requestBodyJsonClass, never a raw Map`() =
        TestCoroutineScope().runBlockingTest {
            networkQueryHiveImpl.getHiveAuthToken(
                token = "signed-token",
                pubkey = "test-pubkey",
                timestamp = "1234567890",
            ).collect()

            assertEquals(
                "requestBodyJsonClass must be HiveAuthRequestDto::class.java, not a raw Map",
                HiveAuthRequestDto::class.java,
                capturedRequestBodyJsonClass,
            )
        }

    @Test
    fun `post is invoked with a HiveAuthRequestDto instance as requestBody with correct field values`() =
        TestCoroutineScope().runBlockingTest {
            networkQueryHiveImpl.getHiveAuthToken(
                token = "signed-token",
                pubkey = "my-pubkey",
                timestamp = "9999",
            ).collect()

            val body = capturedRequestBody
            assertNotNull("requestBody must not be null", body)
            assert(body is HiveAuthRequestDto) {
                "requestBody must be HiveAuthRequestDto but was ${body?.javaClass?.simpleName}"
            }
            val dto = body as HiveAuthRequestDto
            assertEquals("signed-token", dto.token)
            assertEquals("my-pubkey", dto.pubkey)
            assertEquals("9999", dto.timestamp)
        }

    @Test
    fun `post is invoked with HiveAuthTokenDto class as responseJsonClass`() =
        TestCoroutineScope().runBlockingTest {
            networkQueryHiveImpl.getHiveAuthToken("t", "p", "ts").collect()

            assertEquals(HiveAuthTokenDto::class.java, capturedResponseJsonClass)
        }

    @Test
    fun `post targets the correct Hive auth endpoint`() =
        TestCoroutineScope().runBlockingTest {
            networkQueryHiveImpl.getHiveAuthToken("t", "p", "ts").collect()

            assertEquals(
                "https://hive.sphinx.chat/api/auth/sphinx/token",
                capturedUrl,
            )
        }
}
