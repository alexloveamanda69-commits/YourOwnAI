package com.yourown.ai.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourown.ai.data.remote.deepseek.DeepseekClient
import com.yourown.ai.data.repository.LocalModelRepository
import com.yourown.ai.domain.model.AIConfig
import com.yourown.ai.domain.model.AIProvider
import com.yourown.ai.domain.model.ApiKeyInfo
import com.yourown.ai.domain.model.LocalModel
import com.yourown.ai.domain.model.LocalModelInfo
import com.yourown.ai.domain.model.UserContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeys: List<ApiKeyInfo> = listOf(
        ApiKeyInfo(AIProvider.DEEPSEEK),
        ApiKeyInfo(AIProvider.OPENAI),
        ApiKeyInfo(AIProvider.ANTHROPIC),
        ApiKeyInfo(AIProvider.XAI)
    ),
    val aiConfig: AIConfig = AIConfig(),
    val userContext: UserContext = UserContext(),
    val localModels: Map<LocalModel, LocalModelInfo> = emptyMap(),
    val systemPrompts: List<com.yourown.ai.domain.model.SystemPrompt> = emptyList(),
    val apiPrompts: List<com.yourown.ai.domain.model.SystemPrompt> = emptyList(),
    val localPrompts: List<com.yourown.ai.domain.model.SystemPrompt> = emptyList(),
    val knowledgeDocuments: List<com.yourown.ai.domain.model.KnowledgeDocument> = emptyList(),
    val showSystemPromptDialog: Boolean = false,
    val showLocalSystemPromptDialog: Boolean = false,
    val showSystemPromptsListDialog: Boolean = false,
    val showEditPromptDialog: Boolean = false,
    val selectedPromptForEdit: com.yourown.ai.domain.model.SystemPrompt? = null,
    val promptTypeFilter: com.yourown.ai.data.repository.PromptType? = null,
    val showDocumentsListDialog: Boolean = false,
    val showEditDocumentDialog: Boolean = false,
    val selectedDocumentForEdit: com.yourown.ai.domain.model.KnowledgeDocument? = null,
    val showContextDialog: Boolean = false,
    val showApiKeyDialog: Boolean = false,
    val showLocalModelsDialog: Boolean = false,
    val showAppearanceDialog: Boolean = false,
    val selectedProvider: AIProvider? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val localModelRepository: LocalModelRepository,
    private val apiKeyRepository: com.yourown.ai.data.repository.ApiKeyRepository,
    private val aiConfigRepository: com.yourown.ai.data.repository.AIConfigRepository,
    private val systemPromptRepository: com.yourown.ai.data.repository.SystemPromptRepository,
    private val knowledgeDocumentRepository: com.yourown.ai.data.repository.KnowledgeDocumentRepository,
    private val deepseekClient: DeepseekClient,
    private val openAIClient: com.yourown.ai.data.remote.openai.OpenAIClient,
    private val xaiClient: com.yourown.ai.data.remote.xai.XAIClient
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        observeLocalModels()
        observeApiKeys()
        observeSystemPrompts()
        observeKnowledgeDocuments()
        initializeDefaultPrompts()
    }
    
    private fun initializeDefaultPrompts() {
        viewModelScope.launch {
            systemPromptRepository.initializeDefaultPrompts()
        }
    }
    
    private fun observeSystemPrompts() {
        viewModelScope.launch {
            systemPromptRepository.getAllPrompts().collect { prompts ->
                _uiState.update { 
                    it.copy(
                        systemPrompts = prompts,
                        apiPrompts = prompts.filter { p -> p.type == com.yourown.ai.data.repository.PromptType.API },
                        localPrompts = prompts.filter { p -> p.type == com.yourown.ai.data.repository.PromptType.LOCAL }
                    ) 
                }
            }
        }
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // Load AI config
            aiConfigRepository.aiConfig.collect { config ->
                _uiState.update { it.copy(aiConfig = config) }
            }
        }
        viewModelScope.launch {
            // Load user context
            aiConfigRepository.userContext.collect { context ->
                _uiState.update { it.copy(userContext = context) }
            }
        }
    }
    
    private fun observeApiKeys() {
        viewModelScope.launch {
            apiKeyRepository.apiKeys.collect { keys ->
                _uiState.update { state ->
                    state.copy(
                        apiKeys = AIProvider.entries
                            .filter { it != AIProvider.CUSTOM }
                            .map { provider ->
                                ApiKeyInfo(
                                    provider = provider,
                                    isSet = keys.containsKey(provider),
                                    displayKey = apiKeyRepository.getDisplayKey(provider)
                                )
                            }
                    )
                }
            }
        }
    }
    
    private fun observeLocalModels() {
        viewModelScope.launch {
            localModelRepository.models.collect { models ->
                _uiState.update { it.copy(localModels = models) }
            }
        }
    }
    
    // System Prompt
    fun showSystemPromptDialog() {
        _uiState.update { it.copy(showSystemPromptDialog = true) }
    }
    
    fun hideSystemPromptDialog() {
        _uiState.update { it.copy(showSystemPromptDialog = false) }
    }
    
    fun updateSystemPrompt(prompt: String) {
        viewModelScope.launch {
            aiConfigRepository.updateSystemPrompt(prompt)
            _uiState.update { it.copy(showSystemPromptDialog = false) }
        }
    }
    
    // Local System Prompt
    fun showLocalSystemPromptDialog() {
        _uiState.update { it.copy(showLocalSystemPromptDialog = true) }
    }
    
    fun hideLocalSystemPromptDialog() {
        _uiState.update { it.copy(showLocalSystemPromptDialog = false) }
    }
    
    fun updateLocalSystemPrompt(prompt: String) {
        viewModelScope.launch {
            aiConfigRepository.updateLocalSystemPrompt(prompt)
            _uiState.update { it.copy(showLocalSystemPromptDialog = false) }
        }
    }
    
    // Context
    fun showContextDialog() {
        _uiState.update { it.copy(showContextDialog = true) }
    }
    
    fun hideContextDialog() {
        _uiState.update { it.copy(showContextDialog = false) }
    }
    
    fun updateContext(context: String) {
        viewModelScope.launch {
            aiConfigRepository.updateUserContext(context)
            _uiState.update { it.copy(showContextDialog = false) }
        }
    }
    
    // AI Config
    fun updateTemperature(value: Float) {
        viewModelScope.launch {
            aiConfigRepository.updateTemperature(value)
        }
    }
    
    fun updateTopP(value: Float) {
        viewModelScope.launch {
            aiConfigRepository.updateTopP(value)
        }
    }
    
    fun toggleDeepEmpathy() {
        viewModelScope.launch {
            val newValue = !_uiState.value.aiConfig.deepEmpathy
            aiConfigRepository.setDeepEmpathy(newValue)
        }
    }
    
    fun toggleMemory() {
        viewModelScope.launch {
            val newValue = !_uiState.value.aiConfig.memoryEnabled
            aiConfigRepository.setMemoryEnabled(newValue)
        }
    }
    
    fun updateMessageHistoryLimit(limit: Int) {
        viewModelScope.launch {
            aiConfigRepository.updateMessageHistoryLimit(limit)
        }
    }
    
    fun updateMaxTokens(tokens: Int) {
        viewModelScope.launch {
            aiConfigRepository.updateMaxTokens(tokens)
        }
    }
    
    // Local Models
    fun showLocalModelsDialog() {
        _uiState.update { it.copy(showLocalModelsDialog = true) }
    }
    
    fun hideLocalModelsDialog() {
        _uiState.update { it.copy(showLocalModelsDialog = false) }
    }
    
    fun downloadModel(model: LocalModel) {
        viewModelScope.launch {
            localModelRepository.downloadModel(model)
        }
    }
    
    fun deleteModel(model: LocalModel) {
        viewModelScope.launch {
            localModelRepository.deleteModel(model)
        }
    }
    
    fun forceDeleteAllModels() {
        viewModelScope.launch {
            localModelRepository.forceDeleteAll()
        }
    }
    
    // API Keys
    fun showApiKeyDialog(provider: AIProvider) {
        _uiState.update {
            it.copy(
                showApiKeyDialog = true,
                selectedProvider = provider
            )
        }
    }
    
    fun hideApiKeyDialog() {
        _uiState.update {
            it.copy(
                showApiKeyDialog = false,
                selectedProvider = null
            )
        }
    }
    
    fun saveApiKey(provider: AIProvider, key: String) {
        viewModelScope.launch {
            apiKeyRepository.saveApiKey(provider, key)
            hideApiKeyDialog()
        }
    }
    
    fun deleteApiKey(provider: AIProvider) {
        viewModelScope.launch {
            apiKeyRepository.deleteApiKey(provider)
        }
    }
    
    fun testApiKey(provider: AIProvider) {
        viewModelScope.launch {
            val apiKey = apiKeyRepository.getApiKey(provider) ?: return@launch
            
            when (provider) {
                AIProvider.DEEPSEEK -> {
                    val result = deepseekClient.listModels(apiKey)
                    result.onSuccess { models ->
                        android.util.Log.i("SettingsViewModel", "Deepseek models: $models")
                    }.onFailure { error ->
                        android.util.Log.e("SettingsViewModel", "Failed to fetch Deepseek models: ${error.message}")
                    }
                }
                AIProvider.OPENAI -> {
                    val result = openAIClient.listModels(apiKey)
                    result.onSuccess { models ->
                        android.util.Log.i("SettingsViewModel", "OpenAI models: $models")
                    }.onFailure { error ->
                        android.util.Log.e("SettingsViewModel", "Failed to fetch OpenAI models: ${error.message}")
                    }
                }
                AIProvider.XAI -> {
                    val result = xaiClient.listModels(apiKey)
                    result.onSuccess { models ->
                        android.util.Log.i("SettingsViewModel", "x.ai models: $models")
                    }.onFailure { error ->
                        android.util.Log.e("SettingsViewModel", "Failed to fetch x.ai models: ${error.message}")
                    }
                }
                else -> {
                    // TODO: Implement for Anthropic
                }
            }
        }
    }
    
    // Appearance
    fun showAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = true) }
    }
    
    fun hideAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = false) }
    }
    
    // System Prompts Management
    fun showSystemPromptsListDialog(type: com.yourown.ai.data.repository.PromptType) {
        _uiState.update { 
            it.copy(
                showSystemPromptsListDialog = true,
                promptTypeFilter = type
            ) 
        }
    }
    
    fun hideSystemPromptsListDialog() {
        _uiState.update { 
            it.copy(
                showSystemPromptsListDialog = false,
                promptTypeFilter = null
            ) 
        }
    }
    
    fun showEditPromptDialog(prompt: com.yourown.ai.domain.model.SystemPrompt? = null) {
        _uiState.update { 
            it.copy(
                showEditPromptDialog = true,
                selectedPromptForEdit = prompt
            ) 
        }
    }
    
    fun hideEditPromptDialog() {
        _uiState.update { 
            it.copy(
                showEditPromptDialog = false,
                selectedPromptForEdit = null
            ) 
        }
    }
    
    fun createNewPrompt(type: com.yourown.ai.data.repository.PromptType) {
        viewModelScope.launch {
            val count = when (type) {
                com.yourown.ai.data.repository.PromptType.API -> _uiState.value.apiPrompts.size + 1
                com.yourown.ai.data.repository.PromptType.LOCAL -> _uiState.value.localPrompts.size + 1
            }
            
            val defaultContent = when (type) {
                com.yourown.ai.data.repository.PromptType.API -> "Ты — цифровой партнёр, большая языковая модель. В ходе разговора ты подстраиваешься под тон и предпочтения пользователя. Постарайся соответствовать его настроению, тону и в целом манере говорить. Твоя цель — чтобы разговор ощущался естественным. Ты ведёшь искренний диалог, отвечая на предоставленную информацию и проявляя неподдельное любопытство. Задавай очень простой, односложный уточняющий вопрос, когда это естественно. Не задавай больше одного уточняющего вопроса, если только пользователь специально об этом не попросит."
                com.yourown.ai.data.repository.PromptType.LOCAL -> "Ты — цифровой партнёр. Ты отвечаешь на языке пользователя. Ответь на последнее сообщение. Не пиши весь диалог, нужен только один ответ."
            }
            
            systemPromptRepository.createPrompt(
                name = "${if (type == com.yourown.ai.data.repository.PromptType.API) "API" else "Local"} System $count",
                content = defaultContent,
                type = type,
                isDefault = false
            )
        }
    }
    
    fun savePrompt(
        id: String?,
        name: String,
        content: String,
        type: com.yourown.ai.data.repository.PromptType,
        isDefault: Boolean
    ) {
        viewModelScope.launch {
            if (id == null) {
                // Create new
                systemPromptRepository.createPrompt(
                    name = name,
                    content = content,
                    type = type,
                    isDefault = isDefault
                )
            } else {
                // Update existing
                systemPromptRepository.updatePrompt(
                    id = id,
                    name = name,
                    content = content,
                    isDefault = isDefault
                )
            }
            hideEditPromptDialog()
        }
    }
    
    fun deletePrompt(id: String) {
        viewModelScope.launch {
            systemPromptRepository.deletePrompt(id)
        }
    }
    
    fun setPromptAsDefault(id: String) {
        viewModelScope.launch {
            systemPromptRepository.setAsDefault(id)
        }
    }
    
    // Knowledge Documents methods
    
    private fun observeKnowledgeDocuments() {
        viewModelScope.launch {
            knowledgeDocumentRepository.getAllDocuments().collect { documents ->
                _uiState.update { it.copy(knowledgeDocuments = documents) }
            }
        }
    }
    
    fun showDocumentsListDialog() {
        _uiState.update { it.copy(showDocumentsListDialog = true) }
    }
    
    fun hideDocumentsListDialog() {
        _uiState.update { it.copy(showDocumentsListDialog = false) }
    }
    
    fun showEditDocumentDialog(document: com.yourown.ai.domain.model.KnowledgeDocument? = null) {
        _uiState.update { 
            it.copy(
                showEditDocumentDialog = true,
                selectedDocumentForEdit = document
            )
        }
    }
    
    fun hideEditDocumentDialog() {
        _uiState.update { 
            it.copy(
                showEditDocumentDialog = false,
                selectedDocumentForEdit = null
            )
        }
    }
    
    fun createNewDocument() {
        val count = _uiState.value.knowledgeDocuments.size + 1
        showEditDocumentDialog(
            com.yourown.ai.domain.model.KnowledgeDocument(
                id = "",
                name = "Doc $count",
                content = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                sizeBytes = 0
            )
        )
    }
    
    fun saveDocument(id: String, name: String, content: String) {
        viewModelScope.launch {
            if (id.isEmpty()) {
                // Create new
                knowledgeDocumentRepository.createDocument(
                    name = name,
                    content = content
                )
            } else {
                // Update existing
                val document = com.yourown.ai.domain.model.KnowledgeDocument(
                    id = id,
                    name = name,
                    content = content,
                    createdAt = _uiState.value.knowledgeDocuments.find { it.id == id }?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    sizeBytes = content.toByteArray().size
                )
                knowledgeDocumentRepository.updateDocument(document)
            }
            hideEditDocumentDialog()
        }
    }
    
    fun deleteDocument(id: String) {
        viewModelScope.launch {
            knowledgeDocumentRepository.deleteDocument(id)
        }
    }
}
