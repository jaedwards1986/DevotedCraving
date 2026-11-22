package com.forgeai.chat.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesFencedCodeBlockWithLanguageAndSurroundingParagraphs() {
        val markdown = """
            Here is a Kotlin sample:
            ```kotlin
            fun greet(name: String): String {
                return "Hello, ${'$'}name"
            }
            ```
            Done!
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(3, blocks.size)
        assertEquals(MarkdownBlock.Paragraph("Here is a Kotlin sample:"), blocks[0])
        val codeBlock = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.isComplete)
        assertTrue(codeBlock.code.contains("fun greet"))
        assertEquals(MarkdownBlock.Paragraph("Done!"), blocks[2])
    }

    @Test
    fun parsesUnclosedStreamingCodeFenceGracefully() {
        val partial = "```python\ndef add(a, b):\n    return a + b"
        val blocks = MarkdownParser.parseBlocks(partial)

        assertEquals(1, blocks.size)
        val codeBlock = blocks[0] as MarkdownBlock.CodeBlock
        assertEquals("python", codeBlock.language)
        assertFalse(codeBlock.isComplete)
        assertEquals("def add(a, b):\n    return a + b", codeBlock.code)
    }

    @Test
    fun parsesHeadingsListsQuotesAndDivider() {
        val markdown = """
            ## Architecture
            - MVVM with StateFlow
            1. Room persistence
            > Keep state unidirectional
            ---
        """.trimIndent()

        val blocks = MarkdownParser.parseBlocks(markdown)
        assertEquals(5, blocks.size)
        assertEquals(MarkdownBlock.Heading(2, "Architecture"), blocks[0])
        assertEquals(MarkdownBlock.BulletItem("MVVM with StateFlow"), blocks[1])
        assertEquals(MarkdownBlock.OrderedItem("1.", "Room persistence"), blocks[2])
        assertEquals(MarkdownBlock.BlockQuote("Keep state unidirectional"), blocks[3])
        assertEquals(MarkdownBlock.Divider, blocks[4])
    }

    @Test
    fun parsesInlineCodeBoldItalicAndStrikethrough() {
        val spans = MarkdownParser.parseInline(
            "Use `StateFlow` for **reactive** *UI* and ~~legacy~~ code."
        )

        assertTrue(spans.contains(InlineSpan.InlineCode("StateFlow")))
        assertTrue(spans.contains(InlineSpan.Bold("reactive")))
        assertTrue(spans.contains(InlineSpan.Italic("UI")))
        assertTrue(spans.contains(InlineSpan.Strikethrough("legacy")))
    }
}
