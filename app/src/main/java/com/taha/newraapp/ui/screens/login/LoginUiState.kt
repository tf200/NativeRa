package com.taha.newraapp.ui.screens.login

import com.taha.newraapp.ui.common.Language

/**
 * Login screen UI state.
 */
data class LoginUiState(
    val officerId: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val selectedLanguage: Language = Language.FRENCH
)

/**
 * Login screen events.
 */
sealed class LoginEvent {
    data class OfficerIdChanged(val value: String) : LoginEvent()
    data class PasswordChanged(val value: String) : LoginEvent()
    data object TogglePasswordVisibility : LoginEvent()
    data class LanguageChanged(val language: Language) : LoginEvent()
    data object Login : LoginEvent()
    data object ClearError : LoginEvent()
}
