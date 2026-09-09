package chat.sphinx.feature_network_relay_call

import chat.sphinx.concept_network_client.NetworkClient
import chat.sphinx.concept_network_client.NetworkClientClearedListener
import chat.sphinx.concept_relay.CustomException
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.LogType
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.wrapper_relay.AuthorizationToken
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

data class SampleBodyDto(
    @Json(name = "ok") val ok: Boolean = true,
)

class NetworkRelayCallImplRequireSuccessfulTest {

    private lateinit var server: MockWebServer
    private lateinit var subject: NetworkRelayCallImpl

    private val dispatchers = object : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
        val networkClient = object : NetworkClient() {
            override suspend fun getClient(): OkHttpClient = client
            override fun addListener(listener: NetworkClientClearedListener): Boolean = true
            override fun removeListener(listener: NetworkClientClearedListener): Boolean = true
        }
        val relayDataHandler = object : RelayDataHandler() {
            override suspend fun persistAuthorizationToken(token: AuthorizationToken?): Boolean = false
            override suspend fun retrieveAuthorizationToken(): AuthorizationToken? = null
        }
        val logger = object : SphinxLogger() {
            override fun log(tag: String, message: String, type: LogType, throwable: Throwable?) {}
        }
        subject = NetworkRelayCallImpl(
            dispatchers,
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
            networkClient,
            relayDataHandler,
            logger,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requireSuccessful true on 401 JSON body is Response Error with CustomException code`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"error":"unauthorized"}""")
        )

        val result = subject.get(
            url = server.url("/features").toString(),
            responseJsonClass = SampleBodyDto::class.java,
            requireSuccessful = true,
        ).last()

        assertTrue(result is Response.Error)
        val exception = (result as Response.Error<ResponseError>).cause.exception
        assertTrue(exception is CustomException)
        assertEquals(401, (exception as CustomException).code)
    }

    @Test
    fun `requireSuccessful false on 401 JSON body is parsed success`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false}""")
        )

        val result = subject.get(
            url = server.url("/features").toString(),
            responseJsonClass = SampleBodyDto::class.java,
            requireSuccessful = false,
        ).last()

        assertTrue(result is Response.Success)
        assertEquals(false, (result as Response.Success).value.ok)
    }

    @Test
    fun `requireSuccessful true on 2xx empty body parses empty DTO`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = subject.delete<HiveEmptyDto, Any>(
            url = server.url("/features/feat-1").toString(),
            responseJsonClass = HiveEmptyDto::class.java,
            requireSuccessful = true,
        ).last()

        assertTrue(result is Response.Success)
    }

    @Test
    fun `requireSuccessful true on 403 is Response Error with code 403`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"ok":false}""")
        )

        val result = subject.patch(
            url = server.url("/features/feat-1").toString(),
            responseJsonClass = SampleBodyDto::class.java,
            requestBodyJsonClass = SampleBodyDto::class.java,
            requestBody = SampleBodyDto(ok = true),
            requireSuccessful = true,
        ).last()

        assertTrue(result is Response.Error)
        val exception = (result as Response.Error<ResponseError>).cause.exception
        assertTrue(exception is CustomException)
        assertEquals(403, (exception as CustomException).code)
    }
}

data class HiveEmptyDto(
    @Json(name = "success") val success: Boolean? = null,
)
