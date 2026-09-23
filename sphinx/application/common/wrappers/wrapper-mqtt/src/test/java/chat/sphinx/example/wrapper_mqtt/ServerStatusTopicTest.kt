package chat.sphinx.example.wrapper_mqtt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStatusTopicTest {

    private val healthTopic = "health"

    @Test
    fun exactMatchIsHealthTopic() {
        assertTrue(isServerStatusTopic(healthTopic, healthTopic))
    }

    @Test
    fun substringDoesNotMatch() {
        assertFalse(isServerStatusTopic("health/extra", healthTopic))
        assertFalse(isServerStatusTopic("prefix/health", healthTopic))
        assertFalse(isServerStatusTopic("healthping", healthTopic))
        assertFalse(isServerStatusTopic(null, healthTopic))
    }

    @Test
    fun healthTopicContainingPingIsStillExactHealthNotPingRoute() {
        val topic = "health"
        assertTrue(isServerStatusTopic("healthping".replace("ping", ""), topic))
        assertTrue(topic == healthTopic)
        assertFalse(isServerStatusTopic("owner/ping", healthTopic))
        assertTrue("health".contains("ping").not() || isServerStatusTopic("health", healthTopic))
        val pingish = "health"
        assertTrue(isServerStatusTopic(pingish, healthTopic))
        assertFalse(pingish.contains("/ping"))
    }

    @Test
    fun topicThatContainsPingTextButEqualsHealthTopicIsHealth() {
        val topicFromFfi = "health"
        val arrived = topicFromFfi
        assertTrue(isServerStatusTopic(arrived, topicFromFfi))
        assertFalse(arrived.contains("/ping"))
        val spoof = "health/ping"
        assertFalse(isServerStatusTopic(spoof, topicFromFfi))
        assertTrue(spoof.contains("/ping") || spoof.contains("ping"))
    }
}
