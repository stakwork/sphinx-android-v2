package chat.sphinx.example.wrapper_mqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerHealthPolicyTest {

    @Test
    fun startsUnknownWithBannerHidden() {
        val ui = MixerHealthUi.Default
        assertEquals(MixerHealth.UNKNOWN, ui.health)
        assertFalse(ui.showBanner)
    }

    @Test
    fun sessionNotStartedHidesBanner() {
        val ui = displayHealth(
            sessionStarted = false,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = 1_000L
        )
        assertFalse(ui.showBanner)
        assertEquals(MixerHealth.UNKNOWN, ui.health)
    }

    @Test
    fun connectAloneNeverPromotesToOk() {
        val ui = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = 1_000L
        )
        assertEquals(MixerHealth.UNKNOWN, ui.health)
        assertFalse(ui.showBanner)
    }

    @Test
    fun unknownHiddenOnlyDuringGraceBeforeFirstHeartbeat() {
        val hidden = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = HEALTH_GRACE_MS - 1
        )
        assertFalse(hidden.showBanner)

        val shown = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = HEALTH_GRACE_MS
        )
        assertTrue(shown.showBanner)
        assertEquals(MixerHealth.UNKNOWN, shown.health)
    }

    @Test
    fun degradedShowsImmediatelyDuringGrace() {
        val ui = displayHealth(
            sessionStarted = true,
            health = MixerHealth.DEGRADED,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = 500L
        )
        assertTrue(ui.showBanner)
        assertEquals(MixerHealth.DEGRADED, ui.health)
    }

    @Test
    fun laterUnknownAfterHeartbeatShowsImmediately() {
        val ui = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = true,
            sessionStartedAtMs = 0L,
            nowMs = 1_000L
        )
        assertTrue(ui.showBanner)
    }

    @Test
    fun okHidesBanner() {
        val ui = displayHealth(
            sessionStarted = true,
            health = MixerHealth.OK,
            hasReceivedHeartbeat = true,
            sessionStartedAtMs = 0L,
            nowMs = 20_000L
        )
        assertFalse(ui.showBanner)
        assertEquals(MixerHealth.OK, ui.health)
    }

    @Test
    fun graceExpiryFlipsShowBannerWhileHealthStaysUnknown() {
        val before = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = 1_000L
        )
        val after = displayHealth(
            sessionStarted = true,
            health = MixerHealth.UNKNOWN,
            hasReceivedHeartbeat = false,
            sessionStartedAtMs = 0L,
            nowMs = HEALTH_GRACE_MS
        )
        assertEquals(before.health, after.health)
        assertNotEquals(before, after)
        assertTrue(after.showBanner)
    }
}
