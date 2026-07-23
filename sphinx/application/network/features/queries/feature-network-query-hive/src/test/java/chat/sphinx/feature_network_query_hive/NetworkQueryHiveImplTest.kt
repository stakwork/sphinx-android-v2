package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.concept_network_query_hive.model.HiveAuthTokenDto
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkQueryHiveImplTest {

    private val HIVE_AUTH_ENDPOINT = "https://hive.sphinx.chat/api/auth/sphinx/token"

    private data class PostCall(
        val url: String,
        val responseJsonClass: Class<*>,
        val requestBodyJsonClass: Class<*>,
        val requestBody: Any
    )

    private abstract class FakeNetworkRelayCall : NetworkRelayCall() {
        val postCalls = mutableListOf<PostCall>()
        var responseFlow: Flow<LoadResponse<HiveAuthTokenDto, ResponseError>> =
            flow { emit(Response.Success(HiveAuthTokenDto("fake-token"))) }

        override fun <T : Any, RequestBody : Any> post(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?,
            accept400AsSuccess: Boolean
        ): Flow<LoadResponse<T, ResponseError>> {
            postCalls.add(PostCall(url, responseJsonClass, requestBodyJsonClass, requestBody))
            @Suppress("UNCHECKED_CAST")
            return responseFlow as Flow<LoadResponse<T, ResponseError>>
        }
    }

    private fun buildFake(): FakeNetworkRelayCall = object : FakeNetworkRelayCall() {
        override fun <T : Any> get(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any> getList(
            url: String,
            responseJsonClass: Class<T>,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<List<T>, ResponseError>> = throw UnsupportedOperationException()

        override fun getRawJson(
            url: String,
            headers: Map<String, String>?,
            useExtendedNetworkCallClient: Boolean
        ): Flow<LoadResponse<String, ResponseError>> = throw UnsupportedOperationException()

        override suspend fun getWithoutJson(
            url: String,
            headers: Map<String, String>?
        ): Flow<LoadResponse<String, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> put(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> postList(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>,
            requestBody: RequestBody,
            mediaType: String?,
            headers: Map<String, String>?
        ): Flow<LoadResponse<List<T>, ResponseError>> = throw UnsupportedOperationException()

        override fun <T : Any, RequestBody : Any> delete(
            url: String,
            responseJsonClass: Class<T>,
            requestBodyJsonClass: Class<RequestBody>?,
            requestBody: RequestBody?,
            mediaType: String?,
            headers: Map<String, String>?
        ): Flow<LoadResponse<T, ResponseError>> = throw UnsupportedOperationException()

        override suspend fun <T : Any> call(
            responseJsonClass: Class<T>,
            request: Request,
            useExtendedNetworkCallClient: Boolean,
            accept400AsSuccess: Boolean
        ): T = throw UnsupportedOperationException()

        override suspend fun <T : Any> callList(
            responseJsonClass: Class<T>,
            request: Request,
            useExtendedNetworkCallClient: Boolean
        ): List<T> = throw UnsupportedOperationException()
    }

    private fun buildImpl(fake: FakeNetworkRelayCall): NetworkQueryHiveImpl =
        NetworkQueryHiveImpl(fake)

    @Test
    fun `getHiveAuthToken calls post with HiveAuthRequestDto class`() =
        runBlockingTest {
            val fake = buildFake()
            val impl = buildImpl(fake)

            impl.getHiveAuthToken("signed-token", "pubkey123", "1234567890").toList()

            assertEquals(1, fake.postCalls.size)
            val call = fake.postCalls.first()
            assertEquals(HiveAuthRequestDto::class.java, call.requestBodyJsonClass)
        }

    @Test
    fun `getHiveAuthToken never uses a raw Map as request body`() =
        runBlockingTest {
            val fake = buildFake()
            val impl = buildImpl(fake)

            impl.getHiveAuthToken("signed-token", "pubkey123", "1234567890").toList()

            val call = fake.postCalls.first()
            assertTrue(
                "requestBody must be HiveAuthRequestDto, not a Map",
                call.requestBody is HiveAuthRequestDto
            )
        }

    @Test
    fun `getHiveAuthToken sends correct field values in request body`() =
        runBlockingTest {
            val fake = buildFake()
            val impl = buildImpl(fake)

            impl.getHiveAuthToken("my-signed-token", "my-pubkey", "999888777").toList()

            val body = fake.postCalls.first().requestBody as HiveAuthRequestDto
            assertEquals("my-signed-token", body.token)
            assertEquals("my-pubkey", body.pubkey)
            assertEquals("999888777", body.timestamp)
        }

    @Test
    fun `getHiveAuthToken uses correct Hive endpoint URL`() =
        runBlockingTest {
            val fake = buildFake()
            val impl = buildImpl(fake)

            impl.getHiveAuthToken("t", "p", "ts").toList()

            assertEquals(HIVE_AUTH_ENDPOINT, fake.postCalls.first().url)
        }

    @Test
    fun `getHiveAuthToken uses HiveAuthTokenDto as response class`() =
        runBlockingTest {
            val fake = buildFake()
            val impl = buildImpl(fake)

            impl.getHiveAuthToken("t", "p", "ts").toList()

            assertEquals(HiveAuthTokenDto::class.java, fake.postCalls.first().responseJsonClass)
        }
}
