package chat.sphinx.concept_connectivity_helper

import kotlinx.coroutines.flow.StateFlow

data class DeviceNetwork(
    val hasInternet: Boolean,
    val isValidated: Boolean
)

interface NetworkReachability {
    val state: StateFlow<DeviceNetwork>
}

/**
 * A live MQTT transport also counts as proof of reachability, covering setups
 * where VALIDATED never arrives (connectivity checks off, private DNS, VPN).
 */
fun isDeviceOnline(
    hasInternet: Boolean,
    isValidated: Boolean,
    transportConnected: Boolean
): Boolean = hasInternet && (isValidated || transportConnected)
