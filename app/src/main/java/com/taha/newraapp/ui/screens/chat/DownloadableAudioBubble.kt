package com.taha.newraapp.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taha.newraapp.R
import com.taha.newraapp.domain.model.DownloadStatus
import com.taha.newraapp.domain.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Audio bubble placeholder for audio that hasn't been downloaded yet.
 * Shows download button with file size, or progress indicator while downloading.
 */
@Composable
fun DownloadableAudioBubble(
    downloadStatus: DownloadStatus?,
    fileSize: Long,
    isMe: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    messageStatus: MessageStatus = MessageStatus.PENDING,
    timestamp: Long = System.currentTimeMillis()
) {
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

    val bubbleShape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Surface(
        modifier = modifier.widthIn(min = 200.dp, max = 280.dp),
        shape = bubbleShape,
        color = bubbleColor,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (downloadStatus == DownloadStatus.NOT_STARTED || 
                            downloadStatus == DownloadStatus.FAILED ||
                            downloadStatus == null
                        ) {
                            Modifier.clickable(onClick = onDownloadClick)
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Download/Progress button
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isMe) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (downloadStatus) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = if (isMe) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    strokeWidth = 2.dp
                                )
                            }
                            DownloadStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.chat_retry),
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isMe) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = stringResource(R.string.chat_download),
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isMe) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                        }
                    }
                }

                // Audio info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = stringResource(R.string.chat_audio_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                    }
                    
                    Text(
                        text = formatFileSize(fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp row
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )

                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    AudioMessageStatusIndicator(
                        status = messageStatus,
                        isUploading = false,
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "Audio"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
