package com.forgeai.chat.domain.grounding

/** A factual assertion extracted from one generated response. */
data class FactualClaim(
    val claimId: String,
    val text: String,
    val type: ClaimType = ClaimType.OTHER,
    val verifiable: Boolean = true,
    val weight: Double = 1.0
) {
    init {
        require(claimId.isNotBlank())
        require(text.isNotBlank())
        require(weight.isFinite() && weight > 0.0)
    }
}

enum class ClaimType { BIOGRAPHICAL, HISTORICAL, SOURCE_ATTRIBUTION, TEMPORAL, RELATIONAL, OTHER, FICTION }
enum class GroundingDisposition { SUPPORTED, UNSUPPORTED, CONTRADICTED, INSUFFICIENT_EVIDENCE, FICTION }
enum class EvidenceRelation { SUPPORTS, CONTRADICTS }
enum class EvidenceVerification { VERIFIED, UNVERIFIED, DISPUTED, EXPIRED }

/**
 * Evidence is linked to a claim explicitly by claimId. This first version
 * intentionally does not pretend to perform semantic entailment.
 */
data class GroundingEvidence(
    val evidenceId: String,
    val claimId: String,
    val relation: EvidenceRelation,
    val verification: EvidenceVerification,
    val sourceId: String? = null,
    val sourceUrl: String? = null
) {
    init {
        require(evidenceId.isNotBlank())
        require(claimId.isNotBlank())
    }
}

data class GroundingContext(
    val interactionId: String,
    val evidence: List<GroundingEvidence>,
    /** True only when the evidence provider confirms the relevant corpus was exhaustively checked. */
    val evidenceSearchExhaustive: Boolean = false
) {
    init { require(interactionId.isNotBlank()) }
}

data class ClaimSupport(
    val claimId: String,
    val disposition: GroundingDisposition,
    val confidence: Double,
    val evidenceIds: List<String>,
    val contradictionIds: List<String>
)

data class ClaimExtractionResult(
    val claims: List<FactualClaim>,
    val extractionMethod: ExtractionMethod = ExtractionMethod.RULE_BASED,
    val confidence: Double = 1.0,
    val warnings: List<String> = emptyList()
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

enum class ExtractionMethod { RULE_BASED, LLM_AUGMENTED, HYBRID }

data class GroundingEvaluation(
    val interactionId: String,
    val claims: List<FactualClaim>,
    val supports: List<ClaimSupport>,
    val dispositionByClaim: Map<String, GroundingDisposition>,
    val supportedClaims: Int,
    val verifiableClaims: Int,
    val unsupportedClaims: Int,
    val contradictedClaims: Int,
    /** Null when there are no verifiable claims; never manufacture a perfect zero. */
    val unsubstantiatedClaimFactor: Double?,
    val evidenceReferences: List<String>
)
