package chat.sphinx.connectivity_helper

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import chat.sphinx.concept_connectivity_helper.DeviceNetwork
import chat.sphinx.concept_connectivity_helper.NetworkReachability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Tracks whether the device's default network has internet capability and is
 * validated. This instance lives for the whole process, so the default network
 * callback is deliberately never unregistered.
 */
@SuppressLint("MissingPermission")
class NetworkReachabilityImpl(
    context: Context
) : NetworkReachability {

    private val connectivityManager: ConnectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(DeviceNetwork(hasInternet = false, isValidated = false))
    override val state: StateFlow<DeviceNetwork> = _state.asStateFlow()

    init {
        // Seed first; the callback re-delivers the current default network after
        // registering, so any change in between overwrites the seed.
        val seedCapabilities = try {
            connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        } catch (e: Exception) {
            null
        }
        update(seedCapabilities.toDeviceNetwork())

        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    update(networkCapabilities.toDeviceNetwork())
                }

                override fun onLost(network: Network) {
                    update(DeviceNetwork(hasInternet = false, isValidated = false))
                }
            }
        )
    }

    private fun NetworkCapabilities?.toDeviceNetwork(): DeviceNetwork =
        if (this == null) {
            DeviceNetwork(hasInternet = false, isValidated = false)
        } else {
            DeviceNetwork(
                hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            )
        }

    private fun update(new: DeviceNetwork) {
        val old = _state.getAndUpdate { new }
        if (old != new) {
            Log.d(
                TAG,
                "hasInternet=${new.hasInternet}, isValidated=${new.isValidated}"
            )
        }
    }

    private companion object {
        const val TAG = "NetworkReachability"
    }
}
