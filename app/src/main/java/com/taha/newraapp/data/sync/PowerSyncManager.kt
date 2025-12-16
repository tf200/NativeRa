package com.taha.newraapp.data.sync

import android.content.Context
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PowerSync Manager
 * 
 * Singleton-like manager that handles PowerSync database initialization
 * and connection lifecycle. Should be initialized after successful login.
 */
class PowerSyncManager(
    private val context: Context,
    private val apiExecutor: AuthenticatedApiExecutor,
    private val authApi: AuthApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var _database: PowerSyncDatabase? = null
    val database: PowerSyncDatabase?
        get() = _database
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()
    
    private val _hasSynced = MutableStateFlow(false)
    val hasSynced: StateFlow<Boolean> = _hasSynced.asStateFlow()

    /**
     * Initialize and connect PowerSync database.
     * Call this after successful login.
     */
    /**
     * Initialize and connect PowerSync database.
     * Call this after successful login.
     * 
     * @param waitForSync If true (default), suspends until first sync completes.
     *                    Set to false for background services that need immediate DB access.
     */
    suspend fun initialize(waitForSync: Boolean = true) {
        if (_database != null) {
            return // Already initialized
        }
        
        try {
            _syncStatus.value = SyncStatus.Connecting
            
            // Create database driver factory for Android
            val driverFactory = DatabaseDriverFactory(context)
            
            // Create PowerSync database
            _database = PowerSyncDatabase(
                factory = driverFactory,
                schema = AppSchema,
                dbFilename = "powersync.db"
            )
            
            // Create connector with API executor for automatic token refresh
            val connector = PowerSyncConnector(
                apiExecutor = apiExecutor,
                authApi = authApi
            )
            
            // Connect to PowerSync
            _database?.connect(connector)
            
            // Wait for first sync to complete before marking as connected
            // This ensures user data is available before other components try to use it
            // Wait for first sync to complete before marking as connected
            // This ensures user data is available before other components try to use it
            if (waitForSync) {
                _database?.waitForFirstSync()
            }
            
            _hasSynced.value = true
            _isConnected.value = true
            _syncStatus.value = SyncStatus.Connected
            
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Failed to connect")
            throw e
        }
    }
    
    /**
     * Disconnect and cleanup PowerSync.
     * Call this for temporary disconnection (e.g., network issues).
     */
    suspend fun disconnect() {
        try {
            _database?.disconnect()
            _database?.close()
            _database = null
            _isConnected.value = false
            _syncStatus.value = SyncStatus.Idle
        } catch (e: Exception) {
            // Log error but continue
        }
    }
    
    /**
     * Disconnect and clear all PowerSync synced data.
     * Call this on sign-out to completely remove user data.
     * After calling this, initialize() will create a fresh database on next login.
     */
    suspend fun disconnectAndClear() {
        try {
            _database?.disconnectAndClear()
            _database = null
            _isConnected.value = false
            _hasSynced.value = false
            _syncStatus.value = SyncStatus.Idle
        } catch (e: Exception) {
            // Log error but continue - data may already be cleared
        }
    }

}

/**
 * Represents the current sync status
 */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Connecting : SyncStatus()
    data object Connected : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
