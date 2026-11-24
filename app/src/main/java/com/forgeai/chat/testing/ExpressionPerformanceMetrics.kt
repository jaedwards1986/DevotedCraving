package com.forgeai.chat.testing

/**
 * Provenance attached to each generated token. The native decode loop can emit these records
 * without losing which sampler configuration produced a token.
 */
data class GeneratedTokenTrace(
    val tokenId: Int,
    val tokenIndex: Int,
    val configurationId: String,
    val sampler: SamplerConfig
) {
    init {
        require(tokenIndex >= 0) { "tokenIndex must be non-negative" }
        require(configurationId.isNotBlank()) { "configurationId must not be blank" }
        require(sampler.validate().isEmpty()) { "Invalid sampler configuration: ${sampler.validate()}" }
    }
}

object SamplerConfigurationIds {
    fun highVarianceMatrix(): List<Pair<String, SamplerConfig>> =
        InferenceTestFixtures.highVarianceMatrix().mapIndexed { index, config ->
            "HV-${index + 1}" to config
        }
}

/** A human-rated checkpoint for a known set of facts established earlier in the conversation. */
data class MemoryIntegritySample(
    val contextLoadFraction: Double,
    val checkedFactCount: Int,
    val contradictedFactCount: Int
) {
    init {
        require(contextLoadFraction in 0.0..1.0)
        require(checkedFactCount >= 0)
        require(contradictedFactCount in 0..checkedFactCount)
    }

    val contradictionRate: Double
        get() = if (checkedFactCount == 0) 0.0 else contradictedFactCount.toDouble() / checkedFactCount
}

data class MemoryDecaySummary(
    val initialContradictionRate: Double,
    val finalContradictionRate: Double,
    /** Increase in contradiction rate per 1.0 (100 percentage points) of context occupancy. */
    val decayRatePerFullContext: Double,
    val sampleCount: Int
)

object MemoryDecayScorer {
    fun summarize(samples: List<MemoryIntegritySample>): MemoryDecaySummary {
        require(samples.size >= 2) { "At least two context checkpoints are required" }
        val ordered = samples.sortedBy { it.contextLoadFraction }
        require(ordered.zipWithNext().all { (a, b) -> b.contextLoadFraction > a.contextLoadFraction }) {
            "Context load fractions must be unique"
        }
        val first = ordered.first()
        val last = ordered.last()
        val span = last.contextLoadFraction - first.contextLoadFraction
        return MemoryDecaySummary(
            initialContradictionRate = first.contradictionRate,
            finalContradictionRate = last.contradictionRate,
            decayRatePerFullContext = (last.contradictionRate - first.contradictionRate) / span,
            sampleCount = ordered.size
        )
    }
}
