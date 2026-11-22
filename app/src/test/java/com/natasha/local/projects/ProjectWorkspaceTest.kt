package com.natasha.local.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectWorkspaceTest {

    @Test
    fun writeReadAndListStayInsideWorkspace() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("natasha-workspace").toFile())

        workspace.write("src/Main.kt", "fun main() = Unit")

        assertEquals("fun main() = Unit", workspace.read("src/Main.kt"))
        assertEquals(listOf("src/Main.kt"), workspace.list())
    }

    @Test
    fun createRefusesToOverwriteAnExistingFile() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("forgeai-create").toFile())
        workspace.create("src/Main.kt", "original")
        assertThrows(IllegalArgumentException::class.java) {
            workspace.create("src/Main.kt", "replacement")
        }
        assertEquals("original", workspace.read("src/Main.kt"))
    }

    @Test
    fun traversalOutsideWorkspaceIsRejected() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("natasha-workspace").toFile())

        assertThrows(IllegalArgumentException::class.java) {
            workspace.write("../outside.txt", "blocked")
        }
    }

    @Test
    fun absolutePathOutsideWorkspaceIsRejected() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("natasha-workspace").toFile())
        val outside = File(Files.createTempDirectory("natasha-outside").toFile(), "outside.txt").absolutePath

        assertThrows(IllegalArgumentException::class.java) {
            workspace.write(outside, "blocked")
        }
    }
    @Test
    fun renameMovesFileAndRefusesToOverwrite() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("forgeai-workspace").toFile())
        workspace.write("src/Old.kt", "old")
        workspace.write("src/Existing.kt", "keep")

        assertThrows(IllegalArgumentException::class.java) {
            workspace.rename("src/Old.kt", "src/Existing.kt")
        }
        assertEquals("old", workspace.read("src/Old.kt"))
        assertEquals("keep", workspace.read("src/Existing.kt"))

        workspace.rename("src/Old.kt", "archive/Old.kt")
        assertEquals("old", workspace.read("archive/Old.kt"))
        assertThrows(IllegalArgumentException::class.java) {
            workspace.read("src/Old.kt")
        }
    }

    @Test
    fun renameRejectsDestinationOutsideWorkspace() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("forgeai-workspace").toFile())
        workspace.write("inside.txt", "safe")
        assertThrows(IllegalArgumentException::class.java) {
            workspace.rename("inside.txt", "../outside.txt")
        }
        assertEquals("safe", workspace.read("inside.txt"))
    }

    @Test
    fun oversizedWritesAreRejected() {
        val workspace = ProjectWorkspace(Files.createTempDirectory("natasha-workspace").toFile())
        val large = "x".repeat(2 * 1024 * 1024 + 1)
        assertThrows(IllegalArgumentException::class.java) {
            workspace.write("large.txt", large)
        }
    }

}
