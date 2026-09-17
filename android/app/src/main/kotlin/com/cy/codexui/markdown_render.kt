package com.cy.codexui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * the end of the buffer. Streaming callers hand over a [MarkdownStream] so a delta rebuilds only
 * the tail block, links [MarkdownStreamText]; non-streaming callers pass the whole buffer.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
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
    val style = markdownStyle(
        textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
        quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing,
    )
    // The stream is immutable once filled: the buffer arrives whole here, and a caller that has a
    // growing buffer uses [MarkdownStreamText] instead so the parse stays incremental.
    val stream = remember(markdown) { MarkdownStream().apply { append(markdown) } }
    MarkdownStreamText(stream, modifier, style, streaming)
}

/**
 * Render a streaming buffer.
 *
 * [stream.tail] is the only state a delta rewrites, so the blocks in [MarkdownStream.frozen] keep
 * their composition and Skia keeps its text layouts; the caret, when [streaming], is drawn by the
 * tail block's own composable so its blink invalidates that one leaf.
 */
@Composable
fun MarkdownStreamText(
    stream: MarkdownStream,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    fontSize: TextUnit = TranscriptFontSize,
    lineHeight: TextUnit = TranscriptLineHeight,
    streaming: Boolean = true,
    blockSpacing: Dp = 10.dp,
    headingSizeStep: TextUnit = UiType.HeadingSizeStep,
    headingLineHeightStep: TextUnit = UiType.HeadingLeadingStep,
    quoteBarWidth: Dp = 3.dp,
    quoteBarHeight: Dp = 20.dp,
    quoteBarCorner: Dp = UiConsts.CornerBar,
    quoteSpacing: Dp = 10.dp,
) {
    val style = markdownStyle(
        textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
        quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing,
    )
    MarkdownStreamText(stream, modifier, style, streaming)
}

@Composable
private fun MarkdownStreamText(
    stream: MarkdownStream,
    modifier: Modifier,
    style: MarkdownStyle,
    streaming: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(style.blockSpacing)) {
        // Each list read belongs to its own composable: appending a frozen block recomposes this
        // loop only, and a tail rewrite does not touch it at all.
        FrozenBlocks(stream.frozen, style)
        val tail = stream.tail
        if (tail != null) MarkdownBlockView(tail, style, caret = streaming)
    }
}

@Composable
private fun FrozenBlocks(blocks: List<MarkdownBlock>, style: MarkdownStyle) {
    for (index in blocks.indices) {
        // Frozen blocks are append-only, so positional identity is stable. Unchanged blocks
        // compare equal by content and skip.
        MarkdownBlockView(blocks[index], style, caret = false)
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, style: MarkdownStyle, caret: Boolean) {
    when (block) {
        is MarkdownBlock.Paragraph -> StyledText(
            text = inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
        )

        is MarkdownBlock.Heading -> StyledText(
            text = inline(block.text, style.textColor),
            fontSize = (style.fontSize.value +
                (3 - block.level).coerceIn(0, 3) * style.headingSizeStep.value).sp,
            lineHeight = (style.lineHeight.value + style.headingLineHeightStep.value).sp,
            color = style.textColor,
            caret = caret,
            fontWeight = FontWeight.SemiBold,
        )

        is MarkdownBlock.Bullet -> BulletRow(
            marker = stringResource(R.string.markdown_render_bullet),
            text = inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
        )

        is MarkdownBlock.Numbered -> BulletRow(
            marker = stringResource(R.string.markdown_render_numbered, block.index),
            text = inline(block.text, style.textColor),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = style.textColor,
            caret = caret,
        )

        is MarkdownBlock.Quote -> QuoteRow(
            text = inline(block.text, style.textColor),
            style = style,
            caret = caret,
        )

        is MarkdownBlock.Code -> CodeBlock(
            code = block.code,
            language = block.language,
            streaming = block.open,
            caret = caret,
        )

        is MarkdownBlock.OpenCode -> StreamingCodeBlock(block, caret = caret)
    }
}

/** Styling for every block, bundled so a block view compares one stable parameter. */
@Immutable
private data class MarkdownStyle(
    val textColor: Color,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val blockSpacing: Dp,
    val headingSizeStep: TextUnit,
    val headingLineHeightStep: TextUnit,
    val quoteBarWidth: Dp,
    val quoteBarHeight: Dp,
    val quoteBarCorner: Dp,
    val quoteSpacing: Dp,
)

@Composable
private fun markdownStyle(
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    blockSpacing: Dp,
    headingSizeStep: TextUnit,
    headingLineHeightStep: TextUnit,
    quoteBarWidth: Dp,
    quoteBarHeight: Dp,
    quoteBarCorner: Dp,
    quoteSpacing: Dp,
): MarkdownStyle = MarkdownStyle(
    textColor, fontSize, lineHeight, blockSpacing, headingSizeStep, headingLineHeightStep,
    quoteBarWidth, quoteBarHeight, quoteBarCorner, quoteSpacing,
)

/**
 * A body of text plus the streaming caret.
 *
 * The caret is appended here, in the leaf, so a blink recomposes this Text and nothing above it.
 */
@Composable
private fun StyledText(
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    caret: Boolean = false,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    val suffix = if (caret) rememberBlinkingCaret() else ""
    val shown = remember(text, suffix) {
        if (suffix.isEmpty()) text else AnnotatedString.Builder(text).apply { append(suffix) }.toAnnotatedString()
    }
    Text(
        text = shown,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        color = color,
    )
}

@Composable
private fun BulletRow(
    marker: String,
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    caret: Boolean,
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
        StyledText(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            caret = caret,
        )
    }
}

@Composable
private fun QuoteRow(text: AnnotatedString, style: MarkdownStyle, caret: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(style.quoteBarWidth)
                .height(style.quoteBarHeight)
                .clip(RoundedCornerShape(style.quoteBarCorner))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(style.quoteSpacing))
        StyledText(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = style.fontSize,
            lineHeight = style.lineHeight,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            caret = caret,
        )
    }
}

/**
 * A fenced code block. Wraps the whole block in a horizontally scrollable surface with a language
 * chip, which is what the TUI's `code_fence.rs` does with its own fence detection.
 *
 * The body is one Text per line rather than one Text for the block: a streaming fence then lays out
 * only the line that changed, and every earlier line keeps its cached layout.
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    caret: Boolean = false,
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
    val lines = remember(code) {
        val trimmed = code.trimEnd('\n')
        if (trimmed.isEmpty()) emptyList() else trimmed.lines()
    }
    CodeSurface(
        language = language,
        streaming = streaming,
        modifier = modifier,
        corner = corner,
        headerStartPadding = headerStartPadding,
        headerEndPadding = headerEndPadding,
        headerTopPadding = headerTopPadding,
        labelFontSize = labelFontSize,
        labelLineHeight = labelLineHeight,
        contentHorizontalPadding = contentHorizontalPadding,
        contentVerticalPadding = contentVerticalPadding,
    ) {
        for (index in lines.indices) {
            CodeLine(
                text = lines[index],
                caret = caret && index == lines.lastIndex,
                fontSize = codeFontSize,
                lineHeight = codeLineHeight,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StreamingCodeBlock(block: MarkdownBlock.OpenCode, caret: Boolean) {
    CodeSurface(
        language = block.language,
        streaming = true,
        modifier = Modifier,
        corner = UiConsts.CornerRow,
        headerStartPadding = 14.dp,
        headerEndPadding = 12.dp,
        headerTopPadding = 9.dp,
        labelFontSize = UiType.Caption,
        labelLineHeight = UiType.CardTitle,
        contentHorizontalPadding = 14.dp,
        contentVerticalPadding = 10.dp,
    ) {
        val lines = block.lines
        for (index in lines.indices) {
            CodeLine(
                text = lines[index],
                caret = false,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        // The partial line is where the stream is writing; only this scope re-reads when it grows.
        val partial = block.partial
        if (partial.isNotEmpty() || caret) {
            CodeLine(
                text = partial,
                caret = caret,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CodeSurface(
    language: String?,
    streaming: Boolean,
    modifier: Modifier,
    corner: Dp,
    headerStartPadding: Dp,
    headerEndPadding: Dp,
    headerTopPadding: Dp,
    labelFontSize: TextUnit,
    labelLineHeight: TextUnit,
    contentHorizontalPadding: Dp,
    contentVerticalPadding: Dp,
    content: @Composable () -> Unit,
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
            Column { content() }
        }
    }
}

@Composable
private fun CodeLine(
    text: String,
    caret: Boolean,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
) {
    val suffix = if (caret) rememberBlinkingCaret() else ""
    Text(
        text = if (suffix.isEmpty()) text else text + suffix,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = color,
        softWrap = false,
    )
}

/** One-line shell command with the same monospace treatment the TUI's exec cells use. */
@Composable
fun InlineCode(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface,
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
// Inline spans
// ---------------------------------------------------------------------------------------------

/**
 * Style inline spans. Only four span kinds are recognised, which is what agent output actually
 * uses: `code`, **bold**, *italic* and links, rendered as their label because tapping through to a
 * browser is out of scope for the phone shell.
 */
internal fun inline(text: String, color: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val next = nextSpan(text, cursor)
            if (next == null) {
                append(text.substring(cursor))
                break
            }
            append(text.substring(cursor, next.start))
            withStyle(next.style(color)) {
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
    val style: (Color) -> SpanStyle,
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
    style: (Color) -> SpanStyle,
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
    style: (Color) -> SpanStyle,
): Span? {
    val start = text.indexOf(open, from)
    if (start < 0) return null
    val end = text.indexOf(close, start + open.length)
    if (end < 0) return null
    return Span(start, end + close.length, text.substring(start + open.length, end), style)
}

// ---------------------------------------------------------------------------------------------
// Caret
// ---------------------------------------------------------------------------------------------

/**
 * The TUI's block caret: `▍` at the end of a streaming block, blinking on a 600ms period.
 *
 * The animation is created by the composable that draws the caret, so its 60fps invalidation never
 * reaches a parent: a page of frozen blocks does not repaint because the caret blinked.
 */
@Composable
private fun rememberBlinkingCaret(periodMs: Int = StreamingCaretPeriodMs): String {
    val transition = rememberInfiniteTransition(label = "streamingCaret")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streamingCaretPhase",
    )
    return if (phase > 0.5f) BlockCaret else ""
}

private const val StreamingCaretPeriodMs = 600
private const val BlockCaret = "▍"

/** Default paragraph style, shared so transcript cells measure the same. */
val TranscriptTextStyle: TextStyle
    @Composable get() = TextStyle(
        fontSize = TranscriptFontSize,
        lineHeight = TranscriptLineHeight,
        color = MiuixTheme.colorScheme.onSurface,
    )
