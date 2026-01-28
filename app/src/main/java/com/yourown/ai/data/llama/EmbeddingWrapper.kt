package com.yourown.ai.data.llama

import android.content.Context
import android.util.Log
import com.llamatik.library.platform.LlamaBridge
import java.io.File
import kotlin.math.sqrt

/**
 * Wrapper for embedding model inference using Llamatik (llama.cpp)
 * Handles loading GGUF embedding models and generating embeddings
 */
class EmbeddingWrapper(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingWrapper"
    }
    
    private var isModelLoaded = false
    private var currentModelName: String? = null
    private var embeddingDimensions: Int = 0
    private val lock = Any()
    
    /**
     * Load embedding model from file using Llamatik
     */
    fun load(modelFile: File, dimensions: Int): Boolean {
        synchronized(lock) {
            return try {
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                    return false
                }
                
                Log.i(TAG, "Loading embedding model: ${modelFile.name}, size: ${modelFile.length() / 1024 / 1024}MB")
                
                // Unload previous model if loaded
                if (isModelLoaded) {
                    unloadInternal()
                }
                
                // Use Llamatik's initModel() for embeddings
                val success = LlamaBridge.initModel(modelFile.absolutePath)
                
                if (success) {
                    isModelLoaded = true
                    currentModelName = modelFile.name
                    embeddingDimensions = dimensions
                    
                    Log.i(TAG, "Embedding model loaded successfully: ${modelFile.name}")
                } else {
                    Log.e(TAG, "Failed to load embedding model: ${modelFile.name}")
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "Error loading embedding model", e)
                isModelLoaded = false
                currentModelName = null
                false
            }
        }
    }
    
    /**
     * Unload model and free memory
     */
    fun unload() {
        synchronized(lock) {
            unloadInternal()
        }
    }
    
    private fun unloadInternal() {
        if (!isModelLoaded) {
            Log.d(TAG, "No model loaded, skipping unload")
            return
        }
        
        try {
            Log.i(TAG, "Unloading embedding model: $currentModelName")
            
            // Use Llamatik's shutdown() to free resources
            LlamaBridge.shutdown()
            
            isModelLoaded = false
            currentModelName = null
            embeddingDimensions = 0
            
            Log.i(TAG, "Embedding model unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading embedding model", e)
            // Force reset state even if shutdown fails
            isModelLoaded = false
            currentModelName = null
            embeddingDimensions = 0
        }
    }
    
    /**
     * Check if model is loaded
     */
    fun isLoaded(): Boolean = isModelLoaded
    
    /**
     * Get current model name
     */
    fun getCurrentModelName(): String? = currentModelName
    
    /**
     * Generate embedding for text using Llamatik
     * Returns a float array of embedding dimensions
     */
    fun generateEmbedding(text: String): FloatArray {
        if (!isModelLoaded) {
            throw IllegalStateException("Embedding model not loaded")
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided for embedding")
            return FloatArray(embeddingDimensions) { 0f }
        }
        
        return try {
            // Truncate text to max 512 characters to avoid model token limit
            // Most embedding models support up to 512 tokens (~400-500 chars)
            val maxChars = 512
            val truncatedText = if (text.length > maxChars) {
                Log.w(TAG, "Text too long (${text.length} chars), truncating to $maxChars chars")
                text.take(maxChars)
            } else {
                text
            }
            
            Log.d(TAG, "Generating embedding for text (${truncatedText.length} chars): ${truncatedText.take(50)}...")
            
            // Use Llamatik's embed() function
            val embedding = LlamaBridge.embed(truncatedText)
            
            // Verify dimensions match expected size
            if (embedding.size != embeddingDimensions) {
                Log.w(TAG, "Expected ${embeddingDimensions}D but got ${embedding.size}D. Adjusting...")
                embeddingDimensions = embedding.size
            }
            
            Log.d(TAG, "Embedding generated: ${embedding.size} dimensions")
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            throw e
        }
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     * Returns value from -1.0 to 1.0 (higher = more similar)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            throw IllegalArgumentException("Embeddings must have same dimensions")
        }
        
        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            magnitude1 += embedding1[i] * embedding1[i]
            magnitude2 += embedding2[i] * embedding2[i]
        }
        
        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)
        
        return if (magnitude1 > 0f && magnitude2 > 0f) {
            dotProduct / (magnitude1 * magnitude2)
        } else {
            0f
        }
    }
}
