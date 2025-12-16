package com.taha.newraapp.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.taha.newraapp.R
import com.taha.newraapp.domain.model.DownloadStatus
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Image bubble that handles download states:
 * - NOT_STARTED: Shows placeholder with download button
 * - PENDING/DOWNLOADING: Shows placeholder with progress indicator
 * - COMPLETE: Shows the actual image
 * - FAILED: Shows retry button
 */
@Composable
fun DownloadableImageBubble(
    localPath: String?,
    downloadStatus: DownloadStatus?,
    fileSize: Long,
    isMe: Boolean,
    onDownloadClick: () -> Unit,
    onImageClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Float = 0f
) {
    // Check file existence in background to avoid Main thread I/O
    // Check file existence in background to avoid Main thread I/O
    // Only check if we have a path AND status implies completion, or if we want to verify an existing file
    val hasLocalFile by produceState(initialValue = false, key1 = localPath, key2 = downloadStatus) {
        if (!localPath.isNullOrBlank() && (downloadStatus == null || downloadStatus == DownloadStatus.COMPLETE)) {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                value = File(localPath).exists()
            }
        } else {
            value = false
        }
    }
    
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .widthIn(min = 150.dp, max = 220.dp)
            .heightIn(min = 120.dp, max = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bubbleColor)
            .then(
                if (hasLocalFile) {
                    Modifier.clickable(onClick = onImageClick)
                } else if (downloadStatus == DownloadStatus.NOT_STARTED || downloadStatus == DownloadStatus.FAILED) {
                    Modifier.clickable(onClick = onDownloadClick)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            // File downloaded - show actual image
            hasLocalFile -> {
                AsyncImage(
                    model = File(localPath!!),
                    contentDescription = stringResource(R.string.chat_image_attachment),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Downloading or pending - show placeholder with progress
            downloadStatus == DownloadStatus.DOWNLOADING || downloadStatus == DownloadStatus.PENDING -> {
                ImagePlaceholder()
                
                // Progress overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { if (downloadProgress > 0f) downloadProgress else 0f },
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            
            // Not started or failed - show download button
            else -> {
                ImagePlaceholder()
                
                // Download overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Download/Retry icon
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = if (downloadStatus == DownloadStatus.FAILED) {
                                        Icons.Default.Refresh
                                    } else {
                                        Icons.Default.Download
                                    },
                                    contentDescription = if (downloadStatus == DownloadStatus.FAILED) {
                                        stringResource(R.string.chat_retry)
                                    } else {
                                        stringResource(R.string.chat_download)
                                    },
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // File size
                        Text(
                            text = formatFileSize(fileSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Placeholder gradient for images that haven't been downloaded yet.
 */
@Composable
private fun ImagePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF424242),
                        Color(0xFF616161),
                        Color(0xFF757575)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .blur(2.dp),
            tint = Color.White.copy(alpha = 0.3f)
        )
    }
}

/**
 * Format file size for display.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
