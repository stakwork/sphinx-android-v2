package chat.sphinx.dashboard.ui

import chat.sphinx.concept_repository_connect_manager.model.NetworkStatus
import chat.sphinx.example.wrapper_mqtt.MqttTransportState
import org.junit.Assert.assertEquals
import org.junit.Test

class HeaderBoltStateTest {

    private val statuses = listOf(
        NetworkStatus.Loading,
        NetworkStatus.Connected,
        NetworkStatus.Disconnected
    )

    @Test
    fun offlineIsOrangeForEverything() {
        for (t in MqttTransportState.values()) for (s in statuses) {
            assertEquals(HeaderBoltState.Orange, headerBoltState(false, t, s))
        }
    }

    @Test
    fun failedIsOrange() {
        for (s in statuses) {
            assertEquals(HeaderBoltState.Orange, headerBoltState(true, MqttTransportState.Failed, s))
        }
    }

    @Test
    fun connectingIsSpinner() {
        for (s in statuses) {
            assertEquals(HeaderBoltState.Spinner, headerBoltState(true, MqttTransportState.Connecting, s))
        }
    }

    @Test
    fun connectedFollowsNetworkStatus() {
        val c = MqttTransportState.Connected
        assertEquals(HeaderBoltState.Spinner, headerBoltState(true, c, NetworkStatus.Loading))
        assertEquals(HeaderBoltState.Green, headerBoltState(true, c, NetworkStatus.Connected))
        assertEquals(HeaderBoltState.Orange, headerBoltState(true, c, NetworkStatus.Disconnected))
    }
}
