package com.yourown.ai.domain.model

data class KnowledgeDocument(
    val id: String,
    val name: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sizeBytes: Int = 0
)
