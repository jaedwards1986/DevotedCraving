package ai.davoted.craving.ai

enum class VisualPreset {
    QUIET_OBSIDIAN,
    BURGUNDY_SHIMMER,
    WARM_AMBER,
    DEEP_RED_PULSE,
    SOFT_AFTERGLOW
}

enum class AudioPreset {
    SILENCE,
    LOW_AMBIENCE,
    SLOW_RHYTHM,
    TENSION_AMBIENCE,
    WARM_RELEASE
}

enum class HapticPattern {
    NONE,
    SINGLE_TAP,
    SLOW_PULSE,
    DOUBLE_PULSE,
    SOFT_HEARTBEAT
}

/**
 * Safe, declarative presentation cues. These are identifiers, not raw hardware parameters.
 * Device-specific implementations can later map them to Compose animation, audio and vibrator APIs.
 */
data class ExperiencePresentation(
    val visual: VisualPreset,
    val audio: AudioPreset,
    val haptics: HapticPattern,
    val emphasis: Int
)

class ExperiencePresentationMapper {
    fun map(state: ExperienceState): ExperiencePresentation {
        val visual = when {
            state.phase == ScenarioPhase.AFTERGLOW -> VisualPreset.SOFT_AFTERGLOW
            state.mood == ExperienceMood.TENDER -> VisualPreset.WARM_AMBER
            state.mood == ExperienceMood.TEASING -> VisualPreset.BURGUNDY_SHIMMER
            state.intensity >= 4 -> VisualPreset.DEEP_RED_PULSE
            else -> VisualPreset.QUIET_OBSIDIAN
        }

        val audio = when {
            state.pacing == ExperiencePacing.PAUSE -> AudioPreset.SILENCE
            state.phase == ScenarioPhase.AFTERGLOW -> AudioPreset.WARM_RELEASE
            state.mood == ExperienceMood.TEASING -> AudioPreset.TENSION_AMBIENCE
            state.intensity >= 3 -> AudioPreset.SLOW_RHYTHM
            else -> AudioPreset.LOW_AMBIENCE
        }

        val haptics = when {
            state.pacing == ExperiencePacing.PAUSE || state.intensity == 0 -> HapticPattern.NONE
            state.phase == ScenarioPhase.AFTERGLOW -> HapticPattern.SINGLE_TAP
            state.intensity >= 4 -> HapticPattern.SOFT_HEARTBEAT
            state.intensity >= 2 -> HapticPattern.SLOW_PULSE
            else -> HapticPattern.DOUBLE_PULSE
        }

        return ExperiencePresentation(
            visual = visual,
            audio = audio,
            haptics = haptics,
            emphasis = state.intensity.coerceIn(0, 5)
        )
    }
}
