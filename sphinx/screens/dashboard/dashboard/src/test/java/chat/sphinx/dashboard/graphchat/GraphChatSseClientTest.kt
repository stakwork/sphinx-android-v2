package chat.sphinx.dashboard.graphchat

import chat.sphinx.concept_network_client.NetworkClient
import chat.sphinx.concept_network_client.NetworkClientClearedListener
import chat.sphinx.logger.LogType
import chat.sphinx.logger.SphinxLogger
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GraphChatSseClientTest {

    private lateinit var server: MockWebServer
    private val recordedCalls = mutableListOf<Call>()
    private val tokenSource = FakeHiveTokenSource()
    private val logger = RecordingLogger()

    private val dispatchers = object : CoroutineDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        recordedCalls.clear()
        tokenSource.reset()
        logger.messages.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `blank workspaceSlug fails closed without a network call`() = runBlocking {
        var getClientCalls = 0
        val networkClient = object : NetworkClient() {
            override suspend fun getClient(): OkHttpClient {
                getClientCalls++
                return OkHttpClient()
            }
            override fun addListener(listener: NetworkClientClearedListener): Boolean = true
            override fun removeListener(listener: NetworkClientClearedListener): Boolean = true
        }
        val client = GraphChatSseClient(
            networkClient,
            tokenSource,
            dispatchers,
            logger,
            server.url("/api/ask/quick").toString(),
        )

        val events = client.stream("", listOf(GraphChatMessage("user", "hi"))).toList()
        assertEquals(listOf(GraphChatSseEvent.Error(GraphChatSseClient.MISSING_SLUG_MESSAGE)), events)
        assertEquals(0, getClientCalls)
        assertEquals(0, server.requestCount)

        val nullEvents = client.stream(null, listOf(GraphChatMessage("user", "hi"))).toList()
        assertEquals(
            listOf(GraphChatSseEvent.Error(GraphChatSseClient.MISSING_SLUG_MESSAGE)),
            nullEvents,
        )
        assertEquals(0, getClientCalls)
    }

    @Test
    fun `streaming builder does not apply the base 15s readTimeout`() = runBlocking {
        val base = OkHttpClient.Builder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .callTimeout(200, TimeUnit.MILLISECONDS)
            .eventListener(callRecorder())
            .build()
        val client = graphClient(base)

        val streaming = client.newStreamingClient(base)
        assertEquals(0, streaming.readTimeoutMillis.toLong())
        assertEquals(0, streaming.callTimeoutMillis)

        server.enqueue(
            sseResponse("data: {\"type\":\"finish\"}\n\n")
                .setBodyDelay(500, TimeUnit.MILLISECONDS)
        )

        val events = withTimeout(5_000) {
            client.stream("acme", listOf(GraphChatMessage("user", "hi"))).toList()
        }
        assertEquals(listOf(GraphChatSseEvent.Finished), events)
    }

    @Test
    fun `401 triggers exactly one re-auth-and-retry then succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        server.enqueue(sseResponse("data: {\"type\":\"text-delta\",\"delta\":\"Hi\"}\n\ndata: [DONE]\n\n"))

        tokenSource.token = "old-token"
        tokenSource.tokenAfterReauth = "new-token"

        val events = graphClient().stream("acme", listOf(GraphChatMessage("user", "hi"))).toList()
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("Hi"),
                GraphChatSseEvent.Finished,
            ),
            events,
        )
        assertEquals(1, tokenSource.reauthenticateCount)
        assertEquals(2, server.requestCount)
        assertEquals("Bearer old-token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer new-token", server.takeRequest().getHeader("Authorization"))
        assertTrue(logger.messages.any { it.contains("401 re-auth-and-retry") && it.contains("statusCode=401") })
    }

    @Test
    fun `second 401 fails closed after one re-auth`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        tokenSource.token = "old-token"
        tokenSource.tokenAfterReauth = "new-token"

        val events = graphClient().stream("acme", listOf(GraphChatMessage("user", "hi"))).toList()
        assertEquals(
            listOf(GraphChatSseEvent.Error(GraphChatSseClient.UNAUTHORIZED_MESSAGE)),
            events,
        )
        assertEquals(1, tokenSource.reauthenticateCount)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `non-2xx non-401 fails closed without opening the parser`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("data: {\"type\":\"text-delta\",\"delta\":\"nope\"}\n\n"))

        val events = graphClient().stream("acme", listOf(GraphChatMessage("user", "hi"))).toList()
        assertEquals(listOf(GraphChatSseEvent.Error("HTTP 500")), events)
        assertEquals(0, tokenSource.reauthenticateCount)
        assertEquals(1, server.requestCount)
        assertTrue(events.none { it is GraphChatSseEvent.Token })
        assertTrue(logger.messages.any { it.contains("HTTP non-2xx") && it.contains("statusCode=500") })
    }

    @Test
    fun `cancelling the collecting job cancels the underlying OkHttp Call`() = runBlocking {
        server.enqueue(
            sseResponse("data: {\"type\":\"finish\"}\n\n")
                .setBodyDelay(30, TimeUnit.SECONDS)
        )

        val events = mutableListOf<GraphChatSseEvent>()
        val job = launch {
            graphClient().stream("acme", listOf(GraphChatMessage("user", "hi"))).collect {
                events.add(it)
            }
        }
        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertTrue(request != null)
        job.cancelAndJoin()

        assertTrue(recordedCalls.isNotEmpty())
        assertTrue(recordedCalls.first().isCanceled())
        assertTrue(events.isEmpty())
        assertTrue(logger.messages.any { it.contains("cancel") })
    }

    @Test
    fun `streaming OkHttp client does not attach a BODY logging interceptor`() {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val base = OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS)
            .addNetworkInterceptor(logging)
            .addInterceptor(logging)
            .build()
        val streaming = graphClient(base).newStreamingClient(base)

        assertTrue(streaming.interceptors.none { it is HttpLoggingInterceptor })
        assertTrue(streaming.networkInterceptors.none { it is HttpLoggingInterceptor })
        assertFalse(
            streaming.networkInterceptors.any {
                it is HttpLoggingInterceptor && it.level == HttpLoggingInterceptor.Level.BODY
            }
        )
    }

    @Test
    fun `request uses ask quick headers and body without conversationId`() = runBlocking {
        server.enqueue(sseResponse("data: [DONE]\n\n"))

        graphClient().stream(
            "acme",
            listOf(
                GraphChatMessage("user", "hello"),
                GraphChatMessage("assistant", "hi"),
            ),
        ).toList()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/ask/quick", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("text/event-stream", request.getHeader("Accept"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"workspaceSlug\":\"acme\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertFalse(body.contains("conversationId"))
    }

    private fun graphClient(
        base: OkHttpClient = OkHttpClient.Builder()
            .eventListener(callRecorder())
            .build(),
    ): GraphChatSseClient {
        val networkClient = object : NetworkClient() {
            override suspend fun getClient(): OkHttpClient = base
            override fun addListener(listener: NetworkClientClearedListener): Boolean = true
            override fun removeListener(listener: NetworkClientClearedListener): Boolean = true
        }
        return GraphChatSseClient(
            networkClient,
            tokenSource,
            dispatchers,
            logger,
            server.url("/api/ask/quick").toString(),
        )
    }

    private fun callRecorder(): EventListener = object : EventListener() {
        override fun callStart(call: Call) {
            recordedCalls.add(call)
        }
    }

    private fun sseResponse(body: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)

    private class FakeHiveTokenSource : HiveTokenSource {
        var token: String? = "token"
        var tokenAfterReauth: String? = "token"
        var reauthenticateCount = 0
        var authenticateCount = 0

        fun reset() {
            token = "token"
            tokenAfterReauth = "token"
            reauthenticateCount = 0
            authenticateCount = 0
        }

        override suspend fun retrieveHiveToken(): String? = token

        override suspend fun authenticateWithHive(): Boolean {
            authenticateCount++
            return token != null
        }

        override suspend fun reauthenticateWithHive(): Boolean {
            reauthenticateCount++
            token = tokenAfterReauth
            return token != null
        }
    }

    private class RecordingLogger : SphinxLogger() {
        val messages = mutableListOf<String>()
        override fun log(tag: String, message: String, type: LogType, throwable: Throwable?) {
            messages.add(message)
        }
    }
}
