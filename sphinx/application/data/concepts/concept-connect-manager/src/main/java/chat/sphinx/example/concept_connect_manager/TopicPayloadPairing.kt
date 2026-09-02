package chat.sphinx.example.concept_connect_manager

/**
 * Pairs MQTT publish topics with their corresponding payloads, preserving order.
 *
 * A `RunReturn` produced by the native library carries `topics: List<String>` and
 * `payloads: List<ByteArray>`, which must be published as matched (topic, payload)
 * pairs. Substituting a default (e.g. empty) payload when the lists differ in size
 * silently publishes garbage to the broker, so a size mismatch is surfaced as a
 * [Result] failure instead.
 *
 * @return
 *  - [Result.success] with the paired (topic, payload) entries in order when
 *    `topics.size == payloads.size` (including the empty/empty case, which
 *    yields an empty list), or
 *  - [Result.failure] when the list sizes differ.
 */
fun pairTopicsWithPayloads(
    topics: List<String>,
    payloads: List<ByteArray>
): Result<List<Pair<String, ByteArray>>> {
    if (topics.size != payloads.size) {
        return Result.failure(
            IllegalStateException(
                "Cannot publish: topics.size (${topics.size}) does not match payloads.size (${payloads.size})"
            )
        )
    }

    return Result.success(
        topics.mapIndexed { index, topic ->
            Pair(topic, payloads[index])
        }
    )
}