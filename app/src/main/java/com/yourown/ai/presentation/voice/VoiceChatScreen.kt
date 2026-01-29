package com.yourown.ai.presentation.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourown.ai.data.remote.xai.XAIVoiceClient
import kotlinx.coroutines.launch

/**
 * Voice Chat Screen - минималистичный голосовой интерфейс
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Permission handling
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            // Auto-start recording after permission granted
            if (!uiState.isConnected) {
                viewModel.connect()
            } else {
                viewModel.startRecording()
            }
        }
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }
    
    // Recheck API key when screen becomes visible (e.g., returning from Settings)
    LaunchedEffect(Unit) {
        viewModel.checkApiKey()
    }
    
    // Show system prompt selector dialog
    var showSystemPromptSelector by remember { mutableStateOf(false) }
    
    // Show voice selector dialog
    var showVoiceSelector by remember { mutableStateOf(false) }
    
    // Show request logs dialog
    var showLogsDialog by remember { mutableStateOf(false) }
    var selectedLogMessage by remember { mutableStateOf<VoiceMessage?>(null) }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Voice Chat",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            // System Prompt selector button (always enabled)
                            IconButton(
                                onClick = { showSystemPromptSelector = true }
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Select System Prompt"
                                )
                            }
                            
                            // Clear messages
                            IconButton(
                                onClick = viewModel::clearMessages,
                                enabled = uiState.messages.isNotEmpty() && !uiState.isRecording
                            ) {
                                Icon(Icons.Default.DeleteSweep, "Clear messages")
                            }
                        }
                    )
                    
                    // Connection status
                    ConnectionStatus(
                        isConnected = uiState.isConnected,
                        isConnecting = uiState.isConnecting,
                        sessionId = uiState.sessionId
                    )
                    
                    // Voice selector (centered in header)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AssistChip(
                            onClick = { showVoiceSelector = true },
                            label = { 
                                Text(
                                    text = uiState.selectedVoice.displayName,
                                    maxLines = 1
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    !uiState.hasApiKey && uiState.messages.isEmpty() && !uiState.isConnected -> {
                        // No API key state
                        NoApiKeyState(
                            onNavigateToSettings = onNavigateToSettings
                        )
                    }
                    uiState.messages.isEmpty() && !uiState.isConnected -> {
                        // Empty state (has API key)
                        EmptyVoiceState(
                            onConnect = {
                                if (hasAudioPermission) {
                                    viewModel.connect()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                items = uiState.messages,
                                key = { it.id }
                            ) { message ->
                                VoiceMessageBubble(
                                    message = message,
                                    onViewLogs = {
                                        selectedLogMessage = message
                                        showLogsDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Transcript preview (if recording)
            if (uiState.transcript.isNotEmpty()) {
                TranscriptPreview(
                    transcript = uiState.transcript,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Voice control panel
            VoiceControlPanel(
                isRecording = uiState.isRecording,
                isPlaying = uiState.isPlaying,
                audioLevel = uiState.audioLevel,
                onStartRecording = {
                    if (hasAudioPermission) {
                        if (!uiState.isConnected) {
                            viewModel.connect()
                        } else {
                            viewModel.startRecording()
                        }
                    } else {
                        // Request permission
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = viewModel::stopRecording,
                onStopPlayback = viewModel::disconnect, // Stop playback by disconnecting
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
    
    // Error snackbar
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            // Auto-dismiss after 3 seconds
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = viewModel::clearError) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(uiState.error ?: "")
        }
    }
    
    // Voice selector dialog
    if (showVoiceSelector) {
        AlertDialog(
            onDismissRequest = { showVoiceSelector = false },
            icon = {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
            },
            title = {
                Text("Select Voice")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    XAIVoiceClient.Voice.entries.forEach { voice ->
                        Surface(
                            onClick = {
                                viewModel.selectVoice(voice)
                                showVoiceSelector = false
                            },
                            color = if (voice == uiState.selectedVoice) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (voice == uiState.selectedVoice) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column {
                                    Text(
                                        text = voice.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = voice.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoiceSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // System Prompt selector dialog
    if (showSystemPromptSelector) {
        AlertDialog(
            onDismissRequest = { showSystemPromptSelector = false },
            icon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            title = {
                Text("Select System Prompt")
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.systemPrompts,
                        key = { it.id }
                    ) { prompt ->
                        Surface(
                            onClick = {
                                viewModel.selectSystemPrompt(prompt.id)
                                showSystemPromptSelector = false
                            },
                            color = if (prompt.id == uiState.selectedSystemPromptId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (prompt.id == uiState.selectedSystemPromptId) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prompt.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    // Show first line of content as preview
                                    val preview = prompt.content.lines().firstOrNull()?.take(50) ?: ""
                                    if (preview.isNotBlank()) {
                                        Text(
                                            text = preview + if (prompt.content.length > 50) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSystemPromptSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Request Logs dialog
    if (showLogsDialog && selectedLogMessage != null) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            icon = {
                Icon(Icons.Default.Code, contentDescription = null)
            },
            title = {
                Text("Request Logs")
            },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = selectedLogMessage?.requestLogs ?: "No logs available",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Empty state when no messages and not connected
 */
@Composable
fun EmptyVoiceState(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Voice Chat with Grok",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Talk naturally with your AI assistant",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Getting Started card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "How to use",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                InfoItem(
                    icon = Icons.Default.RecordVoiceOver,
                    text = "Choose your preferred voice"
                )
                
                InfoItem(
                    icon = Icons.Default.Mic,
                    text = "Tap microphone to speak"
                )
                
                InfoItem(
                    icon = Icons.Default.Stop,
                    text = "Tap again to stop and send"
                )
                
                InfoItem(
                    icon = Icons.Default.VolumeUp,
                    text = "Listen to AI response"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Voice Chat")
        }
    }
}

@Composable
private fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * No API Key state - shown when X.AI API key is not configured
 */
@Composable
fun NoApiKeyState(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Key,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "API Key Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add your X.AI API key in Settings to use Voice Chat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onNavigateToSettings,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Go to Settings")
        }
    }
}
