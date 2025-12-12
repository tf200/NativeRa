package com.taha.newraapp.ui.screens.chat

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.taha.newraapp.R
import com.taha.newraapp.data.model.UserPresenceStatus
import com.taha.newraapp.data.socket.SocketStatus
import com.taha.newraapp.domain.model.Message
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.ui.theme.TestRaTheme
import com.taha.newraapp.ui.util.CameraUtils
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

@Composable
fun ChatRoomScreen(
    onNavigateUp: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val chatPartner by viewModel.chatPartnerUser.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val chatPartnerPresence by viewModel.chatPartnerPresence.collectAsState()
    val listState = rememberLazyListState()
    
    // Camera state
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var showCameraTypeDialog by remember { mutableStateOf(false) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }
    var tempVideoFile by remember { mutableStateOf<File?>(null) }
    
    // Photo capture launcher
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            // Compress and send photo
            val compressedUri = CameraUtils.compressImage(context, photoUri!!)
            viewModel.sendAttachment(compressedUri, "IMAGE")
        }
    }
    
    // Video capture launcher (low quality)
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && videoUri != null) {
            // Send video
            viewModel.sendAttachment(videoUri!!, "VIDEO")
        }
    }
    
    // Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraTypeDialog = true
        }
    }
    
    // Launch photo capture
    fun launchPhotoCapture() {
        val file = CameraUtils.createImageFile(context)
        tempPhotoFile = file
        photoUri = CameraUtils.getUriForFile(context, file)
        photoLauncher.launch(photoUri!!)
    }
    
    // Launch video capture with low quality
    fun launchVideoCapture() {
        val file = CameraUtils.createVideoFile(context)
        tempVideoFile = file
        videoUri = CameraUtils.getUriForFile(context, file)
        videoLauncher.launch(videoUri!!)
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Mark messages as seen when chat room is opened or new messages arrive.
    // Only triggers once per new batch of unread messages (debounced).
    // The service layer handles filtering out already-read messages.
    val hasUnreadFromOther = messages.any { 
        it.senderId != currentUser?.id && it.status != MessageStatus.READ 
    }
    
    LaunchedEffect(hasUnreadFromOther) {
        if (hasUnreadFromOther) {
            // Small delay to batch rapid updates
            kotlinx.coroutines.delay(200)
            viewModel.markMessagesAsSeen()
        }
    }
    
    // Photo/Video selection dialog
    if (showCameraTypeDialog) {
        AlertDialog(
            onDismissRequest = { showCameraTypeDialog = false },
            title = { Text(stringResource(R.string.chat_camera_dialog_title)) },
            text = { Text(stringResource(R.string.chat_camera_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCameraTypeDialog = false
                    launchPhotoCapture()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.chat_camera_photo))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCameraTypeDialog = false
                    launchVideoCapture()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Videocam, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.chat_camera_video))
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Premium Header with presence status
        ChatHeader(
            chatPartner = chatPartner,
            chatPartnerPresence = chatPartnerPresence,
            onBackClick = onNavigateUp
        )

        // Error banner
        sendError?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.chat_dismiss))
                    }
                }
            }
        }

        // Messages list with date separators
        val todayString = stringResource(R.string.chat_date_today)
        val yesterdayString = stringResource(R.string.chat_date_yesterday)
        
        val groupedItems = remember(messages, todayString, yesterdayString) {
            buildChatListWithDateSeparators(messages, todayString, yesterdayString)
        }
        
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = groupedItems.size,
                key = { index ->
                    when (val item = groupedItems[index]) {
                        is ChatListItem.DateHeader -> "date_${item.dateLabel}"
                        is ChatListItem.MessageItem -> item.message.id
                    }
                }
            ) { index ->
                when (val item = groupedItems[index]) {
                    is ChatListItem.DateHeader -> {
                        DateSeparator(dateLabel = item.dateLabel)
                    }
                    is ChatListItem.MessageItem -> {
                        val isMe = item.message.senderId == currentUser?.id
                        MessageBubble(message = item.message, isMe = isMe)
                    }
                }
            }
        }

        // Input footer with attachment menu
        ChatInputArea(
            inputValue = messageInput,
            onValueChange = viewModel::onMessageinputChanged,
            onSendClick = viewModel::sendMessage,
            onCameraClick = {
                // Request camera permission, then show photo/video dialog
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onAudioClick = {
                // TODO: Launch audio recorder
                // This will be connected to audio recording handling  
            },
            onFileClick = {
                // TODO: Launch file picker
                // This will be connected to file picker intent handling
            }
        )
    }
}

@Composable
fun ChatHeader(
    chatPartner: User?,
    chatPartnerPresence: UserPresenceStatus?,
    onBackClick: () -> Unit
) {
    val initials = if (chatPartner != null) {
        val first = chatPartner.firstName.firstOrNull()?.toString() ?: ""
        val last = chatPartner.lastName.firstOrNull()?.toString() ?: ""
        (first + last).uppercase()
    } else stringResource(R.string.chat_user_initials_fallback)
    
    val displayName = if (chatPartner != null) {
        "${chatPartner.firstName} ${chatPartner.lastName}"
    } else stringResource(R.string.chat_loading)
    
    val statusText = when {
        chatPartnerPresence?.online == true -> stringResource(R.string.chat_online)
        chatPartnerPresence?.lastSeen != null -> formatLastSeen(chatPartnerPresence.lastSeen)
        else -> stringResource(R.string.chat_offline)
    }
    
    val statusColor = if (chatPartnerPresence?.online == true) {
        Color(0xFF4CAF50) // Green for online
    } else {
        Color(0xFF9E9E9E) // Gray for offline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        TestRaTheme.extendedColors.headerBackground,
                        TestRaTheme.extendedColors.headerBackground.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(top = 8.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.chat_back_description),
                    tint = Color.White
                )
            }

            // Avatar with online indicator
            Box(
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Online indicator - dynamic based on presence
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .padding(2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and status
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Action buttons
            IconButton(onClick = { /* TODO: Voice call */ }) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = stringResource(R.string.chat_voice_call_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = { /* TODO: Video call */ }) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = stringResource(R.string.chat_video_call_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
            IconButton(onClick = { /* TODO: More options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.chat_options_description),
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean
) {
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

    val bubbleShape = if (isMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    // Outer row for alignment (start or end)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        // Bubble wraps to content size with max width constraint
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Time and status indicator row - wrap content, aligned end
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                    
                    // Show status indicator only for outgoing messages
                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIndicator(
                            status = message.status,
                            tint = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays the appropriate status indicator icon based on message status.
 * - PENDING: Clock icon (message being sent)
 * - SENT: Single check (message reached server)
 * - DELIVERED: Double check gray (message delivered to recipient)
 * - READ: Double check blue (message seen by recipient)
 * - FAILED: Error indicator (could add later)
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    tint: Color
) {
    val icon = when (status) {
        MessageStatus.PENDING -> Icons.Default.Schedule
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
            MessageStatus.PENDING -> stringResource(R.string.chat_message_sending)
            MessageStatus.SENT -> stringResource(R.string.chat_message_sent)
            MessageStatus.DELIVERED -> stringResource(R.string.chat_message_delivered)
            MessageStatus.READ -> stringResource(R.string.chat_message_read)
            MessageStatus.FAILED -> stringResource(R.string.chat_message_failed)
        },
        modifier = Modifier.size(iconSize),
        tint = iconTint
    )
}

@Composable
fun ChatInputArea(
    inputValue: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCameraClick: () -> Unit = {},
    onAudioClick: () -> Unit = {},
    onFileClick: () -> Unit = {},
    isSending: Boolean = false
) {
    var showAttachmentMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment button with menu
            Box {
                IconButton(
                    onClick = { showAttachmentMenu = true },
                    enabled = !isSending
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = stringResource(R.string.chat_attach_description),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Attachment menu dropdown
                DropdownMenu(
                    expanded = showAttachmentMenu,
                    onDismissRequest = { showAttachmentMenu = false }
                ) {
                    // Camera option (photos/videos)
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.chat_attach_camera))
                            }
                        },
                        onClick = {
                            showAttachmentMenu = false
                            onCameraClick()
                        }
                    )
                    
                    // Audio option
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.chat_attach_audio))
                            }
                        },
                        onClick = {
                            showAttachmentMenu = false
                            onAudioClick()
                        }
                    )
                    
                    // File option
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(stringResource(R.string.chat_attach_file))
                            }
                        },
                        onClick = {
                            showAttachmentMenu = false
                            onFileClick()
                        }
                    )
                }
            }

            // Text input
            OutlinedTextField(
                value = inputValue,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = {
                    Text(stringResource(R.string.chat_input_placeholder))
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                singleLine = false,
                maxLines = 4,
                enabled = !isSending
            )

            // Send button
            FilledIconButton(
                onClick = onSendClick,
                enabled = inputValue.isNotBlank() && !isSending,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send_description)
                    )
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Format a last seen timestamp into a human-readable string.
 */
@Composable
private fun formatLastSeen(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> stringResource(R.string.chat_last_seen_just_now)
        diff < 3600_000 -> stringResource(R.string.chat_last_seen_minutes_ago, diff / 60_000)
        diff < 86400_000 -> stringResource(R.string.chat_last_seen_hours_ago, diff / 3600_000)
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            stringResource(R.string.chat_last_seen_date, sdf.format(Date(timestamp)))
        }
    }
}

// ========================================
// Date Separator Support
// ========================================

/**
 * Represents an item in the chat list - either a date header or a message.
 */
sealed class ChatListItem {
    data class DateHeader(val dateLabel: String) : ChatListItem()
    data class MessageItem(val message: Message) : ChatListItem()
}

/**
 * Date separator composable - displays a centered date pill between messages.
 * Styled like WhatsApp/iMessage date separators.
 */
@Composable
fun DateSeparator(dateLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shadowElevation = 0.dp
        ) {
            Text(
                text = dateLabel,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Build a list of ChatListItems from messages, inserting date headers when the date changes.
 */
private fun buildChatListWithDateSeparators(
    messages: List<Message>,
    todayString: String,
    yesterdayString: String
): List<ChatListItem> {
    if (messages.isEmpty()) return emptyList()
    
    val result = mutableListOf<ChatListItem>()
    var lastDateLabel: String? = null
    
    for (message in messages) {
        val currentDateLabel = formatDateForSeparator(message.timestamp, todayString, yesterdayString)
        
        // Insert date header when the date changes
        if (currentDateLabel != lastDateLabel) {
            result.add(ChatListItem.DateHeader(currentDateLabel))
            lastDateLabel = currentDateLabel
        }
        
        result.add(ChatListItem.MessageItem(message))
    }
    
    return result
}

/**
 * Format a timestamp for the date separator.
 * Returns "Today", "Yesterday", or a formatted date (e.g., "December 10, 2024").
 */
/**
 * Format a timestamp for the date separator.
 * Returns "Today", "Yesterday", or a formatted date (e.g., "December 10, 2024").
 */
private fun formatDateForSeparator(
    timestamp: Long,
    todayString: String,
    yesterdayString: String
): String {
    val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    
    return when {
        isSameDay(messageCalendar, today) -> todayString
        isSameDay(messageCalendar, yesterday) -> yesterdayString
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * Check if two Calendar instances represent the same day.
 */
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
