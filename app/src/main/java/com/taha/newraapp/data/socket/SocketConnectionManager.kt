package com.taha.newraapp.data.socket

import android.util.Log
import com.taha.newraapp.data.network.ConnectivityStatus
import com.taha.newraapp.data.network.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Manages automatic socket reconnection based on network availability.
 * 
 * This class observes network connectivity changes and automatically
 * reconnects the socket when network becomes available after being lost.
 * 
 * Key features:
 * - Listens for network availability changes
 * - Automatically reconnects socket when network comes back
 * - Avoids duplicate reconnection attempts
 * - Uses coroutines for async operations
 */
class SocketConnectionManager(
    private val socketManager: SocketManager,
    private val networkObserver: NetworkConnectivityObserver
) {
    companion object {
        private const val TAG = "SocketConnectionManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isMonitoring = false
    private var wasConnectedBeforeNetworkLoss = false

    /**
     * Start monitoring network changes and auto-reconnecting.
     * Call this once when the app starts or user logs in.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring network, skipping")
            return
        }
        
        isMonitoring = true
        Log.i(TAG, "Starting network monitoring for socket reconnection")

        scope.launch {
            networkObserver.observe
                .distinctUntilChanged()
                .collectLatest { status ->
                    handleConnectivityChange(status)
                }
        }
    }

    /**
     * Stop monitoring network changes.
     * Call this when user logs out.
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping network monitoring")
        isMonitoring = false
    }

    private suspend fun handleConnectivityChange(status: ConnectivityStatus) {
        Log.d(TAG, "Connectivity status changed: $status")
        
        when (status) {
            ConnectivityStatus.AVAILABLE -> {
                // Network is back! Check if we need to reconnect
                val currentSocketStatus = socketManager.connectionStatus.value
                Log.d(TAG, "Network available. Socket status: $currentSocketStatus, wasConnectedBefore: $wasConnectedBeforeNetworkLoss")
                
                if (currentSocketStatus != SocketStatus.CONNECTED && 
                    currentSocketStatus != SocketStatus.CONNECTING) {
                    Log.i(TAG, "🔄 Network available - initiating socket reconnection")
                    socketManager.connect()
                }
            }
            
            ConnectivityStatus.LOSING -> {
                Log.d(TAG, "Network losing - socket may disconnect soon")
                // Track if we were connected before losing network
                wasConnectedBeforeNetworkLoss = socketManager.isConnected()
            }
            
            ConnectivityStatus.LOST, ConnectivityStatus.UNAVAILABLE -> {
                // Track if we were connected before losing network
                if (socketManager.isConnected()) {
                    wasConnectedBeforeNetworkLoss = true
                }
                Log.w(TAG, "📡 Network lost/unavailable. Socket will retry automatically when network returns.")
            }
        }
    }
}
