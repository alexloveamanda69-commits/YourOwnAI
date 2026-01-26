package com.yourown.ai.presentation.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Top bar for chat screen with title editing and model selector
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    conversationTitle: String,
    selectedModel: com.yourown.ai.domain.model.ModelProvider?,
    availableModels: List<com.yourown.ai.domain.model.ModelProvider>,
    localModels: Map<com.yourown.ai.domain.model.LocalModel, com.yourown.ai.domain.model.LocalModelInfo>,
    isSearchMode: Boolean,
    searchQuery: String,
    currentSearchIndex: Int,
    searchMatchCount: Int,
    onBackClick: () -> Unit,
    onEditTitle: () -> Unit,
    onModelSelect: (com.yourown.ai.domain.model.ModelProvider) -> Unit,
    onDownloadModel: (com.yourown.ai.domain.model.LocalModel) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchNext: () -> Unit = {},
    onSearchPrevious: () -> Unit = {},
    onSearchClose: () -> Unit = {},
    onSystemPromptClick: () -> Unit = {},
    onExportChatClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (isSearchMode) {
                // Search mode: Search field with navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search in chat...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        trailingIcon = {
                            if (searchMatchCount > 0) {
                                Text(
                                    text = "${currentSearchIndex + 1}/$searchMatchCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Navigate up button
                    IconButton(
                        onClick = onSearchPrevious,
                        enabled = searchMatchCount > 0
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Previous match")
                    }
                    
                    // Navigate down button
                    IconButton(
                        onClick = onSearchNext,
                        enabled = searchMatchCount > 0
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "Next match")
                    }
                    
                    // Close search button
                    IconButton(onClick = onSearchClose) {
                        Icon(Icons.Default.Close, "Close search")
                    }
                }
            } else {
                // Normal mode: Back, Title+Edit, More Menu, Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back button
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                    
                    // Title + Edit button
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = conversationTitle,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        IconButton(
                            onClick = onEditTitle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Title",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // More menu button
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options")
                        }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Поиск по чату") },
                            onClick = {
                                showMenu = false
                                onSearchClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Системный промпт") },
                            onClick = {
                                showMenu = false
                                onSystemPromptClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Скачать чат") },
                            onClick = {
                                showMenu = false
                                onExportChatClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                        )
                    }
                }
                
                // Settings button
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, "Settings")
                }
            }
            }
            
            // Model selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ModelSelector(
                    selectedModel = selectedModel,
                    availableModels = availableModels,
                    localModels = localModels,
                    onModelSelect = onModelSelect,
                    onDownloadModel = onDownloadModel
                )
            }
        }
    }
}

/**
 * Empty state when no messages
 */
@Composable
fun EmptyState(
    hasModel: Boolean,
    onNewChat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (hasModel) {
                "Start your conversation"
            } else {
                "Select a model to begin"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (hasModel) {
                "Type a message below to start chatting with your AI"
            } else {
                "Download a local model or add an API key in Settings"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}