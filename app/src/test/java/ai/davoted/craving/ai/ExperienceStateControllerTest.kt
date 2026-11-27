package ai.davoted.craving.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperienceStateControllerTest {
    @Test
    fun clampsIntensityAndLimitsPerTurnChange() {
        val controller = ExperienceStateController(
            ExperiencePolicy(maximumIntensity = 4, maximumIntensityStep = 1)
        )
        val current = ExperienceState(intensity = 2)
        val proposal = ExperienceSignalProposal(intensity = 99)

        val result = controller.apply(current, proposal)

        assertEquals(3, result.intensity)
    }

    @Test
    fun doesNotAllowScenarioRegressionOrSkipping() {
        val controller = ExperienceStateController()
        val current = ExperienceState(phase = ScenarioPhase.BUILD)

        val regression = controller.apply(current, ExperienceSignalProposal(phase = ScenarioPhase.OPENING))
        val skip = controller.apply(current, ExperienceSignalProposal(phase = ScenarioPhase.AFTERGLOW))

        assertEquals(ScenarioPhase.BUILD, regression.phase)
        assertEquals(ScenarioPhase.TURNING_POINT, skip.phase)
    }

    @Test
    fun permitsOneStepForwardAndMoodChanges() {
        val controller = ExperienceStateController()
        val current = ExperienceState(phase = ScenarioPhase.TURNING_POINT)

        val result = controller.apply(
            current,
            ExperienceSignalProposal(
                mood = ExperienceMood.TENDER,
                phase = ScenarioPhase.RESOLUTION,
                intensity = 1
            )
        )

        assertEquals(ScenarioPhase.RESOLUTION, result.phase)
        assertEquals(ExperienceMood.TENDER, result.mood)
        assertEquals(1, result.intensity)
    }
}
