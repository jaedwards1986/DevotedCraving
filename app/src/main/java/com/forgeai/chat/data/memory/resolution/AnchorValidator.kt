package com.forgeai.chat.data.memory.resolution

import kotlinx.coroutines.CancellationException

sealed interface CorrectionResolution {
    data class Resolved(
        val targetMemoryId: String,
        val expectedVersion: Long,
        val expectedPreviousValue: String,
        val confidence: Double,
        val evidence: String
    ) : CorrectionResolution

    data class Ambiguous(val candidateMemoryIds: List<String>) : CorrectionResolution
    data class Stale(val targetMemoryId: String, val actualValue: String) : CorrectionResolution
    data class Unresolved(val reason: String) : CorrectionResolution
    data class Invalid(val reason: String) : CorrectionResolution
}

/**
 * Obtains an authoritative snapshot and delegates all anchor checks and matching
 * to the pure CorrectionTargetResolver. Persistence remains the writer's responsibility.
 */
class AnchorValidator(
    private val contextProvider: CorrectionContextProvider,
    minimumConfidence: Double = 0.80,
    private val targetResolver: CorrectionTargetResolver =
        CorrectionTargetResolver(minimumConfidence)
) {
    suspend fun validate(input: AnchoredCorrection): CorrectionResolution {
        val anchor = input.anchor
        val context = try {
            contextProvider.getContext(anchor.conversationId, anchor.sourceMessageId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return CorrectionResolution.Unresolved("Correction context is unavailable.")
        }
        return targetResolver.resolve(input, context)
    }
}
