package com.forgeai.chat.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgeai.chat.domain.markdown.InlineSpan
import com.forgeai.chat.domain.markdown.MarkdownBlock
import com.forgeai.chat.domain.markdown.MarkdownParser
import com.forgeai.chat.ui.theme.CodeBlockBackground
import com.forgeai.chat.ui.theme.CodeBlockHeaderBackground
import com.forgeai.chat.ui.theme.CodeTextDefault
import com.forgeai.chat.ui.theme.SuccessGreenDark
import com.forgeai.chat.ui.theme.SyntaxComment
import com.forgeai.chat.ui.theme.SyntaxKeyword
import com.forgeai.chat.ui.theme.SyntaxNumber
import com.forgeai.chat.ui.theme.SyntaxString
import com.forgeai.chat.ui.theme.SyntaxType
import kotlinx.coroutines.delay

@Composable
fun MarkdownMessageView(
    markdown: String,
    textColor: Color,
    onCodeCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
    val inlineCodeFg = MaterialTheme.colorScheme.secondary

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = remember(block.text, inlineCodeBg, inlineCodeFg) {
                                buildInlineAnnotatedString(block.text, inlineCodeBg, inlineCodeFg)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    }

                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                        Text(
                            text = remember(block.text, inlineCodeBg, inlineCodeFg) {
                                buildInlineAnnotatedString(block.text, inlineCodeBg, inlineCodeFg)
                            },
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = textColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = remember(block.text, inlineCodeBg, inlineCodeFg) {
                                    buildInlineAnnotatedString(block.text, inlineCodeBg, inlineCodeFg)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.OrderedItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.number,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = remember(block.text, inlineCodeBg, inlineCodeFg) {
                                    buildInlineAnnotatedString(block.text, inlineCodeBg, inlineCodeFg)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(20.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = remember(block.text, inlineCodeBg, inlineCodeFg) {
                                    buildInlineAnnotatedString(block.text, inlineCodeBg, inlineCodeFg)
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockCard(
                            language = block.language,
                            code = block.code,
                            isComplete = block.isComplete,
                            onCodeCopied = onCodeCopied
                        )
                    }

                    MarkdownBlock.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    language: String,
    code: String,
    isComplete: Boolean,
    onCodeCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    val highlightedCode = remember(code) { highlightSyntax(code) }
    val displayLang = language.ifBlank { "code" }.lowercase()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CodeBlockBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CodeBlockHeaderBackground)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isComplete) displayLang else "$displayLang · streaming…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                        onCodeCopied("Code copied")
                    }
                ) {
                    AnimatedContent(targetState = copied, label = "copy_code_btn") { isCopied ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Copy code",
                                tint = if (isCopied) SuccessGreenDark else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCopied) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCopied) SuccessGreenDark else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    ),
                    color = CodeTextDefault
                )
            }
        }
    }
}

private fun buildInlineAnnotatedString(
    text: String,
    inlineCodeBg: Color,
    inlineCodeFg: Color
): AnnotatedString = buildAnnotatedString {
    val spans = MarkdownParser.parseInline(text)
    spans.forEach { span ->
        when (span) {
            is InlineSpan.Plain -> append(span.text)
            is InlineSpan.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(span.text)
            }
            is InlineSpan.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(span.text)
            }
            is InlineSpan.BoldItalic -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            ) {
                append(span.text)
            }
            is InlineSpan.Strikethrough -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) {
                append(span.text)
            }
            is InlineSpan.InlineCode -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    background = inlineCodeBg,
                    color = inlineCodeFg
                )
            ) {
                append(" ${span.code} ")
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed", "enum",
    "package", "import", "return", "if", "else", "when", "for", "while", "try",
    "catch", "finally", "suspend", "override", "private", "public", "internal",
    "protected", "companion", "lateinit", "const", "true", "false", "null",
    "def", "async", "await", "function", "let", "export", "from", "SELECT",
    "FROM", "WHERE", "CREATE", "INDEX", "ORDER", "BY", "INSERT", "UPDATE", "DELETE"
)

private val TOKEN_REGEX = Regex(
    "(//[^\\n]*|#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
)

private fun highlightSyntax(code: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    TOKEN_REGEX.findAll(code).forEach { match ->
        if (match.range.first > cursor) {
            append(code.substring(cursor, match.range.first))
        }
        val token = match.value
        val style = when {
            token.startsWith("//") || token.startsWith("#") -> SpanStyle(color = SyntaxComment)
            token.startsWith("\"") || token.startsWith("'") -> SpanStyle(color = SyntaxString)
            token.first().isDigit() -> SpanStyle(color = SyntaxNumber)
            token in CODE_KEYWORDS -> SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Medium)
            token.first().isUpperCase() && token.any { it.isLowerCase() } -> SpanStyle(color = SyntaxType)
            else -> null
        }
        if (style != null) {
            withStyle(style) { append(token) }
        } else {
            append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < code.length) {
        append(code.substring(cursor))
    }
}
