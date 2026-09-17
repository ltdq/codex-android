package com.cy.codexui.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.item.UserMessageItem
import com.cy.codexui.protocol.protocol.v2.AsyncUserInputQuestion
import com.cy.codexui.protocol.protocol.v2.UserInput
import com.cy.codexui.MarkdownStream
import com.cy.codexui.MarkdownStreamText
import com.cy.codexui.MarkdownText
import com.cy.codexui.SquircleShape
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * User turns and agent answers.
 *
 * Mirrors `codex-rs/tui/src/history_cell/messages.rs`: the user's prompt is a right-aligned block
 * that carries every non-text attachment as a labelled chip, and the agent's answer is a labelled
 * markdown body. The streaming variant appends the TUI's block caret while deltas are still
 * arriving.
 */

@Composable
fun UserMessageCell(
    item: UserMessageItem,
    modifier: Modifier = Modifier,
    corner: Dp = UiConsts.CornerCard,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    attachmentSpacing: Dp = 4.dp,
    textSpacing: Dp = 7.dp,
    fontSize: TextUnit = UiType.Message,
    lineHeight: TextUnit = UiType.MessageLine,
) {
    val colors = MiuixTheme.colorScheme
    val text = item.content.filterIsInstance<UserInput.Text>()
        .joinToString("\n\n") { it.text }
        .trim()
    val attachments = item.content.filterNot { it is UserInput.Text }
    if (text.isEmpty() && attachments.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiConsts.UserBubbleMaxWidth)
                .clip(SquircleShape(corner))
                .background(colors.surfaceContainerHighest)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.End,
        ) {
            if (attachments.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(attachmentSpacing),
                    horizontalAlignment = Alignment.End,
                ) {
                    attachments.forEach { attachment -> AttachmentChip(attachment) }
                }
                if (text.isNotEmpty()) Spacer(Modifier.height(textSpacing))
            }
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
fun AgentMessageCell(
    item: AgentMessageItem,
    modifier: Modifier = Modifier,
    stream: MarkdownStream? = null,
    streaming: Boolean = false,
    label: String = stringResource(R.string.messages_cell_assistant_name),
    cwd: String? = null,
    labelFontSize: TextUnit = UiType.Subtitle,
    labelLineHeight: TextUnit = UiType.SheetTitle,
    labelSpacing: Dp = 5.dp,
    questionSpacing: Dp = 10.dp,
) {
    val colors = MiuixTheme.colorScheme
    val questions = item.questions.orEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = labelFontSize,
            lineHeight = labelLineHeight,
            fontWeight = FontWeight.Medium,
            color = colors.primary,
            maxLines = 1,
        )
        Spacer(Modifier.height(labelSpacing))
        // While deltas are being buffered the parsed blocks are the body; once the item completes
        // the stream is dropped and the authoritative text renders instead.
        if (stream != null && stream.hasContent) {
            MarkdownStreamText(stream = stream, streaming = streaming, cwd = cwd)
        } else {
            MarkdownText(markdown = item.text, cwd = cwd)
        }
        if (questions.isNotEmpty()) {
            Spacer(Modifier.height(questionSpacing))
            QuestionList(questions)
        }
    }
}

/** One `@file` / image / skill chip above the bubble text. */
@Composable
private fun AttachmentChip(
    input: UserInput,
    corner: Dp = UiConsts.CornerChip,
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 2.dp,
    fontSize: TextUnit = UiType.Footnote,
    lineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    val label = when (input) {
        is UserInput.Image -> stringResource(R.string.messages_cell_attachment_image)
        is UserInput.LocalImage -> stringResource(R.string.messages_cell_attachment_local_image)
        is UserInput.Skill ->
            stringResource(R.string.messages_cell_attachment_skill, input.name)
        is UserInput.Mention -> stringResource(
            R.string.messages_cell_attachment_mention,
            input.path.ifEmpty { input.name },
        )
        is UserInput.Text -> input.text
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(corner))
            .background(colors.primary.copy(alpha = 0.12f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = colors.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Questions the agent asked inside its message, shown as a bordered list so a request that is
 * waiting on the user never reads as plain prose.
 */
@Composable
private fun QuestionList(
    questions: List<AsyncUserInputQuestion>,
    corner: Dp = UiConsts.CornerControl,
    borderWidth: Dp = 1.dp,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    questionSpacing: Dp = 9.dp,
    optionSpacing: Dp = 3.dp,
    titleFontSize: TextUnit = UiType.RowTitle,
    titleLineHeight: TextUnit = UiType.RowTitleLine,
    bulletWidth: Dp = 12.dp,
    optionFontSize: TextUnit = UiType.Subtitle,
    optionLineHeight: TextUnit = UiType.BodyLine,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(borderWidth, colors.outline.copy(alpha = 0.55f), shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(questionSpacing),
    ) {
        questions.forEach { question ->
            Column(verticalArrangement = Arrangement.spacedBy(optionSpacing)) {
                Text(
                    text = question.title,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    color = colors.onSurface,
                )
                question.options.orEmpty().forEach { option ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = stringResource(R.string.messages_cell_option_bullet),
                            modifier = Modifier.width(bulletWidth),
                            fontSize = optionFontSize,
                            lineHeight = optionLineHeight,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            fontSize = optionFontSize,
                            lineHeight = optionLineHeight,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

// The streaming caret lives with the markdown renderer: it is drawn by the tail block's own
// composable there, so a blink no longer changes the message text and no longer forces a re-parse.
