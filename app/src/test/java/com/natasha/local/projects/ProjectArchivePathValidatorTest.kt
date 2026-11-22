package com.natasha.local.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectArchivePathValidatorTest {
    @Test fun acceptsNestedRelativePath() {
        assertNull(ProjectArchivePathValidator.validate("app/src/Main.kt"))
    }

    @Test fun rejectsParentTraversal() {
        assertEquals("Archive contains an unsafe path", ProjectArchivePathValidator.validate("../outside.txt"))
    }

    @Test fun rejectsAbsolutePath() {
        assertEquals("Archive contains an absolute path", ProjectArchivePathValidator.validate("/tmp/outside.txt"))
    }

    @Test fun rejectsWindowsDrivePath() {
        assertEquals("Archive contains an absolute path", ProjectArchivePathValidator.validate("C:\\temp\\outside.txt"))
    }

    @Test fun rejectsEmptySegments() {
        assertEquals("Archive contains an unsafe path", ProjectArchivePathValidator.validate("src//Main.kt"))
    }

    @Test fun rejectsDotSegments() {
        assertEquals("Archive contains an unsafe path", ProjectArchivePathValidator.validate("src/./Main.kt"))
    }
}
