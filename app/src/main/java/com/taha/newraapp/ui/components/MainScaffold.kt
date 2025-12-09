package com.taha.newraapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    // We hide it for Login screen and ChatRoom (it has its own custom header)
    val showTopBar = currentRoute != Screen.Login.route && currentRoute != Screen.ChatRoom.route

    Scaffold(
        topBar = {
            if (showTopBar) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentScreen?.title ?: "NewRa App",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TestRaTheme.extendedColors.headerBackground,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        // Show back button if we can navigate up and we are not on the start destination (Home)
                        // Assuming Home is the main entry point after login
                        if (navController.previousBackStackEntry != null && currentRoute != Screen.Home.route) {
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
    ) { innerPadding ->
        // We pass the padding to the content, but since the content is likely the NavHost
        // which might have its own internal structure, we need to be careful.
        // Usually, we wrap the content in a Box with the padding.
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
