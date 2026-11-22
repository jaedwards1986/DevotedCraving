package com.natasha.local.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectBuildPlanValidatorTest {
    @Test fun acceptsValidPlan() {
        assertNull(
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan("Create starter files", listOf(ProjectFileChange("src/Main.kt", "fun main() {}")))
            )
        )
    }

    @Test fun rejectsDuplicatePaths() {
        assertEquals(
            "Build plan contains duplicate file paths",
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan(
                    "bad",
                    listOf(ProjectFileChange("Main.kt", "one"), ProjectFileChange("Main.kt", "two"))
                )
            )
        )
    }

    @Test fun rejectsEmptyPlan() {
        assertEquals(
            "Build plan contains no file changes",
            ProjectBuildPlanValidator.validate(ProjectBuildPlan("bad", emptyList()))
        )
    }
    @Test fun rejectsParentTraversal() {
        assertEquals(
            "Invalid or escaping file path",
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan("unsafe", listOf(ProjectFileChange("../outside.txt", "no")))
            )
        )
    }

    @Test fun rejectsAbsolutePaths() {
        assertEquals(
            "Absolute file paths are not allowed",
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan("unsafe", listOf(ProjectFileChange("/tmp/outside.txt", "no")))
            )
        )
    }

    @Test fun rejectsWindowsTraversal() {
        assertEquals(
            "Invalid or escaping file path",
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan("unsafe", listOf(ProjectFileChange("src\\\\..\\\\outside.kt", "no")))
            )
        )
    }

    @Test fun rejectsTooManyChanges() {
        assertEquals(
            "Build plan contains too many file changes",
            ProjectBuildPlanValidator.validate(
                ProjectBuildPlan("large", (1..51).map { ProjectFileChange("file$it.txt", "x") })
            )
        )
    }

}
