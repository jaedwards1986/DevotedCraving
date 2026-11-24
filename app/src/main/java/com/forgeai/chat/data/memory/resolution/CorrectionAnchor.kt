package com.forgeai.chat.data.memory.resolution

import com.forgeai.chat.data.memory.extraction.ExtractionCandidate

data class ContextualAnchor(
    val schemaVersion: Int = 1,
    val targetMemoryId: String? = null,
    val conversationId: Long,
    val sourceMessageId: Long,
    val referencedMessageIds: List<Long>,
    val relation: AnchorRelation,
    val expectedPreviousValue: String?,
    val confidence: Double,
    val evidence: String
)

enum class AnchorRelation {
    DIRECT_CORRECTION,
    CLARIFICATION,
    REPLACEMENT,
    AMBIGUOUS,
    UNKNOWN
}

data class AnchoredCorrection(
    val candidate: ExtractionCandidate,
    val anchor: ContextualAnchor
)
