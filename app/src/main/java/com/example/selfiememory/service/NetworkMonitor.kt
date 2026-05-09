package com.example.selfiememory.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.selfiememory.domain.model.NetworkMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val SSID_RETRY_DELAY_MS = 500L
        private const val SSID_RETRY_COUNT = 3
    }

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun observeNetworkCondition(networkMode: NetworkMode, specificSsid: String): Flow<Boolean> = callbackFlow {
        Log.i(TAG, "Starting network monitoring for mode=$networkMode, ssid=$specificSsid")

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available")
                trySend(isConditionMetForNetwork(network, networkMode, specificSsid))
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Network lost")
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val met = isConditionMetForNetwork(network, networkMode, specificSsid)
                Log.i(TAG, "Network capabilities changed, condition met: $met")
                trySend(met)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        val initialMet = connectivityManager.activeNetwork?.let { isConditionMetForNetwork(it, networkMode, specificSsid) } ?: false
        trySend(initialMet)

        awaitClose {
            Log.i(TAG, "Stopping network monitoring")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun isConditionMetPublic(networkMode: NetworkMode, specificSsid: String): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        return isConditionMetForNetwork(activeNetwork, networkMode, specificSsid)
    }

    @SuppressLint("MissingPermission")
    private fun isConditionMetForNetwork(network: Network, networkMode: NetworkMode, specificSsid: String): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when (networkMode) {
            NetworkMode.CELLULAR -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            NetworkMode.ANY_WLAN -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            NetworkMode.SPECIFIC_WLAN -> {
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
                if (specificSsid.isBlank()) return true

                // Verify this is the active network to avoid stale SSID info from other networks
                val activeNetwork = connectivityManager.activeNetwork
                if (network != activeNetwork) {
                    Log.i(TAG, "Network is not the active network, ignoring SSID check")
                    return false
                }

                val ssid = getCurrentSsidWithRetry()
                Log.i(TAG, "Current SSID: $ssid, Expected: $specificSsid")
                ssid == specificSsid
            }
        }
    }

    /**
     * Gets the current SSID with retry logic to handle propagation delay.
     * On some devices, the SSID may be null or empty immediately after connecting.
     */
    @SuppressLint("MissingPermission")
    private fun getCurrentSsidWithRetry(): String {
        repeat(SSID_RETRY_COUNT) { attempt ->
            val ssid = getCurrentSsid()
            if (ssid.isNotEmpty()) {
                return ssid
            }
            if (attempt < SSID_RETRY_COUNT - 1) {
                Log.i(TAG, "SSID is empty/null, retrying ($attempt + 1/$SSID_RETRY_COUNT)...")
                Thread.sleep(SSID_RETRY_DELAY_MS)
            }
        }
        return getCurrentSsid()
    }

    /**
     * Gets the current SSID from the WiFi info.
     * Uses the non-deprecated API where available.
     */
    @Suppress("DEPRECATION")
    private fun getCurrentSsid(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // On API 33+, try to get SSID from the connectionInfo which is still the most reliable way
            // despite the deprecation warning. The replacement APIs are significantly more complex.
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
        } else {
            wifiManager.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
        }
    }
}
