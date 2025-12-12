package com.taha.newraapp.ui.components

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.domain.repository.UserRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MainScaffold to provide current user data to DashboardTopBar.
 */
class ScaffoldViewModel(
    private val userRepository: UserRepository,
    private val powerSyncManager: PowerSyncManager
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        observeConnectionAndLoadUser()
    }

    private fun observeConnectionAndLoadUser() {
        viewModelScope.launch {
            // Observe PowerSync connection state
            powerSyncManager.isConnected.collect { isConnected ->
                if (isConnected && _currentUser.value == null) {
                    loadCurrentUser()
                }
            }
        }
        
        // Also try loading immediately in case already connected
        viewModelScope.launch {
            delay(500) // Small delay to let connection settle
            if (_currentUser.value == null) {
                loadCurrentUser()
            }
        }
    }

    private suspend fun loadCurrentUser() {
        try {
            val user = userRepository.getCurrentUser()
            Log.d("ScaffoldViewModel", "Loaded user: $user")
            _currentUser.value = user
        } catch (e: Exception) {
            Log.e("ScaffoldViewModel", "Failed to load user", e)
        }
    }
}

