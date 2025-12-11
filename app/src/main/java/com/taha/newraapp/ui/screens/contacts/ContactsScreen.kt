package com.taha.newraapp.ui.screens.contacts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taha.newraapp.ui.theme.Slate100
import com.taha.newraapp.ui.theme.TestRaTheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onNavigateToChat: (String) -> Unit, // officerId
    viewModel: ContactsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Custom Search Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate100),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TestRaTheme.extendedColors.textMuted
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search contacts or messages...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TestRaTheme.extendedColors.textMuted
                    )
                } else {
                    Text(
                        text = searchQuery,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TestRaTheme.extendedColors.textPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Content
        when (val state = uiState) {
            is ContactsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ContactsUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is ContactsUiState.Success -> {
                if (state.contacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if(searchQuery.isEmpty()) "No contacts found" else "No results for '$searchQuery'",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Header
                    Text(
                        text = "RECENT CONVERSATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TestRaTheme.extendedColors.textMuted,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.contacts, key = { it.officerId }) { contact ->
                            MessageItem(
                                contact = contact,
                                onClick = { onNavigateToChat(contact.officerId) }
                            )
                        }
                    }
                }
            }
        }
    }
}
