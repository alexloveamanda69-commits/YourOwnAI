package com.yourown.ai.data.repository

import com.yourown.ai.data.local.YourOwnAIDatabase
import com.yourown.ai.data.local.entity.KnowledgeDocumentEntity
import com.yourown.ai.domain.model.KnowledgeDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing knowledge documents
 */
@Singleton
class KnowledgeDocumentRepository @Inject constructor(
    private val database: YourOwnAIDatabase
) {
    private val dao = database.knowledgeDocumentDao()
    
    /**
     * Get all documents as Flow
     */
    fun getAllDocuments(): Flow<List<KnowledgeDocument>> {
        return dao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get document by ID
     */
    suspend fun getDocumentById(id: String): KnowledgeDocument? {
        return dao.getDocumentById(id)?.toDomain()
    }
    
    /**
     * Create new document
     */
    suspend fun createDocument(name: String, content: String) {
        val now = System.currentTimeMillis()
        val entity = KnowledgeDocumentEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            content = content,
            createdAt = now,
            updatedAt = now,
            sizeBytes = content.toByteArray().size
        )
        dao.insertDocument(entity)
    }
    
    /**
     * Update document
     */
    suspend fun updateDocument(document: KnowledgeDocument) {
        val entity = document.toEntity().copy(
            updatedAt = System.currentTimeMillis(),
            sizeBytes = document.content.toByteArray().size
        )
        dao.updateDocument(entity)
    }
    
    /**
     * Delete document
     */
    suspend fun deleteDocument(documentId: String) {
        dao.deleteById(documentId)
    }
    
    // Mappers
    private fun KnowledgeDocumentEntity.toDomain() = KnowledgeDocument(
        id = id,
        name = name,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sizeBytes = sizeBytes
    )
    
    private fun KnowledgeDocument.toEntity() = KnowledgeDocumentEntity(
        id = id,
        name = name,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sizeBytes = sizeBytes
    )
}
