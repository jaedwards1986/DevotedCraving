package com.forgeai.chat.data.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentAwareFidelityTest {

    @Test
    fun utilityAndTechnicalRequestsNeverReceiveConfiguredRoleplayIntensity() {
        val weather = IntentAwareFidelity.assess("What's the weather today?", configuredIntensity = 5)
        val coding = IntentAwareFidelity.assess("Help me debug this Kotlin build error", configuredIntensity = 4)

        assertEquals(InteractionIntent.UTILITY, weather.intent)
        assertEquals(0, weather.appliedIntensity)
        assertEquals(InteractionIntent.TECHNICAL, coding.intent)
        assertEquals(0, coding.appliedIntensity)
        assertTrue(weather.directive.contains("do not introduce flirtation"))
    }

    @Test
    fun explicitRoleplayUsesConfiguredIntensity() {
        val decision = IntentAwareFidelity.assess(
            "Let's continue our erotic roleplay scene",
            configuredIntensity = 4
        )

        assertEquals(InteractionIntent.INTIMATE_ROLEPLAY, decision.intent)
        assertEquals(4, decision.appliedIntensity)
        assertTrue(decision.directive.contains("4 of 5"))
    }

    @Test
    fun intensityIsClampedAndDoesNotApplyToGeneralConversation() {
        val high = IntentAwareFidelity.assess("Hello, how are you?", configuredIntensity = 99)
        val low = IntentAwareFidelity.assess("Continue the scene", configuredIntensity = -2)

        assertEquals(5, high.configuredIntensity)
        assertEquals(0, high.appliedIntensity)
        assertEquals(InteractionIntent.ROLEPLAY, low.intent)
        assertEquals(0, low.configuredIntensity)
        assertEquals(0, low.appliedIntensity)
    }

    @Test
    fun creativeWritingIsNotAssumedToBeRoleplay() {
        val decision = IntentAwareFidelity.assess(
            "Write a story about a haunted lighthouse",
            configuredIntensity = 5
        )

        assertEquals(InteractionIntent.CREATIVE, decision.intent)
        assertEquals(0, decision.appliedIntensity)
    }

    @Test
    fun decisionsAreDeterministic() {
        val first = IntentAwareFidelity.assess("Flirt with me", configuredIntensity = 3)
        val second = IntentAwareFidelity.assess("Flirt with me", configuredIntensity = 3)

        assertEquals(first, second)
    }
}
