package com.taha.newraapp.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.taha.newraapp.R
import com.taha.newraapp.data.repository.UploadProgress
import com.taha.newraapp.data.repository.UploadState
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    uploadProgress: UploadProgress? = null,
    senderName: String = "",
    onImageClick: (filePath: String, senderName: String, timestamp: Long) -> Unit = { _, _, _ -> },
    onDownloadClick: (messageId: String) -> Unit = { }
) {
    // Memoize callbacks to prevent unnecessary recomposition of children
    val onDownloadClickResolved = remember(message.id, onDownloadClick) {
        { onDownloadClick(message.id) }
    }
    
    val onImageClickResolved = remember(message.id, message.attachmentLocalPath, message.timestamp, senderName, onImageClick) {
        {
            message.attachmentLocalPath?.let { path ->
                onImageClick(path, senderName, message.timestamp)
            }
        }
    }
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Optimization: Memoize shapes to avoid updates on every recomposition
    val bubbleShape = remember(isMe) {
        if (isMe) {
            RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
        } else {
            RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
        }
    }

    // Check attachment type - use fileType, not localPath (path may be null during download)
    val isImageMessage = message.attachmentFileType.equals("IMAGE", ignoreCase = true)
    val isVideoMessage = message.attachmentFileType.equals("VIDEO", ignoreCase = true)
    val isAudioMessage = message.attachmentFileType.equals("AUDIO", ignoreCase = true)
    val isMediaMessage = isImageMessage || isVideoMessage || isAudioMessage
    
    // Parse download status
    val downloadStatus = message.downloadStatus

    // Outer row for alignment (start or end)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        // Bubble wraps to content size with max width constraint
        // Audio messages render their own bubble
        if (isAudioMessage) {
            val hasLocalAudio = !message.attachmentLocalPath.isNullOrBlank() && 
                java.io.File(message.attachmentLocalPath).exists()
            
            if (hasLocalAudio) {
                AudioMessageBubble(
                    localPath = message.attachmentLocalPath!!,
                    isMe = isMe,
                    uploadProgress = uploadProgress,
                    messageStatus = message.status,
                    timestamp = message.timestamp
                )
            } else {
                // Show download placeholder for audio that hasn't been downloaded
                DownloadableAudioBubble(
                    downloadStatus = downloadStatus,
                    fileSize = message.attachmentSize ?: 0L,
                    isMe = isMe,
                    onDownloadClick = onDownloadClickResolved,
                    messageStatus = message.status,
                    timestamp = message.timestamp
                )
            }
        } else {
            Surface(
                modifier = Modifier.widthIn(max = if (isMediaMessage) 220.dp else 280.dp),
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isMediaMessage) 4.dp else 12.dp,
                        vertical = if (isMediaMessage) 4.dp else 8.dp
                    )
                ) {
                    // Image, video, or text content
                    when {
                        isImageMessage -> {
                            DownloadableImageBubble(
                                localPath = message.attachmentLocalPath,
                                downloadStatus = downloadStatus,
                                fileSize = message.attachmentSize ?: 0L,
                                isMe = isMe,
                                onDownloadClick = onDownloadClickResolved,
                                onImageClick = { onImageClickResolved() }
                            )
                        }
                        isVideoMessage -> {
                            DownloadableVideoBubble(
                                localPath = message.attachmentLocalPath,
                                downloadStatus = downloadStatus,
                                fileSize = message.attachmentSize ?: 0L,
                                isMe = isMe,
                                onDownloadClick = onDownloadClickResolved,
                                onVideoClick = { onImageClickResolved() }
                            )
                        }
                        else -> {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Time and status indicator row - wrap content, aligned end
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = if (isMediaMessage) 4.dp else 0.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMediaMessage) Color.White.copy(alpha = 0.9f) else textColor.copy(alpha = 0.7f)
                        )
                        
                        // Show status indicator only for outgoing messages
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            MessageStatusIndicator(
                                status = message.status,
                                tint = if (isMediaMessage) Color.White.copy(alpha = 0.9f) else textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}



/**
 * Displays the appropriate status indicator icon based on message status.
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    tint: Color
) {
    val icon = when (status) {
        MessageStatus.PENDING, MessageStatus.UPLOADING -> Icons.Default.Schedule
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.Done // Could use error icon
    }
    
    val iconTint = when (status) {
        MessageStatus.READ -> Color(0xFF4FC3F7) // Light blue for read
        MessageStatus.FAILED -> Color(0xFFF44336) // Red for failed
        else -> tint
    }
    
    val iconSize = 16.dp
    
    Icon(
        imageVector = icon,
        contentDescription = when (status) {
            MessageStatus.PENDING, MessageStatus.UPLOADING -> stringResource(R.string.chat_message_sending)
            MessageStatus.SENT -> stringResource(R.string.chat_message_sent)
            MessageStatus.DELIVERED -> stringResource(R.string.chat_message_delivered)
            MessageStatus.READ -> stringResource(R.string.chat_message_read)
            MessageStatus.FAILED -> stringResource(R.string.chat_message_failed)
        },
        modifier = Modifier.size(iconSize),
        tint = iconTint
    )
}

// Optimized Date Formatter caching
@Composable
private fun formatTime(timestamp: Long): String {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    return sdf.format(Date(timestamp))
}
