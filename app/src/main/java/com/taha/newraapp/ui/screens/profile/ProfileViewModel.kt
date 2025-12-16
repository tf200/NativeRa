package com.taha.newraapp.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.network.AuthApi
import com.taha.newraapp.data.network.AuthenticatedApiExecutor
import com.taha.newraapp.data.repository.AttachmentRepository
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val tokenManager: TokenManager,
    private val powerSyncManager: PowerSyncManager,
    private val authApi: AuthApi,
    private val apiExecutor: AuthenticatedApiExecutor,
    private val attachmentRepository: AttachmentRepository,
    private val messageDao: MessageDao,
    private val socketManager: SocketManager
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()
    
    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    init {
        Log.d(TAG, "ProfileViewModel initialized")
        loadUser()
    }

    private fun loadUser() {
        Log.d(TAG, "loadUser() called")
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            Log.d(TAG, "User loaded: $user")
            _user.value = user
        }
    }

    /**
     * Sign out the user completely.
     * This method:
     * 1. Attempts to notify the server (fire-and-forget, ignores errors)
     * 2. Disconnects WebSocket
     * 3. Clears Room database (messages, conversations)
     * 4. Deletes local attachment files
     * 5. Clears PowerSync synced data
     * 6. Clears authentication tokens
     */
    fun signOut() {
        if (_isSigningOut.value) return
        
        viewModelScope.launch {
            _isSigningOut.value = true
            Log.d(TAG, "Starting sign-out process...")
            
            // 1. Try to notify server (fire-and-forget)
            try {
                apiExecutor.executeWithBearer { token ->
                    authApi.signout(token)
                }
                Log.d(TAG, "Server notified of sign-out")
            } catch (e: Exception) {
                Log.w(TAG, "Sign-out API failed (continuing): ${e.message}")
            }
            
            // 2. Disconnect socket
            try {
                socketManager.disconnect()
                Log.d(TAG, "Socket disconnected")
            } catch (e: Exception) {
                Log.w(TAG, "Socket disconnect failed: ${e.message}")
            }
            
            // 3. Clear Room database (messages, conversations)
            try {
                messageDao.clearAllMessages()
                messageDao.clearAllConversations()
                Log.d(TAG, "Room database cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Room clear failed: ${e.message}")
            }
            
            // 4. Clear attachment files and pending uploads
            try {
                attachmentRepository.clearAllAttachmentFiles()
                Log.d(TAG, "Attachment files cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Attachment clear failed: ${e.message}")
            }
            
            // 5. Clear PowerSync synced data
            try {
                powerSyncManager.disconnectAndClear()
                Log.d(TAG, "PowerSync data cleared")
            } catch (e: Exception) {
                Log.w(TAG, "PowerSync clear failed: ${e.message}")
            }
            
            // 6. Clear tokens (last, so everything else can use them if needed)
            try {
                tokenManager.clearTokens()
                Log.d(TAG, "Tokens cleared")
            } catch (e: Exception) {
                Log.w(TAG, "Token clear failed: ${e.message}")
            }
            
            Log.d(TAG, "Sign-out complete")
            _isSigningOut.value = false
        }
    }
    
    /**
     * @deprecated Use signOut() instead for complete cleanup
     */
    @Deprecated("Use signOut() instead", ReplaceWith("signOut()"))
    fun logout() {
        signOut()
    }
}

