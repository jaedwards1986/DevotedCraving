package com.forgeai.chat.domain.grounding

import org.junit.Assert.*
import org.junit.Test

class GroundingEvaluatorTest {
    private val evaluator = GroundingEvaluator()

    @Test fun verifiedSupportingEvidenceProducesSupportedAndZeroUcf() {
        val result = evaluate(
            claims = listOf(claim("a")),
            evidence = listOf(evidence("e1", "a", EvidenceRelation.SUPPORTS))
        )
        assertEquals(GroundingDisposition.SUPPORTED, result.dispositionByClaim["a"])
        assertEquals(0.0, result.unsubstantiatedClaimFactor!!, 0.0)
        assertEquals(listOf("e1"), result.evidenceReferences)
    }

    @Test fun missingEvidenceIsInsufficientUnlessSearchWasExhaustive() {
        val incomplete = evaluate(claims = listOf(claim("a")))
        assertEquals(GroundingDisposition.INSUFFICIENT_EVIDENCE, incomplete.dispositionByClaim["a"])
        assertEquals(0.5, incomplete.unsubstantiatedClaimFactor!!, 0.0)

        val exhaustive = evaluator.evaluate(
            ClaimExtractionResult(listOf(claim("a"))),
            GroundingContext("test", emptyList(), evidenceSearchExhaustive = true)
        )
        assertEquals(GroundingDisposition.UNSUPPORTED, exhaustive.dispositionByClaim["a"])
        assertEquals(1.0, exhaustive.unsubstantiatedClaimFactor!!, 0.0)
    }

    @Test fun verifiedContradictionProducesContradictedAndClampedUcf() {
        val result = evaluate(
            claims = listOf(claim("a")),
            evidence = listOf(evidence("e1", "a", EvidenceRelation.CONTRADICTS))
        )
        assertEquals(GroundingDisposition.CONTRADICTED, result.dispositionByClaim["a"])
        assertEquals(1.0, result.unsubstantiatedClaimFactor!!, 0.0)
        assertEquals(1, result.contradictedClaims)
    }

    @Test fun unverifiedEvidenceCannotSupportOrContradictClaim() {
        val result = evaluate(
            claims = listOf(claim("a")),
            evidence = listOf(evidence("e1", "a", EvidenceRelation.SUPPORTS,
                EvidenceVerification.UNVERIFIED))
        )
        assertEquals(GroundingDisposition.INSUFFICIENT_EVIDENCE, result.dispositionByClaim["a"])
    }

    @Test fun explicitlyFictionalClaimsAreExcludedFromUcf() {
        val result = evaluate(claims = listOf(claim("fiction", ClaimType.FICTION)))
        assertEquals(GroundingDisposition.FICTION, result.dispositionByClaim["fiction"])
        assertEquals(0, result.verifiableClaims)
        assertNull(result.unsubstantiatedClaimFactor)
    }

    @Test fun nonVerifiableNonFictionClaimIsNotMislabelledAsFiction() {
        val result = evaluate(claims = listOf(FactualClaim(
            claimId = "opinion",
            text = "A subjective preference",
            verifiable = false
        )))
        assertEquals(GroundingDisposition.INSUFFICIENT_EVIDENCE, result.dispositionByClaim["opinion"])
        assertEquals(0, result.verifiableClaims)
        assertNull(result.unsubstantiatedClaimFactor)
    }

    @Test fun noClaimsProducesNullUcfRatherThanPerfectGrounding() {
        val result = evaluate(claims = emptyList())
        assertTrue(result.supports.isEmpty())
        assertEquals(0, result.verifiableClaims)
        assertNull(result.unsubstantiatedClaimFactor)
    }

    @Test fun weightedUcfIsDeterministicAcrossMultipleClaims() {
        val result = evaluate(
            claims = listOf(claim("supported", weight = 1.0), claim("unknown", weight = 3.0)),
            evidence = listOf(evidence("e1", "supported", EvidenceRelation.SUPPORTS))
        )
        assertEquals(GroundingDisposition.SUPPORTED, result.dispositionByClaim["supported"])
        assertEquals(GroundingDisposition.INSUFFICIENT_EVIDENCE, result.dispositionByClaim["unknown"])
        assertEquals(0.375, result.unsubstantiatedClaimFactor!!, 0.0)
    }

    @Test fun duplicateClaimIdsAreRejected() {
        val extraction = ClaimExtractionResult(listOf(claim("a"), claim("a")))
        try {
            evaluator.evaluate(extraction, GroundingContext("test", emptyList()))
            fail("Expected duplicate claim IDs to be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    private fun evaluate(
        claims: List<FactualClaim>,
        evidence: List<GroundingEvidence> = emptyList()
    ) = evaluator.evaluate(
        ClaimExtractionResult(claims),
        GroundingContext("test-interaction", evidence)
    )

    private fun claim(id: String, type: ClaimType = ClaimType.OTHER, weight: Double = 1.0) =
        FactualClaim(id, "Synthetic claim $id", type = type, weight = weight)

    private fun evidence(
        id: String,
        claimId: String,
        relation: EvidenceRelation,
        verification: EvidenceVerification = EvidenceVerification.VERIFIED
    ) = GroundingEvidence(id, claimId, relation, verification)
}
