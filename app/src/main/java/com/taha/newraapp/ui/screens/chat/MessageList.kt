package com.taha.newraapp.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taha.newraapp.R
import com.taha.newraapp.data.repository.UploadProgress
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    messages: List<Message>,
    currentUser: User?,
    chatPartner: User?,
    uploadProgressMap: Map<String, UploadProgress>,
    isPartnerTyping: Boolean = false,
    onImageClick: (filePath: String, senderName: String, timestamp: Long) -> Unit,
    onDownloadClick: (messageId: String) -> Unit = { }
) {
    val listState = rememberLazyListState()
    
    val todayString = stringResource(R.string.chat_date_today)
    val yesterdayString = stringResource(R.string.chat_date_yesterday)
    
    // Build items and REVERSE them for reverseLayout
    // With reverseLayout, index 0 is at the BOTTOM (newest messages)
    val groupedItems = remember(messages, todayString, yesterdayString) {
        buildChatListWithDateSeparators(messages, todayString, yesterdayString).reversed()
    }
    
    // Smart scroll behavior like WhatsApp:
    // - Always scroll when YOU send a message
    // - Only scroll on received messages if you're already at the bottom
    // - Scroll when typing indicator appears (if at bottom) so it's visible
    LaunchedEffect(messages.size, isPartnerTyping) {
        val isAtBottom = listState.firstVisibleItemIndex <= 1
        
        if (messages.isNotEmpty() && currentUser != null) {
            val lastMessage = messages.lastOrNull()
            val isFromMe = lastMessage?.senderId == currentUser.id
            
            if (isFromMe || isAtBottom) {
                listState.scrollToItem(0)
            }
        } else if (isPartnerTyping && isAtBottom) {
            // Scroll to show typing indicator when it appears
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true, // Key change for WhatsApp-style behavior!
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Typing indicator comes FIRST (appears at bottom due to reverseLayout)
        if (isPartnerTyping) {
            item(key = "typing_indicator") {
                TypingIndicatorBubble()
            }
        }
        
        items(
            count = groupedItems.size,
            key = { index ->
                when (val item = groupedItems[index]) {
                    is ChatListItem.DateHeader -> "date_${item.dateLabel}"
                    is ChatListItem.MessageItem -> item.message.id
                }
            }
        ) { index ->
            when (val item = groupedItems[index]) {
                is ChatListItem.DateHeader -> {
                    DateSeparator(dateLabel = item.dateLabel)
                }
                is ChatListItem.MessageItem -> {
                    val isMe = item.message.senderId == currentUser?.id
                    val uploadProgress = uploadProgressMap[item.message.id]
                    // Determine sender name for media viewer
                    val senderName = if (isMe) {
                        currentUser?.let { "${it.firstName} ${it.lastName}" } ?: "You"
                    } else {
                        chatPartner?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown"
                    }
                    MessageBubble(
                        message = item.message,
                        isMe = isMe,
                        uploadProgress = uploadProgress,
                        senderName = senderName,
                        onImageClick = onImageClick,
                        onDownloadClick = onDownloadClick
                    )
                }
            }
        }
    }
}

/**
 * Represents an item in the chat list - either a date header or a message.
 */
sealed class ChatListItem {
    data class DateHeader(val dateLabel: String) : ChatListItem()
    data class MessageItem(val message: Message) : ChatListItem()
}

/**
 * Date separator composable - displays a centered date pill between messages.
 * Styled like WhatsApp/iMessage date separators.
 */
@Composable
fun DateSeparator(dateLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shadowElevation = 0.dp
        ) {
            Text(
                text = dateLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Build a list of ChatListItems from messages, inserting date headers when the date changes.
 * Optimized to re-use calendar instances.
 */
private fun buildChatListWithDateSeparators(
    messages: List<Message>,
    todayString: String,
    yesterdayString: String
): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()
    
    val result = mutableListOf<ChatListItem>()
    var lastDateLabel: String? = null
    
    // Optimization: Create Calendar instances once outside the loop
    val messageCalendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    
    for (message in messages) {
        messageCalendar.timeInMillis = message.timestamp
        
        val currentDateLabel = when {
             isSameDay(messageCalendar, today) -> todayString
             isSameDay(messageCalendar, yesterday) -> yesterdayString
             else -> dateFormat.format(messageCalendar.time)
        }
        
        // Insert date header when the date changes
        if (currentDateLabel != lastDateLabel) {
            result.add(ChatListItem.DateHeader(currentDateLabel))
            lastDateLabel = currentDateLabel
        }
        
        result.add(ChatListItem.MessageItem(message))
    }
    
    return result
}

/**
 * Check if two Calendar instances represent the same day.
 */
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
