package com.forgeai.chat.domain.settings

import org.junit.Assert.*
import org.junit.Test

class NatashaIntentEngineTest {
    @Test fun moreAssertiveProducesStructuredIncrease() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Make yourself more assertive"))
        assertEquals(NatashaIntent.ALTER_PERSONALITY, parsed.intent)
        assertEquals(NatashaSettingDimension.ASSERTIVENESS, parsed.target)
        assertEquals(NatashaSettingOperation.INCREASE, parsed.operation)
        assertEquals(0.6, parsed.proposedValue!!, 0.0001)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test fun decreaseUsesCurrentValueAndClampsToMinimum() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Be less affectionate", mapOf(NatashaSettingDimension.AFFECTION to 0.15)))
        assertEquals(0.1, parsed.proposedValue!!, 0.0001)
        assertEquals(-0.05, parsed.delta!!, 0.0001)
    }

    @Test fun numericSetProducesBoundedValue() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Set dominance to 0.65"))
        assertEquals(NatashaSettingDimension.DOMINANCE, parsed.target)
        assertEquals(NatashaSettingOperation.SET, parsed.operation)
        assertEquals(0.65, parsed.proposedValue!!, 0.0001)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test fun outOfRangeNumericSetRequiresConfirmation() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Set dominance to 0.9"))
        assertEquals(0.7, parsed.proposedValue!!, 0.0001)
        assertTrue(parsed.requiresConfirmation)
        assertNotNull(parsed.clarification)
    }

    @Test fun ambiguousIntensityAsksWhichDimension() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Make yourself more intense"))
        assertNull(parsed.target)
        assertTrue(parsed.requiresConfirmation)
    }

    @Test fun resetReturnsDimensionDefault() {
        val parsed = requireNotNull(NatashaIntentEngine.parse("Reset my playfulness setting", mapOf(NatashaSettingDimension.PLAYFULNESS to 0.9)))
        assertEquals(NatashaIntent.RESET_SETTING, parsed.intent)
        assertEquals(0.6, parsed.proposedValue!!, 0.0001)
    }

    @Test fun ordinaryConversationIsNotASettingCommand() {
        assertNull(NatashaIntentEngine.parse("I think you have been more assertive lately"))
        assertNull(NatashaIntentEngine.parse("What does dominance mean?"))
    }

    @Test fun outputCanBeSerializedAsStructuredJson() {
        val json = requireNotNull(NatashaIntentEngine.parse("Increase your playfulness")).toJson()
        assertTrue(json.contains("\"intent\":\"ALTER_PERSONALITY\""))
        assertTrue(json.contains("\"target\":\"playfulness\""))
        assertTrue(json.contains("\"operation\":\"INCREASE\""))
    }
}
