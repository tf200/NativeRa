package com.taha.newraapp.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taha.newraapp.domain.model.User
import com.taha.newraapp.ui.theme.Amber500
import com.taha.newraapp.ui.theme.Slate100
import com.taha.newraapp.ui.theme.TestRaTheme
import org.koin.androidx.compose.koinViewModel

// Profile screen colors for info cards
private val PurpleBg = Color(0xFFF3E8FF)
private val PurpleIcon = Color(0xFF7C3AED)
private val BlueBg = Color(0xFFDBEAFE)
private val BlueIcon = Color(0xFF2563EB)
private val AmberBg = Color(0xFFFEF3C7)
private val AmberIcon = Color(0xFFD97706)
private val GreenBg = Color(0xFFD1FAE5)
private val GreenIcon = Color(0xFF059669)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val user by viewModel.user.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEFBFF))
    ) {
        if (user != null) {
            ProfileContent(
                user = user!!,
                onBack = onBack,
                onLogout = {
                    viewModel.logout()
                    onLogout()
                }
            )
        } else {
            // Loading State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    user: User,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ===========================================
        // A. Header Section with Overlap Effect
        // ===========================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp) // 160dp cover + 60dp for avatar overlap
        ) {
            // Cover Background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            
            // Avatar - Overlapping the cover
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(4.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        
        // ===========================================
        // B. Name Section
        // ===========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${user.firstName} ${user.lastName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TestRaTheme.extendedColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = user.role,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = TestRaTheme.extendedColors.textMuted
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ===========================================
        // C. Info Cards List
        // ===========================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileInfoItem(
                icon = Icons.Default.Security,
                label = "Officer ID",
                value = user.officerId,
                iconBgColor = PurpleBg,
                iconTint = PurpleIcon
            )
            
            ProfileInfoItem(
                icon = Icons.Default.LocationOn,
                label = "Station Center",
                value = user.center,
                iconBgColor = BlueBg,
                iconTint = BlueIcon
            )
            
            ProfileInfoItem(
                icon = Icons.Default.Star,
                label = "Rank / Role",
                value = user.role,
                iconBgColor = AmberBg,
                iconTint = AmberIcon
            )
            
            ProfileInfoItem(
                icon = Icons.Default.Phone,
                label = "Phone Number",
                value = user.phoneNumber,
                iconBgColor = GreenBg,
                iconTint = GreenIcon
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ===========================================
        // D. Logout Button
        // ===========================================
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(50.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout", fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }
}

/**
 * Reusable Profile Info Item Card
 */
@Composable
fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    iconBgColor: Color,
    iconTint: Color
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate100),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text Column
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = TestRaTheme.extendedColors.textMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TestRaTheme.extendedColors.textPrimary
                )
            }
        }
    }
}
