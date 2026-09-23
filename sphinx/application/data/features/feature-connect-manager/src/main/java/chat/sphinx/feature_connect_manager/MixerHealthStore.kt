package chat.sphinx.feature_connect_manager

import android.util.Log
import chat.sphinx.example.wrapper_mqtt.MixerHealth
import chat.sphinx.example.wrapper_mqtt.MixerHealthTracker
import chat.sphinx.example.wrapper_mqtt.MixerHealthUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.sphinxrs.ServerHealth
import uniffi.sphinxrs.ServerStatus
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Android health store beside ConnectManagerImpl.
 *
 * Holds the last parsed [ServerStatus] on this side only. Snapshot writes,
 * including the StateFlow update, happen under the tracker lock before
 * [onHeartbeat] returns, so a staleness tick cannot observe an in-flight
 * heartbeat. [evaluate] is not called until the first successful parse.
 */
class MixerHealthStore(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val evaluate: (ServerStatus, Long, Long) -> ServerHealth = { last, lastSeenMs, nowMs ->
        uniffi.sphinxrs.evaluateServerHealth(
            last,
            lastSeenMs.toULong(),
            nowMs.toULong(),
            STALE_INTERVAL_MS.toULong(),
            STALE_MAX_MISSED
        )
    },
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "mixer-health").apply { isDaemon = true }
        },
    private val onUi: (MixerHealthUi) -> Unit = {}
) {
    private val emissions = MutableStateFlow(MixerHealthUi.Default)
    val health: StateFlow<MixerHealthUi> = emissions.asStateFlow()

    private var loggedHealth: MixerHealth? = null
    private var graceFuture: ScheduledFuture<*>? = null
    private var staleFuture: ScheduledFuture<*>? = null

    private val tracker = MixerHealthTracker(
        clock = clock,
        evaluate = { status, lastSeenMs, nowMs ->
            evaluate(status as ServerStatus, lastSeenMs, nowMs).toMixerHealth()
        },
        onUi = { ui ->
            logTransition(ui)
            if (emissions.value != ui) {
                emissions.value = ui
            }
            onUi(ui)
        }
    )

    fun nextEventId(): Long = tracker.nextEventId()

    fun currentSnapshot(): MixerHealthUi = tracker.currentSnapshot()

    fun hasReceivedHeartbeat(): Boolean = tracker.hasReceivedHeartbeat()

    fun onConnected() {
        cancelGrace()
        tracker.onConnected()
        scheduleGrace()
        scheduleStale()
    }

    fun onConnectionLost() {
        cancelGrace()
        tracker.onConnectionLost()
        scheduleStale()
    }

    fun resetAccount() {
        cancelGrace()
        cancelStale()
        tracker.resetAccount()
    }

    fun onHeartbeat(status: ServerStatus, seenAtMs: Long = clock()) {
        cancelGrace()
        tracker.onHeartbeat(status, seenAtMs)
    }

    fun onParseFailure() {
        tracker.onParseFailure()
    }

    fun onStaleTick(nowMs: Long = clock()) {
        tracker.onStaleTick(nowMs)
    }

    fun onGraceExpired(nowMs: Long = clock()) {
        tracker.onGraceExpired(nowMs)
    }

    fun isStaleCheckRunning(): Boolean {
        val future = staleFuture
        return future != null && !future.isCancelled && !future.isDone
    }

    fun isGraceScheduled(): Boolean {
        val future = graceFuture
        return future != null && !future.isCancelled && !future.isDone
    }

    fun shutdown() {
        cancelGrace()
        cancelStale()
        scheduler.shutdownNow()
    }

    private fun scheduleGrace() {
        cancelGrace()
        graceFuture = scheduler.schedule(
            { onGraceExpired() },
            GRACE_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun scheduleStale() {
        val existing = staleFuture
        if (existing != null && !existing.isCancelled && !existing.isDone) {
            return
        }
        staleFuture = scheduler.scheduleAtFixedRate(
            { onStaleTick() },
            STALE_INTERVAL_MS,
            STALE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun cancelGrace() {
        graceFuture?.cancel(false)
        graceFuture = null
    }

    private fun cancelStale() {
        staleFuture?.cancel(false)
        staleFuture = null
    }

    private fun logTransition(ui: MixerHealthUi) {
        if (loggedHealth != ui.health) {
            Log.d(LOG_TAG, "mixer health ${loggedHealth ?: "none"} -> ${ui.health}")
            loggedHealth = ui.health
        }
    }

    companion object {
        const val STALE_INTERVAL_MS: Long = 30_000L
        const val STALE_MAX_MISSED: UInt = 1u
        const val GRACE_MS: Long = 15_000L
        const val LOG_TAG = "MQTT_MESSAGES"
    }
}

internal fun ServerHealth.toMixerHealth(): MixerHealth {
    return when (this) {
        ServerHealth.OK -> MixerHealth.OK
        ServerHealth.DEGRADED -> MixerHealth.DEGRADED
        ServerHealth.UNKNOWN -> MixerHealth.UNKNOWN
    }
}
