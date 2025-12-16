package com.taha.newraapp.ui.screens.chat

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.taha.newraapp.R
import com.taha.newraapp.data.repository.UploadProgress
import com.taha.newraapp.data.repository.UploadState
import com.taha.newraapp.domain.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audio message bubble with play/pause controls, progress, and status indicators.
 */
@Composable
fun AudioMessageBubble(
    localPath: String,
    isMe: Boolean,
    modifier: Modifier = Modifier,
    uploadProgress: UploadProgress? = null,
    messageStatus: MessageStatus = MessageStatus.PENDING,
    timestamp: Long = System.currentTimeMillis()
) {

    
    // State for playback
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    
    // Lazy MediaPlayer: only created when needed
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Load duration asynchronously to avoid main thread IO
    LaunchedEffect(localPath) {
        val file = File(localPath)
        if (file.exists()) {
             withContext(kotlinx.coroutines.Dispatchers.IO) {
                 try {
                     val retriever = android.media.MediaMetadataRetriever()
                     retriever.setDataSource(localPath)
                     val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                     totalDuration = durationStr?.toLongOrNull() ?: 0L
                     retriever.release()
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
        }
    }
     
     // Initialize/Release MediaPlayer on dispose
     DisposableEffect(Unit) {
         onDispose {
             mediaPlayer?.release()
             mediaPlayer = null
         }
     }
    
    // Update position while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPosition = player.currentPosition.toLong()
                } else {
                     // Sync state if player stopped externally
                     isPlaying = false 
                }
            } ?: run { isPlaying = false }
            delay(100)
        }
    }
    
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    
    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    
    // Determine if upload is in progress
    val isUploading = uploadProgress != null && 
        (uploadProgress.state == UploadState.UPLOADING || uploadProgress.state == UploadState.PENDING)

    Surface(
        modifier = modifier.widthIn(min = 200.dp, max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        color = bubbleColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause button with circular upload progress
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Upload progress ring around the button
                    if (isUploading) {
                        if (uploadProgress?.state == UploadState.UPLOADING) {
                            CircularProgressIndicator(
                                progress = { uploadProgress.progress },
                                modifier = Modifier.size(48.dp),
                                color = contentColor,
                                strokeWidth = 3.dp,
                                trackColor = contentColor.copy(alpha = 0.2f)
                            )
                        } else {
                            // Pending - indeterminate
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = contentColor,
                                strokeWidth = 3.dp,
                                trackColor = contentColor.copy(alpha = 0.2f)
                            )
                        }
                    }
                    
                    // Play/Pause button
                    FilledIconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer?.pause()
                                isPlaying = false
                            } else {
                                // Initialize player if null
                                if (mediaPlayer == null) {
                                    val file = File(localPath)
                                    if (file.exists()) {
                                        try {
                                             mediaPlayer = MediaPlayer().apply {
                                                 setDataSource(localPath)
                                                 prepare()
                                                 setOnCompletionListener {
                                                     isPlaying = false
                                                     currentPosition = 0L
                                                 }
                                             }
                                             // If duration wasn't loaded successfully before, update it now
                                             if (totalDuration == 0L) {
                                                 totalDuration = mediaPlayer?.duration?.toLong() ?: 0L
                                             }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            return@FilledIconButton
                                        }
                                    }
                                }
                                mediaPlayer?.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = contentColor.copy(alpha = 0.2f),
                            contentColor = contentColor,
                            disabledContainerColor = contentColor.copy(alpha = 0.1f),
                            disabledContentColor = contentColor.copy(alpha = 0.5f)
                        ),
                        enabled = !isUploading
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.audio_pause)
                            } else {
                                stringResource(R.string.audio_play)
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    // Waveform / Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        StaticWaveform(
                            progress = if (totalDuration > 0) {
                                currentPosition.toFloat() / totalDuration.toFloat()
                            } else 0f,
                            activeColor = contentColor,
                            inactiveColor = contentColor.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Duration and status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current / Total duration
                        Text(
                            text = if (isPlaying || currentPosition > 0) {
                                "${formatDuration(currentPosition)} / ${formatDuration(totalDuration)}"
                            } else {
                                formatDuration(totalDuration)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        
                        // Time and status indicators
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = formatTime(timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                            
                            // Show status indicator only for outgoing messages
                            if (isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                AudioMessageStatusIndicator(
                                    status = messageStatus,
                                    isUploading = isUploading,
                                    tint = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Status indicator for audio messages.
 */
@Composable
fun AudioMessageStatusIndicator(
    status: MessageStatus,
    isUploading: Boolean,
    tint: Color
) {
    if (isUploading) {
        // Show clock/pending icon while uploading
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = stringResource(R.string.chat_message_sending),
            modifier = Modifier.size(14.dp),
            tint = tint
        )
        return
    }
    
    val icon = when (status) {
        MessageStatus.PENDING, MessageStatus.UPLOADING -> Icons.Default.Schedule
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.Schedule
    }
    
    val iconTint = when (status) {
        MessageStatus.READ -> Color(0xFF4FC3F7) // Light blue for read
        MessageStatus.FAILED -> Color(0xFFF44336) // Red for failed
        else -> tint
    }
    
    Icon(
        imageVector = icon,
        contentDescription = when (status) {
            MessageStatus.PENDING, MessageStatus.UPLOADING -> stringResource(R.string.chat_message_sending)
            MessageStatus.SENT -> stringResource(R.string.chat_message_sent)
            MessageStatus.DELIVERED -> stringResource(R.string.chat_message_delivered)
            MessageStatus.READ -> stringResource(R.string.chat_message_read)
            MessageStatus.FAILED -> stringResource(R.string.chat_message_failed)
        },
        modifier = Modifier.size(14.dp),
        tint = iconTint
    )
}

/**
 * Static waveform bars with progress indication.
 */
@Composable
private fun StaticWaveform(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        listOf(0.3f, 0.6f, 0.4f, 0.8f, 0.5f, 0.7f, 0.4f, 0.9f, 0.6f, 0.5f,
               0.7f, 0.4f, 0.6f, 0.8f, 0.5f, 0.3f, 0.7f, 0.5f, 0.6f, 0.4f,
               0.8f, 0.5f, 0.7f, 0.4f, 0.6f, 0.5f, 0.8f, 0.6f, 0.4f, 0.7f)
    }
    
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val barSpacing = 2.dp.toPx()
        val totalBarWidth = barWidth + barSpacing
        val numBars = (size.width / totalBarWidth).toInt().coerceAtMost(barHeights.size)
        
        val centerY = size.height / 2
        val progressIndex = (progress * numBars).toInt()
        
        for (i in 0 until numBars) {
            val x = i * totalBarWidth + barWidth / 2
            val barHeight = barHeights[i % barHeights.size] * size.height * 0.8f
            val color = if (i <= progressIndex) activeColor else inactiveColor
            
            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@Composable
private fun formatTime(timestamp: Long): String {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    return sdf.format(Date(timestamp))
}
