package chat.sphinx.grapheneos_manager.optimizers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import android.widget.ImageView
import chat.sphinx.concept_image_loader.ImageLoader

class NetworkOptimizer(
    context: Context,
    private val imageLoader: ImageLoader<ImageView>
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun optimizeNetworkUsage() {
        // Use NetworkCallback instead of deprecated methods
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("Network", "Network available: $network")
                // Resume network operations
                resumeNetworkOperations()
            }

            override fun onLost(network: Network) {
                Log.d("Network", "Network lost: $network")
                // Pause network operations
                pauseNetworkOperations()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

                // Adjust behavior based on network type
                adjustNetworkBehavior(isWifi, isCellular)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    private fun adjustNetworkBehavior(isWifi: Boolean, isCellular: Boolean) {
        when {
            isWifi -> {
                // Enable high-bandwidth operations
                enableHighBandwidthOperations()
            }
            isCellular -> {
                // Limit bandwidth usage
                limitBandwidthOperations()
            }
            else -> {
                // No reliable network
                pauseNetworkOperations()
            }
        }
    }

    private fun resumeNetworkOperations() {
        // Resume image loading and database sync
        imageLoader.resumeImageLoading()
    }

    private fun pauseNetworkOperations() {
        // Pause non-essential network operations
        imageLoader.pauseImageLoading()
    }

    private fun enableHighBandwidthOperations() {
        // Allow high-quality image loading
        imageLoader.setHighQualityMode(true)
    }

    private fun limitBandwidthOperations() {
        // Use lower quality images on cellular
        imageLoader.setHighQualityMode(false)
    }
}