package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NetworkQueryHiveImplUnitTest {

    private val testDispatcher = kotlinx.coroutines.test.TestCoroutineDispatcher()

    /**
     * A fake NetworkRelayCall that records the most recent POST call arguments
     * and returns the configured [nextPostResponse].
     */
    private inner class FakeNetworkRelayCall : NetworkRelayCall() {

        var lastPostUrl: String? = null
        var lastPostBody: Any? = null
        var nextPostResponse: LoadResponse<Any, ResponseError> =
            Response.Error(ResponseError("not configured"))

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any, RequestBody : Any> post(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
            accept400AsSuccess: Boolean
        ): Flow<LoadResponse<T, ResponseError>> {
            lastPostUrl = url
            lastPostBody = requestBody
            return flowOf(nextPostResponse as LoadResponse<T, ResponseError>)
        }

        // Minimal no-op implementations for the rest of the abstract contract

        override fun <T : Any> get(
            url: String, responseJsonClass: Class<T>,
            headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

        override fun <T : Any> getList(
            url: String, responseJsonClass: Class<T>,
            headers: Map<String, String>?, useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<List<T>, ResponseError>> = flowOf()

        override fun getRawJson(
            url: String, headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<String, ResponseError>> = flowOf()

        override suspend fun getWithoutJson(
            url: String, headers: Map<String, String>?
        ): Flow<LoadResponse<String, ResponseError>> = flowOf()

        override fun <T : Any, RequestBody : Any> put(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?, requestBody: RequestBody?,
            mediaType: String?, headers: Map<String, String>?
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

        override fun <T : Any, RequestBody : Any> postList(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>, requestBody: RequestBody,
            mediaType: String?, headers: Map<String, String>?
        ): Flow<LoadResponse<List<T>, ResponseError>> = flowOf()

        override fun <T : Any, RequestBody : Any> delete(
            url: String, responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?, requestBody: RequestBody?,
            mediaType: String?, headers: Map<String, String>?
        ): Flow<LoadResponse<T, ResponseError>> = flowOf()

        override suspend fun <T : Any> call(
            responseJsonClass: Class<T>, request: Request,
            useExtendedNetworkCallClient: Boolean, accept400AsSuccess: Boolean
        ): T = throw UnsupportedOperationException("Not used in unit tests")

        override suspend fun <T : Any> callList(
            responseJsonClass: Class<T>, request: Request,
            useExtendedNetworkCallClient: Boolean
        ): List<T> = throw UnsupportedOperationException("Not used in unit tests")
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    fun `authenticateWithHive posts to the correct endpoint URL`() =
        testDispatcher.runBlockingTest {
            val fake = FakeNetworkRelayCall()
            fake.nextPostResponse = Response.Success(HiveTokenDto("abc"))
            val impl = NetworkQueryHiveImpl(fake)

            impl.authenticateWithHive("t", "pk", "ts").collect {}

            assertEquals(
                "https://hive.sphinx.chat/api/auth/sphinx/token",
                fake.lastPostUrl
            )
        }

    @Test
    fun `authenticateWithHive sends token, pubkey, and timestamp in request body`() =
        testDispatcher.runBlockingTest {
            val fake = FakeNetworkRelayCall()
            fake.nextPostResponse = Response.Success(HiveTokenDto("abc"))
            val impl = NetworkQueryHiveImpl(fake)

            impl.authenticateWithHive(
                token = "myToken",
                pubkey = "myPubkey",
                timestamp = "myTimestamp"
            ).collect {}

            @Suppress("UNCHECKED_CAST")
            val body = fake.lastPostBody as? Map<String, String>
            assertNotNull("request body must not be null", body)
            assertEquals("myToken", body!!["token"])
            assertEquals("myPubkey", body["pubkey"])
            assertEquals("myTimestamp", body["timestamp"])
            assertEquals("body must contain exactly 3 keys", 3, body.size)
        }

    @Test
    fun `authenticateWithHive returns Success with HiveTokenDto on valid response`() =
        testDispatcher.runBlockingTest {
            val fake = FakeNetworkRelayCall()
            fake.nextPostResponse = Response.Success(HiveTokenDto("returned-token"))
            val impl = NetworkQueryHiveImpl(fake)

            var successValue: HiveTokenDto? = null
            impl.authenticateWithHive("t", "pk", "ts").collect { response ->
                if (response is Response.Success) {
                    successValue = response.value
                }
            }

            assertNotNull("expected a successful response", successValue)
            assertEquals("returned-token", successValue!!.token)
        }

    @Test
    fun `authenticateWithHive surfaces Response Error when relay call returns error`() =
        testDispatcher.runBlockingTest {
            val fake = FakeNetworkRelayCall()
            fake.nextPostResponse = Response.Error(ResponseError("missing token field"))
            val impl = NetworkQueryHiveImpl(fake)

            var errorResponse: Response.Error<ResponseError>? = null
            impl.authenticateWithHive("t", "pk", "ts").collect { response ->
                if (response is Response.Error) {
                    errorResponse = response
                }
            }

            assertNotNull("expected an error response, not silent null", errorResponse)
            assertTrue(
                "error message should describe the failure",
                errorResponse!!.cause.message.isNotBlank()
            )
        }
}
