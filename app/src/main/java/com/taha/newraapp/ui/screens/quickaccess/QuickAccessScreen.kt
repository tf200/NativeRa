package com.taha.newraapp.ui.screens.quickaccess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.taha.newraapp.R
import com.taha.newraapp.ui.navigation.Screen
import com.taha.newraapp.ui.theme.Amber500
import com.taha.newraapp.ui.theme.Slate100
import com.taha.newraapp.ui.theme.TestRaTheme

@Composable
fun QuickAccessScreen(
    onNavigateTo: (String) -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===========================================
        // Live Alerts Section
        // ===========================================
        item {
            SectionHeader(title = stringResource(R.string.quick_access_live_alerts), action = stringResource(R.string.quick_access_see_all), onActionClick = { onNavigateTo(Screen.Alerts.route) })
        }
        item {
            AlertCard(
                severity = "URGENT",
                severityColor = MaterialTheme.colorScheme.error, // Red
                title = "Accident: Highway A1 - KM 45",
                time = "10 min"
            )
        }
        item {
            AlertCard(
                severity = "MEDIUM",
                severityColor = Amber500, // Amber/Orange
                title = "Congestion: Tunis Center",
                time = "35 min"
            )
        }

        // ===========================================
        // Actions Section (New Report + Grid)
        // ===========================================
        item {
            NewReportCard(onClick = { onNavigateTo(Screen.AccidentForm.route) })
        }

        item {
            // Grid Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionItem(
                    title = stringResource(R.string.quick_access_map),
                    icon = Icons.Default.Map,
                    iconColor = MaterialTheme.colorScheme.primary, // Purple
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTo(Screen.GeoPosition.route) }
                )
                QuickActionItem(
                    title = stringResource(R.string.quick_access_messages),
                    icon = Icons.Default.Chat,
                    iconColor = MaterialTheme.colorScheme.primary, // Purple
                    badgeCount = 3,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTo(Screen.Chat.route) }
                )
            }
        }
        item {
            // Grid Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionItem(
                    title = stringResource(R.string.quick_access_alerts),
                    icon = Icons.Default.Notifications,
                    iconColor = Amber500, // Amber/Yellow
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTo(Screen.Alerts.route) }
                )
                QuickActionItem(
                    title = stringResource(R.string.quick_access_settings),
                    icon = Icons.Default.Settings,
                    iconColor = Color.Gray, // Gray
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTo(Screen.Settings.route) }
                )
            }
        }

        // ===========================================
        // Recent Reports Section
        // ===========================================
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = stringResource(R.string.quick_access_recent_reports), action = stringResource(R.string.quick_access_see_all))
        }
        
        item {
            RecentReportItem(title = "Collision", location = "Ave Habib Bourguiba • 2v", time = "2h", status = "Draft", statusColor = Amber500, icon = Icons.Default.CarCrash)
        }
        item {
            RecentReportItem(title = "Pedestrian", location = "La Marsa • 1v", time = "5h", status = "Submitted", statusColor = Color(0xFF4CAF50), icon = Icons.Default.DirectionsWalk) // Green
        }
        item {
            RecentReportItem(title = "Hit & Run", location = "Ariana • 1v", time = "1d", status = "In Review", statusColor = TestRaTheme.extendedColors.textPrimary, icon = Icons.Default.Warning) // Purple
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String, onActionClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TestRaTheme.extendedColors.textPrimary
        )
        TextButton(onClick = onActionClick) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = TestRaTheme.extendedColors.textPrimary
            )
        }
    }
}

@Composable
fun AlertCard(severity: String, severityColor: Color, title: String, time: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate100),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(severityColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = severityColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = severityColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = severity,
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = TestRaTheme.extendedColors.textMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = TestRaTheme.extendedColors.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TestRaTheme.extendedColors.textPrimary
                )
                Text(
                    text = stringResource(R.string.quick_access_view_details),
                    style = MaterialTheme.typography.labelMedium,
                    color = TestRaTheme.extendedColors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun NewReportCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), // Purple
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().height(120.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.quick_access_new_report),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.quick_access_log_accident),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
                
                // Plus button with decorative circle behind it
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    // Decorative circle - larger, behind the plus button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                    )
                    // Plus button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                         Icon(
                             imageVector = Icons.Default.Add,
                             contentDescription = "Add",
                             tint = Color.White
                         )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate100),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.aspectRatio(1.3f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TestRaTheme.extendedColors.textPrimary
                )
            }
            
            if (badgeCount > 0) {
                 Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RecentReportItem(
    title: String,
    location: String,
    time: String,
    status: String,
    statusColor: Color,
    icon: ImageVector = Icons.Default.Description
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Amber500.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), // Using Amber bg for icon
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Amber500
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TestRaTheme.extendedColors.textPrimary
                )
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodySmall,
                    color = TestRaTheme.extendedColors.textMuted
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor, // Darker tint of status color
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                 Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = TestRaTheme.extendedColors.textMuted
                )
            }
        }
    }
}
