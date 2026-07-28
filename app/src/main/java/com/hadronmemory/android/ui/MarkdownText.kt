package com.hadronmemory.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight markdown renderer, the Compose counterpart of hadron-ios'
 * `MarkdownText`: block structure (headings, code fences, everything else) is
 * split line-wise here, and inline markdown — bold, italic, code, links — is
 * folded into an `AnnotatedString`.
 *
 * Deliberately not a full CommonMark implementation. Node bodies are prose
 * with the occasional heading and list; a parser dependency would cost more
 * than it renders.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = inlineMarkdown(block.text),
                    style = headingStyle(block.level),
                    modifier = Modifier.padding(top = 4.dp),
                )

                is MarkdownBlock.Code -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// MARK: - Blocks

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

/**
 * Headings and fenced code become their own blocks; consecutive other lines
 * merge into a paragraph until a blank line (single newlines survive inside a
 * paragraph, so list items stay one per line).
 */
private fun parseBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val current = mutableListOf<String>()
    var fence: MutableList<String>? = null

    fun flush() {
        val paragraph = current.joinToString("\n").trim()
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph)
        current.clear()
    }

    for (line in text.split("\n")) {
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            if (fence == null) {
                flush()
                fence = mutableListOf()
            } else {
                blocks += MarkdownBlock.Code(fence.joinToString("\n"))
                fence = null
            }
            continue
        }
        if (fence != null) {
            fence += line
            continue
        }

        val heading = parseHeading(trimmed)
        when {
            trimmed.isEmpty() -> flush()
            heading != null -> {
                flush()
                blocks += MarkdownBlock.Heading(heading.first, heading.second)
            }
            else -> current += line
        }
    }
    // An unterminated fence still has content worth showing.
    fence?.let { if (it.isNotEmpty()) blocks += MarkdownBlock.Code(it.joinToString("\n")) }
    flush()
    return blocks
}

private fun parseHeading(line: String): Pair<Int, String>? {
    val hashes = line.takeWhile { it == '#' }.length
    if (hashes !in 1..6) return null
    val rest = line.drop(hashes)
    if (!rest.startsWith(" ")) return null
    return hashes to rest.trim()
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge
    2 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}.copy(fontWeight = FontWeight.Bold)

// MARK: - Inline

private val INLINE = Regex(
    // link | bold | italic | code
    """\[([^\]]+)]\(([^)\s]+)\)|\*\*(.+?)\*\*|(?<![*\w])\*([^*\n]+)\*(?!\*)|`([^`\n]+)`""",
    RegexOption.DOT_MATCHES_ALL,
)

@Composable
private fun inlineMarkdown(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, linkColor) {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        )
        buildAnnotatedString {
            var cursor = 0
            for (match in INLINE.findAll(text)) {
                if (match.range.first < cursor) continue
                append(text.substring(cursor, match.range.first))
                val (label, url, bold, italic, code) = match.groupValues.drop(1).take(5)
                when {
                    url.isNotEmpty() ->
                        withLink(LinkAnnotation.Url(url, linkStyles)) { append(label) }

                    bold.isNotEmpty() ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }

                    italic.isNotEmpty() ->
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }

                    code.isNotEmpty() ->
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
}
