package com.taha.newraapp.ui.navigation

/**
 * Sealed class for type-safe navigation routes.
 */
sealed class Screen(val route: String, val title: String) {
    // ===========================================
    // Authentication
    // ===========================================
    data object Login : Screen("login", "Login")
    
    // ===========================================
    // Main App
    // ===========================================
    data object Home : Screen("home", "Home")
    data object QuickAccess : Screen("quick_access", "Quick Access")
    
    // ===========================================
    // Accident Recording
    // ===========================================
    data object AccidentForm : Screen("accident_form", "New Accident Report")
    data object AccidentDetails : Screen("accident_details/{id}", "Accident Details") {
        fun createRoute(id: String) = "accident_details/$id"
    }
    data object AccidentHistory : Screen("accident_history", "Accident History")
    
    // ===========================================
    // Officer Communication
    // ===========================================
    data object Chat : Screen("chat", "Officer Chat")
    data object ChatRoom : Screen("chat_room/{chatId}", "Chat Room") {
        fun createRoute(chatId: String) = "chat_room/$chatId"
    }
    
    // ===========================================
    // Other Features
    // ===========================================
    data object Backoffice : Screen("backoffice", "Backoffice")
    data object Alerts : Screen("alerts", "Alerts")
    data object GeoPosition : Screen("geo_position", "Geo Position")

    // ===========================================
    // Settings & Profile
    // ===========================================
    data object Profile : Screen("profile", "Profile")
    data object Settings : Screen("settings", "Settings")
    
    // ===========================================
    // Media Viewer
    // ===========================================
    data object MediaViewer : Screen("media_viewer/{filePath}/{senderName}/{timestamp}", "Media Viewer") {
        fun createRoute(filePath: String, senderName: String, timestamp: Long): String {
            return "media_viewer/${android.net.Uri.encode(filePath)}/${android.net.Uri.encode(senderName)}/$timestamp"
        }
    }

    companion object {
        val items by lazy {
            listOf(
                Login,
                Home,
                QuickAccess,
                AccidentForm,
                AccidentDetails,
                AccidentHistory,
                Chat,
                ChatRoom,
                Backoffice,
                Alerts,
                GeoPosition,
                Profile,
                Settings,
                MediaViewer
            )
        }
    }
}
