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
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.taha.newraapp.MainActivity
import com.taha.newraapp.R
import com.taha.newraapp.data.local.dao.MessageDao
import com.taha.newraapp.data.local.entities.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import kotlin.math.abs

/**
 * Manages WhatsApp-style notifications for incoming messages.
 * 
 * Features:
 * - Per-sender notifications with InboxStyle (shows last 5 messages)
 * - Summary notification when multiple senders
 * - Circular avatar with initials
 * - Message grouping per sender
 * - Smart suppression using ActiveChatTracker
 * - Deep linking to specific chat
 * - Badge count on app icon
 */
class MessageNotificationManager(
    private val context: Context,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "MessageNotification"
        private const val CHANNEL_ID = "chat_messages"
        private const val GROUP_KEY = "com.taha.newraapp.MESSAGES"
        private const val SUMMARY_ID = 0
        
        // Avatar settings
        private const val AVATAR_SIZE = 128
        private const val AVATAR_TEXT_SIZE = 56f
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Show notification for incoming message.
     * Handles per-sender notifications and summary notification.
     * 
     * @return true if notification was shown, false if suppressed
     */
    suspend fun showMessageNotification(
        messageId: String,
        senderId: String,
        senderName: String,
        messageContent: String,
        messageType: String,
        attachmentType: String? = null
    ): Boolean {
        try {
            // Check for notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Notification permission not granted, suppressing notification")
                    return false
                }
            }

            // Check if this chat is currently active on screen
            if (ActiveChatTracker.isActiveChatWith(senderId)) {
                Log.d(TAG, "Suppressing notification - chat is active: $senderName")
                return false
            }
            
            Log.i(TAG, "Showing notification from: $senderName")
            
            // Format message preview
            val messagePreview = formatMessagePreview(messageContent, messageType, attachmentType)
            
            // Get recent messages from this sender for InboxStyle
            val recentMessages = getRecentMessagesForNotification(senderId, messageId)
            
            // Create notification for this sender
            showSenderNotification(
                senderId = senderId,
                senderName = senderName,
                messages = recentMessages + messagePreview,
                messageCount = recentMessages.size + 1
            )
            
            // Update summary notification if multiple senders
            updateSummaryNotification()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification", e)
            return false
        }
    }
    
    /**
     * Show notification for a specific sender with InboxStyle.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun showSenderNotification(
        senderId: String,
        senderName: String,
        messages: List<String>,
        messageCount: Int
    ) {
        // Create circular avatar bitmap on Default dispatcher (CPU intensive)
        val avatarBitmap = withContext(Dispatchers.Default) {
            createCircularAvatar(senderName)
        }
        val avatarIcon = IconCompat.createWithBitmap(avatarBitmap)
        
        // Create Person for messaging style
        val sender = Person.Builder()
            .setName(senderName)
            .setIcon(avatarIcon)
            .build()
        
        // Create intent to open this specific chat
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "chat")
            putExtra("chat_user_id", senderId)
            putExtra("chat_user_name", senderName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            senderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build InboxStyle notification
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("$senderName ($messageCount)")
        
        // Add messages to inbox (last 5)
        messages.takeLast(5).forEach { message ->
            inboxStyle.addLine(message)
        }
        
        if (messages.size > 5) {
            inboxStyle.setSummaryText("+${messages.size - 5} more messages")
        }
        
        // Build notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatarBitmap)
            .setContentTitle(senderName)
            .setContentText(messages.lastOrNull() ?: "New message")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(pendingIntent)
            .setNumber(messageCount)
            .addPerson(sender)
            .build()
        
        // Show notification (permission checked in caller)
        try {
            notificationManager.notify(senderId.hashCode(), notification)
            Log.d(TAG, "Notification shown for $senderName ($messageCount messages)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception showing notification", e)
        }
    }
    
    /**
     * Update summary notification when multiple senders have unread messages.
     */
    private suspend fun updateSummaryNotification() {
        try {
            // Check permission again as good practice, though usually covered by caller
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.app.ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            // Get active notifications (excludes summary)
            val activeNotifications =
                notificationManager.activeNotifications
                    .filter { it.id != SUMMARY_ID }
                    .size

            // Only show summary if 2+ senders
            if (activeNotifications < 2) {
                notificationManager.cancel(SUMMARY_ID)
                return
            }
            
            // Get total unread count
            val totalUnread = messageDao.getTotalUnreadCount().first()
            
            // Create summary notification
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New Messages")
                .setContentText("$totalUnread new messages from $activeNotifications chats")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setNumber(totalUnread)
                .build()
            
            notificationManager.notify(SUMMARY_ID, summaryNotification)
            Log.d(TAG, "Summary notification updated: $totalUnread from $activeNotifications chats")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating summary notification", e)
        }
    }
    
    /**
     * Get recent message previews from a sender for InboxStyle.
     */
    private suspend fun getRecentMessagesForNotification(senderId: String, excludeMessageId: String): List<String> {
        return try {
            val messages = withContext(Dispatchers.IO) {
                messageDao.getMessages(senderId).first()
            }
            
            // Get last 4 unread messages (we'll add the new one as 5th)
            messages
                .filter { it.status != "READ" && it.senderId == senderId && it.id != excludeMessageId }
                .takeLast(4)
                .map { message: MessageEntity ->
                    formatMessagePreview(
                        message.content,
                        message.type,
                        message.attachmentFileType
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent messages", e)
            emptyList()
        }
    }
    
    /**
     * Format message preview text.
     */
    private fun formatMessagePreview(
        content: String,
        messageType: String,
        attachmentType: String?
    ): String {
        return when {
            messageType == "media" -> {
                when (attachmentType) {
                    "IMAGE" -> "📷 Photo"
                    "VIDEO" -> "🎥 Video"
                    "AUDIO" -> "🎵 Audio"
                    else -> "📎 File"
                }
            }
            content.isNotBlank() -> content
            else -> "New message"
        }
    }
    
    /**
     * Create a circular avatar bitmap with initials.
     */
    private fun createCircularAvatar(name: String): Bitmap {
        val bitmap = createBitmap(AVATAR_SIZE, AVATAR_SIZE)
        val canvas = Canvas(bitmap)
        
        // Generate color from name (consistent for same name)
        val color = generateColorFromName(name)
        
        // Draw circle
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
        
        // Draw initials
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
    
    /**
     * Get initials from name (first letter of first 2 words).
     */
    private fun getInitials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }
    
    /**
     * Generate consistent color from name.
     */
    private fun generateColorFromName(name: String): Int {
        val colors = listOf(
            "#E91E63".toColorInt(), // Pink
            "#9C27B0".toColorInt(), // Purple
            "#673AB7".toColorInt(), // Deep Purple
            "#3F51B5".toColorInt(), // Indigo
            "#2196F3".toColorInt(), // Blue
            "#00BCD4".toColorInt(), // Cyan
            "#009688".toColorInt(), // Teal
            "#4CAF50".toColorInt(), // Green
            "#FF9800".toColorInt(), // Orange
            "#FF5722".toColorInt()  // Deep Orange
        )
        
        val hash = name.hashCode()
        return colors[abs(hash) % colors.size]
    }
    
    /**
     * Clear notifications for a specific user (when they open the chat).
     */
    suspend fun clearNotificationsForUser(userId: String) {
        notificationManager.cancel(userId.hashCode())
        Log.d(TAG, "Cleared notifications for user: $userId")
        
        // Update summary notification count
        updateSummaryNotification()
    }
    
    /**
     * Clear all message notifications.
     */
    fun clearAllNotifications() {
        notificationManager.cancel(SUMMARY_ID)
        // Can't easily cancel all individual notifications without tracking IDs
        // They'll be auto-canceled when user opens respective chats
    }
    
    /**
     * Create notification channel (required for Android O+).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New message notifications"
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            enableLights(true)
            lightColor = Color.BLUE
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
