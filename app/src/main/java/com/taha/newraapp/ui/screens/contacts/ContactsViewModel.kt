package com.taha.newraapp.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.domain.usecase.ContactUiModel
import com.taha.newraapp.domain.usecase.GetPrioritizedContactsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ContactsViewModel(
    getPrioritizedContactsUseCase: GetPrioritizedContactsUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _contacts = getPrioritizedContactsUseCase()

    val uiState: StateFlow<ContactsUiState> = combine(_searchQuery, _contacts) { query, contacts ->
        if (query.isBlank()) {
            ContactsUiState.Success(contacts)
        } else {
            val filtered = contacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.center.contains(query, ignoreCase = true)
            }
            ContactsUiState.Success(filtered)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactsUiState.Loading
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}

sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    data class Success(val contacts: List<ContactUiModel>) : ContactsUiState
    data class Error(val message: String) : ContactsUiState
}
