package chat.sphinx.activitymain

import chat.sphinx.concept_connectivity_helper.DeviceNetwork
import chat.sphinx.example.wrapper_mqtt.MixerHealth
import chat.sphinx.example.wrapper_mqtt.MixerHealthUi
import chat.sphinx.example.wrapper_mqtt.MqttTransportState
import io.matthewnelson.concept_authentication.state.AuthenticationState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerHealthBannerTest {

    private val notRequired = AuthenticationState.NotRequired
    private val allHealth = listOf(MixerHealth.OK, MixerHealth.DEGRADED, MixerHealth.UNKNOWN)

    @Test
    fun offlineAlwaysNull() {
        for (h in allHealth) for (t in MqttTransportState.values()) {
            assertEquals(null, bannerHealth(MixerHealthUi(h, true), notRequired, false, t))
        }
    }

    @Test
    fun onlineButTransportNotConnectedIsNull() {
        for (h in allHealth) {
            for (t in listOf(MqttTransportState.Connecting, MqttTransportState.Failed)) {
                assertEquals(null, bannerHealth(MixerHealthUi(h, true), notRequired, true, t))
            }
        }
    }

    @Test
    fun onlineConnectedReturnsDegradedAndUnknown() {
        val c = MqttTransportState.Connected
        assertEquals(
            MixerHealth.DEGRADED,
            bannerHealth(MixerHealthUi(MixerHealth.DEGRADED, true), notRequired, true, c)
        )
        assertEquals(
            MixerHealth.UNKNOWN,
            bannerHealth(MixerHealthUi(MixerHealth.UNKNOWN, true), notRequired, true, c)
        )
    }

    @Test
    fun onlineConnectedOkOrHiddenIsNull() {
        val c = MqttTransportState.Connected
        assertEquals(null, bannerHealth(MixerHealthUi(MixerHealth.OK, true), notRequired, true, c))
        assertEquals(null, bannerHealth(MixerHealthUi(MixerHealth.UNKNOWN, false), notRequired, true, c))
    }

    @Test
    fun authRequiredIsNull() {
        assertEquals(
            null,
            bannerHealth(
                MixerHealthUi(MixerHealth.UNKNOWN, true),
                AuthenticationState.Required.LoggedOut,
                true,
                MqttTransportState.Connected
            )
        )
    }

    private fun collectBanners(
        health: MutableStateFlow<MixerHealthUi>,
        network: MutableStateFlow<DeviceNetwork>,
        transport: MutableStateFlow<MqttTransportState>,
        block: () -> Unit
    ): List<MixerHealth?> {
        val out = mutableListOf<MixerHealth?>()
        runBlocking {
            val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                serverHealthBannerFlow(
                    health,
                    MutableStateFlow<AuthenticationState>(notRequired),
                    network,
                    transport
                ).collect { out.add(it) }
            }
            block()
            job.cancel()
        }
        return out
    }

    @Test
    fun unvalidatedNetworkWithConnectedTransportShowsBanner() {
        val out = collectBanners(
            MutableStateFlow(MixerHealthUi(MixerHealth.UNKNOWN, true)),
            MutableStateFlow(DeviceNetwork(hasInternet = true, isValidated = false)),
            MutableStateFlow(MqttTransportState.Connected)
        ) {}
        assertEquals(listOf<MixerHealth?>(MixerHealth.UNKNOWN), out)
    }

    @Test
    fun reEmitsWhenOnlyNetworkFlips() {
        val network = MutableStateFlow(DeviceNetwork(true, true))
        val out = collectBanners(
            MutableStateFlow(MixerHealthUi(MixerHealth.DEGRADED, true)),
            network,
            MutableStateFlow(MqttTransportState.Connected)
        ) {
            network.value = DeviceNetwork(false, false)
            network.value = DeviceNetwork(true, true)
        }
        assertEquals(listOf<MixerHealth?>(MixerHealth.DEGRADED, null, MixerHealth.DEGRADED), out)
    }

    @Test
    fun reEmitsWhenOnlyTransportFlips() {
        val transport = MutableStateFlow(MqttTransportState.Connected)
        val out = collectBanners(
            MutableStateFlow(MixerHealthUi(MixerHealth.DEGRADED, true)),
            MutableStateFlow(DeviceNetwork(true, true)),
            transport
        ) {
            transport.value = MqttTransportState.Failed
            transport.value = MqttTransportState.Connected
        }
        assertEquals(listOf<MixerHealth?>(MixerHealth.DEGRADED, null, MixerHealth.DEGRADED), out)
    }

    @Test
    fun connectionLostOrderNeverShowsBanner() {
        val health = MutableStateFlow(MixerHealthUi(MixerHealth.OK, true))
        val transport = MutableStateFlow(MqttTransportState.Connected)
        val out = collectBanners(
            health,
            MutableStateFlow(DeviceNetwork(true, true)),
            transport
        ) {
            transport.value = MqttTransportState.Connecting
            health.value = MixerHealthUi(MixerHealth.UNKNOWN, true)
        }
        assertTrue(out.all { it == null })
    }
}
