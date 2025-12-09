package com.taha.newraapp.data.sync

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.taha.newraapp.data.network.AuthenticatedApiExecutor

/**
 * PowerSync Backend Connector
 * 
 * Handles authentication and data synchronization with the PowerSync backend.
 * This connector is responsible for:
 * 1. Fetching credentials (JWT token) for PowerSync connection from /powersync/authenticate
 * 2. Uploading local changes to the backend
 * 
 * Uses AuthenticatedApiExecutor for automatic token refresh handling.
 */
class PowerSyncConnector(
    private val apiExecutor: AuthenticatedApiExecutor,
    private val authApi: com.taha.newraapp.data.network.AuthApi
) : PowerSyncBackendConnector() {

    /**
     * Fetch credentials for PowerSync connection.
     * Calls /powersync/authenticate endpoint with the access token.
     * If token is expired (401), the executor automatically refreshes and retries.
     * Returns the PowerSync-specific token and URL from the backend.
     */
    override suspend fun fetchCredentials(): PowerSyncCredentials {
        return apiExecutor.executeWithBearer { authorization ->
            val response = authApi.getPowerSyncToken(authorization)
            PowerSyncCredentials(
                endpoint = response.powersyncUrl,
                token = response.token
            )
        }
    }

    /**
     * Upload local changes to the backend.
     * This is called when there are pending local changes to sync.
     */
    override suspend fun uploadData(database: PowerSyncDatabase) {
        // Get the upload queue of pending changes
        val transaction = database.getNextCrudTransaction() ?: return
        
        try {
            // Process each pending operation
            for (op in transaction.crud) {
                when (op.op) {
                    com.powersync.db.crud.UpdateType.PUT -> {
                        // Handle insert/update
                        // TODO: Send to your backend API using apiExecutor
                        // apiExecutor.executeWithBearer { auth ->
                        //     api.upsert(auth, op.table, op.id, op.opData)
                        // }
                    }
                    com.powersync.db.crud.UpdateType.PATCH -> {
                        // Handle partial update
                        // TODO: Send to your backend API
                    }
                    com.powersync.db.crud.UpdateType.DELETE -> {
                        // Handle delete
                        // TODO: Send to your backend API
                        // apiExecutor.executeWithBearer { auth ->
                        //     api.delete(auth, op.table, op.id)
                        // }
                    }
                }
            }
            
            // Mark transaction as complete after successful upload
            transaction.complete(null)
        } catch (e: Exception) {
            // Log error and let PowerSync retry later
            throw e
        }
    }
}

