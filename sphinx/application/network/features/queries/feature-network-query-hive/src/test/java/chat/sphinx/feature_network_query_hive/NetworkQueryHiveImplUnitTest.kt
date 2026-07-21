package chat.sphinx.feature_network_query_hive

import chat.sphinx.concept_network_query_hive.model.HiveAuthRequestDto
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkQueryHiveImplUnitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var moshi: Moshi
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        moshi = Moshi.Builder().build()
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Creates a [NetworkQueryHiveImpl] subclass that overrides [endpointAuth]
     * to point at the mock server instead of hive.sphinx.chat.
     */
    private fun buildSut(): NetworkQueryHiveImpl {
        val authUrl = mockWebServer.url("/api/auth/sphinx/token").toString()
        return object : NetworkQueryHiveImpl(client, moshi) {
            override val endpointAuth: String get() = authUrl
        }
    }

    @Test
    fun `successful 200 response maps to Response_Success with token`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"token":"hive-bearer-token-123"}""")
        )

        val result = buildSut().getHiveAuthToken(
            HiveAuthRequestDto("signed-token", "pubkey123", "1720000000")
        )
            .filterNot { it is LoadResponse.Loading }
            .firstOrNull()

        assertTrue("Expected Success but got: $result", result is Response.Success)
        val dto = (result as Response.Success).value
        assertEquals("hive-bearer-token-123", dto.token)
    }

    @Test
    fun `non-2xx response maps to Response_Error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"unauthorized"}""")
        )

        val result = buildSut().getHiveAuthToken(
            HiveAuthRequestDto("bad-token", "pubkey123", "1720000000")
        )
            .filterNot { it is LoadResponse.Loading }
            .firstOrNull()

        assertTrue("Expected Error but got: $result", result is Response.Error)
    }

    @Test
    fun `null token field in response body deserializes to HiveAuthTokenDto with null token`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"token":null}""")
        )

        val result = buildSut().getHiveAuthToken(
            HiveAuthRequestDto("signed-token", "pubkey123", "1720000000")
        )
            .filterNot { it is LoadResponse.Loading }
            .firstOrNull()

        // Null token field should deserialize without throwing; token should be null
        assertTrue("Expected Success but got: $result", result is Response.Success)
        val dto = (result as Response.Success).value
        assertNull("token should be null", dto.token)
    }

    @Test
    fun `missing token field in response body deserializes without throwing`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{}""")
        )

        val result = buildSut().getHiveAuthToken(
            HiveAuthRequestDto("signed-token", "pubkey123", "1720000000")
        )
            .filterNot { it is LoadResponse.Loading }
            .firstOrNull()

        assertNotNull("Result should not be null", result)
        // Missing field → null token, should succeed without throwing
        assertTrue("Expected Success but got: $result", result is Response.Success)
        val dto = (result as Response.Success).value
        assertNull("token should be null when field is absent", dto.token)
    }

    @Test
    fun `server error 500 maps to Response_Error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500)
        )

        val result = buildSut().getHiveAuthToken(
            HiveAuthRequestDto("signed-token", "pubkey123", "1720000000")
        )
            .filterNot { it is LoadResponse.Loading }
            .firstOrNull()

        assertTrue("Expected Error but got: $result", result is Response.Error)
    }
}
