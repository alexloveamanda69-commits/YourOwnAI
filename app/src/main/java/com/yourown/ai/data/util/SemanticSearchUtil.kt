package com.yourown.ai.data.util

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Semantic search utility with embedding similarity + keyword boost + exact match
 * 
 * Algorithm components:
 * 1. K-NN Vector Search - cosine similarity on embeddings
 * 2. Keyword Boost - +0.10 per matching token (max +0.25)
 * 3. Exact Match Boost - +0.15 for identical normalized strings
 */
object SemanticSearchUtil {
    
    /**
     * Search result with similarity score
     */
    data class SearchResult<T>(
        val item: T,
        val score: Float,
        val embeddingSimilarity: Float,
        val keywordBoost: Float,
        val exactMatchBoost: Float
    )
    
    /**
     * Find top K similar items using semantic search
     * 
     * @param query User query text
     * @param queryEmbedding Embedding of the query
     * @param items All items to search through
     * @param getText Function to extract searchable text from item
     * @param getEmbedding Function to get embedding for item
     * @param k Maximum number of results to return
     * @return List of top K results sorted by score (descending)
     */
    fun <T> findSimilar(
        query: String,
        queryEmbedding: FloatArray,
        items: List<T>,
        getText: (T) -> String,
        getEmbedding: (T) -> FloatArray?,
        k: Int = 5
    ): List<SearchResult<T>> {
        if (items.isEmpty() || queryEmbedding.isEmpty()) {
            return emptyList()
        }
        
        val normalizedQuery = normalizeText(query)
        val queryTokens = tokenize(normalizedQuery)
        
        // Calculate scores for all items
        val results = items.mapNotNull { item ->
            val itemText = getText(item)
            val itemEmbedding = getEmbedding(item) ?: return@mapNotNull null
            
            if (itemEmbedding.isEmpty()) return@mapNotNull null
            
            // 1. Embedding similarity (cosine)
            val embeddingSimilarity = cosineSimilarity(queryEmbedding, itemEmbedding)
            
            // 2. Keyword boost
            val normalizedItem = normalizeText(itemText)
            val itemTokens = tokenize(normalizedItem)
            val keywordBoost = calculateKeywordBoost(queryTokens, itemTokens)
            
            // 3. Exact match boost
            val exactMatchBoost = calculateExactMatchBoost(normalizedQuery, normalizedItem, queryTokens, itemTokens)
            
            // Final score (clamped to [0, 1])
            val finalScore = min(1.0f, embeddingSimilarity + keywordBoost + exactMatchBoost)
            
            SearchResult(
                item = item,
                score = finalScore,
                embeddingSimilarity = embeddingSimilarity,
                keywordBoost = keywordBoost,
                exactMatchBoost = exactMatchBoost
            )
        }
        
        // Sort by score (descending) and take top K
        return results
            .sortedByDescending { it.score }
            .take(k)
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     * Result is in range [0, 1] where 1 is most similar
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator == 0f) return 0f
        
        // Convert from [-1, 1] to [0, 1]
        val similarity = dotProduct / denominator
        return (similarity + 1f) / 2f
    }
    
    /**
     * Normalize text for comparison
     * - Lowercase
     * - Trim whitespace
     * - Remove extra spaces
     */
    private fun normalizeText(text: String): String {
        return text.lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
    }
    
    /**
     * Tokenize text into words
     * Simple split by whitespace and punctuation
     */
    private fun tokenize(text: String): Set<String> {
        return text
            .split(Regex("[\\s,;.!?()\\[\\]{}\"']+"))
            .filter { it.isNotBlank() && it.length >= 2 } // Filter short tokens
            .toSet()
    }
    
    /**
     * Calculate keyword boost based on token overlap
     * +0.10 per matching token (max +0.25)
     */
    private fun calculateKeywordBoost(queryTokens: Set<String>, itemTokens: Set<String>): Float {
        val matchingTokens = queryTokens.intersect(itemTokens).size
        val boost = matchingTokens * 0.10f
        return min(0.25f, boost)
    }
    
    /**
     * Calculate exact match boost
     * +0.15 if normalized strings are identical
     * +0.10 if all query tokens are in item
     */
    private fun calculateExactMatchBoost(
        normalizedQuery: String,
        normalizedItem: String,
        queryTokens: Set<String>,
        itemTokens: Set<String>
    ): Float {
        var boost = 0f
        
        // Exact string match
        if (normalizedQuery == normalizedItem) {
            boost += 0.15f
        }
        
        // All query tokens present in item
        if (queryTokens.isNotEmpty() && itemTokens.containsAll(queryTokens)) {
            boost += 0.10f
        }
        
        return boost
    }
}
