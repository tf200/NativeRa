package com.taha.newraapp.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.local.TokenManager
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
    private val powerSyncManager: PowerSyncManager
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    init {
        Log.d("ProfileViewModel", "ProfileViewModel initialized")
        loadUser()
    }

    private fun loadUser() {
        Log.d("ProfileViewModel", "loadUser() called")
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            Log.d("ProfileViewModel", "User loaded: $user")
            _user.value = user
        }
    }

    fun logout() {
        viewModelScope.launch {
            powerSyncManager.disconnect()
            tokenManager.clearTokens()
        }
    }
}
