package chat.sphinx.example.wrapper_mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Store policy without JNI. The Android store delegates snapshot writes here
 * and only adds the FFI evaluator plus timers.
 */
class MixerHealthTrackerTest {

    private class FakeClock(var now: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private class RecordingEvaluator : (Any, Long, Long) -> MixerHealth {
        val calls = AtomicInteger(0)
        var next: MixerHealth = MixerHealth.OK
        override fun invoke(status: Any, lastSeenMs: Long, nowMs: Long): MixerHealth {
            calls.incrementAndGet()
            return next
        }
    }

    @Test
    fun startsUnknownHiddenAndDoesNotEvaluate() {
        val evaluator = RecordingEvaluator()
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = evaluator)
        assertEquals(MixerHealthUi.Default, tracker.currentSnapshot())
        assertEquals(0, evaluator.calls.get())
        assertFalse(tracker.hasReceivedHeartbeat())
    }

    @Test
    fun connectDoesNotPromoteToOkOrEvaluate() {
        val evaluator = RecordingEvaluator()
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = evaluator)
        tracker.onConnected()
        assertEquals(MixerHealth.UNKNOWN, tracker.currentSnapshot().health)
        assertFalse(tracker.currentSnapshot().showBanner)
        tracker.onStaleTick()
        assertEquals(0, evaluator.calls.get())
    }

    @Test
    fun stalenessUsesThirtySecondWindowThenRecovers() {
        val clock = FakeClock(10_000L)
        val evaluator = RecordingEvaluator()
        val tracker = MixerHealthTracker(clock = clock, evaluate = evaluator)
        tracker.onConnected()
        tracker.onHeartbeat("beat", seenAtMs = clock.now)
        assertEquals(1, evaluator.calls.get())
        assertEquals(MixerHealth.OK, tracker.currentSnapshot().health)
        assertEquals(30_000L, MixerHealthTracker.STALE_INTERVAL_MS)
        assertEquals(1, MixerHealthTracker.STALE_MAX_MISSED)

        evaluator.next = MixerHealth.DEGRADED
        clock.now += MixerHealthTracker.STALE_INTERVAL_MS
        tracker.onStaleTick(clock.now)
        assertEquals(MixerHealth.DEGRADED, tracker.currentSnapshot().health)
        assertTrue(tracker.currentSnapshot().showBanner)

        evaluator.next = MixerHealth.OK
        tracker.onHeartbeat("beat-2", seenAtMs = clock.now + 1)
        assertEquals(MixerHealth.OK, tracker.currentSnapshot().health)
        assertFalse(tracker.currentSnapshot().showBanner)
    }

    @Test
    fun parseFailureIsNotAHeartbeat() {
        val evaluator = RecordingEvaluator()
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = evaluator)
        tracker.onConnected()
        tracker.onParseFailure()
        assertFalse(tracker.hasReceivedHeartbeat())
        assertEquals(0, evaluator.calls.get())
        tracker.onStaleTick()
        assertEquals(0, evaluator.calls.get())
        assertEquals(MixerHealth.UNKNOWN, tracker.currentSnapshot().health)
    }

    @Test
    fun connectionLostShowsUnknownImmediately() {
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = RecordingEvaluator())
        tracker.onConnected()
        tracker.onConnectionLost()
        assertEquals(MixerHealth.UNKNOWN, tracker.currentSnapshot().health)
        assertTrue(tracker.currentSnapshot().showBanner)
        assertTrue(tracker.hasReceivedHeartbeat())
    }

    @Test
    fun resetAccountClearsHeartbeatAndHidesBanner() {
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = RecordingEvaluator())
        tracker.onConnected()
        tracker.onHeartbeat("beat")
        tracker.resetAccount()
        assertEquals(MixerHealthUi.Default, tracker.currentSnapshot())
        assertFalse(tracker.hasReceivedHeartbeat())
    }

    @Test
    fun graceExpiryFlipsShowBanner() {
        val clock = FakeClock(5_000L)
        val emissions = mutableListOf<MixerHealthUi>()
        val tracker = MixerHealthTracker(
            clock = clock,
            evaluate = RecordingEvaluator(),
            onUi = { emissions.add(it) }
        )
        tracker.onConnected()
        assertFalse(tracker.currentSnapshot().showBanner)
        clock.now = 5_000L + HEALTH_GRACE_MS
        tracker.onGraceExpired(clock.now)
        assertEquals(MixerHealth.UNKNOWN, tracker.currentSnapshot().health)
        assertTrue(tracker.currentSnapshot().showBanner)
        assertTrue(emissions.size >= 2)
        assertNotEquals(emissions.first(), emissions.last())
    }

    @Test
    fun degradedDuringGraceShowsImmediately() {
        val evaluator = RecordingEvaluator()
        evaluator.next = MixerHealth.DEGRADED
        val tracker = MixerHealthTracker(clock = FakeClock(0L), evaluate = evaluator)
        tracker.onConnected()
        tracker.onHeartbeat("beat", seenAtMs = 100L)
        assertTrue(tracker.currentSnapshot().showBanner)
        assertEquals(MixerHealth.DEGRADED, tracker.currentSnapshot().health)
    }

    @Test
    fun laterUnknownAfterHeartbeatShowsImmediately() {
        val evaluator = RecordingEvaluator()
        val tracker = MixerHealthTracker(clock = FakeClock(), evaluate = evaluator)
        tracker.onConnected()
        tracker.onHeartbeat("beat")
        evaluator.next = MixerHealth.UNKNOWN
        tracker.onHeartbeat("beat-2", seenAtMs = 2_000L)
        assertTrue(tracker.currentSnapshot().showBanner)
        assertEquals(MixerHealth.UNKNOWN, tracker.currentSnapshot().health)
    }

    @Test
    fun heartbeatWriteBlocksConcurrentTick() {
        val clock = FakeClock(50_000L)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val evaluator = object : (Any, Long, Long) -> MixerHealth {
            override fun invoke(status: Any, lastSeenMs: Long, nowMs: Long): MixerHealth {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
                return MixerHealth.OK
            }
        }
        val tracker = MixerHealthTracker(clock = clock, evaluate = evaluator)
        tracker.onConnected()
        val pool = Executors.newFixedThreadPool(2)
        pool.submit { tracker.onHeartbeat("beat", clock.now) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        val tickDone = CountDownLatch(1)
        var sawHeartbeat = false
        pool.submit {
            tracker.onStaleTick(clock.now + 30_000L)
            sawHeartbeat = tracker.hasReceivedHeartbeat()
            tickDone.countDown()
        }
        Thread.sleep(40)
        assertEquals(1, tickDone.count)
        release.countDown()
        assertTrue(tickDone.await(2, TimeUnit.SECONDS))
        assertTrue(sawHeartbeat)
        assertEquals(MixerHealth.OK, tracker.currentSnapshot().health)
        pool.shutdownNow()
    }
}
