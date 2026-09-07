package chat.sphinx.example.concept_connect_manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairTopicsWithPayloadsUnitTest {

    @Test
    fun `equal length lists pair correctly and in order`() {
        val topics = listOf("topic/a", "topic/b", "topic/c")
        val payloads = listOf(
            byteArrayOf(1),
            byteArrayOf(2, 3),
            byteArrayOf(4, 5, 6)
        )

        val result = pairTopicsWithPayloads(topics, payloads)

        assertTrue(result.isSuccess)
        val pairs = result.getOrThrow()
        assertEquals(3, pairs.size)

        assertEquals("topic/a", pairs[0].first)
        assertTrue(pairs[0].second.contentEquals(byteArrayOf(1)))

        assertEquals("topic/b", pairs[1].first)
        assertTrue(pairs[1].second.contentEquals(byteArrayOf(2, 3)))

        assertEquals("topic/c", pairs[2].first)
        assertTrue(pairs[2].second.contentEquals(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun `payloads shorter than topics returns failure`() {
        val topics = listOf("topic/a", "topic/b", "topic/c")
        val payloads = listOf(
            byteArrayOf(1),
            byteArrayOf(2)
        )

        val result = pairTopicsWithPayloads(topics, payloads)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(
            "Cannot publish: topics.size (3) does not match payloads.size (2)",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `payloads longer than topics returns failure`() {
        val topics = listOf("topic/a")
        val payloads = listOf(
            byteArrayOf(1),
            byteArrayOf(2)
        )

        val result = pairTopicsWithPayloads(topics, payloads)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(
            "Cannot publish: topics.size (1) does not match payloads.size (2)",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `both empty lists returns an empty success`() {
        val result = pairTopicsWithPayloads(emptyList(), emptyList())

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun `single topic and payload pairs correctly`() {
        val payload = "payload".toByteArray(Charsets.UTF_8)

        val result = pairTopicsWithPayloads(listOf("topic/single"), listOf(payload))

        assertTrue(result.isSuccess)
        val pairs = result.getOrThrow()
        assertEquals(1, pairs.size)
        assertEquals("topic/single", pairs[0].first)
        assertTrue(pairs[0].second.contentEquals(payload))
    }
}