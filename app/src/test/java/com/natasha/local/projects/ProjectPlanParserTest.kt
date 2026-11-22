package com.natasha.local.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectPlanParserTest {
    @Test fun parsesAValidMultiFileProposal() {
        val response = """
            Here is the proposed change.
            <forgeai_plan>
            {"summary":"Add starter files","changes":[
              {"path":"README.md","content":"# Demo"},
              {"path":"src/Main.kt","content":"fun main() = Unit"}
            ]}
            </forgeai_plan>
        """.trimIndent()
        val plan = ProjectPlanParser.parse(response)
        assertEquals("Add starter files", plan?.summary)
        assertEquals(listOf("README.md", "src/Main.kt"), plan?.changes?.map { it.path })
    }

    @Test fun rejectsTraversalAndMalformedProposals() {
        val unsafe = """<forgeai_plan>{"summary":"bad","changes":[{"path":"../outside","content":"no"}]}</forgeai_plan>"""
        assertNull(ProjectPlanParser.parse(unsafe))
        assertNull(ProjectPlanParser.parse("""<forgeai_plan>{"summary":"missing close"}"""))
        assertNull(ProjectPlanParser.parse("ordinary assistant response"))
    }

    @Test fun rejectsOversizedFileContent() {
        val tooLarge = "x".repeat(2 * 1024 * 1024 + 1)
        val escaped = org.json.JSONObject.quote(tooLarge)
        val response = """<forgeai_plan>{"summary":"large","changes":[{"path":"large.txt","content":$escaped}]}</forgeai_plan>"""
        assertNull(ProjectPlanParser.parse(response))
    }
}
