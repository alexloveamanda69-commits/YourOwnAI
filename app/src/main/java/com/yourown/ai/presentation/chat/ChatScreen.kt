package com.yourown.ai.presentation.chat

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourown.ai.presentation.chat.components.*
import kotlinx.coroutines.launch
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String?,
    onNavigateToSettings: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Check if scrolled to bottom
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val lastIndex = uiState.messages.size - 1
            lastVisibleItem?.index == lastIndex || uiState.messages.isEmpty()
        }
    }
    
    // Load conversation when ID changes
    LaunchedEffect(conversationId) {
        conversationId?.let {
            viewModel.selectConversation(it)
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            // Use scrollToItem (not animate) to avoid jitter during streaming
            listState.scrollToItem(uiState.messages.size - 1)
        }
    }
    
    // Auto-scroll after streaming completes
    LaunchedEffect(uiState.shouldScrollToBottom) {
        if (uiState.shouldScrollToBottom && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
            viewModel.onScrolledToBottom()
        }
    }
    
    // Auto-scroll to search result
    LaunchedEffect(uiState.currentSearchIndex, uiState.searchQuery) {
        if (uiState.isSearchMode && uiState.searchMatchCount > 0) {
            val messageIndex = viewModel.getCurrentSearchMessageIndex()
            messageIndex?.let {
                listState.animateScrollToItem(it)
            }
        }
    }
    
    // Scroll to bottom function
    fun scrollToBottom() {
        coroutineScope.launch {
            if (uiState.messages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Основной контент - Column с чатом и input, реагирует на клавиатуру
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // TopBar - фиксирован вверху
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                ChatTopBar(
                    conversationTitle = uiState.currentConversation?.title ?: "YourOwnAI",
                    selectedModel = uiState.selectedModel,
                    availableModels = uiState.availableModels,
                    localModels = uiState.localModels,
                    isSearchMode = uiState.isSearchMode,
                    searchQuery = uiState.searchQuery,
                    currentSearchIndex = uiState.currentSearchIndex,
                    searchMatchCount = uiState.searchMatchCount,
                    onBackClick = onNavigateBack,
                    onEditTitle = viewModel::showEditTitleDialog,
                    onModelSelect = viewModel::selectModel,
                    onDownloadModel = viewModel::downloadModel,
                    onSettingsClick = onNavigateToSettings,
                    onSearchClick = viewModel::toggleSearchMode,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onSearchNext = viewModel::navigateToNextSearchResult,
                    onSearchPrevious = viewModel::navigateToPreviousSearchResult,
                    onSearchClose = viewModel::toggleSearchMode,
                    onSystemPromptClick = viewModel::showSystemPromptDialog,
                    onExportChatClick = viewModel::exportChat
                )
            }
            
            // Контент + Input - эта часть реагирует на клавиатуру
            Column(
                modifier = Modifier
                    .weight(1f)
                    .imePadding()
            ) {
                // Чат - занимает все место
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.messages.isEmpty()) {
                        EmptyState(
                            hasModel = uiState.selectedModel != null,
                            onNewChat = {}
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.id },
                                contentType = { "message" }
                            ) { message ->
                                MessageBubble(
                                    message = message,
                                    onLike = { viewModel.toggleLike(message.id) },
                                    onRegenerate = { viewModel.regenerateMessage(message.id) },
                                    onViewLogs = {
                                        message.requestLogs?.let { viewModel.showRequestLogs(it) }
                                    },
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                    },
                                    onDelete = {
                                        viewModel.deleteMessage(message.id)
                                    },
                                    searchQuery = if (uiState.isSearchMode) uiState.searchQuery else ""
                                )
                            }

                            // Loading indicator
                            if (uiState.isLoading) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Thinking...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Scroll to bottom button - inside Box, not Column
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isAtBottom && uiState.messages.isNotEmpty(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            FloatingActionButton(
                                onClick = { scrollToBottom() },
                                modifier = Modifier.size(40.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to bottom",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                // Input - внизу, внутри imePadding колонки
                MessageInput(
                    text = uiState.inputText,
                    onTextChange = viewModel::updateInputText,
                    onSend = viewModel::sendMessage,
                    enabled = !uiState.isLoading && uiState.selectedModel != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                )
            }
        }
    }
    
    // Dialogs
    if (uiState.showEditTitleDialog) {
        EditTitleDialog(
            currentTitle = uiState.currentConversation?.title ?: "",
            onDismiss = viewModel::hideEditTitleDialog,
            onSave = viewModel::updateConversationTitle
        )
    }
    
    if (uiState.showRequestLogsDialog && uiState.selectedMessageLogs != null) {
        RequestLogsDialog(
            logs = uiState.selectedMessageLogs!!,
            onDismiss = viewModel::hideRequestLogs
        )
    }
    
    if (uiState.showSystemPromptDialog) {
        SystemPromptDialog(
            systemPrompts = uiState.systemPrompts,
            selectedPromptId = uiState.selectedSystemPromptId,
            onDismiss = viewModel::hideSystemPromptDialog,
            onSelectPrompt = viewModel::selectSystemPrompt
        )
    }
    
    if (uiState.showExportDialog && uiState.exportedChatText != null) {
        ExportChatDialog(
            chatText = uiState.exportedChatText!!,
            onDismiss = viewModel::hideExportDialog,
            onShare = {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, uiState.exportedChatText)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Поделиться чатом"))
            }
        )
    }
}
