package com.taha.newraapp.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
    onNavigateToChat: (String) -> Unit, // user id
    viewModel: ContactsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Functional Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search contacts or messages...",
                    color = TestRaTheme.extendedColors.textMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TestRaTheme.extendedColors.textMuted
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = TestRaTheme.extendedColors.textMuted
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Slate100,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )
        
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
                val hasRecentConversations = state.recentConversations.isNotEmpty()
                val hasAllContacts = state.allContacts.isNotEmpty()
                
                if (!hasRecentConversations && !hasAllContacts) {
                    // No contacts at all
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No contacts found" else "No results for '$searchQuery'",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Two-section layout
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section 1: Recent Conversations
                        if (hasRecentConversations) {
                            item {
                                Text(
                                    text = "RECENT CONVERSATIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TestRaTheme.extendedColors.textMuted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(state.recentConversations, key = { "recent_${it.id}" }) { contact ->
                                MessageItem(
                                    contact = contact,
                                    onClick = { onNavigateToChat(contact.id) }
                                )
                            }
                        }
                        
                        // Section 2: All Contacts (without conversations)
                        if (hasAllContacts) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "ALL CONTACTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TestRaTheme.extendedColors.textMuted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(state.allContacts, key = { "all_${it.id}" }) { contact ->
                                MessageItem(
                                    contact = contact,
                                    onClick = { onNavigateToChat(contact.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

