package com.example.selfiememory.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
                trySend(isConditionMet(networkMode, specificSsid))
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "Network lost")
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val met = isConditionMet(networkMode, specificSsid)
                Log.i(TAG, "Network capabilities changed, condition met: $met")
                trySend(met)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Check initial state
        val currentNetworks = connectivityManager.allNetworks
        val initialMet = currentNetworks.any { isConditionMet(networkMode, specificSsid) }
        trySend(initialMet)

        awaitClose {
            Log.i(TAG, "Stopping network monitoring")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    fun isConditionMetPublic(networkMode: NetworkMode, specificSsid: String): Boolean {
        return isConditionMet(networkMode, specificSsid)
    }

    @SuppressLint("MissingPermission")
    private fun isConditionMet(networkMode: NetworkMode, specificSsid: String): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return when (networkMode) {
            NetworkMode.CELLULAR -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            NetworkMode.ANY_WLAN -> capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            NetworkMode.SPECIFIC_WLAN -> {
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
                if (specificSsid.isBlank()) return true

                val wifiInfo = wifiManager.connectionInfo
                val currentSsid = wifiInfo?.ssid?.removeSurrounding("\"") ?: ""
                Log.i(TAG, "Current SSID: $currentSsid, Expected: $specificSsid")
                currentSsid == specificSsid
            }
        }
    }
}
