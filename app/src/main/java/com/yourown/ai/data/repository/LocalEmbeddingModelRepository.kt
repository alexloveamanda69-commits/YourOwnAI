package com.yourown.ai.data.repository

import android.content.Context
import com.yourown.ai.di.DownloadClient
import com.yourown.ai.domain.model.DownloadStatus
import com.yourown.ai.domain.model.LocalEmbeddingModel
import com.yourown.ai.domain.model.LocalEmbeddingModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalEmbeddingModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @DownloadClient private val okHttpClient: OkHttpClient
) {
    private val _models = MutableStateFlow<Map<LocalEmbeddingModel, LocalEmbeddingModelInfo>>(
        LocalEmbeddingModel.entries.associateWith { LocalEmbeddingModelInfo(it) }
    )
    val models: StateFlow<Map<LocalEmbeddingModel, LocalEmbeddingModelInfo>> = _models.asStateFlow()
    
    // Mutex to ensure only one model downloads at a time
    private val downloadMutex = Mutex()
    
    private val embeddingModelsDir = File(context.filesDir, "embedding_models").apply {
        if (!exists()) mkdirs()
    }
    
    init {
        checkDownloadedModels()
        android.util.Log.d("LocalEmbeddingModelRepo", "Initialized with dir: ${embeddingModelsDir.absolutePath}")
    }
    
    private fun checkDownloadedModels() {
        LocalEmbeddingModel.entries.forEach { model ->
            val file = File(embeddingModelsDir, model.modelName)
            val expectedSize = model.sizeInMB * 1024 * 1024L
            val minSize = (expectedSize * 0.95).toLong()
            
            if (file.exists() && file.length() >= minSize) {
                if (isValidGGUFFile(file)) {
                    _models.update { map ->
                        map.toMutableMap().apply {
                            this[model] = LocalEmbeddingModelInfo(
                                model = model,
                                status = DownloadStatus.Downloaded,
                                filePath = file.absolutePath
                            )
                        }
                    }
                } else {
                    android.util.Log.w("LocalEmbeddingModelRepo", 
                        "Model ${model.displayName} is CORRUPT. Deleting.")
                    file.delete()
                    _models.update { map ->
                        map.toMutableMap().apply {
                            this[model] = LocalEmbeddingModelInfo(
                                model = model,
                                status = DownloadStatus.Failed("Corrupt file - please redownload")
                            )
                        }
                    }
                }
            } else if (file.exists()) {
                android.util.Log.w("LocalEmbeddingModelRepo", 
                    "Model ${model.displayName} incomplete (${file.length() / 1024 / 1024}MB / ${model.sizeInMB}MB). Deleting.")
                file.delete()
            }
        }
    }
    
    private fun isValidGGUFFile(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header)
                // GGUF magic number: "GGUF"
                header.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalEmbeddingModelRepo", "Error validating GGUF file", e)
            false
        }
    }
    
    fun getModelFile(model: LocalEmbeddingModel): File {
        return File(embeddingModelsDir, model.modelName)
    }
    
    suspend fun downloadModel(model: LocalEmbeddingModel) {
        // Set queued status BEFORE entering mutex - so UI shows "Queued..." immediately
        _models.update { map ->
            map.toMutableMap().apply {
                this[model] = LocalEmbeddingModelInfo(
                    model = model,
                    status = DownloadStatus.Queued
                )
            }
        }
        
        downloadMutex.withLock {
            // Now start actual download
            _models.update { map ->
                map.toMutableMap().apply {
                    this[model] = LocalEmbeddingModelInfo(
                        model = model,
                        status = DownloadStatus.Downloading(0)
                    )
                }
            }
            
            try {
                val file = File(embeddingModelsDir, model.modelName)
                
                // If file exists and valid, skip download
                if (file.exists() && isValidGGUFFile(file)) {
                    _models.update { map ->
                        map.toMutableMap().apply {
                            this[model] = LocalEmbeddingModelInfo(
                                model = model,
                                status = DownloadStatus.Downloaded,
                                filePath = file.absolutePath
                            )
                        }
                    }
                    return
                }
                
                // Delete partial/corrupt file
                if (file.exists()) {
                    file.delete()
                }
                
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(model.huggingFaceUrl)
                        .build()
                    
                    val response = okHttpClient.newCall(request).execute()
                    
                    if (!response.isSuccessful) {
                        throw Exception("Download failed: ${response.code}")
                    }
                    
                    val body = response.body ?: throw Exception("Empty response body")
                    val contentLength = body.contentLength()
                    
                    var lastProgressUpdate = System.currentTimeMillis()
                    var lastProgress = 0
                    
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                
                                if (contentLength > 0) {
                                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                    val now = System.currentTimeMillis()
                                    
                                    // Update every 500ms or every 1% progress
                                    if (now - lastProgressUpdate > 500 || progress > lastProgress) {
                                        lastProgressUpdate = now
                                        lastProgress = progress
                                        
                                        _models.update { map ->
                                            map.toMutableMap().apply {
                                                this[model] = LocalEmbeddingModelInfo(
                                                    model = model,
                                                    status = DownloadStatus.Downloading(progress)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Verify downloaded file
                    if (!isValidGGUFFile(file)) {
                        file.delete()
                        throw Exception("Downloaded file is not a valid GGUF model")
                    }
                    
                    _models.update { map ->
                        map.toMutableMap().apply {
                            this[model] = LocalEmbeddingModelInfo(
                                model = model,
                                status = DownloadStatus.Downloaded,
                                filePath = file.absolutePath
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalEmbeddingModelRepo", "Download failed for ${model.displayName}", e)
                _models.update { map ->
                    map.toMutableMap().apply {
                        this[model] = LocalEmbeddingModelInfo(
                            model = model,
                            status = DownloadStatus.Failed(e.message ?: "Unknown error")
                        )
                    }
                }
            }
        }
    }
    
    suspend fun deleteModel(model: LocalEmbeddingModel) {
        withContext(Dispatchers.IO) {
            val file = File(embeddingModelsDir, model.modelName)
            if (file.exists()) {
                file.delete()
            }
            
            _models.update { map ->
                map.toMutableMap().apply {
                    this[model] = LocalEmbeddingModelInfo(
                        model = model,
                        status = DownloadStatus.NotDownloaded
                    )
                }
            }
        }
    }
    
    fun isModelDownloaded(model: LocalEmbeddingModel): Boolean {
        val file = File(embeddingModelsDir, model.modelName)
        return file.exists() && isValidGGUFFile(file)
    }
}
