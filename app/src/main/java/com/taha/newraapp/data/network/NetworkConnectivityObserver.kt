package com.taha.newraapp.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Connectivity status enum
 */
enum class ConnectivityStatus {
    AVAILABLE,
    UNAVAILABLE,
    LOSING,
    LOST
}

/**
 * Observes network connectivity changes using Android's ConnectivityManager.
 * Provides a Flow that emits connectivity status changes.
 */
class NetworkConnectivityObserver(
    private val context: Context
) {
    companion object {
        private const val TAG = "NetworkConnectivityObserver"
    }

    private val connectivityManager = 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Returns a Flow that emits connectivity status changes.
     * The flow will emit AVAILABLE when network becomes available,
     * and LOST when network is lost.
     */
    val observe: Flow<ConnectivityStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                trySend(ConnectivityStatus.AVAILABLE)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                Log.d(TAG, "Network losing, max ms to live: $maxMsToLive")
                trySend(ConnectivityStatus.LOSING)
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                trySend(ConnectivityStatus.LOST)
            }

            override fun onUnavailable() {
                Log.d(TAG, "Network unavailable")
                trySend(ConnectivityStatus.UNAVAILABLE)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        
        // Emit initial state
        val isConnected = isCurrentlyConnected()
        trySend(if (isConnected) ConnectivityStatus.AVAILABLE else ConnectivityStatus.UNAVAILABLE)

        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Check if currently connected to the internet.
     */
    fun isCurrentlyConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
