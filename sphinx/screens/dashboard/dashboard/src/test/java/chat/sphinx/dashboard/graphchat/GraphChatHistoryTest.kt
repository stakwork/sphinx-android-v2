package chat.sphinx.dashboard.graphchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphChatHistoryTest {

    @Test
    fun `cap at 20 drops the oldest message`() {
        val history = GraphChatHistory()

        repeat(GraphChatHistory.MAX_MESSAGES + 1) { index ->
            history.append("ws-1", GraphChatMessage(GraphChatMessage.ROLE_USER, "m$index"))
        }

        val stored = history.get("ws-1")
        assertEquals(GraphChatHistory.MAX_MESSAGES, stored.size)
        assertEquals("m1", stored.first().content)
        assertEquals("m20", stored.last().content)
    }

    @Test
    fun `workspaces are isolated from each other`() {
        val history = GraphChatHistory()
        history.append("ws-1", GraphChatMessage(GraphChatMessage.ROLE_USER, "one"))
        history.append("ws-2", GraphChatMessage(GraphChatMessage.ROLE_USER, "two"))

        assertEquals(listOf("one"), history.get("ws-1").map { it.content })
        assertEquals(listOf("two"), history.get("ws-2").map { it.content })
    }

    @Test
    fun `get restores the same session messages on revisit`() {
        val history = GraphChatHistory()
        history.append("ws-1", GraphChatMessage(GraphChatMessage.ROLE_USER, "hello"))
        history.append(
            "ws-1",
            GraphChatMessage(GraphChatMessage.ROLE_ASSISTANT, "hi there"),
        )

        val restored = history.get("ws-1")
        assertEquals(2, restored.size)
        assertEquals(GraphChatMessage.ROLE_USER, restored[0].role)
        assertEquals("hello", restored[0].content)
        assertEquals(GraphChatMessage.ROLE_ASSISTANT, restored[1].role)
        assertEquals("hi there", restored[1].content)
    }

    @Test
    fun `replace persists a partial assistant reply`() {
        val history = GraphChatHistory()
        history.replace(
            "ws-1",
            listOf(
                GraphChatMessage(GraphChatMessage.ROLE_USER, "q"),
                GraphChatMessage(GraphChatMessage.ROLE_ASSISTANT, "partial"),
            ),
        )

        assertEquals(
            listOf("q", "partial"),
            history.get("ws-1").map { it.content },
        )
    }

    @Test
    fun `replace caps at 20`() {
        val history = GraphChatHistory()
        val messages = (0 until GraphChatHistory.MAX_MESSAGES + 5).map { index ->
            GraphChatMessage(GraphChatMessage.ROLE_USER, "m$index")
        }

        history.replace("ws-1", messages)

        val stored = history.get("ws-1")
        assertEquals(GraphChatHistory.MAX_MESSAGES, stored.size)
        assertEquals("m5", stored.first().content)
        assertEquals("m24", stored.last().content)
    }

    @Test
    fun `clearAll empties every workspace`() {
        val history = GraphChatHistory()
        history.append("ws-1", GraphChatMessage(GraphChatMessage.ROLE_USER, "one"))
        history.append("ws-2", GraphChatMessage(GraphChatMessage.ROLE_USER, "two"))

        history.clearAll()

        assertTrue(history.get("ws-1").isEmpty())
        assertTrue(history.get("ws-2").isEmpty())
    }
}
