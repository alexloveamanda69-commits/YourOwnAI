package com.yourown.ai.data.repository

import com.yourown.ai.data.local.dao.MemoryDao
import com.yourown.ai.data.local.entity.MemoryEntity
import com.yourown.ai.data.local.entity.toDomain
import com.yourown.ai.data.local.entity.toEntity
import com.yourown.ai.data.util.SemanticSearchUtil
import com.yourown.ai.domain.model.MemoryEntry
import com.yourown.ai.domain.service.EmbeddingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing user memories
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embeddingService: EmbeddingService
) {
    
    /**
     * Get all memories across all conversations
     */
    fun getAllMemories(): Flow<List<MemoryEntry>> {
        return memoryDao.getAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get memories for a specific conversation
     */
    fun getMemoriesForConversation(conversationId: String): Flow<List<MemoryEntry>> {
        return memoryDao.getMemoriesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get memory by ID
     */
    suspend fun getMemoryById(id: String): MemoryEntry? {
        return memoryDao.getMemoryById(id)?.toDomain()
    }
    
    /**
     * Get memory for a specific message (if exists)
     */
    suspend fun getMemoryForMessage(messageId: String): MemoryEntry? {
        return memoryDao.getMemoryForMessage(messageId)?.toDomain()
    }
    
    /**
     * Search memories by fact content
     */
    fun searchMemories(query: String): Flow<List<MemoryEntry>> {
        return memoryDao.searchMemories(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    /**
     * Get total memory count
     */
    suspend fun getTotalMemoryCount(): Int {
        return memoryDao.getTotalMemoryCount()
    }
    
    /**
     * Insert new memory
     */
    suspend fun insertMemory(memory: MemoryEntry) {
        memoryDao.insertMemory(memory.toEntity())
    }
    
    /**
     * Update existing memory
     */
    suspend fun updateMemory(memory: MemoryEntry) {
        memoryDao.updateMemory(memory.toEntity())
    }
    
    /**
     * Archive memory (soft delete)
     */
    suspend fun archiveMemory(id: String) {
        memoryDao.archiveMemory(id)
    }
    
    /**
     * Delete memory permanently
     */
    suspend fun deleteMemory(memory: MemoryEntry) {
        memoryDao.deleteMemory(memory.toEntity())
    }
    
    /**
     * Delete all memories for a conversation
     */
    suspend fun deleteMemoriesForConversation(conversationId: String) {
        memoryDao.deleteMemoriesForConversation(conversationId)
    }
    
    /**
     * Delete all memories (for debugging/testing)
     */
    suspend fun deleteAllMemories() {
        memoryDao.deleteAllMemories()
    }
    
    /**
     * Find similar memories using semantic search (embedding + keyword matching)
     * 
     * @param query User's current message
     * @param limit Maximum number of memories to return (default 5)
     * @return List of most relevant memories
     */
    suspend fun findSimilarMemories(query: String, limit: Int = 5, minAgeDays: Int = 0): List<MemoryEntry> {
        try {
            // Get all memories
            val allMemories = getAllMemories().first()
            if (allMemories.isEmpty()) return emptyList()
            
            // Filter memories by age (only retrieve memories older than minAgeDays)
            val currentTimeMillis = System.currentTimeMillis()
            val minAgeMillis = minAgeDays * 24 * 60 * 60 * 1000L
            val filteredMemories = if (minAgeDays > 0) {
                allMemories.filter { memory ->
                    (currentTimeMillis - memory.createdAt) >= minAgeMillis
                }
            } else {
                allMemories
            }
            
            if (filteredMemories.isEmpty()) return emptyList()
            
            // Generate embedding for query
            val queryEmbeddingResult = embeddingService.generateEmbedding(query)
            if (queryEmbeddingResult.isFailure) {
                android.util.Log.w("MemoryRepository", "Failed to generate query embedding for memory search")
                return emptyList()
            }
            val queryEmbedding = queryEmbeddingResult.getOrNull() ?: return emptyList()
            
            // For memories, we'll generate embeddings on-the-fly since they're not stored
            // In production, you'd want to store embeddings in the database
            val memoriesWithEmbeddings = filteredMemories.mapNotNull { memory ->
                val memoryEmbeddingResult = embeddingService.generateEmbedding(memory.fact)
                if (memoryEmbeddingResult.isSuccess) {
                    memory to memoryEmbeddingResult.getOrNull()
                } else {
                    null
                }
            }
            
            // Use semantic search to find similar memories
            val results = SemanticSearchUtil.findSimilar(
                query = query,
                queryEmbedding = queryEmbedding,
                items = memoriesWithEmbeddings,
                getText = { it.first.fact },
                getEmbedding = { it.second },
                k = limit
            )
            
            android.util.Log.d("MemoryRepository", "Found ${results.size} similar memories for query")
            
            return results.map { it.item.first }
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepository", "Error finding similar memories", e)
            return emptyList()
        }
    }
}
