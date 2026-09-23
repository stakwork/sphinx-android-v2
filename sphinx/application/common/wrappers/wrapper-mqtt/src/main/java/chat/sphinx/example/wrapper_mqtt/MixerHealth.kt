package chat.sphinx.example.wrapper_mqtt

/**
 * JVM mirror of the FFI [uniffi.sphinxrs.ServerHealth] enum.
 * JVM modules must not reference the Android `resources` types directly.
 */
enum class MixerHealth {
    UNKNOWN,
    DEGRADED,
    OK
}

/**
 * Banner visibility plus the current mixer health.
 * [showBanner] is independent of [health] so launch grace can hide Unknown
 * without pretending the mixer is Ok.
 */
data class MixerHealthUi(
    val health: MixerHealth,
    val showBanner: Boolean
) {
    companion object {
        val Default = MixerHealthUi(MixerHealth.UNKNOWN, false)
    }
}

const val HEALTH_GRACE_MS: Long = 15_000L

/**
 * Banner policy. Pure so unit tests never touch JNI or Android.
 *
 * - Session not started: hide.
 * - Within [graceMs] of connect and no heartbeat yet: hide Unknown only.
 * - Degraded always shows, including during grace.
 * - First heartbeat or grace expiry (whichever first) ends grace.
 * - A later Unknown after a heartbeat shows immediately.
 * - Ok always hides.
 */
fun displayHealth(
    sessionStarted: Boolean,
    health: MixerHealth,
    hasReceivedHeartbeat: Boolean,
    sessionStartedAtMs: Long,
    nowMs: Long,
    graceMs: Long = HEALTH_GRACE_MS
): MixerHealthUi {
    if (!sessionStarted) {
        return MixerHealthUi(health, false)
    }
    val showBanner = when (health) {
        MixerHealth.OK -> false
        MixerHealth.DEGRADED -> true
        MixerHealth.UNKNOWN -> {
            val withinGrace = !hasReceivedHeartbeat &&
                (nowMs - sessionStartedAtMs) < graceMs
            !withinGrace
        }
    }
    return MixerHealthUi(health, showBanner)
}
