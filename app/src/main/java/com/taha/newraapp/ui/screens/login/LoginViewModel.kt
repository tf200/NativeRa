package com.taha.newraapp.ui.screens.login

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.model.request.DeviceInfo
import com.taha.newraapp.domain.usecase.LoginUseCase
import com.taha.newraapp.domain.usecase.UpdateFcmTokenUseCase
import com.taha.newraapp.ui.common.Language
import kotlinx.coroutines.launch
import android.util.Log

/**
 * ViewModel for the Login screen.
 * Manages login state and user interactions.
 */
class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val updateFcmTokenUseCase: UpdateFcmTokenUseCase
) : ViewModel() {
    
    companion object {
        private const val TAG = "FCM_DEBUG"
    }
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    init {
        // Initialize selected language from current app locale
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val currentLocale = if (!appLocales.isEmpty) appLocales.get(0) else Locale.getDefault()
        val language = Language.entries.find { it.code == currentLocale?.language } ?: Language.FRENCH
        _uiState.update { it.copy(selectedLanguage = language) }
    }
    
    fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.OfficerIdChanged -> {
                _uiState.update { it.copy(officerId = event.value, errorMessage = null) }
            }
            is LoginEvent.PasswordChanged -> {
                _uiState.update { it.copy(password = event.value, errorMessage = null) }
            }
            is LoginEvent.TogglePasswordVisibility -> {
                _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
            }
            is LoginEvent.LanguageChanged -> {
                val localeList = LocaleListCompat.forLanguageTags(event.language.code)
                AppCompatDelegate.setApplicationLocales(localeList)
                _uiState.update { it.copy(selectedLanguage = event.language) }
            }
            is LoginEvent.Login -> {
                performLogin()
            }
            is LoginEvent.ClearError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
        }
    }
    
    private fun performLogin() {
        val currentState = _uiState.value
        
        // Basic validation
        when {
            currentState.officerId.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "empty_officer_id") }
                return
            }
            currentState.password.isBlank() -> {
                _uiState.update { it.copy(errorMessage = "empty_password") }
                return
            }
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // Create device info with real device information
            val deviceInfo = DeviceInfo(
                deviceId = UUID.randomUUID().toString(),
                name = Build.DEVICE,
                model = Build.MODEL,
                operatingSystem = "Android",
                osVersion = Build.VERSION.RELEASE,
                manufacturer = Build.MANUFACTURER
            )
            
            val result = loginUseCase(currentState.officerId, currentState.password, deviceInfo)
            
            if (result.isSuccess) {
                // Fire and forget FCM token update
                Log.w(TAG, "LoginViewModel: Login SUCCESS, triggering FCM token update...")
                launch {
                    try {
                        Log.w(TAG, "LoginViewModel: Calling updateFcmTokenUseCase()...")
                        val fcmResult = updateFcmTokenUseCase()
                        Log.w(TAG, "LoginViewModel: FCM update result: isSuccess=${fcmResult.isSuccess}, error=${fcmResult.exceptionOrNull()?.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "LoginViewModel: FCM token update EXCEPTION!", e)
                        e.printStackTrace()
                    }
                }
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                _uiState.update { it.copy(isLoading = false, errorMessage = error) }
            }
        }
    }
}
