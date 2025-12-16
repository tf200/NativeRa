package com.taha.newraapp.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taha.newraapp.R
import com.taha.newraapp.data.model.UserPresenceStatus
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.ui.theme.TestRaTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatHeader(
    chatPartner: User?,
    chatPartnerPresence: UserPresenceStatus?,
    isTyping: Boolean = false,
    onBackClick: () -> Unit,
    onAudioCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {}
) {
    val initials = if (chatPartner != null) {
        val first = chatPartner.firstName.firstOrNull()?.toString() ?: ""
        val last = chatPartner.lastName.firstOrNull()?.toString() ?: ""
        (first + last).uppercase()
    } else stringResource(R.string.chat_user_initials_fallback)
    
    val displayName = if (chatPartner != null) {
        "${chatPartner.firstName} ${chatPartner.lastName}"
    } else stringResource(R.string.chat_loading)
    
    val statusText = when {
        isTyping -> stringResource(R.string.chat_typing)
        chatPartnerPresence?.online == true -> stringResource(R.string.chat_online)
        chatPartnerPresence?.lastSeen != null -> formatLastSeen(chatPartnerPresence.lastSeen)
        else -> stringResource(R.string.chat_offline) // Use offline resource if needed or just empty
    }
    
    // Avatar indicator color - stays green when online, even while typing
    val avatarIndicatorColor = if (chatPartnerPresence?.online == true) {
        Color(0xFF4CAF50) // Green for online
    } else {
        Color(0xFF9E9E9E) // Gray for offline
    }
    
    // Text status color - changes to primary when typing
    val textStatusColor = when {
        isTyping -> MaterialTheme.colorScheme.primary // Primary color for typing
        chatPartnerPresence?.online == true -> Color(0xFF4CAF50) // Green for online
        else -> Color(0xFF9E9E9E) // Gray for offline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp)
            .background(com.taha.newraapp.ui.theme.LightPrimary) // Solid deep purple
            .statusBarsPadding() // Handle edge-to-edge status bar
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.chat_back_description),
                    tint = Color.White
                )
            }

            // Avatar with online indicator
            Box(
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White), // White circle
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = com.taha.newraapp.ui.theme.LightPrimary // Purple text
                    )
                }
                
                // Online indicator - dynamic based on presence
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(avatarIndicatorColor)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(textStatusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Action buttons
            IconButton(onClick = onAudioCallClick) {
                Icon(
                    imageVector = Icons.Outlined.Phone,
                    contentDescription = stringResource(R.string.chat_voice_call_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = onVideoCallClick) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = stringResource(R.string.chat_video_call_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = { /* TODO: More options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.chat_options_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

/**
 * Format a last seen timestamp into a human-readable string.
 */
@Composable
private fun formatLastSeen(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> stringResource(R.string.chat_last_seen_just_now)
        diff < 3600_000 -> stringResource(R.string.chat_last_seen_minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(R.string.chat_last_seen_hours_ago, diff / 3600_000)
        else -> {
            val sdf = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
            stringResource(R.string.chat_last_seen_date, sdf.format(Date(timestamp)))
        }
    }
}
