package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.ReviewTarget
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/review`: choose the [ReviewTarget] variant and start it. `review/start` injects a new turn,
 * so findings arrive as transcript items — nothing to hold here. Mirrors chatwidget/review_popups.rs.
 */
@Composable
fun ReviewScreen(
    threadId: String,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    var choice by remember { mutableStateOf(ReviewChoice.Uncommitted) }
    var branch by remember { mutableStateOf("") }
    var sha by remember { mutableStateOf("") }
    var commitTitle by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    // One expression answers both "may it submit" and "what it sends"; two would disagree into a half-filled target.
    val target = reviewTarget(choice, branch, sha, commitTitle, instructions)

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.review_screen_title),
            summary = threadId,
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.review_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            insideMargin = PaddingValues(14.dp, 10.dp),
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.review_screen_section),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Merge,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                ReviewChoiceRow(
                    choice = ReviewChoice.Uncommitted,
                    selected = choice == ReviewChoice.Uncommitted,
                    onSelect = { choice = ReviewChoice.Uncommitted },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                ReviewChoiceRow(
                    choice = ReviewChoice.BaseBranch,
                    selected = choice == ReviewChoice.BaseBranch,
                    onSelect = { choice = ReviewChoice.BaseBranch },
                )
                if (choice == ReviewChoice.BaseBranch) {
                    ReviewFields {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.review_field_branch),
                                fontSize = UiType.Meta,
                                lineHeight = UiType.MetaLine,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(UiConsts.Space4))
                            TextField(
                                value = branch,
                                onValueChange = { branch = it },
                                label =
                                    (stringResource(R.string.review_field_branch_placeholder))
                                        .orEmpty(),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                ReviewChoiceRow(
                    choice = ReviewChoice.Commit,
                    selected = choice == ReviewChoice.Commit,
                    onSelect = { choice = ReviewChoice.Commit },
                )
                if (choice == ReviewChoice.Commit) {
                    ReviewFields {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.review_field_sha),
                                fontSize = UiType.Meta,
                                lineHeight = UiType.MetaLine,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(UiConsts.Space4))
                            TextField(
                                value = sha,
                                onValueChange = { sha = it },
                                label =
                                    (stringResource(R.string.review_field_sha_placeholder))
                                        .orEmpty(),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                            )
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.review_field_commit_title),
                                fontSize = UiType.Meta,
                                lineHeight = UiType.MetaLine,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(UiConsts.Space4))
                            TextField(
                                value = commitTitle,
                                onValueChange = { commitTitle = it },
                                label =
                                    (stringResource(R.string.review_field_commit_title_placeholder))
                                        .orEmpty(),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                ReviewChoiceRow(
                    choice = ReviewChoice.Custom,
                    selected = choice == ReviewChoice.Custom,
                    onSelect = { choice = ReviewChoice.Custom },
                )
                if (choice == ReviewChoice.Custom) {
                    ReviewFields {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.review_field_instructions),
                                fontSize = UiType.Meta,
                                lineHeight = UiType.MetaLine,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(UiConsts.Space4))
                            TextField(
                                value = instructions,
                                onValueChange = { instructions = it },
                                label =
                                    (stringResource(R.string.review_field_instructions_placeholder))
                                        .orEmpty(),
                                useLabelAsPlaceholder = true,
                                singleLine = false,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { target?.let { onEvent(AppEvent.StartReview(threadId, it)) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = target != null,
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_start),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The answer to "start review" lands in the transcript, not on this page; say where it goes.
            Text(
                text = stringResource(R.string.review_footnote),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** Whole row is the hit target; a text field inside it would swallow the selecting tap. */
@Composable
private fun ReviewChoiceRow(
    choice: ReviewChoice,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .squircleSurface(
                    color = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                    cornerRadius = UiConsts.RowCorner,
                )
                .combinedClickable(onClick = onSelect)
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.title(),
                fontSize = UiType.SheetRowTitle,
                lineHeight = UiType.SheetRowTitleLine,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = colors.onSurface,
            )
            Text(
                text = choice.detail(),
                modifier = Modifier.padding(top = UiConsts.Space1),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space8))
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.review_selected),
                modifier = Modifier.size(UiConsts.IconRow),
                tint = colors.primary,
            )
        }
    }
}

@Composable
private fun ReviewFields(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = UiConsts.RowIndent,
                    end = UiConsts.Space4,
                    bottom = UiConsts.Space10,
                ),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        content = content,
    )
}

/** A local enum, not the target: three variants cannot exist until their fields are non-blank. */
private enum class ReviewChoice {
    Uncommitted,

    BaseBranch,

    Commit,

    Custom,
}

@Composable
@ReadOnlyComposable
private fun ReviewChoice.title(): String =
    stringResource(
        when (this) {
            ReviewChoice.Uncommitted -> R.string.review_target_uncommitted
            ReviewChoice.BaseBranch -> R.string.review_target_base_branch
            ReviewChoice.Commit -> R.string.review_target_commit
            ReviewChoice.Custom -> R.string.review_target_custom
        }
    )

@Composable
@ReadOnlyComposable
private fun ReviewChoice.detail(): String =
    stringResource(
        when (this) {
            ReviewChoice.Uncommitted -> R.string.review_target_uncommitted_detail
            ReviewChoice.BaseBranch -> R.string.review_target_base_branch_detail
            ReviewChoice.Commit -> R.string.review_target_commit_detail
            ReviewChoice.Custom -> R.string.review_target_custom_detail
        }
    )

/** The target the selection describes, or `null` while a required field is blank; trimming keeps keyboard whitespace out of the payload. */
private fun reviewTarget(
    choice: ReviewChoice,
    branch: String,
    sha: String,
    commitTitle: String,
    instructions: String,
): ReviewTarget? =
    when (choice) {
        ReviewChoice.Uncommitted -> ReviewTarget.UncommittedChanges
        ReviewChoice.BaseBranch ->
            branch.trim().takeIf { it.isNotEmpty() }?.let { ReviewTarget.BaseBranch(it) }

        ReviewChoice.Commit ->
            sha.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { ReviewTarget.Commit(sha = it, title = commitTitle.trim().ifEmpty { null }) }

        ReviewChoice.Custom ->
            instructions.trim().takeIf { it.isNotEmpty() }?.let { ReviewTarget.Custom(it) }
    }
