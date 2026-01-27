package com.yourown.ai.domain.service

import com.yourown.ai.domain.model.LocalEmbeddingModel

/**
 * Service for local embedding model inference
 * Used for semantic search, RAG, and similarity matching
 */
interface EmbeddingService {
    
    /**
     * Load an embedding model from local storage
     */
    suspend fun loadModel(model: LocalEmbeddingModel): Result<Unit>
    
    /**
     * Unload current embedding model
     */
    suspend fun unloadModel()
    
    /**
     * Check if an embedding model is currently loaded
     */
    fun isModelLoaded(): Boolean
    
    /**
     * Get currently loaded embedding model
     */
    fun getCurrentModel(): LocalEmbeddingModel?
    
    /**
     * Generate embedding vector for a single text
     * @param text Input text to embed
     * @return Result containing embedding vector (FloatArray)
     */
    suspend fun generateEmbedding(text: String): Result<FloatArray>
    
    /**
     * Generate embedding vectors for multiple texts (batch processing)
     * @param texts List of texts to embed
     * @return Result containing list of embedding vectors
     */
    suspend fun generateEmbeddings(texts: List<String>): Result<List<FloatArray>>
    
    /**
     * Calculate cosine similarity between two embedding vectors
     * @return Similarity score from -1.0 to 1.0 (higher = more similar)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float
}
