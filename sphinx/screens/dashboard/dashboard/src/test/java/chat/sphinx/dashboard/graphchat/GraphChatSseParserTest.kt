package chat.sphinx.dashboard.graphchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphChatSseParserTest {

    private val parser = GraphChatSseParser()

    @Test
    fun `text-delta emits Token`() {
        val events = parser.parse(sseData("""{"type":"text-delta","delta":"Hello"}"""))
        assertEquals(listOf(GraphChatSseEvent.Token("Hello")), events)
    }

    @Test
    fun `bare delta and text fields emit Token`() {
        val events = parser.parse(
            sseData("""{"delta":"Hi"}""") + sseData("""{"text":" there"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("Hi"),
                GraphChatSseEvent.Token(" there"),
            ),
            events,
        )
    }

    @Test
    fun `type delta and type text emit Token`() {
        val events = parser.parse(
            sseData("""{"type":"delta","delta":"A"}""") +
                sseData("""{"type":"text","text":"B"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("A"),
                GraphChatSseEvent.Token("B"),
            ),
            events,
        )
    }

    @Test
    fun `tool-input-available and tool-call emit ToolStatus with name`() {
        val events = parser.parse(
            sseData("""{"type":"tool-input-available","toolName":"search"}""") +
                sseData("""{"type":"tool-call","name":"lookup"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.ToolStatus("search"),
                GraphChatSseEvent.ToolStatus("lookup"),
            ),
            events,
        )
    }

    @Test
    fun `tool-output-available emits ToolStatus`() {
        val events = parser.parse(sseData("""{"type":"tool-output-available"}"""))
        assertEquals(listOf(GraphChatSseEvent.ToolStatus(null)), events)
    }

    @Test
    fun `finish and done emit Finished`() {
        assertEquals(
            listOf(GraphChatSseEvent.Finished),
            parser.parse(sseData("""{"type":"finish"}""")),
        )
        assertEquals(
            listOf(GraphChatSseEvent.Finished),
            parser.parse(sseData("""{"type":"done"}""")),
        )
    }

    @Test
    fun `error event emits Error with message`() {
        val events = parser.parse(sseData("""{"type":"error","message":"boom"}"""))
        assertEquals(listOf(GraphChatSseEvent.Error("boom")), events)
    }

    @Test
    fun `literal DONE payload emits Finished`() {
        val events = parser.parse("data: [DONE]\n\n")
        assertEquals(listOf(GraphChatSseEvent.Finished), events)
    }

    @Test
    fun `ignored types are skipped`() {
        val events = parser.parse(
            sseData("""{"type":"text-start"}""") +
                sseData("""{"type":"reasoning-start"}""") +
                sseData("""{"type":"reasoning-delta","delta":"think"}""") +
                sseData("""{"type":"data-usage"}""") +
                sseData("""{"type":"text-delta","delta":"ok"}""") +
                sseData("""{"type":"finish"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("ok"),
                GraphChatSseEvent.Finished,
            ),
            events,
        )
    }

    @Test
    fun `comment keepalive lines are ignored`() {
        val events = parser.parse(
            ": ping\n\n" +
                sseData("""{"type":"text-delta","delta":"Hi"}""") +
                ": keepalive\n" +
                sseData("""{"type":"finish"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("Hi"),
                GraphChatSseEvent.Finished,
            ),
            events,
        )
    }

    @Test
    fun `event field line is honored when json type is missing`() {
        val events = parser.parse(
            "event: text-delta\ndata: {\"delta\":\"Hi\"}\n\n" +
                "event: finish\ndata: {}\n\n"
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("Hi"),
                GraphChatSseEvent.Finished,
            ),
            events,
        )
    }

    @Test
    fun `data payload split across two reads is not dropped`() {
        val events = parser.parseChunks(
            listOf(
                "data: {\"type\":\"text-delta\",\"del",
                "ta\":\"Hello world\"}\n\n",
            )
        )
        assertEquals(listOf(GraphChatSseEvent.Token("Hello world")), events)
    }

    @Test
    fun `incomplete trailing line without final blank is flushed at EOF`() {
        val events = parser.parse("""data: {"type":"finish"}""")
        assertEquals(listOf(GraphChatSseEvent.Finished), events)
    }

    @Test
    fun `DONE terminates and later events are not parsed`() {
        val events = parser.parse(
            sseData("""{"type":"text-delta","delta":"Hi"}""") +
                "data: [DONE]\n\n" +
                sseData("""{"type":"text-delta","delta":"ignored"}""")
        )
        assertEquals(
            listOf(
                GraphChatSseEvent.Token("Hi"),
                GraphChatSseEvent.Finished,
            ),
            events,
        )
    }

    @Test
    fun `unrecognized types are ignored rather than failing`() {
        val events = parser.parse(
            sseData("""{"type":"mystery","delta":"nope"}""") +
                sseData("""{"type":"text-delta","delta":"ok"}""")
        )
        assertEquals(listOf(GraphChatSseEvent.Token("ok")), events)
        assertTrue(events.none { it is GraphChatSseEvent.Error })
    }

    @Test
    fun `consecutive data lines are concatenated into one payload`() {
        val events = parser.parse(
            "data: {\"type\":\"text-delta\",\"delta\":\n" +
                "data: \"Hi\"}\n\n"
        )
        assertEquals(listOf(GraphChatSseEvent.Token("Hi")), events)
    }

    private fun sseData(json: String): String = "data: $json\n\n"
}
