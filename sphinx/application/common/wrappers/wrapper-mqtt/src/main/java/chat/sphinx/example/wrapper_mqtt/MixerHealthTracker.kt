package chat.sphinx.example.wrapper_mqtt

import java.util.concurrent.atomic.AtomicLong

/**
 * JVM health snapshot. The Android store maps FFI [uniffi.sphinxrs.ServerStatus]
 * onto [last] and calls [onHeartbeat]. Evaluator is injected so tests never
 * touch JNI. All snapshot writes, including the listener callback, happen
 * under [lock] before the caller returns.
 */
class MixerHealthTracker(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val evaluate: ((Any, Long, Long) -> MixerHealth)? = null,
    private val onUi: (MixerHealthUi) -> Unit = {}
) {
    private val lock = Any()

    private var sessionStarted: Boolean = false
    private var sessionStartedAtMs: Long = 0L
    private var hasReceivedHeartbeatFlag: Boolean = false
    private var last: Any? = null
    private var lastSeenMs: Long = 0L
    private var current: MixerHealthUi = MixerHealthUi.Default
    private val eventIds = AtomicLong(0L)

    var evaluateCalls: Int = 0
        private set

    fun nextEventId(): Long = eventIds.incrementAndGet()

    fun currentSnapshot(): MixerHealthUi = synchronized(lock) { current }

    fun hasReceivedHeartbeat(): Boolean = synchronized(lock) { hasReceivedHeartbeatFlag }

    fun onConnected() {
        synchronized(lock) {
            sessionStarted = true
            sessionStartedAtMs = clock()
            hasReceivedHeartbeatFlag = false
            last = null
            lastSeenMs = 0L
            publishLocked(MixerHealth.UNKNOWN)
        }
    }

    fun onConnectionLost() {
        synchronized(lock) {
            if (!sessionStarted) {
                sessionStarted = true
                sessionStartedAtMs = clock()
            }
            hasReceivedHeartbeatFlag = true
            last = null
            lastSeenMs = 0L
            publishLocked(MixerHealth.UNKNOWN)
        }
    }

    fun resetAccount() {
        synchronized(lock) {
            sessionStarted = false
            sessionStartedAtMs = 0L
            hasReceivedHeartbeatFlag = false
            last = null
            lastSeenMs = 0L
            publishLocked(MixerHealth.UNKNOWN)
        }
    }

    fun onHeartbeat(status: Any, seenAtMs: Long = clock()) {
        synchronized(lock) {
            if (!sessionStarted) {
                sessionStarted = true
                sessionStartedAtMs = seenAtMs
            }
            hasReceivedHeartbeatFlag = true
            last = status
            lastSeenMs = seenAtMs
            publishLocked(evaluateLocked(status, seenAtMs, seenAtMs))
        }
    }

    fun onParseFailure() {
        synchronized(lock) {
            if (!sessionStarted) {
                sessionStarted = true
                sessionStartedAtMs = clock()
            }
            publishLocked(MixerHealth.UNKNOWN)
        }
    }

    fun onStaleTick(nowMs: Long = clock()) {
        synchronized(lock) {
            if (!sessionStarted || !hasReceivedHeartbeatFlag) {
                return
            }
            val status = last ?: return
            if (lastSeenMs == 0L) {
                return
            }
            publishLocked(evaluateLocked(status, lastSeenMs, nowMs), nowMs)
        }
    }

    fun onGraceExpired(nowMs: Long = clock()) {
        synchronized(lock) {
            if (!sessionStarted || hasReceivedHeartbeatFlag) {
                return
            }
            publishLocked(MixerHealth.UNKNOWN, nowMs)
        }
    }

    private fun evaluateLocked(status: Any, seenAtMs: Long, nowMs: Long): MixerHealth {
        val evaluator = evaluate ?: return MixerHealth.UNKNOWN
        evaluateCalls += 1
        return runCatching { evaluator(status, seenAtMs, nowMs) }
            .getOrDefault(MixerHealth.UNKNOWN)
    }

    private fun publishLocked(health: MixerHealth, nowMs: Long = clock()) {
        val next = displayHealth(
            sessionStarted = sessionStarted,
            health = health,
            hasReceivedHeartbeat = hasReceivedHeartbeatFlag,
            sessionStartedAtMs = sessionStartedAtMs,
            nowMs = nowMs
        )
        current = next
        onUi(next)
    }

    companion object {
        const val STALE_INTERVAL_MS: Long = 30_000L
        const val STALE_MAX_MISSED: Int = 1
    }
}
