package com.taha.newraapp.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taha.newraapp.R
import java.util.Locale

/**
 * Recording bar UI that shows timer, waveform, and controls.
 * Replaces ChatInputArea when recording is active.
 */
@Composable
fun AudioRecordingBar(
    durationMillis: Long,
    amplitude: Int,
    onStopClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Store amplitude history for waveform
    val amplitudeHistory = remember { mutableStateListOf<Float>() }
    val maxBars = 40
    
    // Update amplitude history
    LaunchedEffect(amplitude) {
        val normalizedAmp = (amplitude.toFloat() / 32767f).coerceIn(0.05f, 1f)
        amplitudeHistory.add(normalizedAmp)
        if (amplitudeHistory.size > maxBars) {
            amplitudeHistory.removeAt(0)
        }
    }
    
    // Pulsing animation for recording indicator
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button
            IconButton(onClick = onCancelClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.audio_cancel),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            
            // Recording indicator dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = Color.Red.copy(alpha = pulseAlpha),
                        shape = CircleShape
                    )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Timer
            Text(
                text = formatDuration(durationMillis),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Waveform visualization
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                WaveformVisualizer(
                    amplitudes = amplitudeHistory,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Stop/Send button
            FilledIconButton(
                onClick = onStopClick,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.audio_stop)
                )
            }
        }
    }
}

/**
 * Canvas-based waveform visualizer.
 */
@Composable
private fun WaveformVisualizer(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier) {
        if (amplitudes.isEmpty()) return@Canvas
        
        val barWidth = 4.dp.toPx()
        val barSpacing = 2.dp.toPx()
        val totalBarWidth = barWidth + barSpacing
        val maxBars = (size.width / totalBarWidth).toInt()
        
        val startIndex = maxOf(0, amplitudes.size - maxBars)
        val visibleAmplitudes = amplitudes.subList(startIndex, amplitudes.size)
        
        val centerY = size.height / 2
        
        visibleAmplitudes.forEachIndexed { index, amplitude ->
            val x = index * totalBarWidth + barWidth / 2
            val barHeight = (amplitude * size.height * 0.8f).coerceAtLeast(4.dp.toPx())
            
            drawLine(
                color = barColor,
                start = Offset(x, centerY - barHeight / 2),
                end = Offset(x, centerY + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Format milliseconds to MM:SS string.
 */
private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
