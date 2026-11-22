package com.forgeai.chat.domain.markdown

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(
        val language: String,
        val code: String,
        val isComplete: Boolean
    ) : MarkdownBlock

    data class BulletItem(val text: String) : MarkdownBlock
    data class OrderedItem(val number: String, val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

sealed interface InlineSpan {
    data class Plain(val text: String) : InlineSpan
    data class Bold(val text: String) : InlineSpan
    data class Italic(val text: String) : InlineSpan
    data class BoldItalic(val text: String) : InlineSpan
    data class InlineCode(val code: String) : InlineSpan
    data class Strikethrough(val text: String) : InlineSpan
}

object MarkdownParser {

    private val FENCE_REGEX = Regex("^\\s*(```|~~~)\\s*([A-Za-z0-9_+#.-]*)\\s*$")
    private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+)$")
    private val BULLET_REGEX = Regex("^\\s*[-*+]\\s+(.+)$")
    private val ORDERED_REGEX = Regex("^\\s*(\\d+\\.)\\s+(.+)$")
    private val QUOTE_REGEX = Regex("^\\s*>\\s?(.*)$")
    private val DIVIDER_REGEX = Regex("^\\s*([-*_])\\1{2,}\\s*$")

    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        if (markdown.isEmpty()) return emptyList()

        val lines = markdown.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraphBuffer = mutableListOf<String>()

        var inCodeFence = false
        var fenceToken = "```"
        var codeLanguage = ""
        val codeLines = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n")))
                paragraphBuffer.clear()
            }
        }

        for (line in lines) {
            if (inCodeFence) {
                val trimmed = line.trim()
                if (trimmed.startsWith(fenceToken) && trimmed.removePrefix(fenceToken).isBlank()) {
                    blocks.add(
                        MarkdownBlock.CodeBlock(
                            language = codeLanguage,
                            code = codeLines.joinToString("\n"),
                            isComplete = true
                        )
                    )
                    codeLines.clear()
                    codeLanguage = ""
                    inCodeFence = false
                } else {
                    codeLines.add(line)
                }
                continue
            }

            val fenceMatch = FENCE_REGEX.matchEntire(line)
            if (fenceMatch != null) {
                flushParagraph()
                inCodeFence = true
                fenceToken = fenceMatch.groupValues[1]
                codeLanguage = fenceMatch.groupValues[2].trim()
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                continue
            }

            if (DIVIDER_REGEX.matches(line)) {
                flushParagraph()
                blocks.add(MarkdownBlock.Divider)
                continue
            }

            val headingMatch = HEADING_REGEX.matchEntire(line.trimStart())
            if (headingMatch != null) {
                flushParagraph()
                blocks.add(
                    MarkdownBlock.Heading(
                        level = headingMatch.groupValues[1].length,
                        text = headingMatch.groupValues[2].trim()
                    )
                )
                continue
            }

            val quoteMatch = QUOTE_REGEX.matchEntire(line)
            if (quoteMatch != null) {
                flushParagraph()
                blocks.add(MarkdownBlock.BlockQuote(quoteMatch.groupValues[1]))
                continue
            }

            val bulletMatch = BULLET_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                blocks.add(MarkdownBlock.BulletItem(bulletMatch.groupValues[1]))
                continue
            }

            val orderedMatch = ORDERED_REGEX.matchEntire(line)
            if (orderedMatch != null) {
                flushParagraph()
                blocks.add(
                    MarkdownBlock.OrderedItem(
                        number = orderedMatch.groupValues[1],
                        text = orderedMatch.groupValues[2]
                    )
                )
                continue
            }

            paragraphBuffer.add(line)
        }

        if (inCodeFence) {
            blocks.add(
                MarkdownBlock.CodeBlock(
                    language = codeLanguage,
                    code = codeLines.joinToString("\n"),
                    isComplete = false
                )
            )
        } else {
            flushParagraph()
        }

        return blocks
    }

    fun parseInline(text: String): List<InlineSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = mutableListOf<InlineSpan>()
        val plainBuffer = StringBuilder()
        var index = 0

        fun flushPlain() {
            if (plainBuffer.isNotEmpty()) {
                spans.add(InlineSpan.Plain(plainBuffer.toString()))
                plainBuffer.setLength(0)
            }
        }

        while (index < text.length) {
            when {
                text[index] == '`' -> {
                    val closing = text.indexOf('`', startIndex = index + 1)
                    if (closing > index + 1) {
                        flushPlain()
                        spans.add(InlineSpan.InlineCode(text.substring(index + 1, closing)))
                        index = closing + 1
                    } else {
                        plainBuffer.append(text[index])
                        index++
                    }
                }

                text.startsWith("***", index) -> {
                    val closing = text.indexOf("***", startIndex = index + 3)
                    if (closing > index + 3) {
                        flushPlain()
                        spans.add(InlineSpan.BoldItalic(text.substring(index + 3, closing)))
                        index = closing + 3
                    } else {
                        plainBuffer.append(text[index])
                        index++
                    }
                }

                text.startsWith("**", index) -> {
                    val closing = text.indexOf("**", startIndex = index + 2)
                    if (closing > index + 2) {
                        flushPlain()
                        spans.add(InlineSpan.Bold(text.substring(index + 2, closing)))
                        index = closing + 2
                    } else {
                        plainBuffer.append(text[index])
                        index++
                    }
                }

                text.startsWith("~~", index) -> {
                    val closing = text.indexOf("~~", startIndex = index + 2)
                    if (closing > index + 2) {
                        flushPlain()
                        spans.add(InlineSpan.Strikethrough(text.substring(index + 2, closing)))
                        index = closing + 2
                    } else {
                        plainBuffer.append(text[index])
                        index++
                    }
                }

                text[index] == '*' && !text.startsWith("**", index) -> {
                    val closing = text.indexOf('*', startIndex = index + 1)
                    if (closing > index + 1 && text[index + 1] != ' ') {
                        flushPlain()
                        spans.add(InlineSpan.Italic(text.substring(index + 1, closing)))
                        index = closing + 1
                    } else {
                        plainBuffer.append(text[index])
                        index++
                    }
                }

                else -> {
                    plainBuffer.append(text[index])
                    index++
                }
            }
        }

        flushPlain()
        return spans
    }
}
