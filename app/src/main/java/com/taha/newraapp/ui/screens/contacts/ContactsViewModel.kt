package com.taha.newraapp.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.service.ChatPreloadService
import com.taha.newraapp.data.socket.TypingService
import com.taha.newraapp.domain.usecase.ContactUiModel
import com.taha.newraapp.domain.usecase.GetPrioritizedContactsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ContactsViewModel(
    getPrioritizedContactsUseCase: GetPrioritizedContactsUseCase,
    private val chatPreloadService: ChatPreloadService,
    typingService: TypingService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    // Set of user IDs who are currently typing
    val typingUsers: StateFlow<Set<String>> = typingService.typingState
        .map { it.keys }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private val _contactsResult = getPrioritizedContactsUseCase()

    val uiState: StateFlow<ContactsUiState> = combine(_searchQuery, _contactsResult) { query, result ->
        if (query.isBlank()) {
            ContactsUiState.Success(
                recentConversations = result.recentConversations,
                allContacts = result.allContacts
            )
        } else {
            // Filter both lists when searching
            val filteredRecent = result.recentConversations.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.center.contains(query, ignoreCase = true) ||
                        (contact.lastMessage?.contains(query, ignoreCase = true) == true)
            }
            val filteredAll = result.allContacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                        contact.center.contains(query, ignoreCase = true)
            }
            ContactsUiState.Success(
                recentConversations = filteredRecent,
                allContacts = filteredAll
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ContactsUiState.Loading
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Preload chat data when user taps on a contact.
     * Call this BEFORE navigating to ChatRoom for instant data display.
     */
    fun preloadChatData(peerId: String) {
        chatPreloadService.preloadChatData(peerId)
    }
}

sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    
    data class Success(
        val recentConversations: List<ContactUiModel>,  // Contacts with messages
        val allContacts: List<ContactUiModel>           // Contacts without messages
    ) : ContactsUiState
    
    data class Error(val message: String) : ContactsUiState
}

