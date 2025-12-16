package com.taha.newraapp.data.repository

import android.util.Base64
import android.util.Log
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class UserRepositoryImpl(
    private val powerSyncManager: PowerSyncManager,
    private val tokenManager: TokenManager
) : UserRepository {

    /**
     * Get current user using simple PowerSync query
     */
    override suspend fun getCurrentUser(): User? {
        val token = tokenManager.refreshToken.first()
        if (token == null) {
            return null
        }
        
        // JWT 'sub' claim contains the database ID (not the officerId field)
        val userId = extractSubFromToken(token)
        if (userId == null) {
            return null
        }
        
        return getUserById(userId)
    }

    /**
     * Get user by ID using PowerSync getOptional.
     * Used for fetching sender info for notifications.
     */
    override suspend fun getUserById(id: String): User? {

        val database = powerSyncManager.database ?: return null

        val query = "SELECT id, officerId, firstName, lastName, center, role, phoneNumber, isValid, isFrozen, contacts FROM User WHERE id = ? LIMIT 1"
        
        val result = database.getOptional(
            query,
            parameters = listOf(id)
        ) { cursor ->
            User(
                id = cursor.getString(0) ?: "",
                officerId = cursor.getString(1) ?: "",
                firstName = cursor.getString(2) ?: "",
                lastName = cursor.getString(3) ?: "",
                center = cursor.getString(4) ?: "",
                role = cursor.getString(5) ?: "",
                phoneNumber = cursor.getString(6) ?: "",
                isValid = (cursor.getLong(7) ?: 0L) == 1L,
                isFrozen = (cursor.getLong(8) ?: 0L) == 1L,
                contacts = cursor.getString(9) ?: "[]"
            )
        }
        
        return result
    }

    /**
     * Get all users using PowerSync getAll
     */
    override suspend fun getAllUsers(): List<User> {
        val database = powerSyncManager.database ?: run {
            return emptyList()
        }

        return database.getAll(
            "SELECT id, officerId, firstName, lastName, center, role, phoneNumber, isValid, isFrozen, contacts FROM User WHERE id IS NOT NULL"
        ) { cursor ->
            User(
                id = cursor.getString(0) ?: "",
                officerId = cursor.getString(1) ?: "",
                firstName = cursor.getString(2) ?: "",
                lastName = cursor.getString(3) ?: "",
                center = cursor.getString(4) ?: "",
                role = cursor.getString(5) ?: "",
                phoneNumber = cursor.getString(6) ?: "",
                isValid = (cursor.getLong(7) ?: 0L) == 1L,
                isFrozen = (cursor.getLong(8) ?: 0L) == 1L,
                contacts = cursor.getString(9) ?: "[]"
            )
        }
    }

    /**
     * Observe all users as a Flow (for reactive updates)
     */
    override fun observeAllUsers(): kotlinx.coroutines.flow.Flow<List<User>> {
        return kotlinx.coroutines.flow.flow {
            // Initial emission
            emit(getAllUsers())
            // For now, just emits once. PowerSync can provide reactive updates if needed.
        }
    }

    /**
     * Get all users from a specific center
     */
    override suspend fun getUsersByCenter(center: String): List<User> {
        val database = powerSyncManager.database ?: run {
            return emptyList()
        }

        return database.getAll(
            "SELECT id, officerId, firstName, lastName, center, role, phoneNumber, isValid, isFrozen, contacts FROM User WHERE center = ?",
            parameters = listOf(center)
        ) { cursor ->
            User(
                id = cursor.getString(0) ?: "",
                officerId = cursor.getString(1) ?: "",
                firstName = cursor.getString(2) ?: "",
                lastName = cursor.getString(3) ?: "",
                center = cursor.getString(4) ?: "",
                role = cursor.getString(5) ?: "",
                phoneNumber = cursor.getString(6) ?: "",
                isValid = (cursor.getLong(7) ?: 0L) == 1L,
                isFrozen = (cursor.getLong(8) ?: 0L) == 1L,
                contacts = cursor.getString(9) ?: "[]"
            )
        }
    }

    /**
     * Get all valid (active) users
     */
    override suspend fun getActiveUsers(): List<User> {
        val database = powerSyncManager.database ?: run {
            return emptyList()
        }

        return database.getAll(
            "SELECT id, officerId, firstName, lastName, center, role, phoneNumber, isValid, isFrozen, contacts FROM User WHERE isValid = 1 AND isFrozen = 0"
        ) { cursor ->
            User(
                id = cursor.getString(0) ?: "",
                officerId = cursor.getString(1) ?: "",
                firstName = cursor.getString(2) ?: "",
                lastName = cursor.getString(3) ?: "",
                center = cursor.getString(4) ?: "",
                role = cursor.getString(5) ?: "",
                phoneNumber = cursor.getString(6) ?: "",
                isValid = (cursor.getLong(7) ?: 0L) == 1L,
                isFrozen = (cursor.getLong(8) ?: 0L) == 1L,
                contacts = cursor.getString(9) ?: "[]"
            )
        }
    }

    private fun extractSubFromToken(token: String): String? {
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val json = JSONObject(payload)
                return json.optString("sub")
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Failed to parse JWT", e)
        }
        return null
    }
}
