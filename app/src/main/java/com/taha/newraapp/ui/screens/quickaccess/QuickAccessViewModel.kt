package com.taha.newraapp.ui.screens.quickaccess

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taha.newraapp.data.local.dao.MessageDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for QuickAccessScreen.
 * 
 * Optimization notes for Compose recomposition:
 * - Uses StateFlow with SharingStarted.WhileSubscribed(5000) for efficient subscription management
 * - The 5-second timeout prevents unnecessary resubscription during configuration changes
 * - StateFlow emits only when value actually changes, preventing unnecessary recompositions
 */
class QuickAccessViewModel(
    messageDao: MessageDao,
    private val updateFcmTokenUseCase: com.taha.newraapp.domain.usecase.UpdateFcmTokenUseCase
) : ViewModel() {

    fun refreshFcmToken() {
        viewModelScope.launch {
            try {
                updateFcmTokenUseCase()
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    /**
     * Total unread message count across all conversations.
     * This is a hot StateFlow that only emits when the count actually changes,
     * which is optimal for Compose recomposition.
     */
    val unreadMessageCount: StateFlow<Int> = messageDao.getTotalUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}
