package com.taha.newraapp.data.socket

import android.util.Log
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * GlobalMessageHandler is responsible for starting and managing MessageSyncService
 * listeners at the application level. This ensures incoming messages are processed
 * and stored in the database even when no ChatRoomScreen is open.
 * 
 * Lifecycle:
 * 1. Call initialize() from Application.onCreate()
 * 2. Observes socket connection status
 * 3. When connected + user authenticated: starts all message listeners
 * 4. When disconnected: stops listeners
 * 
 * This solves the issue where messages were only processed when a chat room was open.
 */
class GlobalMessageHandler(
    private val socketManager: SocketManager,
    private val messageSyncService: MessageSyncService,
    private val typingService: TypingService,
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "GlobalMessageHandler"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectionObserverJob: Job? = null
    private var isInitialized = false

    /**
     * Initialize the global message handler.
     * Call this from Application.onCreate() after Koin is started.
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }

        isInitialized = true
        Log.i(TAG, "Initializing GlobalMessageHandler")

        // Start observing socket connection status
        // Note: Socket only connects AFTER PowerSync has synced (see LoginUseCase)
        // so user data is guaranteed to be available when socket connects
        connectionObserverJob = scope.launch {
            socketManager.connectionStatus.collect { status ->
                Log.d(TAG, "Socket status changed: $status")
                
                when (status) {
                    SocketStatus.CONNECTED -> {
                        onSocketConnected()
                    }
                    SocketStatus.DISCONNECTED,
                    SocketStatus.ERROR -> {
                        onSocketDisconnected()
                    }
                    SocketStatus.CONNECTING -> {
                        // Do nothing while connecting
                    }
                }
            }
        }
    }

    /**
     * Called when socket connection is established.
     * Sets up the current user ID and starts all message listeners.
     */
    private suspend fun onSocketConnected() {
        try {
            // Check if user is authenticated
            val token = tokenManager.accessToken.first()
            if (token.isNullOrBlank()) {
                Log.w(TAG, "Socket connected but no auth token, skipping listener init")
                return
            }

            // Get current user to set the ID for message filtering
            val currentUser = userRepository.getCurrentUser()
            if (currentUser == null) {
                Log.w(TAG, "Socket connected but no current user found, skipping listener init")
                return
            }

            // Set current user ID on MessageSyncService
            messageSyncService.setCurrentUserId(currentUser.id)
            Log.i(TAG, "Set current user ID for message sync: ${currentUser.id}")

            // Start all message-related listeners
            Log.i(TAG, "Starting global message listeners")
            messageSyncService.startSync()
            messageSyncService.startIncomingMessageListener()
            messageSyncService.startDeliveryConfirmationListener()
            messageSyncService.startSeenConfirmationListener()
            typingService.startTypingListener()

            Log.i(TAG, "✓ Global message listeners started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting message listeners", e)
        }
    }

    /**
     * Called when socket disconnects.
     * Stops all message listeners to prevent resource leaks.
     */
    private fun onSocketDisconnected() {
        Log.i(TAG, "Stopping global message listeners")
        messageSyncService.stopSync()
        messageSyncService.stopIncomingListener()
        messageSyncService.stopDeliveryListener()
        messageSyncService.stopSeenListener()
        typingService.stopTypingListener()
    }

    /**
     * Clean up resources.
     * Typically not needed as this lives for the app's lifetime.
     */
    fun destroy() {
        Log.d(TAG, "Destroying GlobalMessageHandler")
        connectionObserverJob?.cancel()
        connectionObserverJob = null
        onSocketDisconnected()
        isInitialized = false
    }
}
