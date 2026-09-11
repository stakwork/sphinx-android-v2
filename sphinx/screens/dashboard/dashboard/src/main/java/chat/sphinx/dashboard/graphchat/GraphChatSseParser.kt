package chat.sphinx.dashboard.graphchat

import com.squareup.moshi.Moshi
import okio.BufferedSource

/**
 * Spec-tolerant SSE parser for Hive `POST /api/ask/quick`.
 *
 * Buffers incomplete trailing reads so a payload split across chunks is not dropped.
 * Dispatches on a blank line, and also flushes a pending event at EOF if there is
 * no terminating blank. Silently ignores unrecognized event types.
 */
class GraphChatSseParser(
    private val moshi: Moshi = Moshi.Builder().build(),
) {

    fun parse(text: String): List<GraphChatSseEvent> = parseChunks(listOf(text))

    fun parseChunks(chunks: List<String>): List<GraphChatSseEvent> {
        val session = Session()
        val events = mutableListOf<GraphChatSseEvent>()
        for (chunk in chunks) {
            events.addAll(session.pushText(chunk))
            if (session.isFinished) break
        }
        if (!session.isFinished) {
            events.addAll(session.finish())
        }
        return events
    }

    fun parse(source: BufferedSource, onEvent: (GraphChatSseEvent) -> Boolean) {
        val session = Session()
        while (!session.isFinished) {
            val line = source.readUtf8Line() ?: break
            val event = session.acceptLine(line) ?: continue
            if (!onEvent(event)) return
        }
        if (!session.isFinished) {
            for (event in session.finish()) {
                if (!onEvent(event)) return
            }
        }
    }

    inner class Session {
        private var eventField: String? = null
        private val dataLines = mutableListOf<String>()
        private var remainder = ""
        var isFinished: Boolean = false
            private set

        fun pushText(chunk: String): List<GraphChatSseEvent> {
            if (isFinished) return emptyList()
            remainder += chunk
            val events = mutableListOf<GraphChatSseEvent>()
            while (!isFinished) {
                val newline = remainder.indexOf('\n')
                if (newline < 0) break
                var line = remainder.substring(0, newline)
                remainder = remainder.substring(newline + 1)
                if (line.endsWith('\r')) {
                    line = line.substring(0, line.length - 1)
                }
                acceptLine(line)?.let { events.add(it) }
            }
            return events
        }

        fun acceptLine(line: String): GraphChatSseEvent? {
            if (isFinished) return null
            if (line.startsWith(":")) return null
            if (line.isEmpty()) return flush()
            when {
                line.startsWith("event:") -> {
                    eventField = stripFieldValue(line, "event:")
                }
                line.startsWith("data:") -> {
                    dataLines.add(stripFieldValue(line, "data:"))
                }
            }
            return null
        }

        fun finish(): List<GraphChatSseEvent> {
            if (isFinished) return emptyList()
            val events = mutableListOf<GraphChatSseEvent>()
            if (remainder.isNotEmpty()) {
                var line = remainder
                remainder = ""
                if (line.endsWith('\r')) {
                    line = line.substring(0, line.length - 1)
                }
                acceptLine(line)?.let { events.add(it) }
            }
            if (!isFinished) {
                flush()?.let { events.add(it) }
            }
            isFinished = true
            return events
        }

        private fun flush(): GraphChatSseEvent? {
            val data = dataLines.joinToString("\n")
            val field = eventField
            dataLines.clear()
            eventField = null
            if (data.isEmpty()) return null
            val event = mapEvent(data, field) ?: return null
            if (event is GraphChatSseEvent.Finished || event is GraphChatSseEvent.Error) {
                isFinished = true
            }
            return event
        }
    }

    fun newSession(): Session = Session()

    private fun stripFieldValue(line: String, prefix: String): String {
        val value = line.substring(prefix.length)
        return if (value.startsWith(' ')) value.substring(1) else value
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapEvent(data: String, sseEvent: String?): GraphChatSseEvent? {
        val trimmed = data.trim()
        if (trimmed == DONE_PAYLOAD) {
            return GraphChatSseEvent.Finished
        }

        val parsed = try {
            moshi.adapter(Any::class.java).fromJson(trimmed)
        } catch (_: Exception) {
            return null
        }

        val json = parsed as? Map<*, *> ?: return null
        val jsonType = stringValue(json, "type")
        val sseType = sseEvent
            ?.takeIf { it.isNotBlank() && it != DEFAULT_SSE_EVENT }
        val type = jsonType?.takeIf { it.isNotBlank() } ?: sseType.orEmpty()

        return when (type) {
            TYPE_TEXT_DELTA, TYPE_DELTA, TYPE_TEXT -> {
                val text = stringValue(json, "delta") ?: stringValue(json, "text")
                text?.let { GraphChatSseEvent.Token(it) }
            }
            TYPE_TOOL_INPUT_AVAILABLE,
            TYPE_TOOL_CALL,
            TYPE_TOOL_OUTPUT_AVAILABLE -> {
                GraphChatSseEvent.ToolStatus(toolName(json))
            }
            TYPE_FINISH, TYPE_DONE -> GraphChatSseEvent.Finished
            TYPE_ERROR -> {
                GraphChatSseEvent.Error(
                    stringValue(json, "message") ?: stringValue(json, "error")
                )
            }
            TYPE_TEXT_START,
            TYPE_REASONING_START,
            TYPE_REASONING_DELTA,
            TYPE_REASONING_END,
            TYPE_DATA_USAGE -> null
            else -> {
                if (type.isEmpty()) {
                    val text = stringValue(json, "delta") ?: stringValue(json, "text")
                    text?.let { GraphChatSseEvent.Token(it) }
                } else {
                    null
                }
            }
        }
    }

    private fun toolName(json: Map<*, *>): String? {
        stringValue(json, "name")?.let { return it }
        stringValue(json, "toolName")?.let { return it }
        stringValue(json, "tool_name")?.let { return it }
        stringValue(json, "tool")?.let { return it }
        val toolCall = json["toolCall"] as? Map<*, *>
        return stringValue(toolCall, "name")
    }

    private fun stringValue(json: Map<*, *>?, key: String): String? {
        if (json == null) return null
        val value = json[key] ?: return null
        return when (value) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> null
        }
    }

    companion object {
        private const val DONE_PAYLOAD = "[DONE]"
        private const val DEFAULT_SSE_EVENT = "message"

        private const val TYPE_TEXT_DELTA = "text-delta"
        private const val TYPE_DELTA = "delta"
        private const val TYPE_TEXT = "text"
        private const val TYPE_TOOL_INPUT_AVAILABLE = "tool-input-available"
        private const val TYPE_TOOL_CALL = "tool-call"
        private const val TYPE_TOOL_OUTPUT_AVAILABLE = "tool-output-available"
        private const val TYPE_FINISH = "finish"
        private const val TYPE_DONE = "done"
        private const val TYPE_ERROR = "error"
        private const val TYPE_TEXT_START = "text-start"
        private const val TYPE_REASONING_START = "reasoning-start"
        private const val TYPE_REASONING_DELTA = "reasoning-delta"
        private const val TYPE_REASONING_END = "reasoning-end"
        private const val TYPE_DATA_USAGE = "data-usage"
    }
}
