package com.taha.newraapp.ui.screens.media

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.taha.newraapp.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Full-screen media viewer with pinch-to-zoom for images and ExoPlayer for videos.
 * Displays sender name and timestamp in the top app bar like WhatsApp.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    filePath: String,
    senderName: String,
    timestamp: Long,
    onBack: () -> Unit
) {
    // Determine if this is a video based on file extension
    val isVideo = remember(filePath) {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        extension in listOf("mp4", "3gp", "mkv", "webm", "mov", "avi")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = formatDateTime(timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back_description),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isVideo) {
                VideoPlayer(filePath = filePath)
            } else {
                ZoomableImage(filePath = filePath)
            }
        }
    }
}

/**
 * Zoomable image viewer with pinch-to-zoom and pan gestures.
 */
@Composable
private fun ZoomableImage(filePath: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val newOffset = if (newScale > 1f) {
            offset + panChange
        } else {
            Offset.Zero
        }
        scale = newScale
        offset = newOffset
    }
    
    LaunchedEffect(scale) {
        if (scale <= 1f) {
            offset = Offset.Zero
        }
    }

    AsyncImage(
        model = File(filePath),
        contentDescription = stringResource(R.string.chat_image_attachment),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(state = transformableState),
        contentScale = ContentScale.Fit
    )
}

/**
 * Video player using ExoPlayer with controls.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(filePath: String) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri("file://$filePath")
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Format timestamp to human-readable date and time.
 */
private fun formatDateTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
