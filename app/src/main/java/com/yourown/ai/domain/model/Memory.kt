package com.yourown.ai.domain.model

import com.yourown.ai.domain.prompt.AIPrompts

/**
 * Deep Empathy analysis result
 */
data class DialogueFocus(
    val focusPoints: List<String>,
    val isStrongFocus: List<Boolean>
) {
    /**
     * Get the strongest focus point (if any)
     */
    fun getStrongestFocus(): String? {
        val strongIndex = isStrongFocus.indexOfFirst { it }
        return if (strongIndex != -1 && focusPoints.size > strongIndex) {
            focusPoints[strongIndex]
        } else {
            null
        }
    }
    
    /**
     * Check if there are any focus points
     */
    fun hasFocus(): Boolean = focusPoints.isNotEmpty()
}

/**
 * Memory entry extracted from conversation
 */
data class MemoryEntry(
    val id: String,
    val conversationId: String,
    val messageId: String,
    val fact: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
) {
    companion object {
        /**
         * Parse memory from AI response
         * Format: "Факт" or "Нет ключевой информации"
         */
        fun parseFromResponse(
            response: String,
            conversationId: String,
            messageId: String
        ): MemoryEntry? {
            val trimmed = response.trim()
            
            // Check for "no key info" response
            if (trimmed.equals("Нет ключевой информации", ignoreCase = true) ||
                trimmed.isEmpty()) {
                return null
            }
            
            return MemoryEntry(
                id = generateId(),
                conversationId = conversationId,
                messageId = messageId,
                fact = trimmed
            )
        }
        
        private fun generateId(): String {
            return "mem_${System.currentTimeMillis()}_${(0..999).random()}"
        }
    }
}

/**
 * Memory statistics
 */
data class MemoryStats(
    val totalMemories: Int
)
