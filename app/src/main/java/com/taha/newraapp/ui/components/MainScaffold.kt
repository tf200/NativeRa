package com.taha.newraapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.taha.newraapp.ui.navigation.Screen
import com.taha.newraapp.ui.theme.TestRaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentScreen = Screen.items.find { it.route == currentRoute }

    // Logic to determine if we should show the TopBar
    val showTopBar = currentRoute != Screen.Login.route && currentRoute != Screen.ChatRoom.route

    Scaffold(
        topBar = {
            if (showTopBar) {
                if (currentRoute == Screen.QuickAccess.route || currentRoute == Screen.Home.route) {
                    DashboardTopBar()
                } else if (currentRoute == Screen.Chat.route) {
                    // Messages screen TopBar
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Messages",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TestRaTheme.extendedColors.textPrimary
                                )
                                Text(
                                    text = "3 Unread",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TestRaTheme.extendedColors.textMuted
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = TestRaTheme.extendedColors.textPrimary,
                            navigationIconContentColor = TestRaTheme.extendedColors.textPrimary,
                            actionIconContentColor = TestRaTheme.extendedColors.textPrimary
                        ),
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* TODO: Search */ }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = if (currentRoute == Screen.AccidentForm.route) "Report #2024-889" else (currentScreen?.title ?: "NewRa App"),
                                style = MaterialTheme.typography.titleLarge,
                                color = TestRaTheme.extendedColors.textPrimary
                            )
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.White,
                            titleContentColor = TestRaTheme.extendedColors.textPrimary,
                            navigationIconContentColor = TestRaTheme.extendedColors.textPrimary,
                            actionIconContentColor = TestRaTheme.extendedColors.textPrimary
                        ),
                        navigationIcon = {
                            if (navController.previousBackStackEntry != null) {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        containerColor = TestRaTheme.extendedColors.progressBarBackground // Use a light background for the scaffold part
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

@Composable
fun DashboardTopBar() {
    Surface(
        color = Color.White,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // Pushes content below the status bar
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TestRaTheme.extendedColors.headerBackground) // Purple bg
                    .border(1.dp, Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Text Info
            Column {
                Text(
                    text = "Officer Ben Ali",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TestRaTheme.extendedColors.textPrimary
                )
                Text(
                    text = "Station: Tunis South",
                    style = MaterialTheme.typography.bodySmall,
                    color = TestRaTheme.extendedColors.textSecondary
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Notification Bell with Badge
            Box {
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(TestRaTheme.extendedColors.progressBarBackground, CircleShape) // Light gray circle bg
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TestRaTheme.extendedColors.textPrimary
                    )
                }
                // Red Dot Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
