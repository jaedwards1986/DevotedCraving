package com.forgeai.chat.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NatashaSettingsCommandParserTest {
    @Test
    fun callMeCommandUpdatesAddressingPreference() {
        val result = NatashaSettingsCommandParser.apply("Call me Studly", "Natasha identity")
        requireNotNull(result)
        assertEquals("I'll address you as Studly.", result.confirmation)
        assert(result.updatedSystemPrompt.contains("Address the user as \"Studly\""))
    }

    @Test
    fun styleCommandAppendsPreferenceWithoutReplacingIdentity() {
        val result = NatashaSettingsCommandParser.apply("Make yourself more playful", "Natasha identity")
        requireNotNull(result)
        assert(result.updatedSystemPrompt.startsWith("Natasha identity"))
        assert(result.updatedSystemPrompt.contains("more playful"))
    }

    @Test
    fun ordinaryMessageIsNotTreatedAsASettingCommand() {
        assertNull(NatashaSettingsCommandParser.apply("What do you think about this?", "Natasha identity"))
    }
}
