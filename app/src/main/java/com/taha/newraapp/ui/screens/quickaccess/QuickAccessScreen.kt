package com.taha.newraapp.ui.screens.quickaccess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taha.newraapp.ui.navigation.Screen
import com.taha.newraapp.ui.theme.TestRaTheme

data class QuickAccessItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false
)

@Composable
fun QuickAccessScreen(
    onNavigateTo: (String) -> Unit,
    onLogout: () -> Unit
) {
    val items = listOf(
        QuickAccessItem(
            title = "Accidents",
            icon = Icons.Default.CarCrash,
            onClick = { onNavigateTo(Screen.AccidentHistory.route) }
        ),
        QuickAccessItem(
            title = "Backoffice",
            icon = Icons.Default.Dashboard,
            onClick = { onNavigateTo(Screen.Backoffice.route) }
        ),
        QuickAccessItem(
            title = "Alertes",
            icon = Icons.Default.Notifications,
            onClick = { onNavigateTo(Screen.Alerts.route) }
        ),
        QuickAccessItem(
            title = "Messages",
            icon = Icons.Default.Chat,
            onClick = { onNavigateTo(Screen.Chat.route) }
        ),
        QuickAccessItem(
            title = "GeoPosition",
            icon = Icons.Default.LocationOn,
            onClick = { onNavigateTo(Screen.GeoPosition.route) }
        ),
        QuickAccessItem(
            title = "Profile",
            icon = Icons.Default.Person,
            onClick = { onNavigateTo(Screen.Profile.route) }
        ),
        QuickAccessItem(
            title = "Paramètres",
            icon = Icons.Default.Settings,
            onClick = { onNavigateTo(Screen.Settings.route) }
        ),
        QuickAccessItem(
            title = "Déconnexion",
            icon = Icons.Default.ExitToApp,
            onClick = onLogout,
            isDestructive = true
        )
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { item ->
            QuickAccessCard(item = item)
        }
    }
}

@Composable
fun QuickAccessCard(item: QuickAccessItem) {
    val containerColor = if (item.isDestructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        TestRaTheme.extendedColors.cardBackground
    }
    
    val contentColor = if (item.isDestructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        TestRaTheme.extendedColors.textPrimary
    }

    val iconColor = if (item.isDestructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = item.onClick,
        modifier = Modifier
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                tint = iconColor
            )
            
            // Spacer not strictly needed if we use Arrangement.Center or spacedBy
            // But let's add some padding to text
            
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = contentColor,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
