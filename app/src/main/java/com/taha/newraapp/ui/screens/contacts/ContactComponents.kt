package com.taha.newraapp.ui.screens.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taha.newraapp.domain.usecase.ContactUiModel
import com.taha.newraapp.ui.theme.Slate100
import com.taha.newraapp.ui.theme.TestRaTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageItem(
    contact: ContactUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Online Indicator
            Box {
                MessageAvatar(
                    name = contact.name,
                    isDispatch = contact.center.contains("Dispatch", ignoreCase = true) || 
                                 contact.center.contains("HQ", ignoreCase = true)
                )
                
                // Online indicator
                if (contact.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50)) // Green
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TestRaTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = contact.lastMessage ?: contact.center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (contact.unreadCount > 0) TestRaTheme.extendedColors.textPrimary else TestRaTheme.extendedColors.textMuted,
                    fontWeight = if (contact.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            // Right side - Time and Badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (contact.lastMessageTime != null) {
                    Text(
                        text = formatTime(contact.lastMessageTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (contact.unreadCount > 0) MaterialTheme.colorScheme.primary else TestRaTheme.extendedColors.textMuted
                    )
                }

                if (contact.unreadCount > 0) {
                    UnreadBadge(count = contact.unreadCount)
                }
            }
        }
    }
}

@Composable
fun MessageAvatar(name: String, isDispatch: Boolean = false) {
    val initial = name.firstOrNull()?.uppercase() ?: "?"
    val backgroundColor = if (isDispatch) {
        MaterialTheme.colorScheme.error // Red for Dispatch/HQ
    } else {
        MaterialTheme.colorScheme.primary // Purple for others
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// Keep old ContactItem for backwards compatibility if needed elsewhere
@Composable
fun ContactItem(
    contact: ContactUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MessageItem(contact = contact, onClick = onClick, modifier = modifier)
}

@Composable
fun ContactAvatar(name: String) {
    MessageAvatar(name = name, isDispatch = false)
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    
    return when {
        diff < 60 * 60 * 1000 -> { // Less than 1 hour
            "${(diff / (60 * 1000)).toInt()}m"
        }
        diff < 24 * 60 * 60 * 1000 -> { // Less than 24 hours
            "${(diff / (60 * 60 * 1000)).toInt()}h"
        }
        diff < 48 * 60 * 60 * 1000 -> { // Yesterday
            "1d"
        }
        else -> {
            "${(diff / (24 * 60 * 60 * 1000)).toInt()}d"
        }
    }
}
