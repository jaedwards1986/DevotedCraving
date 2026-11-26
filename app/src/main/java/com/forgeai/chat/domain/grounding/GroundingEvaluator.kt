package com.forgeai.chat.domain.grounding

/**
 * Deterministic evidence-disposition evaluator.
 *
 * The caller supplies claim-to-evidence links. A missing match is insufficient
 * unless the evidence provider explicitly attests that its search was exhaustive.
 */
class GroundingEvaluator {
    fun evaluate(
        extraction: ClaimExtractionResult,
        context: GroundingContext
    ): GroundingEvaluation {
        require(extraction.claims.map { it.claimId }.distinct().size == extraction.claims.size) {
            "Claim IDs must be unique within an extraction result."
        }

        val verifiedEvidence = context.evidence.filter {
            it.verification == EvidenceVerification.VERIFIED
        }
        val supports = extraction.claims.map { claim ->
            if (claim.type == ClaimType.FICTION) {
                ClaimSupport(
                    claimId = claim.claimId,
                    disposition = GroundingDisposition.FICTION,
                    confidence = 1.0,
                    evidenceIds = emptyList(),
                    contradictionIds = emptyList()
                )
            } else if (!claim.verifiable) {
                ClaimSupport(
                    claimId = claim.claimId,
                    disposition = GroundingDisposition.INSUFFICIENT_EVIDENCE,
                    confidence = 0.5,
                    evidenceIds = emptyList(),
                    contradictionIds = emptyList()
                )
            } else {
                val matching = verifiedEvidence.filter { it.claimId == claim.claimId }
                val supporting = matching.filter { it.relation == EvidenceRelation.SUPPORTS }
                val contradicting = matching.filter { it.relation == EvidenceRelation.CONTRADICTS }
                val disposition = when {
                    contradicting.isNotEmpty() -> GroundingDisposition.CONTRADICTED
                    supporting.isNotEmpty() -> GroundingDisposition.SUPPORTED
                    context.evidenceSearchExhaustive -> GroundingDisposition.UNSUPPORTED
                    else -> GroundingDisposition.INSUFFICIENT_EVIDENCE
                }
                ClaimSupport(
                    claimId = claim.claimId,
                    disposition = disposition,
                    confidence = when (disposition) {
                        GroundingDisposition.SUPPORTED, GroundingDisposition.CONTRADICTED -> 1.0
                        GroundingDisposition.UNSUPPORTED -> 0.75
                        GroundingDisposition.INSUFFICIENT_EVIDENCE -> 0.5
                        GroundingDisposition.FICTION -> 1.0
                    },
                    evidenceIds = supporting.map { it.evidenceId },
                    contradictionIds = contradicting.map { it.evidenceId }
                )
            }
        }

        val dispositionMap = supports.associate { it.claimId to it.disposition }
        val verifiable = extraction.claims.filter {
            it.verifiable && it.type != ClaimType.FICTION
        }
        val supportById = supports.associateBy { it.claimId }
        val weightedPenalty = verifiable.sumOf { claim ->
            val penalty = when (supportById.getValue(claim.claimId).disposition) {
                GroundingDisposition.SUPPORTED -> 0.0
                GroundingDisposition.INSUFFICIENT_EVIDENCE -> 0.5
                GroundingDisposition.UNSUPPORTED -> 1.0
                GroundingDisposition.CONTRADICTED -> 1.5
                GroundingDisposition.FICTION -> 0.0
            }
            claim.weight * penalty
        }
        val totalWeight = verifiable.sumOf { it.weight }
        val ucf = if (totalWeight > 0.0) {
            (weightedPenalty / totalWeight).coerceIn(0.0, 1.0)
        } else null

        return GroundingEvaluation(
            interactionId = context.interactionId,
            claims = extraction.claims,
            supports = supports,
            dispositionByClaim = dispositionMap,
            supportedClaims = supports.count { it.disposition == GroundingDisposition.SUPPORTED },
            verifiableClaims = verifiable.size,
            unsupportedClaims = supports.count { it.disposition == GroundingDisposition.UNSUPPORTED },
            contradictedClaims = supports.count { it.disposition == GroundingDisposition.CONTRADICTED },
            unsubstantiatedClaimFactor = ucf,
            evidenceReferences = supports.flatMap { it.evidenceIds + it.contradictionIds }.distinct()
        )
    }
}
