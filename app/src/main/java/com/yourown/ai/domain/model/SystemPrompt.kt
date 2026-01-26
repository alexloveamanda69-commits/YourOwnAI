package com.yourown.ai.domain.model

import com.yourown.ai.data.repository.PromptType

data class SystemPrompt(
    val id: String,
    val name: String,
    val content: String,
    val type: PromptType,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val usageCount: Int
)
