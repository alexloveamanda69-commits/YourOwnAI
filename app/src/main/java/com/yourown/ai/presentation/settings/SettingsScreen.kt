package com.yourown.ai.presentation.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourown.ai.domain.model.AIConfig
import com.yourown.ai.domain.model.AIProvider
import com.yourown.ai.domain.model.ApiKeyInfo
import com.yourown.ai.presentation.settings.dialogs.ApiKeyDialog
import com.yourown.ai.presentation.settings.dialogs.AppearanceDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Keys Section
            ApiKeysSection(
                apiKeys = uiState.apiKeys,
                onAddKey = viewModel::showApiKeyDialog,
                onDeleteKey = viewModel::deleteApiKey,
                onShowLocalModels = viewModel::showLocalModelsDialog
            )
            
            // AI Configuration Section
            AIConfigurationSection(
                config = uiState.aiConfig,
                uiState = uiState,
                apiPrompts = uiState.apiPrompts,
                localPrompts = uiState.localPrompts,
                viewModel = viewModel,
                onEditSystemPrompt = viewModel::showSystemPromptDialog,
                onEditLocalSystemPrompt = viewModel::showLocalSystemPromptDialog,
                onTemperatureChange = viewModel::updateTemperature,
                onTopPChange = viewModel::updateTopP,
                onToggleDeepEmpathy = viewModel::toggleDeepEmpathy,
                onToggleMemory = viewModel::toggleMemory,
                onEditMemoryPrompt = viewModel::showMemoryPromptDialog,
                onMemoryLimitChange = viewModel::updateMemoryLimit,
                onToggleRAG = viewModel::toggleRAG,
                onChunkSizeChange = { viewModel.updateRAGChunkSize(it.toInt()) },
                onChunkOverlapChange = { viewModel.updateRAGChunkOverlap(it.toInt()) },
                onRAGChunkLimitChange = viewModel::updateRAGChunkLimit,
                onMessageHistoryChange = viewModel::updateMessageHistoryLimit,
                onMaxTokensChange = viewModel::updateMaxTokens
            )
            
            // Embedding Models Section
            EmbeddingModelsSection(
                onShowEmbeddingModels = viewModel::showEmbeddingModelsDialog,
                onRecalculateEmbeddings = viewModel::recalculateAllEmbeddings,
                isRecalculating = uiState.isRecalculatingEmbeddings,
                recalculationProgress = uiState.recalculationProgress,
                recalculationProgressPercent = uiState.recalculationProgressPercent
            )
            
            // Knowledge & Memory Section
            KnowledgeMemorySection(
                hasContext = uiState.userContext.content.isNotEmpty(),
                documentsCount = uiState.knowledgeDocuments.size,
                documentProcessingStatus = uiState.documentProcessingStatus,
                uiState = uiState,
                viewModel = viewModel,
                onEditContext = viewModel::showContextDialog,
                onManageDocuments = viewModel::showDocumentsListDialog,
                onAddDocument = viewModel::createNewDocument,
                onViewMemories = viewModel::showMemoriesDialog
            )
            
            // Appearance Section
            AppearanceSection(
                onShowAppearance = viewModel::showAppearanceDialog
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Dialogs
    if (uiState.showSystemPromptsListDialog && uiState.promptTypeFilter != null) {
        val promptsList = when (uiState.promptTypeFilter) {
            com.yourown.ai.data.repository.PromptType.API -> uiState.apiPrompts
            com.yourown.ai.data.repository.PromptType.LOCAL -> uiState.localPrompts
            else -> emptyList()
        }
        
        com.yourown.ai.presentation.settings.components.SystemPromptsListDialog(
            prompts = promptsList,
            promptType = uiState.promptTypeFilter!!,
            onDismiss = viewModel::hideSystemPromptsListDialog,
            onAddNew = { viewModel.showEditPromptDialog(null) },
            onEdit = { viewModel.showEditPromptDialog(it) },
            onDelete = viewModel::deletePrompt,
            onSetDefault = viewModel::setPromptAsDefault
        )
    }
    
    if (uiState.showEditPromptDialog) {
        com.yourown.ai.presentation.settings.components.EditPromptDialog(
            prompt = uiState.selectedPromptForEdit,
            promptType = uiState.promptTypeFilter ?: com.yourown.ai.data.repository.PromptType.API,
            allPrompts = uiState.systemPrompts,
            onDismiss = viewModel::hideEditPromptDialog,
            onSave = { name, content, isDefault ->
                viewModel.savePrompt(
                    id = uiState.selectedPromptForEdit?.id,
                    name = name,
                    content = content,
                    type = uiState.promptTypeFilter ?: com.yourown.ai.data.repository.PromptType.API,
                    isDefault = isDefault
                )
            }
        )
    }
    
    if (uiState.showDocumentsListDialog) {
        com.yourown.ai.presentation.settings.components.KnowledgeDocumentsListDialog(
            documents = uiState.knowledgeDocuments,
            processingStatus = uiState.documentProcessingStatus,
            onDismiss = viewModel::hideDocumentsListDialog,
            onAddDocument = viewModel::createNewDocument,
            onEditDocument = { viewModel.showEditDocumentDialog(it) },
            onDeleteDocument = viewModel::deleteDocument
        )
    }
    
    if (uiState.showEditDocumentDialog) {
        com.yourown.ai.presentation.settings.components.EditDocumentDialog(
            document = uiState.selectedDocumentForEdit,
            onDismiss = viewModel::hideEditDocumentDialog,
            onSave = { id, name, content ->
                viewModel.saveDocument(id, name, content)
            }
        )
    }
    
    if (uiState.showContextDialog) {
        ContextDialog(
            currentContext = uiState.userContext.content,
            onDismiss = viewModel::hideContextDialog,
            onSave = viewModel::updateContext
        )
    }
    
    if (uiState.showApiKeyDialog && uiState.selectedProvider != null) {
        ApiKeyDialog(
            provider = uiState.selectedProvider!!,
            onDismiss = viewModel::hideApiKeyDialog,
            onSave = { key -> viewModel.saveApiKey(uiState.selectedProvider!!, key) }
        )
    }
    
    if (uiState.showLocalModelsDialog) {
        LocalModelsDialog(
            models = uiState.localModels,
            onDismiss = viewModel::hideLocalModelsDialog,
            onDownload = viewModel::downloadModel,
            onDelete = viewModel::deleteModel
        )
    }
    
    if (uiState.showEmbeddingModelsDialog) {
        EmbeddingModelsDialog(
            models = uiState.embeddingModels,
            onDismiss = viewModel::hideEmbeddingModelsDialog,
            onDownload = viewModel::downloadEmbeddingModel,
            onDelete = viewModel::deleteEmbeddingModel
        )
    }
    
    if (uiState.showAppearanceDialog) {
        AppearanceDialog(
            onDismiss = viewModel::hideAppearanceDialog
        )
    }
    
    if (uiState.showMemoriesDialog) {
        com.yourown.ai.presentation.settings.components.MemoriesDialog(
            memories = uiState.memories,
            onDismiss = viewModel::hideMemoriesDialog,
            onEditMemory = viewModel::showEditMemoryDialog,
            onDeleteMemory = viewModel::deleteMemory
        )
    }
    
    if (uiState.showEditMemoryDialog && uiState.selectedMemoryForEdit != null) {
        com.yourown.ai.presentation.settings.components.EditMemoryDialog(
            memory = uiState.selectedMemoryForEdit!!,
            onDismiss = viewModel::hideEditMemoryDialog,
            onSave = viewModel::saveMemory
        )
    }
    
    if (uiState.showMemoryPromptDialog) {
        com.yourown.ai.presentation.settings.components.MemoryExtractionPromptDialog(
            currentPrompt = uiState.aiConfig.memoryExtractionPrompt,
            onDismiss = viewModel::hideMemoryPromptDialog,
            onSave = viewModel::updateMemoryExtractionPrompt,
            onReset = viewModel::resetMemoryExtractionPrompt
        )
    }
    
    if (uiState.showDeepEmpathyPromptDialog) {
        com.yourown.ai.presentation.settings.components.DeepEmpathyPromptDialog(
            currentPrompt = uiState.aiConfig.deepEmpathyPrompt,
            onDismiss = viewModel::hideDeepEmpathyPromptDialog,
            onSave = viewModel::updateDeepEmpathyPrompt,
            onReset = viewModel::resetDeepEmpathyPrompt
        )
    }
    
    if (uiState.showDeepEmpathyAnalysisDialog) {
        com.yourown.ai.presentation.settings.components.DeepEmpathyAnalysisDialog(
            currentPrompt = uiState.aiConfig.deepEmpathyAnalysisPrompt,
            onDismiss = viewModel::hideDeepEmpathyAnalysisDialog,
            onSave = viewModel::updateDeepEmpathyAnalysisPrompt,
            onReset = viewModel::resetDeepEmpathyAnalysisPrompt
        )
    }
    
    if (uiState.showContextInstructionsDialog) {
        com.yourown.ai.presentation.settings.components.ContextInstructionsDialog(
            currentInstructions = uiState.aiConfig.contextInstructions,
            onDismiss = viewModel::hideContextInstructionsDialog,
            onSave = viewModel::updateContextInstructions,
            onReset = viewModel::resetContextInstructions
        )
    }
    
    if (uiState.showSwipeMessagePromptDialog) {
        com.yourown.ai.presentation.settings.components.SwipeMessagePromptDialog(
            currentPrompt = uiState.aiConfig.swipeMessagePrompt,
            onDismiss = viewModel::hideSwipeMessagePromptDialog,
            onSave = viewModel::updateSwipeMessagePrompt,
            onReset = viewModel::resetSwipeMessagePrompt
        )
    }
    
    if (uiState.showMemoryInstructionsDialog) {
        com.yourown.ai.presentation.settings.components.MemoryInstructionsDialog(
            currentInstructions = uiState.aiConfig.memoryInstructions,
            onDismiss = viewModel::hideMemoryInstructionsDialog,
            onSave = viewModel::updateMemoryInstructions,
            onReset = viewModel::resetMemoryInstructions
        )
    }
    
    if (uiState.showRAGInstructionsDialog) {
        com.yourown.ai.presentation.settings.components.RAGInstructionsDialog(
            currentInstructions = uiState.aiConfig.ragInstructions,
            onDismiss = viewModel::hideRAGInstructionsDialog,
            onSave = viewModel::updateRAGInstructions,
            onReset = viewModel::resetRAGInstructions
        )
    }
    
    if (uiState.showEmbeddingRequiredDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideEmbeddingRequiredDialog,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Embedding Model Required") },
            text = {
                Text(
                    "To enable Memory or RAG features, you need a downloaded embedding model.\n\n" +
                    "Available models:\n" +
                    "• all-MiniLM-L6-v2 (21 MB, fast)\n" +
                    "• mxbai-embed-large (670 MB, better quality)\n\n" +
                    "Please download an embedding model to continue."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.hideEmbeddingRequiredDialog()
                    viewModel.showEmbeddingModelsDialog()
                }) {
                    Text("Download Embedding Model")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideEmbeddingRequiredDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ApiKeysSection(
    apiKeys: List<ApiKeyInfo>,
    onAddKey: (AIProvider) -> Unit,
    onDeleteKey: (AIProvider) -> Unit,
    onShowLocalModels: () -> Unit
) {
    SettingsSection(
        title = "API Keys",
        icon = Icons.Default.Key,
        subtitle = "Configure your AI providers"
    ) {
        apiKeys.forEach { keyInfo ->
            ApiKeyItem(
                keyInfo = keyInfo,
                onAdd = { onAddKey(keyInfo.provider) },
                onEdit = { onAddKey(keyInfo.provider) },
                onDelete = { onDeleteKey(keyInfo.provider) }
            )
            if (keyInfo != apiKeys.last()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { /* TODO: Add custom provider */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add custom provider")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        FilledTonalButton(
            onClick = onShowLocalModels,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Local Model")
        }
    }
}

@Composable
private fun ApiKeyItem(
    keyInfo: ApiKeyInfo,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyInfo.provider.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (keyInfo.isSet && keyInfo.displayKey != null) {
                Text(
                    text = keyInfo.displayKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (keyInfo.isSet) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(20.dp))
                }
            } else {
                FilledTonalButton(onClick = onAdd) {
                    Text("Add")
                }
            }
            
            IconButton(onClick = { /* TODO: Show help */ }) {
                Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AIConfigurationSection(
    config: AIConfig,
    uiState: SettingsUiState,
    apiPrompts: List<com.yourown.ai.domain.model.SystemPrompt>,
    localPrompts: List<com.yourown.ai.domain.model.SystemPrompt>,
    viewModel: SettingsViewModel,
    onEditSystemPrompt: () -> Unit,
    onEditLocalSystemPrompt: () -> Unit,
    onTemperatureChange: (Float) -> Unit,
    onTopPChange: (Float) -> Unit,
    onToggleDeepEmpathy: () -> Unit,
    onToggleMemory: () -> Unit,
    onEditMemoryPrompt: () -> Unit,
    onMemoryLimitChange: (Int) -> Unit,
    onToggleRAG: () -> Unit,
    onChunkSizeChange: (Float) -> Unit,
    onChunkOverlapChange: (Float) -> Unit,
    onRAGChunkLimitChange: (Int) -> Unit,
    onMessageHistoryChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit
) {
    SettingsSection(
        title = "AI Configuration",
        icon = Icons.Default.SmartToy,
        subtitle = "Customize your AI's behavior"
    ) {
        // System Prompts (API models)
        SettingItemClickable(
            title = "System Prompt (API)",
            subtitle = "For cloud models (Deepseek, OpenAI, Grok) • Total: ${apiPrompts.size}",
            onClick = { viewModel.showSystemPromptsListDialog(com.yourown.ai.data.repository.PromptType.API) },
            trailing = {
                Row {
                    IconButton(onClick = { viewModel.createNewPrompt(com.yourown.ai.data.repository.PromptType.API) }) {
                        Icon(Icons.Default.Add, "Add API prompt", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // System Prompts (Local models)
        SettingItemClickable(
            title = "System Prompt (Local)",
            subtitle = "For local models (Qwen, Llama) • Total: ${localPrompts.size}",
            onClick = { viewModel.showSystemPromptsListDialog(com.yourown.ai.data.repository.PromptType.LOCAL) },
            trailing = {
                Row {
                    IconButton(onClick = { viewModel.createNewPrompt(com.yourown.ai.data.repository.PromptType.LOCAL) }) {
                        Icon(Icons.Default.Add, "Add Local prompt", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Temperature
        SliderSetting(
            title = "Temperature",
            subtitle = "How creative and free your AI can sound",
            value = config.temperature,
            valueRange = AIConfig.MIN_TEMPERATURE..AIConfig.MAX_TEMPERATURE,
            onValueChange = onTemperatureChange
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Top-p
        SliderSetting(
            title = "Top-p",
            subtitle = "How chaotic your AI can be",
            value = config.topP,
            valueRange = AIConfig.MIN_TOP_P..AIConfig.MAX_TOP_P,
            onValueChange = onTopPChange
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Deep Empathy
        ToggleSetting(
            title = "Deep Empathy",
            subtitle = "How deeply your AI can hear you",
            checked = config.deepEmpathy,
            onCheckedChange = { onToggleDeepEmpathy() }
        )
        
        // Deep Empathy Prompt (shown when Deep Empathy is enabled)
        if (config.deepEmpathy) {
            SettingItemClickable(
                title = "Deep Empathy Prompt",
                subtitle = "Customize focus tracking prompt • Required: {dialogue_focus}",
                onClick = { viewModel.showDeepEmpathyPromptDialog() },
                trailing = {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            )
            
            // Advanced Deep Empathy Settings
            SettingItemClickable(
                title = if (uiState.showAdvancedDeepEmpathySettings) "▼ Advanced Deep Empathy Settings" else "▶ Advanced Deep Empathy Settings",
                subtitle = "Customize dialogue focus analysis",
                onClick = { viewModel.toggleAdvancedDeepEmpathySettings() }
            )
            
            if (uiState.showAdvancedDeepEmpathySettings) {
                SettingItemClickable(
                    title = "Analysis Prompt",
                    subtitle = "How AI finds focus points • Required: {text}",
                    onClick = { viewModel.showDeepEmpathyAnalysisDialog() },
                    trailing = {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Memory
        ToggleSetting(
            title = "Memory",
            subtitle = "AI remembers important things automatically",
            checked = config.memoryEnabled,
            onCheckedChange = { onToggleMemory() }
        )
        
        // Memory Extraction Prompt
        if (config.memoryEnabled) {
            SettingItemClickable(
                title = "Memory Extraction Prompt",
                subtitle = "Customize how AI extracts memories • Required: {text}",
                onClick = onEditMemoryPrompt,
                trailing = {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            )
            
            // Memory Limit Slider
            SliderSetting(
                title = "Memory Limit",
                subtitle = "AI memory for memories limit",
                value = config.memoryLimit.toFloat(),
                valueRange = com.yourown.ai.domain.model.AIConfig.MIN_MEMORY_LIMIT.toFloat()..com.yourown.ai.domain.model.AIConfig.MAX_MEMORY_LIMIT.toFloat(),
                onValueChange = { onMemoryLimitChange(it.toInt()) },
                valueFormatter = { "${it.toInt()} memories" }
            )
            
            // Advanced Memory Settings
            SettingItemClickable(
                title = if (uiState.showAdvancedMemorySettings) "▼ Advanced Memory Settings" else "▶ Advanced Memory Settings",
                subtitle = "Customize memory behavior",
                onClick = { viewModel.toggleAdvancedMemorySettings() }
            )
            
            if (uiState.showAdvancedMemorySettings) {
                OutlinedTextField(
                    value = config.memoryTitle,
                    onValueChange = { viewModel.updateMemoryTitle(it) },
                    label = { Text("Memory Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SliderSetting(
                    title = "Memory Age Filter",
                    subtitle = "Only retrieve memories older than X days",
                    value = config.memoryMinAgeDays.toFloat(),
                    valueRange = com.yourown.ai.domain.model.AIConfig.MIN_MEMORY_MIN_AGE_DAYS.toFloat()..com.yourown.ai.domain.model.AIConfig.MAX_MEMORY_MIN_AGE_DAYS.toFloat(),
                    onValueChange = { viewModel.updateMemoryMinAgeDays(it.toInt()) },
                    valueFormatter = { "${it.toInt()} days" }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingItemClickable(
                    title = "Memory Instructions",
                    subtitle = "How AI should use memories",
                    onClick = { viewModel.showMemoryInstructionsDialog() },
                    trailing = {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // RAG (Retrieval Augmented Generation)
        ToggleSetting(
            title = "RAG",
            subtitle = "Use knowledge documents in responses",
            checked = config.ragEnabled,
            onCheckedChange = { onToggleRAG() }
        )
        
        // RAG Settings (show only when RAG is enabled)
        if (config.ragEnabled) {
            // RAG Chunk Limit Slider
            SliderSetting(
                title = "RAG Chunk Limit",
                subtitle = "AI knowledge memory limit",
                value = config.ragChunkLimit.toFloat(),
                valueRange = com.yourown.ai.domain.model.AIConfig.MIN_RAG_CHUNK_LIMIT.toFloat()..com.yourown.ai.domain.model.AIConfig.MAX_RAG_CHUNK_LIMIT.toFloat(),
                onValueChange = { onRAGChunkLimitChange(it.toInt()) },
                valueFormatter = { "${it.toInt()} chunks" }
            )
            
            // Advanced RAG Settings
            SettingItemClickable(
                title = if (uiState.showAdvancedRAGSettings) "▼ Advanced RAG Settings" else "▶ Advanced RAG Settings",
                subtitle = "Customize RAG behavior",
                onClick = { viewModel.toggleAdvancedRAGSettings() }
            )
            
            if (uiState.showAdvancedRAGSettings) {
                OutlinedTextField(
                    value = config.ragTitle,
                    onValueChange = { viewModel.updateRAGTitle(it) },
                    label = { Text("RAG Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Chunk Size
                SliderSetting(
                    title = "Chunk Size",
                    subtitle = "Text size for each document chunk",
                    value = config.ragChunkSize.toFloat(),
                    valueRange = AIConfig.MIN_CHUNK_SIZE.toFloat()..AIConfig.MAX_CHUNK_SIZE.toFloat(),
                    onValueChange = onChunkSizeChange,
                    valueFormatter = { "${it.toInt()} characters" }
                )
                
                // Chunk Overlap
                SliderSetting(
                    title = "Chunk Overlap",
                    subtitle = "Overlapping characters between chunks",
                    value = config.ragChunkOverlap.toFloat(),
                    valueRange = AIConfig.MIN_CHUNK_OVERLAP.toFloat()..AIConfig.MAX_CHUNK_OVERLAP.toFloat(),
                    onValueChange = onChunkOverlapChange,
                    valueFormatter = { "${it.toInt()} characters" }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingItemClickable(
                    title = "RAG Instructions",
                    subtitle = "How AI should use knowledge documents",
                    onClick = { viewModel.showRAGInstructionsDialog() },
                    trailing = {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Message History Limit (API models only)
        DropdownSetting(
            title = "Message History (API only)",
            subtitle = "How many messages to send to API models • Local models always use last message",
            value = config.messageHistoryLimit,
            options = (AIConfig.MIN_MESSAGE_HISTORY..AIConfig.MAX_MESSAGE_HISTORY).toList(),
            onValueChange = onMessageHistoryChange,
            valueSuffix = "pairs"
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Max Tokens
        DropdownSetting(
            title = "Max Tokens",
            subtitle = "Maximum length of AI response",
            value = config.maxTokens,
            options = listOf(256, 512, 1024, 2048, 4096, 8192),
            onValueChange = onMaxTokensChange,
            valueSuffix = "tokens"
        )
    }
}

@Composable
private fun KnowledgeMemorySection(
    hasContext: Boolean,
    documentsCount: Int,
    documentProcessingStatus: com.yourown.ai.data.repository.DocumentProcessingStatus,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onEditContext: () -> Unit,
    onManageDocuments: () -> Unit,
    onAddDocument: () -> Unit,
    onViewMemories: () -> Unit
) {
    SettingsSection(
        title = "Knowledge & Memory",
        icon = Icons.Default.Psychology,
        subtitle = "Teach your AI about you and your world"
    ) {
        // Context
        SettingItemClickable(
            title = "Context",
            subtitle = "What you want your AI to know about you or anything else",
            onClick = onEditContext,
            trailing = {
                Row {
                    if (hasContext) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Set",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { /* TODO: Show help */ }) {
                        Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        // Advanced Context Settings
        SettingItemClickable(
            title = if (uiState.showAdvancedContextSettings) "▼ Advanced Context Settings" else "▶ Advanced Context Settings",
            subtitle = "Customize enhanced context instructions",
            onClick = { viewModel.toggleAdvancedContextSettings() }
        )
        
        if (uiState.showAdvancedContextSettings) {
            SettingItemClickable(
                title = "Context Instructions",
                subtitle = "How AI uses enhanced context",
                onClick = { viewModel.showContextInstructionsDialog() },
                trailing = {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            )
            
            SettingItemClickable(
                title = "Swipe Message Prompt",
                subtitle = "Prompt for replied messages",
                onClick = { viewModel.showSwipeMessagePromptDialog() },
                trailing = {
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                }
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Knowledge Documents
        SettingItemClickable(
            title = "Knowledge data",
            subtitle = "Text documents for AI teaching",
            onClick = onManageDocuments,
            trailing = {
                Row {
                    IconButton(onClick = onManageDocuments) {
                        Icon(Icons.Default.Description, "View Documents", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // RAG Documents
        SettingItemClickable(
            title = "RAG Documents",
            subtitle = "What you want to teach your AI - documents, notes, conversations",
            onClick = onAddDocument,
            trailing = {
                Row {
                    IconButton(onClick = onAddDocument) {
                        Icon(Icons.Default.Add, "Add", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { /* TODO: Show help */ }) {
                        Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
                    }
                }
            }
        )
        
        // Document processing indicator
        when (val status = documentProcessingStatus) {
            is com.yourown.ai.data.repository.DocumentProcessingStatus.Processing -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Processing: ${status.documentName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = status.currentStep,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { status.progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${status.progress}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            is com.yourown.ai.data.repository.DocumentProcessingStatus.Deleting -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Deleting: ${status.documentName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            is com.yourown.ai.data.repository.DocumentProcessingStatus.Completed -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "✓ Processing completed!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            is com.yourown.ai.data.repository.DocumentProcessingStatus.Failed -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Processing failed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = status.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            else -> { /* No processing indicator needed */ }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        // Saved Memories
        SettingItemClickable(
            title = "Saved Memories",
            subtitle = "View saved memories",
            onClick = onViewMemories,
            trailing = {
                Icon(Icons.Default.ChevronRight, "View")
            }
        )
    }
}

@Composable
private fun EmbeddingModelsSection(
    onShowEmbeddingModels: () -> Unit,
    onRecalculateEmbeddings: () -> Unit,
    isRecalculating: Boolean,
    recalculationProgress: String?,
    recalculationProgressPercent: Float
) {
    SettingsSection(
        title = "Embedding Models",
        icon = Icons.Default.Memory,
        subtitle = "Models for semantic search and RAG"
    ) {
        // Download Embedding Model button
        FilledTonalButton(
            onClick = onShowEmbeddingModels,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download Embedding Model")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Recalculate All Embeddings button
        OutlinedButton(
            onClick = onRecalculateEmbeddings,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isRecalculating
        ) {
            if (isRecalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Recalculate All Embeddings")
        }
        
        // Show progress bar and text
        if (isRecalculating || recalculationProgress != null) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            if (isRecalculating) {
                LinearProgressIndicator(
                    progress = { recalculationProgressPercent },
                    modifier = Modifier.fillMaxWidth(),
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recalculationProgress ?: "Processing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "${(recalculationProgressPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (recalculationProgress != null) {
                // Completion or error message
                Text(
                    text = recalculationProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (recalculationProgress.startsWith("✅")) {
                        MaterialTheme.colorScheme.primary
                    } else if (recalculationProgress.startsWith("❌")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Warning text
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "⚠️ Important: Recalculate all embeddings after switching embedding models",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AppearanceSection(
    onShowAppearance: () -> Unit
) {
    SettingsSection(
        title = "Appearance",
        icon = Icons.Default.Palette,
        subtitle = "Theme, colors, and fonts"
    ) {
        SettingItemClickable(
            title = "Customize",
            subtitle = "Change theme, colors, and text size",
            onClick = onShowAppearance,
            trailing = {
                Icon(Icons.Default.ChevronRight, "Customize")
            }
        )
    }
}

// Helper Composables

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            content()
        }
    }
}

@Composable
private fun SettingItemClickable(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing()
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "%.2f".format(it) }
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueFormatter(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { /* TODO: Show help */ }) {
                    Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
                }
            }
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            IconButton(onClick = { /* TODO: Show help */ }) {
                Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    title: String,
    subtitle: String,
    value: Int,
    options: List<Int>,
    onValueChange: (Int) -> Unit,
    valueSuffix: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.menuAnchor()
                    ) {
                        Text(
                            text = "$value",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "$option $valueSuffix",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                IconButton(onClick = { /* TODO: Show help */ }) {
                    Icon(Icons.Default.HelpOutline, "Help", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
