package chat.sphinx.example.wrapper_mqtt

/**
 * Exact-equality predicate for the mixer health topic.
 * Never substring-match: a topic that merely contains the health topic
 * (or "ping") must not be treated as a heartbeat.
 */
fun isServerStatusTopic(topic: String?, healthTopic: String): Boolean {
    return topic != null && topic == healthTopic
}
