package com.yourown.ai.data.service

import android.content.Context
import android.util.Log
import com.yourown.ai.data.llama.EmbeddingWrapper
import com.yourown.ai.data.repository.LocalEmbeddingModelRepository
import com.yourown.ai.domain.model.DownloadStatus
import com.yourown.ai.domain.model.LocalEmbeddingModel
import com.yourown.ai.domain.service.EmbeddingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of EmbeddingService using llama.cpp
 * Thread-safe singleton for embedding model operations
 */
@Singleton
class EmbeddingServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingModelRepository: LocalEmbeddingModelRepository
) : EmbeddingService {
    
    companion object {
        private const val TAG = "EmbeddingService"
    }
    
    private val embeddingWrapper = EmbeddingWrapper(context)
    private var currentModel: LocalEmbeddingModel? = null
    
    // Mutex to prevent concurrent operations (llama.cpp is NOT thread-safe!)
    private val loadMutex = Mutex()
    private val embeddingMutex = Mutex()
    
    /**
     * Automatically selects and loads the best available embedding model
     * Priority: MXBAI_EMBED_LARGE > ALL_MINILM_L6_V2
     * Returns Result.success if a model was loaded, Result.failure if no models available
     */
    private suspend fun autoLoadBestModel(): Result<Unit> {
        val models = embeddingModelRepository.models.value
        
        // Priority order: larger, better model first
        val preferredOrder = listOf(
            LocalEmbeddingModel.MXBAI_EMBED_LARGE,
            LocalEmbeddingModel.ALL_MINILM_L6_V2
        )
        
        for (model in preferredOrder) {
            val modelInfo = models[model]
            if (modelInfo?.status is DownloadStatus.Downloaded) {
                Log.i(TAG, "Auto-loading best available model: ${model.displayName}")
                return loadModel(model)
            }
        }
        
        Log.e(TAG, "No downloaded embedding models found")
        return Result.failure(IllegalStateException("No embedding models downloaded"))
    }
    
    override suspend fun loadModel(model: LocalEmbeddingModel): Result<Unit> = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            try {
                Log.d(TAG, "Attempting to load embedding model: ${model.displayName}")
                
                // If already loaded, skip
                if (currentModel == model && embeddingWrapper.isLoaded()) {
                    Log.i(TAG, "Embedding model ${model.displayName} already loaded")
                    return@withContext Result.success(Unit)
                }
                
                // Check if model is downloaded
                val models = embeddingModelRepository.models.value
                val modelInfo = models[model]
                
                val modelFile = getModelFile(model)
                Log.d(TAG, "Model file path: ${modelFile.absolutePath}")
                Log.d(TAG, "File exists: ${modelFile.exists()}")
                
                if (!modelFile.exists()) {
                    Log.e(TAG, "Embedding model file not found")
                    return@withContext Result.failure(
                        IllegalStateException("Model file not found. Please download the model first.")
                    )
                }
                
                if (modelInfo?.status !is DownloadStatus.Downloaded) {
                    Log.e(TAG, "Embedding model not downloaded: ${modelInfo?.status}")
                    return@withContext Result.failure(
                        IllegalStateException("Model ${model.displayName} is not downloaded")
                    )
                }
                
                // Unload previous model
                if (embeddingWrapper.isLoaded()) {
                    Log.i(TAG, "Unloading previous embedding model")
                    embeddingWrapper.unload()
                }
                
                Log.i(TAG, "Loading embedding model: ${model.displayName}")
                
                // Load model
                val success = embeddingWrapper.load(modelFile, model.dimensions)
                
                if (success) {
                    currentModel = model
                    Log.i(TAG, "Embedding model loaded successfully: ${model.displayName}")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to load embedding model"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading embedding model", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun unloadModel(): Unit = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            try {
                Log.i(TAG, "Unloading embedding model")
                embeddingWrapper.unload()
                currentModel = null
            } catch (e: Exception) {
                Log.e(TAG, "Error unloading embedding model", e)
            }
        }
    }
    
    override fun isModelLoaded(): Boolean {
        return embeddingWrapper.isLoaded()
    }
    
    override fun getCurrentModel(): LocalEmbeddingModel? {
        return currentModel
    }
    
    override suspend fun generateEmbedding(text: String): Result<FloatArray> = withContext(Dispatchers.IO) {
        embeddingMutex.withLock {
            try {
                // Auto-load best available model if none loaded
                if (!embeddingWrapper.isLoaded()) {
                    val autoLoadResult = autoLoadBestModel()
                    if (autoLoadResult.isFailure) {
                        return@withContext Result.failure(
                            IllegalStateException("No embedding model available. Please download an embedding model first.")
                        )
                    }
                }
                
                if (text.isBlank()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Text cannot be blank")
                    )
                }
                
                Log.d(TAG, "Generating embedding for text (${text.length} chars)")
                val embedding = embeddingWrapper.generateEmbedding(text)
                
                Result.success(embedding)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating embedding", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun generateEmbeddings(texts: List<String>): Result<List<FloatArray>> = withContext(Dispatchers.IO) {
        embeddingMutex.withLock {
            try {
                // Auto-load best available model if none loaded
                if (!embeddingWrapper.isLoaded()) {
                    val autoLoadResult = autoLoadBestModel()
                    if (autoLoadResult.isFailure) {
                        return@withContext Result.failure(
                            IllegalStateException("No embedding model available. Please download an embedding model first.")
                        )
                    }
                }
                
                if (texts.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                Log.d(TAG, "Generating embeddings for ${texts.size} texts")
                
                val embeddings = texts.map { text ->
                    if (text.isBlank()) {
                        FloatArray(currentModel?.dimensions ?: 0) { 0f }
                    } else {
                        embeddingWrapper.generateEmbedding(text)
                    }
                }
                
                Result.success(embeddings)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating embeddings", e)
                Result.failure(e)
            }
        }
    }
    
    override fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return embeddingWrapper.cosineSimilarity(embedding1, embedding2)
    }
    
    private fun getModelFile(model: LocalEmbeddingModel): File {
        return embeddingModelRepository.getModelFile(model)
    }
}
