package com.taha.newraapp.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.taha.newraapp.data.local.TokenManager
import com.taha.newraapp.data.socket.SocketConnectionManager
import com.taha.newraapp.data.socket.SocketManager
import com.taha.newraapp.data.sync.PowerSyncManager
import com.taha.newraapp.ui.components.MainScaffold
import com.taha.newraapp.ui.screens.contacts.ContactsScreen
import com.taha.newraapp.ui.screens.login.LoginScreen
import com.taha.newraapp.ui.screens.media.MediaViewerScreen
import com.taha.newraapp.ui.screens.profile.ProfileScreen
import com.taha.newraapp.ui.screens.quickaccess.QuickAccessScreen
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * Main navigation host for the NewRa App.
 * 
 * ARCHITECTURE: NavHost is at root level, NOT inside MainScaffold.
 * - Screens that need shared TopBar (Chat, QuickAccess, etc.) wrap themselves with MainScaffold
 * - Standalone screens (ChatRoom, MediaViewer, Login) render independently
 * This enables smooth slide transitions where new screen slides over old screen without TopBar flickering.
 * 
 * SESSION PERSISTENCE: On startup, checks for existing valid tokens.
 * If tokens exist, navigates directly to QuickAccess and re-initializes PowerSync/Socket.
 */
@Composable
fun NewRaNavHost(
    navController: NavHostController = rememberNavController(),
    tokenManager: TokenManager = koinInject(),
    powerSyncManager: PowerSyncManager = koinInject(),
    socketManager: SocketManager = koinInject(),
    socketConnectionManager: SocketConnectionManager = koinInject()
) {
    // Check for existing session on startup
    var startDestination by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("SESSION_DEBUG", "=== APP STARTUP SESSION CHECK ===")
        val token = tokenManager.accessToken.first()
        android.util.Log.d("SESSION_DEBUG", "Access token present: ${token != null}")
        if (token != null) {
            android.util.Log.d("SESSION_DEBUG", "Token (last 10 chars): ...${token.takeLast(10)}")
            // User has existing session - go to main app
            startDestination = Screen.QuickAccess.route
            
            // Re-initialize PowerSync and Socket for returning users
            try {
                android.util.Log.d("SESSION_DEBUG", "Initializing PowerSync...")
                powerSyncManager.initialize()
                android.util.Log.d("SESSION_DEBUG", "✅ PowerSync initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("SESSION_DEBUG", "❌ PowerSync init failed: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
            
            try {
                android.util.Log.d("SESSION_DEBUG", "Connecting Socket...")
                socketManager.connect()
                // Start network monitoring for automatic reconnection
                socketConnectionManager.startMonitoring()
                android.util.Log.d("SESSION_DEBUG", "✅ Socket connected")
            } catch (e: Exception) {
                android.util.Log.e("SESSION_DEBUG", "❌ Socket connect failed: ${e.message}")
                e.printStackTrace()
            }
        } else {
            android.util.Log.d("SESSION_DEBUG", "No token found - navigating to Login")
            // No session - go to login
            startDestination = Screen.Login.route
        }
        isInitializing = false
    }
    
    // Show loading while checking session
    if (isInitializing || startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    
    NavHost(
        navController = navController,
        startDestination = startDestination!!
    ) {
        // ===========================================
        // Authentication (standalone, no scaffold)
        // ===========================================
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.QuickAccess.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ===========================================
        // Main App (wrapped in MainScaffold)
        // ===========================================
        composable(Screen.Home.route) {
            MainScaffold(navController = navController, route = Screen.Home.route) {
                QuickAccessScreen(
                    onNavigateTo = { route -> navController.navigate(route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.QuickAccess.route) {
            MainScaffold(navController = navController, route = Screen.QuickAccess.route) {
                QuickAccessScreen(
                    onNavigateTo = { route -> navController.navigate(route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
        
        composable(Screen.Backoffice.route) {
            MainScaffold(navController = navController, route = Screen.Backoffice.route) {
                PlaceholderScreen(title = "Backoffice")
            }
        }
        
        composable(Screen.Alerts.route) {
            MainScaffold(navController = navController, route = Screen.Alerts.route) {
                PlaceholderScreen(title = "Alerts")
            }
        }
        
        // MapScreen - STANDALONE (own back button, like ChatRoom)
        composable(
            route = Screen.GeoPosition.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(220)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(220)
                )
            }
        ) {
            com.taha.newraapp.ui.screens.map.MapScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        // ===========================================
        // Accident Recording (wrapped in MainScaffold)
        // ===========================================
        composable(Screen.AccidentForm.route) {
            MainScaffold(navController = navController, route = Screen.AccidentForm.route) {
                PlaceholderScreen(title = "New Accident Report")
            }
        }

        composable(Screen.AccidentHistory.route) {
            MainScaffold(navController = navController, route = Screen.AccidentHistory.route) {
                PlaceholderScreen(title = "Accident History")
            }
        }

        // ===========================================
        // Officer Communication
        // ===========================================
        // Contacts/Chat list - wrapped in MainScaffold for TopBar
        composable(Screen.Chat.route) {
            MainScaffold(navController = navController, route = Screen.Chat.route) {
                ContactsScreen(
                    onNavigateToChat = { officerId ->
                        navController.navigate(Screen.ChatRoom.createRoute(officerId))
                    }
                )
            }
        }

        // ChatRoom - STANDALONE, slides over Contacts like WhatsApp
        composable(
            route = Screen.ChatRoom.route,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                )
            }
        ) {
             com.taha.newraapp.ui.screens.chat.ChatRoomScreen(
                 onNavigateUp = { navController.navigateUp() },
                 onImageClick = { filePath, senderName, timestamp ->
                     navController.navigate(Screen.MediaViewer.createRoute(filePath, senderName, timestamp))
                 }
             )
        }

        // ===========================================
        // Settings & Profile (wrapped in MainScaffold)
        // ===========================================
        composable(Screen.Profile.route) {
            MainScaffold(navController = navController, route = Screen.Profile.route) {
                ProfileScreen(
                    onBack = { navController.navigateUp() },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.Settings.route) {
            MainScaffold(navController = navController, route = Screen.Settings.route) {
                PlaceholderScreen(title = "Settings")
            }
        }

        // ===========================================
        // Media Viewer - STANDALONE (own app bar)
        // ===========================================
        composable(
            route = Screen.MediaViewer.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) { backStackEntry ->
            val filePath = Uri.decode(backStackEntry.arguments?.getString("filePath") ?: "")
            val senderName = Uri.decode(backStackEntry.arguments?.getString("senderName") ?: "")
            val timestamp = backStackEntry.arguments?.getString("timestamp")?.toLongOrNull() ?: 0L
            
            MediaViewerScreen(
                filePath = filePath,
                senderName = senderName,
                timestamp = timestamp,
                onBack = { navController.navigateUp() }
            )
        }
    }
}

/**
 * Placeholder screen for development.
 * Replace with actual screen implementations.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
