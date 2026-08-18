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
        val activeNetwork = connectivityManager.activeNetwork
        Log.i(TAG, "isConditionMetPublic: activeNetwork=$activeNetwork, mode=$networkMode, ssid=$specificSsid")
        if (activeNetwork == null) {
            Log.i(TAG, "No active network")
            return false
        }
        return isConditionMetForNetwork(activeNetwork, networkMode, specificSsid)
    }

    @SuppressLint("MissingPermission")
    private fun isConditionMetForNetwork(network: Network, networkMode: NetworkMode, specificSsid: String): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        Log.i(TAG, "isConditionMetForNetwork: capabilities=$capabilities")
        if (capabilities == null) return false

        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Log.i(TAG, "Transport: wifi=$hasWifi, cellular=$hasCellular, internet=$hasInternet")

        return when (networkMode) {
            NetworkMode.CELLULAR -> {
                Log.i(TAG, "CELLULAR mode: $hasCellular")
                hasCellular
            }
            NetworkMode.ANY_WLAN -> {
                Log.i(TAG, "ANY_WLAN mode: $hasWifi")
                hasWifi
            }
            NetworkMode.SPECIFIC_WLAN -> {
                if (!hasWifi) {
                    Log.i(TAG, "SPECIFIC_WLAN: not WiFi")
                    return false
                }
                if (specificSsid.isBlank()) {
                    Log.i(TAG, "SPECIFIC_WLAN: ssid blank, rejecting")
                    return false
                }

                val activeNetwork = connectivityManager.activeNetwork
                if (network != activeNetwork) {
                    Log.i(TAG, "Network is not the active network, ignoring SSID check")
                    return false
                }

                val ssid = getCurrentSsid(capabilities)
                Log.i(TAG, "Current SSID: $ssid, Expected: $specificSsid")
                ssid == specificSsid
            }
        }
    }

    /**
     * Gets available SSIDs from WiFi scan results.
     * This works on all Android versions and doesn't have the limitations
     * of configuredNetworks on Android 10+.
     *
     * Requires ACCESS_FINE_LOCATION permission on Android 10+ (API 29+).
     */
    @SuppressLint("MissingPermission")
    fun getAvailableSsids(): List<String> {
        return try {
            wifiManager.scanResults
                .mapNotNull { it.SSID.takeIf { ssid -> ssid.isNotBlank() } }
                .distinct()
                .sorted()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting scan results - permission may be missing", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting scan results", e)
            emptyList()
        }
    }

    /**
     * Gets the current SSID from the WiFi info.
     * Uses the non-deprecated API where available.
     */
    @Suppress("DEPRECATION")
    private fun getCurrentSsid(capabilities: NetworkCapabilities): String {
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else null
        return (wifiInfo?.ssid ?: wifiManager.connectionInfo?.ssid)
            ?.removeSurrounding("\"")
            ?.takeUnless { it == "<unknown ssid>" }
            .orEmpty()
    }
}
