package com.forgeai.chat.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NatashaSettingsCommandParserTest {
    @Test
    fun callMeCommandUpdatesAddressingPreference() {
        val result = NatashaSettingsCommandParser.apply("Call me Studly", "Natasha identity")
        requireNotNull(result)
        assertEquals("Got it. I'll call you Studly.", result.confirmation)
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
    fun styleCommandReplacesPreviousStyleInsteadOfAccumulatingConflicts() {
        val first = requireNotNull(NatashaSettingsCommandParser.apply("Make yourself more playful", "Natasha identity"))
        val second = requireNotNull(NatashaSettingsCommandParser.apply("Make yourself more explicit and direct", first.updatedSystemPrompt))
        assert(!second.updatedSystemPrompt.contains("more playful"))
        assert(second.updatedSystemPrompt.contains("more explicit and direct"))
    }

    @Test
    fun stylePreferenceCanBeCleared() {
        val first = requireNotNull(NatashaSettingsCommandParser.apply("Be more playful", "Natasha identity"))
        val cleared = requireNotNull(NatashaSettingsCommandParser.apply("Clear my style preference", first.updatedSystemPrompt))
        assert(!cleared.updatedSystemPrompt.contains("User-requested style preference:"))
        assert(cleared.updatedSystemPrompt.contains("Natasha identity"))
    }

    @Test
    fun ordinaryMessageIsNotTreatedAsASettingCommand() {
        assertNull(NatashaSettingsCommandParser.apply("What do you think about this?", "Natasha identity"))
    }
}
