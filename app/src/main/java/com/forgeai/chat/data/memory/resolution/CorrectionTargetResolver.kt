package com.forgeai.chat.data.memory.resolution

import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import java.util.Locale

/**
 * Pure correction-target matching over an already captured context snapshot.
 * This class never reads or mutates persistence.
 */
class CorrectionTargetResolver(
    private val minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE
) {
    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0.0..1.0)
    }

    fun resolve(
        input: AnchoredCorrection,
        context: CorrectionContext
    ): CorrectionResolution {
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
        if (anchor.relation !in ELIGIBLE_RELATIONS) {
            return CorrectionResolution.Unresolved("Anchor relation is not eligible to change a memory.")
        }
        if (anchor.expectedPreviousValue.isNullOrBlank()) {
            return CorrectionResolution.Unresolved("Expected previous value is required for safe target matching.")
        }
        if (context.conversationId != anchor.conversationId ||
            context.sourceMessageId != anchor.sourceMessageId
        ) {
            return CorrectionResolution.Invalid("Context snapshot belongs to a different conversation or source message.")
        }
        if (anchor.referencedMessageIds.any { it !in context.availableMessageIds } ||
            anchor.sourceMessageId !in context.availableMessageIds
        ) {
            return CorrectionResolution.Invalid("Anchor references messages outside the supplied conversation context.")
        }

        val duplicateIds = context.activeMemories.groupingBy { it.memoryId }
            .eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) {
            return CorrectionResolution.Invalid("Context contains duplicate memory identities.")
        }

        val expected = normalize(anchor.expectedPreviousValue)
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
            val matches = context.activeMemories.filter {
                it.active && it.conversationId == anchor.conversationId &&
                    normalize(it.currentValue) == expected
            }
            when (matches.size) {
                0 -> return CorrectionResolution.Unresolved("No active memory matches the expected previous value.")
                1 -> matches.single()
                else -> return CorrectionResolution.Ambiguous(matches.map { it.memoryId }.sorted())
            }
        }

        return CorrectionResolution.Resolved(
            targetMemoryId = selected.memoryId,
            expectedVersion = selected.version,
            expectedPreviousValue = anchor.expectedPreviousValue.trim(),
            confidence = anchor.confidence,
            evidence = anchor.evidence.trim()
        )
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.80
        val ELIGIBLE_RELATIONS = setOf(
            AnchorRelation.DIRECT_CORRECTION,
            AnchorRelation.REPLACEMENT
        )
    }
}
