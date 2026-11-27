package ai.davoted.craving.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperiencePresentationMapperTest {
    private val mapper = ExperiencePresentationMapper()

    @Test
    fun pauseSuppressesAudioAndHaptics() {
        val result = mapper.map(
            ExperienceState(pacing = ExperiencePacing.PAUSE, intensity = 3)
        )

        assertEquals(AudioPreset.SILENCE, result.audio)
        assertEquals(HapticPattern.NONE, result.haptics)
    }

    @Test
    fun afterglowUsesWarmPresentation() {
        val result = mapper.map(
            ExperienceState(
                mood = ExperienceMood.AFTERGLOW,
                intensity = 1,
                phase = ScenarioPhase.AFTERGLOW
            )
        )

        assertEquals(VisualPreset.SOFT_AFTERGLOW, result.visual)
        assertEquals(AudioPreset.WARM_RELEASE, result.audio)
        assertEquals(HapticPattern.SINGLE_TAP, result.haptics)
    }

    @Test
    fun presentationEmphasisNeverExceedsSupportedRange() {
        val result = mapper.map(ExperienceState(intensity = 99))
        assertEquals(5, result.emphasis)
    }
}
