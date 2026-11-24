package com.forgeai.chat.data.memory.resolution

import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import java.util.Locale

sealed interface CorrectionResolution {
    data class Resolved(
        val targetMemoryId: String,
        val expectedVersion: Long,
        val confidence: Double,
        val evidence: String
    ) : CorrectionResolution

    data class Ambiguous(val candidateMemoryIds: List<String>) : CorrectionResolution
    data class Stale(val targetMemoryId: String, val actualValue: String) : CorrectionResolution
    data class Unresolved(val reason: String) : CorrectionResolution
    data class Invalid(val reason: String) : CorrectionResolution
}

/**
 * Validates a model-proposed correction anchor against an authoritative context snapshot.
 * It does not write or mutate memories. The writer must re-check target/version transactionally.
 */
class AnchorValidator(
    private val contextProvider: CorrectionContextProvider,
    private val minimumConfidence: Double = 0.80
) {
    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
    }

    suspend fun validate(input: AnchoredCorrection): CorrectionResolution {
        val candidate = input.candidate
        val anchor = input.anchor

        if (candidate.category != ExtractionCategory.CORRECTION) {
            return CorrectionResolution.Invalid("Only correction candidates can carry correction anchors.")
        }
        if (anchor.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return CorrectionResolution.Invalid("Unsupported contextual-anchor schema version.")
        }
        if (anchor.conversationId <= 0L || anchor.sourceMessageId <= 0L ||
            anchor.referencedMessageIds.isEmpty() ||
            anchor.referencedMessageIds.any { it <= 0L }
        ) {
            return CorrectionResolution.Invalid("Anchor contains invalid conversation or message identity.")
        }
        if (!anchor.confidence.isFinite() || anchor.confidence !in minimumConfidence..1.0) {
            return CorrectionResolution.Invalid("Anchor confidence is below the required threshold or invalid.")
        }
        if (anchor.evidence.isBlank()) {
            return CorrectionResolution.Invalid("Anchor evidence is required.")
        }
        if (anchor.relation !in setOf(AnchorRelation.DIRECT_CORRECTION, AnchorRelation.REPLACEMENT)) {
            return CorrectionResolution.Unresolved("Anchor relation is not eligible to change a memory.")
        }
        if (anchor.expectedPreviousValue.isNullOrBlank()) {
            return CorrectionResolution.Unresolved("Expected previous value is required for safe target matching.")
        }

        val context = try {
            contextProvider.getContext(anchor.conversationId, anchor.sourceMessageId)
        } catch (error: Exception) {
            return CorrectionResolution.Unresolved("Correction context is unavailable.")
        }

        if (context.conversationId != anchor.conversationId ||
            context.sourceMessageId != anchor.sourceMessageId
        ) {
            return CorrectionResolution.Invalid("Context provider returned a mismatched conversation or source message.")
        }
        if (anchor.referencedMessageIds.any { it !in context.availableMessageIds } ||
            anchor.sourceMessageId !in context.availableMessageIds
        ) {
            return CorrectionResolution.Invalid("Anchor references messages outside the supplied conversation context.")
        }

        val expected = normalize(anchor.expectedPreviousValue)
        val eligible = context.activeMemories.filter {
            it.active && it.conversationId == anchor.conversationId &&
                normalize(it.currentValue) == expected
        }

        val selected = if (anchor.targetMemoryId != null) {
            val target = context.activeMemories.firstOrNull { it.memoryId == anchor.targetMemoryId }
                ?: return CorrectionResolution.Unresolved("Proposed target memory does not exist in the supplied context.")
            if (target.conversationId != anchor.conversationId) {
                return CorrectionResolution.Invalid("Proposed target belongs to another conversation.")
            }
            if (!target.active) {
                return CorrectionResolution.Unresolved("Proposed target is not active.")
            }
            if (normalize(target.currentValue) != expected) {
                return CorrectionResolution.Stale(target.memoryId, target.currentValue)
            }
            target
        } else {
            when (eligible.size) {
                0 -> return CorrectionResolution.Unresolved("No active memory matches the expected previous value.")
                1 -> eligible.single()
                else -> return CorrectionResolution.Ambiguous(eligible.map { it.memoryId }.sorted())
            }
        }

        return CorrectionResolution.Resolved(
            targetMemoryId = selected.memoryId,
            expectedVersion = selected.version,
            confidence = anchor.confidence,
            evidence = anchor.evidence.trim()
        )
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
