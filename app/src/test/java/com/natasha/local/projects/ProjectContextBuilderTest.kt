package com.natasha.local.projects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextBuilderTest {
    @Test fun excludesCredentialFilesAndGeneratedDirectories() {
        assertFalse(ProjectContextBuilder.isEligiblePath(".env"))
        assertFalse(ProjectContextBuilder.isEligiblePath("config/api-token.json"))
        assertFalse(ProjectContextBuilder.isEligiblePath("app/release-key.jks"))
        assertFalse(ProjectContextBuilder.isEligiblePath("build/generated/BuildConfig.kt"))
        assertTrue(ProjectContextBuilder.isEligiblePath("app/src/MainActivity.kt"))
        assertTrue(ProjectContextBuilder.isEligiblePath("README.md"))
    }

    @Test fun buildsBoundedContextAndIncludesProjectBrief() {
        val files = listOf(
            ProjectContextFile("README.md", "Project overview"),
            ProjectContextFile(".env", "API_KEY=do-not-include"),
            ProjectContextFile("src/Main.kt", "x".repeat(10_000))
        )
        val context = ProjectContextBuilder.build("ForgeAI", "Local app project", files, 1_000)
        assertTrue(context.contains("ACTIVE PROJECT: ForgeAI"))
        assertTrue(context.contains("PROJECT BRIEF: Local app project"))
        assertTrue(context.contains("README.md"))
        assertFalse(context.contains("API_KEY"))
        assertTrue(context.length <= 1_000)
    }

    @Test fun redactsCredentialAssignmentsJsonValuesAndRecognizableTokens() {
        val source = """
            storePassword=hunter2
            {"apiKey":"sample-secret-value","mode":"debug"}
            ghp_abcdefghijklmnopqrstuvwxyz1234567890
        """.trimIndent()
        val safe = ProjectContextBuilder.redactSecrets(source)
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("sample-secret-value"))
        assertFalse(safe.contains("ghp_abcdefghijklmnopqrstuvwxyz1234567890"))
        assertTrue(safe.contains("[REDACTED]"))
        assertTrue(safe.contains("[REDACTED TOKEN]"))
    }

    @Test fun deduplicatesPathsAndCapsFileCount() {
        val files = (1..20).map { ProjectContextFile("src/File$it.kt", "content$it") } +
            ProjectContextFile("src/File1.kt", "duplicate")
        val context = ProjectContextBuilder.build("Demo", "", files)
        assertTrue(context.contains("content12"))
        assertFalse(context.contains("content13"))
        assertFalse(context.contains("duplicate"))
    }
}
