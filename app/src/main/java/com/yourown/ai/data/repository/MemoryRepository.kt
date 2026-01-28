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
     * Insert new memory with pre-computed embedding
     */
    suspend fun insertMemory(memory: MemoryEntry) {
        // Generate embedding for the memory fact
        val embeddingResult = embeddingService.generateEmbedding(memory.fact)
        val embeddingString = if (embeddingResult.isSuccess) {
            embeddingResult.getOrNull()?.joinToString(",")
        } else {
            null
        }
        
        // Save memory with embedding
        memoryDao.insertMemory(memory.toEntity(embedding = embeddingString))
    }
    
    /**
     * Update existing memory and recalculate embedding
     */
    suspend fun updateMemory(memory: MemoryEntry) {
        // Regenerate embedding for updated fact
        val embeddingResult = embeddingService.generateEmbedding(memory.fact)
        val embeddingString = if (embeddingResult.isSuccess) {
            embeddingResult.getOrNull()?.joinToString(",")
        } else {
            null
        }
        
        // Update memory with new embedding
        memoryDao.updateMemory(memory.toEntity(embedding = embeddingString))
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
     * Now uses pre-computed embeddings for better performance
     * 
     * @param query User's current message
     * @param limit Maximum number of memories to return (default 5)
     * @param minAgeDays Minimum age in days for memories to be retrieved
     * @return List of most relevant memories
     */
    suspend fun findSimilarMemories(query: String, limit: Int = 5, minAgeDays: Int = 0): List<MemoryEntry> {
        try {
            // Get all memory entities (with embeddings)
            val allMemoryEntities = memoryDao.getAllMemories().first()
            if (allMemoryEntities.isEmpty()) return emptyList()
            
            // Filter memories by age (only retrieve memories older than minAgeDays)
            val currentTimeMillis = System.currentTimeMillis()
            val minAgeMillis = minAgeDays * 24 * 60 * 60 * 1000L
            val filteredEntities = if (minAgeDays > 0) {
                allMemoryEntities.filter { entity ->
                    (currentTimeMillis - entity.createdAt) >= minAgeMillis
                }
            } else {
                allMemoryEntities
            }
            
            if (filteredEntities.isEmpty()) return emptyList()
            
            // Generate embedding for query
            val queryEmbeddingResult = embeddingService.generateEmbedding(query)
            if (queryEmbeddingResult.isFailure) {
                android.util.Log.w("MemoryRepository", "Failed to generate query embedding for memory search")
                return emptyList()
            }
            val queryEmbedding = queryEmbeddingResult.getOrNull() ?: return emptyList()
            
            // Use pre-computed embeddings from database
            val memoriesWithEmbeddings = filteredEntities.mapNotNull { entity ->
                val embedding = entity.embedding?.let { embStr ->
                    embStr.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                }
                
                // If embedding is missing, generate it (fallback)
                val finalEmbedding = if (embedding == null) {
                    android.util.Log.w("MemoryRepository", "Missing embedding for memory ${entity.id}, generating...")
                    val result = embeddingService.generateEmbedding(entity.fact)
                    result.getOrNull()
                } else {
                    embedding
                }
                
                if (finalEmbedding != null) {
                    entity.toDomain() to finalEmbedding
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
    
    /**
     * Recalculate embeddings for all memories
     * Use this when switching embedding models
     * @param onProgress callback with (current, total, percentage) progress
     */
    suspend fun recalculateAllEmbeddings(
        onProgress: (current: Int, total: Int, percentage: Float) -> Unit = { _, _, _ -> }
    ): Result<Int> {
        return try {
            val allEntities = memoryDao.getAllMemories().first()
            val totalCount = allEntities.size
            var processedCount = 0
            
            allEntities.forEachIndexed { index, entity ->
                val embeddingResult = embeddingService.generateEmbedding(entity.fact)
                if (embeddingResult.isSuccess) {
                    val embeddingString = embeddingResult.getOrNull()?.joinToString(",")
                    memoryDao.updateMemory(
                        entity.copy(embedding = embeddingString)
                    )
                    processedCount++
                }
                
                // Update progress
                val percentage = if (totalCount > 0) (index + 1).toFloat() / totalCount else 0f
                onProgress(index + 1, totalCount, percentage)
            }
            
            android.util.Log.i("MemoryRepository", "Recalculated embeddings for $processedCount memories")
            Result.success(processedCount)
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepository", "Error recalculating memory embeddings", e)
            Result.failure(e)
        }
    }
}
