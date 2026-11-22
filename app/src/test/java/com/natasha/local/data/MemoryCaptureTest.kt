package com.natasha.local.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCaptureTest {
    @Test fun capturesExplicitPreferences() {
        assertEquals(listOf("I prefer concise answers"), MemoryCapture.extract("I prefer concise answers."))
    }

    @Test fun capturesExplicitRememberRequests() {
        assertEquals(listOf("Remember that my app is called ForgeAI"), MemoryCapture.extract("Remember that my app is called ForgeAI."))
    }

    @Test fun ignoresSensitiveStatements() {
        assertTrue(MemoryCapture.extract("I take medication for my diagnosis.").isEmpty())
    }

    @Test fun ignoresOrdinaryChatWithoutMemoryCue() {
        assertTrue(MemoryCapture.extract("That was a funny movie.").isEmpty())
    }
}