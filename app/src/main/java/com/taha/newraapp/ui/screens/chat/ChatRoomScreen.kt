package com.taha.newraapp.ui.screens.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import com.taha.newraapp.R
import com.taha.newraapp.data.service.ActiveChatTracker
import com.taha.newraapp.data.service.MessageNotificationManager
import com.taha.newraapp.domain.model.MessageStatus
import com.taha.newraapp.ui.util.CameraUtils
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun ChatRoomScreen(
    onNavigateUp: () -> Unit,
    onImageClick: (filePath: String, senderName: String, timestamp: Long) -> Unit = { _, _, _ -> },
    viewModel: ChatViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val chatPartner by viewModel.chatPartnerUser.collectAsState()
    val messageInput by viewModel.messageInput.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val chatPartnerPresence by viewModel.chatPartnerPresence.collectAsState()
    val uploadProgressMap by viewModel.uploadProgressMap.collectAsState()
    val isPartnerTyping by viewModel.isPartnerTyping.collectAsState()
    
    // Notification suppression and clearing
    val notificationManager = koinInject<MessageNotificationManager>()
    
    // Track this chat as active and clear notifications when opened
    DisposableEffect(chatPartner?.id) {
        chatPartner?.id?.let { peerId ->
            ActiveChatTracker.setActiveChat(peerId)
        }
        
        onDispose {
            ActiveChatTracker.clearActiveChat()
        }
    }
    
    // Clear notifications in a LaunchedEffect (since it's a suspend function)
    LaunchedEffect(chatPartner?.id) {
        chatPartner?.id?.let { peerId ->
            notificationManager.clearNotificationsForUser(peerId)
        }
    }
    
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
    
    // Audio permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRecording(context)
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

    // Mark messages as seen when chat room is opened or new messages arrive.
    // Optimized: Only recalculate when messages or currentUser changes.
    val hasUnreadFromOther = remember(messages, currentUser) {
        messages.any { 
            it.senderId != currentUser?.id && it.status != MessageStatus.READ 
        }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Premium Header with presence status - stays fixed when keyboard opens
            ChatHeader(
                chatPartner = chatPartner,
                chatPartnerPresence = chatPartnerPresence,
                isTyping = isPartnerTyping,
                onBackClick = onNavigateUp,
                onAudioCallClick = {
                    chatPartner?.let { partner ->
                        val initials = "${partner.firstName.firstOrNull() ?: ""}${partner.lastName.firstOrNull() ?: ""}".uppercase()
                        val displayName = "${currentUser?.firstName ?: ""} ${currentUser?.lastName ?: ""}".trim()
                        context.startActivity(
                            com.taha.newraapp.ui.screens.call.OutgoingCallActivity.createIntent(
                                context = context,
                                calleeId = partner.id,
                                calleeName = "${partner.firstName} ${partner.lastName}".trim(),
                                calleeInitials = initials.ifEmpty { "?" },
                                isVideoCall = false,
                                userDisplayName = displayName.ifEmpty { "You" }
                            )
                        )
                    }
                },
                onVideoCallClick = {
                    chatPartner?.let { partner ->
                        val initials = "${partner.firstName.firstOrNull() ?: ""}${partner.lastName.firstOrNull() ?: ""}".uppercase()
                        val displayName = "${currentUser?.firstName ?: ""} ${currentUser?.lastName ?: ""}".trim()
                        context.startActivity(
                            com.taha.newraapp.ui.screens.call.OutgoingCallActivity.createIntent(
                                context = context,
                                calleeId = partner.id,
                                calleeName = "${partner.firstName} ${partner.lastName}".trim(),
                                calleeInitials = initials.ifEmpty { "?" },
                                isVideoCall = true,
                                userDisplayName = displayName.ifEmpty { "You" }
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Input area in bottomBar to anchor it above keyboard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Show recording bar or normal input based on recording state
                val isRecording by viewModel.isRecording.collectAsState()
                val recordingDuration by viewModel.recordingDuration.collectAsState()
                val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()

                if (isRecording) {
                    AudioRecordingBar(
                        durationMillis = recordingDuration,
                        amplitude = recordingAmplitude,
                        onStopClick = {
                            viewModel.stopRecording()?.let { filePath ->
                                viewModel.sendAttachment(
                                    Uri.fromFile(File(filePath)),
                                    "AUDIO"
                                )
                            }
                        },
                        onCancelClick = {
                            viewModel.cancelRecording()
                        }
                    )
                } else {
                    // Input footer extracted to ChatInput.kt
                    ChatInputArea(
                        inputValue = messageInput,
                        onValueChange = viewModel::onMessageinputChanged,
                        onSendClick = viewModel::sendMessage,
                        onCameraClick = {
                            // Request camera permission, then show photo/video dialog
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onAudioClick = {
                            // Request audio permission and start recording
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onFileClick = {
                            // TODO: Launch file picker
                        }
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0) // Don't consume any insets here
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // imePadding removed from here as it is now handled by bottomBar
                .background(MaterialTheme.colorScheme.background)
        ) {
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

            // Messages list extracted to MessageList.kt
            MessageList(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                messages = messages,
                currentUser = currentUser,
                chatPartner = chatPartner,
                uploadProgressMap = uploadProgressMap,
                isPartnerTyping = isPartnerTyping,
                onImageClick = onImageClick,
                onDownloadClick = { messageId -> viewModel.downloadAttachment(messageId) }
            )
        }
    }
}
