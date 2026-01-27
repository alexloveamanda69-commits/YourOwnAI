package com.yourown.ai.domain.model

/**
 * Local embedding models for semantic search and RAG
 */
enum class LocalEmbeddingModel(
    val displayName: String,
    val modelName: String,
    val sizeInMB: Int,
    val dimensions: Int, // Vector size
    val huggingFaceUrl: String,
    val description: String
) {
    ALL_MINILM_L6_V2(
        displayName = "all-MiniLM-L6-v2",
        modelName = "all-MiniLM-L6-v2.Q4_K_M.gguf",
        sizeInMB = 21,
        dimensions = 384,
        huggingFaceUrl = "https://huggingface.co/leliuga/all-MiniLM-L6-v2-GGUF/resolve/main/all-MiniLM-L6-v2.Q4_K_M.gguf",
        description = "Fast and compact embedder (Q4 quantized)"
    ),
    MXBAI_EMBED_LARGE(
        displayName = "mxbai-embed-large",
        modelName = "mxbai-embed-large-v1-f16.gguf",
        sizeInMB = 670,
        dimensions = 1024,
        huggingFaceUrl = "https://huggingface.co/mixedbread-ai/mxbai-embed-large-v1/resolve/main/gguf/mxbai-embed-large-v1-f16.gguf",
        description = "Higher quality embedder for accurate search"
    );
    
    fun getSizeFormatted(): String {
        return if (sizeInMB >= 1000) {
            "%.1f GB".format(sizeInMB / 1024f)
        } else {
            "$sizeInMB MB"
        }
    }
}

/**
 * Embedding model info with download status
 */
data class LocalEmbeddingModelInfo(
    val model: LocalEmbeddingModel,
    val status: DownloadStatus = DownloadStatus.NotDownloaded,
    val filePath: String? = null
)
