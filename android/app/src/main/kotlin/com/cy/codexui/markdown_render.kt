package com.cy.codexui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.codeSurface
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Default paragraph metrics, shared so transcript cells measure the same. */
private val TranscriptFontSize = UiType.Message
private val TranscriptLineHeight = UiType.MessageLine

/**
 * Markdown-lite renderer for agent output.
 *
 * The TUI renders through `codex-rs/tui/src/markdown_render.rs` with a streaming variant that
 * tolerates half-finished fences (`markdown_render/streaming.rs`). The phone keeps the same
 * contract but a smaller surface: paragraphs, bullets, numbered items, headings, block quotes,
 * fenced code and inline `code` / **bold** / *italic* spans are styled; anything else passes
 * through as text.
 *
 * A partially streamed document is legal input — an unterminated fence simply renders as code to
 * the end of the buffer.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
    fontSize: TextUnit = TranscriptFontSize,
    lineHeight: TextUnit = TranscriptLineHeight,
    streaming: Boolean = false,
    blockSpacing: Dp = 10.dp,
    headingSizeStep: TextUnit = UiType.HeadingSizeStep,
    headingLineHeightStep: TextUnit = UiType.HeadingLeadingStep,
    quoteBarWidth: Dp = 3.dp,
    quoteBarHeight: Dp = 20.dp,
    quoteBarCorner: Dp = UiConsts.CornerBar,
    quoteSpacing: Dp = 10.dp,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(blockSpacing)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = inline(block.text, textColor),
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = textColor,
                )

                is MarkdownBlock.Heading -> Text(
                    text = inline(block.text, textColor),
                    fontSize = (fontSize.value + (3 - block.level).coerceIn(0, 3) * headingSizeStep.value).sp,
                    lineHeight = (lineHeight.value + headingLineHeightStep.value).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )

                is MarkdownBlock.Bullet -> BulletRow(
                    marker = stringResource(R.string.markdown_render_bullet),
                    text = inline(block.text, textColor),
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = textColor,
                )

                is MarkdownBlock.Numbered -> BulletRow(
                    marker = stringResource(R.string.markdown_render_numbered, block.index),
                    text = inline(block.text, textColor),
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = textColor,
                )

                is MarkdownBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .width(quoteBarWidth)
                            .height(quoteBarHeight)
                            .clip(RoundedCornerShape(quoteBarCorner))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(quoteSpacing))
                    Text(
                        text = inline(block.text, textColor),
                        modifier = Modifier.weight(1f),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                is MarkdownBlock.Code -> CodeBlock(
                    code = block.code,
                    language = block.language,
                    streaming = streaming,
                )
            }
        }
    }
}

@Composable
private fun BulletRow(
    marker: String,
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: androidx.compose.ui.graphics.Color,
    markerWidth: Dp = UiConsts.IconLeading,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = marker,
            modifier = Modifier.width(markerWidth),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
        )
    }
}

/**
 * A fenced code block. Wraps the whole block in a horizontally scrollable surface with a language
 * chip, which is what the TUI's `code_fence.rs` does with its own fence detection.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    corner: Dp = UiConsts.CornerRow,
    headerStartPadding: Dp = 14.dp,
    headerEndPadding: Dp = 12.dp,
    headerTopPadding: Dp = 9.dp,
    labelFontSize: TextUnit = UiType.Caption,
    labelLineHeight: TextUnit = UiType.CardTitle,
    contentHorizontalPadding: Dp = 14.dp,
    contentVerticalPadding: Dp = 10.dp,
    codeFontSize: TextUnit = UiType.Body,
    codeLineHeight: TextUnit = UiType.BodyLine,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(codeSurface()),
    ) {
        if (!language.isNullOrBlank() || streaming) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = headerStartPadding,
                        end = headerEndPadding,
                        top = headerTopPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language?.ifBlank { null } ?: stringResource(R.string.markdown_render_code),
                    modifier = Modifier.weight(1f),
                    fontSize = labelFontSize,
                    lineHeight = labelLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (streaming) {
                    Text(
                        text = stringResource(R.string.markdown_render_generating),
                        fontSize = labelFontSize,
                        lineHeight = labelLineHeight,
                        color = colors.primary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = contentHorizontalPadding, vertical = contentVerticalPadding),
        ) {
            Text(
                text = code.trimEnd('\n'),
                fontSize = codeFontSize,
                lineHeight = codeLineHeight,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                softWrap = false,
            )
        }
    }
}

/** One-line shell command with the same monospace treatment the TUI's exec cells use. */
@Composable
fun InlineCode(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurface,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 5.dp,
    verticalPadding: Dp = 1.dp,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.BodyLine,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(codeSurface())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = color,
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------------------------
// Parsing
// ---------------------------------------------------------------------------------------------

internal sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val index: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?) : MarkdownBlock
}

private val BulletMarker = Regex("^[-*+]\\s+(.*)$")
private val NumberedMarker = Regex("^(\\d+)[.)]\\s+(.*)$")
private val HeadingMarker = Regex("^(#{1,3})\\s+(.*)$")
private val FenceMarker = Regex("^```\\s*(\\S*)\\s*$")

/**
 * Split a markdown buffer into blocks.
 *
 * Deliberately tolerant: paragraphs are separated by blank lines, and a fence that never closes
 * keeps consuming until the buffer ends so a streaming message renders its code as code.
 */
internal fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trimEnd()
        val fence = FenceMarker.find(line.trim())
        when {
            fence != null -> {
                flushParagraph()
                val language = fence.groupValues[1].ifBlank { null }
                val code = StringBuilder()
                index++
                while (index < lines.size && FenceMarker.find(lines[index].trim()) == null) {
                    code.appendLine(lines[index])
                    index++
                }
                blocks += MarkdownBlock.Code(code.toString().trimEnd('\n'), language)
            }

            line.isBlank() -> flushParagraph()

            HeadingMarker.matches(line) -> {
                flushParagraph()
                val match = HeadingMarker.find(line)!!
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }

            BulletMarker.matches(line.trim()) -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(BulletMarker.find(line.trim())!!.groupValues[1])
            }

            NumberedMarker.matches(line.trim()) -> {
                flushParagraph()
                val match = NumberedMarker.find(line.trim())!!
                blocks += MarkdownBlock.Numbered(
                    index = match.groupValues[1].toIntOrNull() ?: 1,
                    text = match.groupValues[2],
                )
            }

            line.trimStart().startsWith("> ") -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(line.trimStart().removePrefix("> "))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.trim())
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}

/**
 * Style inline spans. Only four span kinds are recognised, which is what agent output actually
 * uses: `code`, **bold**, *italic* and links, rendered as their label because tapping through to a
 * browser is out of scope for the phone shell.
 */
internal fun inline(
    text: String,
    color: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val primary = color
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val next = nextSpan(text, cursor)
            if (next == null) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, next.start))
            withStyle(next.style(primary)) {
                append(next.render)
            }
            cursor = next.end
        }
    }
}

private class Span(
    val start: Int,
    val end: Int,
    val render: String,
    val style: (androidx.compose.ui.graphics.Color) -> SpanStyle,
)

private fun nextSpan(text: String, from: Int): Span? {
    val candidates = listOfNotNull(
        spanOf(text, from, '`', '`') { c ->
            SpanStyle(fontFamily = FontFamily.Monospace, color = c, background = c.copy(alpha = 0.07f))
        },
        spanOf(text, from, "**", "**") { _ -> SpanStyle(fontWeight = FontWeight.SemiBold) },
        spanOf(text, from, '*', '*') { _ -> SpanStyle(fontStyle = FontStyle.Italic) },
    )
    return candidates.minByOrNull { it.start }
}

private fun spanOf(
    text: String,
    from: Int,
    open: Char,
    close: Char,
    style: (androidx.compose.ui.graphics.Color) -> SpanStyle,
): Span? {
    val start = text.indexOf(open, from)
    if (start < 0) return null
    val end = text.indexOf(close, start + 1)
    if (end < 0 || end == start + 1) return null
    return Span(start, end + 1, text.substring(start + 1, end), style)
}

private fun spanOf(
    text: String,
    from: Int,
    open: String,
    close: String,
    style: (androidx.compose.ui.graphics.Color) -> SpanStyle,
): Span? {
    val start = text.indexOf(open, from)
    if (start < 0) return null
    val end = text.indexOf(close, start + open.length)
    if (end < 0) return null
    return Span(start, end + close.length, text.substring(start + open.length, end), style)
}

/** Default paragraph style, shared so transcript cells measure the same. */
val TranscriptTextStyle: TextStyle
    @Composable get() = TextStyle(
        fontSize = TranscriptFontSize,
        lineHeight = TranscriptLineHeight,
        color = MiuixTheme.colorScheme.onSurface,
    )
