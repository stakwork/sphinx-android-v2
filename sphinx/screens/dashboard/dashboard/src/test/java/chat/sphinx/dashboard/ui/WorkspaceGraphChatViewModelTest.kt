package chat.sphinx.dashboard.ui

import chat.sphinx.dashboard.graphchat.GraphChatHistory
import chat.sphinx.dashboard.graphchat.GraphChatMessage
import chat.sphinx.dashboard.graphchat.GraphChatSseEvent
import chat.sphinx.dashboard.graphchat.GraphChatStreamSource
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceGraphChatViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : CoroutineDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank slug fails closed without starting a stream`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)

        viewModel.load("ws-1", "   ")
        viewModel.send("hello")

        assertTrue(viewModel.error.value)
        assertFalse(viewModel.inFlight.value)
        assertTrue(viewModel.messages.value.isEmpty())
        assertEquals(0, stream.streamCalls)
    }

    @Test
    fun `null slug fails closed without starting a stream`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)

        viewModel.load("ws-1", null)
        viewModel.send("hello")

        assertTrue(viewModel.error.value)
        assertEquals(0, stream.streamCalls)
    }

    @Test
    fun `send appends the user row immediately and blocks the composer`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")

        viewModel.send("  hello  ")

        assertEquals(listOf("hello"), viewModel.messages.value.map { it.content })
        assertEquals(GraphChatMessage.ROLE_USER, viewModel.messages.value.single().role)
        assertTrue(viewModel.inFlight.value)
        assertFalse(viewModel.error.value)
        assertEquals(1, stream.streamCalls)
        assertEquals("slug-1", stream.lastSlug)
        assertEquals(
            listOf(GraphChatMessage(GraphChatMessage.ROLE_USER, "hello")),
            stream.lastMessages,
        )
    }

    @Test
    fun `first token inserts an assistant row that accumulates further tokens`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")

        stream.emit(GraphChatSseEvent.Token("Hel"))
        assertEquals(2, viewModel.messages.value.size)
        assertEquals("Hel", viewModel.messages.value.last().content)
        assertEquals(GraphChatMessage.ROLE_ASSISTANT, viewModel.messages.value.last().role)

        stream.emit(GraphChatSseEvent.Token("lo"))
        assertEquals("Hello", viewModel.messages.value.last().content)
        assertTrue(viewModel.inFlight.value)
    }

    @Test
    fun `tool status updates the bar without adding a processing bubble`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")

        stream.emit(GraphChatSseEvent.ToolStatus("search_graph"))

        assertEquals("search_graph", viewModel.statusDetail.value)
        assertEquals(1, viewModel.messages.value.size)
        assertEquals(GraphChatMessage.ROLE_USER, viewModel.messages.value.single().role)
        assertTrue(viewModel.inFlight.value)
    }

    @Test
    fun `finished persists both messages and re-enables the composer`() = runTest {
        val history = GraphChatHistory()
        val stream = FakeStreamSource()
        val viewModel = viewModel(history, stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        stream.emit(GraphChatSseEvent.Token("Hi"))
        stream.emit(GraphChatSseEvent.Finished)

        assertFalse(viewModel.inFlight.value)
        assertFalse(viewModel.error.value)
        assertEquals(
            listOf("hello", "Hi"),
            viewModel.messages.value.map { it.content },
        )
        assertEquals(
            listOf("hello", "Hi"),
            history.get("ws-1").map { it.content },
        )
    }

    @Test
    fun `error keeps user and already-shown assistant text and offers retry`() = runTest {
        val history = GraphChatHistory()
        val stream = FakeStreamSource()
        val viewModel = viewModel(history, stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        stream.emit(GraphChatSseEvent.Token("Hel"))
        stream.emit(GraphChatSseEvent.Error("boom"))

        assertTrue(viewModel.error.value)
        assertFalse(viewModel.inFlight.value)
        assertEquals(
            listOf("hello", "Hel"),
            viewModel.messages.value.map { it.content },
        )
        assertEquals(
            listOf("hello", "Hel"),
            history.get("ws-1").map { it.content },
        )

        viewModel.retry()
        assertEquals(2, stream.streamCalls)
        assertTrue(viewModel.inFlight.value)
        assertFalse(viewModel.error.value)
    }

    @Test
    fun `cancel stops status and persists already-shown assistant text`() = runTest {
        val history = GraphChatHistory()
        val stream = FakeStreamSource()
        val viewModel = viewModel(history, stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        stream.emit(GraphChatSseEvent.Token("Hel"))

        viewModel.cancelInFlight()

        assertFalse(viewModel.inFlight.value)
        assertEquals(null, viewModel.statusDetail.value)
        assertEquals(
            listOf("hello", "Hel"),
            viewModel.messages.value.map { it.content },
        )
        assertEquals(
            listOf("hello", "Hel"),
            history.get("ws-1").map { it.content },
        )
    }

    @Test
    fun `second send is ignored while a reply is in flight`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        viewModel.send("follow up")

        assertEquals(1, stream.streamCalls)
        assertEquals(listOf("hello"), viewModel.messages.value.map { it.content })
    }

    @Test
    fun `load restores session history for the workspace`() = runTest {
        val history = GraphChatHistory()
        history.append("ws-1", GraphChatMessage(GraphChatMessage.ROLE_USER, "prior"))
        val viewModel = viewModel(history)
        viewModel.load("ws-1", "slug-1")

        assertEquals(listOf("prior"), viewModel.messages.value.map { it.content })
        assertFalse(viewModel.inFlight.value)
        assertFalse(viewModel.error.value)
    }

    @Test
    fun `follow-up after finish appends a new user row and starts another stream`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        stream.emit(GraphChatSseEvent.Token("Hi"))
        stream.emit(GraphChatSseEvent.Finished)

        viewModel.send("again")

        assertEquals(2, stream.streamCalls)
        assertTrue(viewModel.inFlight.value)
        assertEquals(
            listOf("hello", "Hi", "again"),
            viewModel.messages.value.map { it.content },
        )
    }

    @Test
    fun `switching workspace cancels in-flight and restores isolated history`() = runTest {
        val history = GraphChatHistory()
        history.append("ws-2", GraphChatMessage(GraphChatMessage.ROLE_USER, "other"))
        val stream = FakeStreamSource()
        val viewModel = viewModel(history, stream)
        viewModel.load("ws-1", "slug-1")
        viewModel.send("hello")
        stream.emit(GraphChatSseEvent.Token("Hel"))

        viewModel.load("ws-2", "slug-2")

        assertFalse(viewModel.inFlight.value)
        assertEquals(listOf("other"), viewModel.messages.value.map { it.content })
        assertEquals(
            listOf("hello", "Hel"),
            history.get("ws-1").map { it.content },
        )
    }

    @Test
    fun `blank send is ignored`() = runTest {
        val stream = FakeStreamSource()
        val viewModel = viewModel(stream = stream)
        viewModel.load("ws-1", "slug-1")

        viewModel.send("   ")

        assertEquals(0, stream.streamCalls)
        assertTrue(viewModel.messages.value.isEmpty())
        assertFalse(viewModel.inFlight.value)
    }

    private fun viewModel(
        history: GraphChatHistory = GraphChatHistory(),
        stream: GraphChatStreamSource = FakeStreamSource(),
    ): WorkspaceGraphChatViewModel {
        return WorkspaceGraphChatViewModel(
            dispatchers = dispatchers,
            history = history,
            streamSource = stream,
        )
    }

    private class FakeStreamSource : GraphChatStreamSource {
        var streamCalls = 0
        var lastSlug: String? = null
        var lastMessages: List<GraphChatMessage>? = null

        private val events = MutableSharedFlow<GraphChatSseEvent>(extraBufferCapacity = 16)

        override fun stream(
            workspaceSlug: String?,
            messages: List<GraphChatMessage>,
        ): Flow<GraphChatSseEvent> {
            streamCalls++
            lastSlug = workspaceSlug
            lastMessages = messages
            return flow {
                events.collect { event ->
                    emit(event)
                }
            }
        }

        suspend fun emit(event: GraphChatSseEvent) {
            events.emit(event)
        }
    }
}
