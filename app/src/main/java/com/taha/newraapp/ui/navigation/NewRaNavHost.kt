package com.taha.newraapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.taha.newraapp.ui.screens.login.LoginScreen

import com.taha.newraapp.ui.components.MainScaffold
import com.taha.newraapp.ui.screens.contacts.ContactsScreen
import com.taha.newraapp.ui.screens.profile.ProfileScreen
import com.taha.newraapp.ui.screens.quickaccess.QuickAccessScreen

/**
 * Main navigation host for the NewRa App.
 */
@Composable
fun NewRaNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    MainScaffold(navController = navController) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // ===========================================
            // Authentication
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
            // Main App
            // ===========================================
            composable(Screen.Home.route) {
                // Home is currently unused or can be redirected to QuickAccess
                QuickAccessScreen(
                    onNavigateTo = { route -> navController.navigate(route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.QuickAccess.route) {
                QuickAccessScreen(
                    onNavigateTo = { route -> navController.navigate(route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            
            composable(Screen.Backoffice.route) {
                PlaceholderScreen(title = "Backoffice")
            }
            
            composable(Screen.Alerts.route) {
                PlaceholderScreen(title = "Alerts")
            }
            
            composable(Screen.GeoPosition.route) {
                PlaceholderScreen(title = "Geo Position")
            }

            // ===========================================
            // Accident Recording
            // ===========================================
            composable(Screen.AccidentForm.route) {
                PlaceholderScreen(title = "New Accident Report")
            }

            composable(Screen.AccidentHistory.route) {
                PlaceholderScreen(title = "Accident History")
            }

            // ===========================================
            // Officer Communication
            // ===========================================
            composable(Screen.Chat.route) {
                ContactsScreen(
                    onNavigateToChat = { officerId ->
                        navController.navigate(Screen.ChatRoom.createRoute(officerId))
                    }
                )
            }

            composable(
                route = Screen.ChatRoom.route,
                enterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300)
                    )
                }
            ) {
                 com.taha.newraapp.ui.screens.chat.ChatRoomScreen(
                     onNavigateUp = { navController.navigateUp() }
                 )
            }

            // ===========================================
            // Settings & Profile
            // ===========================================
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onBack = { navController.navigateUp() },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Settings.route) {
                PlaceholderScreen(title = "Settings")
            }
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
