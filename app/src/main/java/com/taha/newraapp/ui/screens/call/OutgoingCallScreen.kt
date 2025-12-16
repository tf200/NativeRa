package com.taha.newraapp.ui.screens.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taha.newraapp.ui.theme.LightPrimary

/**
 * Outgoing call screen shown while waiting for callee to answer.
 * Features:
 * - Animated pulsing avatar
 * - "Calling..." status
 * - End call button
 * - Matches app's purple theme
 */
@Composable
fun OutgoingCallScreen(
    calleeName: String,
    calleeInitials: String,
    isVideoCall: Boolean,
    callStatus: String = "Calling...",
    onEndCallClick: () -> Unit,
    onMuteClick: () -> Unit = {},
    onSpeakerClick: () -> Unit = {},
    isMuted: Boolean = false,
    isSpeakerOn: Boolean = false
) {
    // Pulsing animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    // Gradient background matching app theme
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            LightPrimary,
            LightPrimary.copy(alpha = 0.85f),
            Color(0xFF4A3B8C) // Darker purple at bottom
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Call type indicator
            if (isVideoCall) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Video Call",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            } else {
                Text(
                    text = "Audio Call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Pulsing Avatar with rings
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Outer pulsing ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                )
                
                // Middle pulsing ring
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(pulseScale * 0.95f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = calleeInitials,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Callee name
            Text(
                text = calleeName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Call status (Calling..., Ringing..., etc.)
            Text(
                text = callStatus,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons row
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Mute button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onMuteClick,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMuted) Color.White.copy(alpha = 0.3f) 
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MicOff,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mute",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                // Speaker button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onSpeakerClick,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeakerOn) Color.White.copy(alpha = 0.3f) 
                                else Color.White.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Speaker",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Speaker",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // End call button (red)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onEndCallClick,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)) // Red
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "End Call",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
