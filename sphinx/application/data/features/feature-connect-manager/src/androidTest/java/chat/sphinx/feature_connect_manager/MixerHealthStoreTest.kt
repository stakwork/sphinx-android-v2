package chat.sphinx.feature_connect_manager

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * Exercises the real [MixerHealthStore] timers (grace/stale scheduling)
 * against a real [java.util.concurrent.ScheduledExecutorService], rather than
 * a fake contract, since the store's own daemon-thread scheduling is what
 * production code actually depends on.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MixerHealthStoreTest {

    private val store = MixerHealthStore()

    @After
    fun teardown() {
        store.shutdown()
    }

    @Test
    fun onConnectedSchedulesGraceAndStaleCheck() {
        store.onConnected()
        assertTrue(store.isGraceScheduled())
        assertTrue(store.isStaleCheckRunning())
    }

    @Test
    fun connectionLostCancelsGraceButKeepsStaleCheckRunning() {
        store.onConnected()
        store.onConnectionLost()
        assertTrue(store.isStaleCheckRunning())
        assertFalse(store.isGraceScheduled())
    }

    @Test
    fun resetAccountCancelsBothGraceAndStaleCheck() {
        store.onConnected()
        store.resetAccount()
        assertFalse(store.isStaleCheckRunning())
        assertFalse(store.isGraceScheduled())
    }
}
