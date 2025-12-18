package com.taha.newraapp.ui.screens.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.taha.newraapp.ui.theme.LightPrimary
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.SurfaceViewRenderer

/**
 * WhatsApp-style call screen.
 * 
 * Features:
 * - Full-screen remote video when available, avatar when not
 * - Small local video preview (PiP style)
 * - Dynamic UI based on camera states
 * - Mute/camera/speaker/end call controls
 */
@Composable
fun CallScreen(
    room: Room?,
    remoteName: String,
    remoteInitials: String,
    isVideoCall: Boolean,
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    onMicToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    // Track remote participant's video state
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    // Observe room events for remote track changes
    LaunchedEffect(room) {
        room?.events?.collect { event ->
            when (event) {
                is RoomEvent.TrackSubscribed -> {
                    val track = event.track
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                    }
                }
                is RoomEvent.TrackUnsubscribed -> {
                    if (event.track is VideoTrack) {
                        remoteVideoTrack = null
                    }
                }
                else -> {}
            }
        }
    }
    
    // Get local video track when camera is enabled
    LaunchedEffect(room, isCameraEnabled) {
        if (isCameraEnabled && room != null) {
            // Delay to allow track to be created and published
            kotlinx.coroutines.delay(300)
            val publication = room.localParticipant.getTrackPublication(Track.Source.CAMERA)
            localVideoTrack = publication?.track as? VideoTrack
        } else if (!isCameraEnabled) {
            localVideoTrack = null
        }
    }
    
    // Gradient background colors
    val backgroundColors = listOf(
        LightPrimary,
        LightPrimary.copy(alpha = 0.85f),
        Color(0xFF4A3B8C) // Darker purple at bottom
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (remoteVideoTrack != null) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier.background(Brush.verticalGradient(backgroundColors))
                }
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Main content - either remote video or avatar
        if (remoteVideoTrack != null) {
            // Full-screen remote video
            RemoteVideoView(
                room = room,
                videoTrack = remoteVideoTrack!!,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Avatar display for audio call or when remote has no video
            AvatarDisplay(
                name = remoteName,
                initials = remoteInitials,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Top bar with name and call info
        TopCallBar(
            remoteName = remoteName,
            isVideoCall = isVideoCall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
        )
        
        // Local video preview (PiP) - shown when camera is enabled
        if (localVideoTrack != null && isCameraEnabled) {
            LocalVideoPreview(
                room = room,
                videoTrack = localVideoTrack!!,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        
        // Bottom controls
        CallControls(
            isMicEnabled = isMicEnabled,
            isCameraEnabled = isCameraEnabled,
            isVideoCall = isVideoCall,
            onMicToggle = onMicToggle,
            onCameraToggle = onCameraToggle,
            onSwitchCamera = onSwitchCamera,
            onEndCall = onEndCall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun TopCallBar(
    remoteName: String,
    isVideoCall: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = remoteName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isVideoCall) "Video call" else "Audio call",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AvatarDisplay(
    name: String,
    initials: String,
    modifier: Modifier = Modifier
) {
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            // Outer pulse ring
            Box(
                modifier = Modifier
                    .size((160 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
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
                    text = initials,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightPrimary
                )
            }
        }
    }
}

@Composable
private fun RemoteVideoView(
    room: Room?,
    videoTrack: VideoTrack,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context)
        },
        modifier = modifier,
        update = { renderer ->
            // Use tag to check if already initialized to find "Already initialized" crash
            if (room != null && renderer.tag != "initialized") {
                room.initVideoRenderer(renderer)
                renderer.tag = "initialized"
            }
            if (renderer.tag == "initialized") {
                videoTrack.addRenderer(renderer)
            }
        },
        onRelease = { renderer ->
            videoTrack.removeRenderer(renderer)
            renderer.release()
        }
    )
}

@Composable
private fun LocalVideoPreview(
    room: Room?,
    videoTrack: VideoTrack,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                 SurfaceViewRenderer(context).apply {
                     setMirror(true)
                 }
            },
            modifier = Modifier.fillMaxSize(),
            update = { renderer ->
                if (room != null && renderer.tag != "initialized") {
                    room.initVideoRenderer(renderer)
                    renderer.tag = "initialized"
                }
                if (renderer.tag == "initialized") {
                    videoTrack.addRenderer(renderer)
                }
            },
            onRelease = { renderer ->
                videoTrack.removeRenderer(renderer)
                renderer.release()
            }
        )
    }
}

@Composable
private fun CallControls(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    isVideoCall: Boolean,
    onMicToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Mute button
        ControlButton(
            icon = if (isMicEnabled) Icons.Filled.Mic else Icons.Outlined.MicOff,
            label = if (isMicEnabled) "Mute" else "Unmute",
            isActive = !isMicEnabled,
            onClick = onMicToggle
        )
        
        // Camera toggle (for video calls or to switch during audio call)
        ControlButton(
            icon = if (isCameraEnabled) Icons.Filled.Videocam else Icons.Outlined.VideocamOff,
            label = if (isCameraEnabled) "Video Off" else "Video On",
            isActive = !isCameraEnabled,
            onClick = onCameraToggle
        )
        
        // Switch camera (only when camera is on)
        if (isCameraEnabled) {
            ControlButton(
                icon = Icons.Filled.Cameraswitch,
                label = "Switch",
                isActive = false,
                onClick = onSwitchCamera
            )
        }
        
        // End call button (always visible, red)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onEndCall,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935)) // Red
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "End",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Color.White.copy(alpha = 0.3f)
                    else Color.White.copy(alpha = 0.15f)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}
