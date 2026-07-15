package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class NetworkQueryHiveImplUrlTest {

    private val capturedUrls = mutableListOf<String>()

    /**
     * Minimal fake NetworkRelayCall that records the URL it was invoked with
     * and returns an empty flow. We only need to assert URL/body placement.
     */
    private val fakeNetworkRelayCall = object : NetworkRelayCall() {

        override fun <T : Any> get(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

        override fun <T : Any> getList(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<List<T>, ResponseError>> = flowOf()

        override fun getRawJson(
            url: String,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean,
        ): Flow<LoadResponse<String, ResponseError>> = flowOf()

        override suspend fun getWithoutJson(
            url: String,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<String, ResponseError>> = flowOf()

        override fun <T : Any, RequestBody : Any> put(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

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
            capturedUrls.add(url)
            return flowOf()
        }

        override fun <T : Any, RequestBody : Any> postList(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<List<T>, ResponseError>> = flowOf()

        override fun <T : Any, RequestBody : Any> delete(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?,
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

        override suspend fun <T : Any> call(
            responseJsonClass: Class<T>,
            request: okhttp3.Request,
            useExtendedNetworkCallClient: Boolean,
            accept400AsSuccess: Boolean,
        ): T = throw UnsupportedOperationException("not used in tests")

        override suspend fun <T : Any> callList(
            responseJsonClass: Class<T>,
            request: okhttp3.Request,
            useExtendedNetworkCallClient: Boolean,
        ): List<T> = throw UnsupportedOperationException("not used in tests")
    }

    private lateinit var impl: NetworkQueryHiveImpl

    @Before
    fun setUp() {
        capturedUrls.clear()
        impl = NetworkQueryHiveImpl(fakeNetworkRelayCall)
    }

    @Test
    fun `authenticateWithHive posts to the correct Hive endpoint`() {
        val requestBody = HiveAuthRequestDto(
            token = "signed-token",
            pubkey = "pubkey",
            timestamp = "1234567890000"
        )
        impl.authenticateWithHive(requestBody)

        assertEquals(1, capturedUrls.size)
        assertEquals("https://hive.sphinx.chat/api/auth/sphinx/token", capturedUrls.first())
    }

    @Test
    fun `authenticateWithHive URL contains no token query parameter`() {
        val requestBody = HiveAuthRequestDto(
            token = "my-signed-token",
            pubkey = "mypubkey",
            timestamp = "9999"
        )
        impl.authenticateWithHive(requestBody)

        val url = capturedUrls.first()
        assertFalse("URL must not embed 'token' as query param", url.contains("token="))
        assertFalse("URL must not embed 'pubkey' as query param", url.contains("pubkey="))
        assertFalse("URL must not embed 'timestamp' as query param", url.contains("timestamp="))
    }

    @Test
    fun `authenticateWithHive URL does not contain the signed token value`() {
        val sensitiveToken = "super-secret-signed-token"
        val requestBody = HiveAuthRequestDto(
            token = sensitiveToken,
            pubkey = "mypubkey",
            timestamp = "9999"
        )
        impl.authenticateWithHive(requestBody)

        val url = capturedUrls.first()
        assertFalse("URL must not contain the signed token value", url.contains(sensitiveToken))
    }
}
