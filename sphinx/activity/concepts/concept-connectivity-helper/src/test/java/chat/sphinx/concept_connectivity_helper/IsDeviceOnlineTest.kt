package chat.sphinx.concept_connectivity_helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsDeviceOnlineTest {

    @Test
    fun validatedIsOnlineRegardlessOfTransport() {
        assertTrue(isDeviceOnline(true, true, true))
        assertTrue(isDeviceOnline(true, true, false))
    }

    @Test
    fun unvalidatedButTransportConnectedIsOnline() {
        assertTrue(isDeviceOnline(true, false, true))
    }

    @Test
    fun unvalidatedAndTransportDownIsOffline() {
        assertFalse(isDeviceOnline(true, false, false))
    }

    @Test
    fun noInternetIsOfflineEvenWithTransportConnected() {
        assertFalse(isDeviceOnline(false, true, true))
        assertFalse(isDeviceOnline(false, false, true))
        assertFalse(isDeviceOnline(false, true, false))
        assertFalse(isDeviceOnline(false, false, false))
    }
}
