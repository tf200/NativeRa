package com.taha.newraapp.data.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import com.taha.newraapp.R
import com.taha.newraapp.ui.screens.call.IncomingCallActivity
import kotlin.math.abs

/**
 * Manages incoming call notifications with full-screen intent support.
 * 
 * Features:
 * - Full-screen intent when screen is off (phone call style)
 * - Heads-up notification when screen is on
 * - Accept/Decline action buttons
 * - Ringtone and vibration
 * - Circular avatar with caller initials
 */
class CallNotificationManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "CallNotification"
        private const val CHANNEL_ID = "incoming_calls"
        private const val NOTIFICATION_ID = 999999  // Fixed ID for call notification
        
        // Avatar settings
        private const val AVATAR_SIZE = 128
        private const val AVATAR_TEXT_SIZE = 56f
        
        // Extra keys for intent
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_CALL_TYPE = "call_type"
        const val EXTRA_CALLER_ID = "caller_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_ACTION = "action"
        
        const val ACTION_ACCEPT = "accept"
        const val ACTION_DECLINE = "decline"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Show incoming call notification.
     * Uses full-screen intent when screen is off, heads-up when on.
     */
    fun showIncomingCallNotification(
        callId: String,
        roomId: String,
        callType: String,
        callerId: String,
        callerName: String
    ) {
        Log.d(TAG, "Showing incoming call notification from: $callerName ($callType)")
        
        // Check permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted")
                return
            }
        }
        
        // Create avatar
        val avatarBitmap = createCircularAvatar(callerName)
        
        // Full-screen intent - opens IncomingCallActivity
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Accept action
        val acceptIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_CALL_TYPE, callType)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_ACTION, ACTION_ACCEPT)
        }
        
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            1,
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Decline action - uses broadcast receiver
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = "com.taha.newraapp.DECLINE_CALL"
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
        }
        
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification
        val callTypeLabel = if (callType == "video") "Video call" else "Audio call"
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatarBitmap)
            .setContentTitle(callerName)
            .setContentText(callTypeLabel)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(false)
            .setOngoing(true)  // Can't be swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(
                R.drawable.ic_notification, // TODO: Use accept icon
                "Accept",
                acceptPendingIntent
            )
            .addAction(
                R.drawable.ic_notification, // TODO: Use decline icon  
                "Decline",
                declinePendingIntent
            )
            .setTimeoutAfter(30_000)  // Auto-dismiss after 30 seconds
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Incoming call notification shown")
            
            // Wake up screen if off
            wakeUpScreen()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception showing call notification", e)
        }
    }
    
    /**
     * Cancel the incoming call notification.
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Call notification cancelled")
    }
    
    /**
     * Wake up the screen to show full-screen intent.
     */
    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        if (!powerManager.isInteractive) {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "newraapp:incoming_call"
            )
            wakeLock.acquire(10_000)  // 10 seconds
            Log.d(TAG, "Screen woken up for incoming call")
        }
    }
    
    /**
     * Create notification channel for incoming calls.
     */
    private fun createNotificationChannel() {
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .build()
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming call notifications"
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
            enableLights(true)
            lightColor = Color.GREEN
            setSound(ringtoneUri, audioAttributes)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)  // Ring even in DND
        }
        
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    /**
     * Create a circular avatar bitmap with initials.
     */
    private fun createCircularAvatar(name: String): Bitmap {
        val bitmap = createBitmap(AVATAR_SIZE, AVATAR_SIZE)
        val canvas = Canvas(bitmap)
        
        val color = generateColorFromName(name)
        
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
        }
        canvas.drawCircle(
            AVATAR_SIZE / 2f,
            AVATAR_SIZE / 2f,
            AVATAR_SIZE / 2f,
            paint
        )
        
        val initials = getInitials(name)
        val textPaint = Paint().apply {
            isAntiAlias = true
            this.color = Color.WHITE
            textSize = AVATAR_TEXT_SIZE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        val textBounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        
        val textX = AVATAR_SIZE / 2f
        val textY = AVATAR_SIZE / 2f - textBounds.exactCenterY()
        
        canvas.drawText(initials, textX, textY, textPaint)
        
        return bitmap
    }
    
    private fun getInitials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }
    
    private fun generateColorFromName(name: String): Int {
        val colors = listOf(
            "#4CAF50".toColorInt(), // Green (call theme)
            "#2196F3".toColorInt(), // Blue
            "#9C27B0".toColorInt(), // Purple
            "#FF5722".toColorInt(), // Deep Orange
            "#00BCD4".toColorInt()  // Cyan
        )
        val hash = name.hashCode()
        return colors[abs(hash) % colors.size]
    }
}
