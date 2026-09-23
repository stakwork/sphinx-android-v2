package chat.sphinx.feature_connect_manager

import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Instrumented test for [MixerHealthStore]'s timer ownership contract. Runs
 * against the real `ScheduledExecutorService` (no fakes), verifying that
 * `resetMQTT`/connection loss must not cancel the repeating staleness check,
 * while `resetAccount` (logout) must cancel both timers.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class MixerHealthStoreTest {

    private lateinit var store: MixerHealthStore

    @Before
    fun setUp() {
        store = MixerHealthStore()
    }

    @After
    fun tearDown() {
        store.shutdown()
    }

    @Test
    fun onConnectedSchedulesGraceAndStaleCheck() {
        store.onConnected()

        assertTrue(store.isGraceScheduled())
        assertTrue(store.isStaleCheckRunning())
    }

    @Test
    fun onConnectionLostCancelsGraceButKeepsStaleCheckRunning() {
        store.onConnected()
        store.onConnectionLost()

        assertTrue(store.isStaleCheckRunning())
        assertFalse(store.isGraceScheduled())
    }

    @Test
    fun resetAccountCancelsBothTimers() {
        store.onConnected()
        store.resetAccount()

        assertFalse(store.isStaleCheckRunning())
        assertFalse(store.isGraceScheduled())
    }
}
