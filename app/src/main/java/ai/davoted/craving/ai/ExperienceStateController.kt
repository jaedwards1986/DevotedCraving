package ai.davoted.craving.ai

/**
 * Deterministic boundary between probabilistic model output and app-owned experience state.
 * Model output can never bypass these constraints.
 */
class ExperienceStateController(
    private val policy: ExperiencePolicy = ExperiencePolicy()
) {
    init {
        require(policy.minimumIntensity in 0..5)
        require(policy.maximumIntensity in policy.minimumIntensity..5)
        require(policy.maximumIntensityStep >= 0)
    }

    fun apply(
        current: ExperienceState,
        proposal: ExperienceSignalProposal
    ): ExperienceState {
        val requestedIntensity = proposal.intensity.coerceIn(
            policy.minimumIntensity,
            policy.maximumIntensity
        )
        val intensity = requestedIntensity.coerceIn(
            (current.intensity - policy.maximumIntensityStep).coerceAtLeast(policy.minimumIntensity),
            (current.intensity + policy.maximumIntensityStep).coerceAtMost(policy.maximumIntensity)
        )

        val phase = if (policy.allowPhaseRegression) {
            proposal.phase
        } else {
            val currentIndex = current.phase.ordinal
            val requestedIndex = proposal.phase.ordinal
            ScenarioPhase.entries[
                requestedIndex.coerceIn(currentIndex, (currentIndex + 1).coerceAtMost(ScenarioPhase.entries.lastIndex))
            ]
        }

        return ExperienceState(
            mood = proposal.mood,
            intensity = intensity,
            pacing = proposal.pacing,
            phase = phase
        )
    }
}
