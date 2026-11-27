package ai.davoted.craving.ai

import kotlinx.serialization.Serializable

/**
 * Structured state proposed by the local model. These are suggestions, not device commands.
 * The deterministic controller validates every field before it reaches presentation systems.
 */
@Serializable
data class ExperienceSignalProposal(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val mood: ExperienceMood = ExperienceMood.CALM,
    val intensity: Int = 0,
    val pacing: ExperiencePacing = ExperiencePacing.STEADY,
    val phase: ScenarioPhase = ScenarioPhase.OPENING
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
enum class ExperienceMood {
    CALM,
    PLAYFUL,
    TEASING,
    CONFIDENT,
    INTENSE,
    TENDER,
    AFTERGLOW
}

@Serializable
enum class ExperiencePacing {
    SLOW,
    STEADY,
    BRISK,
    PAUSE
}

@Serializable
enum class ScenarioPhase {
    OPENING,
    BUILD,
    TURNING_POINT,
    RESOLUTION,
    AFTERGLOW
}

/** Validated app-owned state consumed by UI and, later, sensory controllers. */
data class ExperienceState(
    val mood: ExperienceMood = ExperienceMood.CALM,
    val intensity: Int = 0,
    val pacing: ExperiencePacing = ExperiencePacing.STEADY,
    val phase: ScenarioPhase = ScenarioPhase.OPENING
)

data class ExperiencePolicy(
    val minimumIntensity: Int = 0,
    val maximumIntensity: Int = 5,
    val maximumIntensityStep: Int = 1,
    val allowPhaseRegression: Boolean = false
)
