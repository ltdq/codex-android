package com.cy.codexui.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.v2.GoalStatus
import com.cy.codexui.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codexui.label
import com.cy.codexui.status.formatTokens
import com.cy.codexui.ButtonRole
import com.cy.codexui.CodexButton
import com.cy.codexui.ModalSheet
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.pressableRow
import com.cy.codexui.successColor
import com.cy.codexui.warningColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Goal mode sheet.
 *
 * Mirrors `chatwidget/goal_menu.rs` and `chatwidget/goal_status.rs`: with no goal the sheet is a
 * single objective field that starts one, with a goal it reports progress — the same
 * `tokensUsed` / `timeUsedSeconds` numbers the status line shows — and offers to clear it.
 */
@Composable
fun GoalSheet(
    goal: ThreadGoalUpdated?,
    onSet: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var objective by remember(goal?.threadId, goal?.objective) { mutableStateOf("") }
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.goal_sheet_title),
        subtitle = if (goal == null) {
            stringResource(R.string.goal_sheet_subtitle_idle)
        } else {
            goal.status.label()
        },
    ) {
        if (goal == null) {
            TextField(
                value = objective,
                onValueChange = { objective = it },
                modifier = Modifier.fillMaxWidth(),
                insideMargin = DpSize(UiConsts.Space12, UiConsts.Space10),
                label = stringResource(R.string.goal_sheet_objective_hint),
                minLines = 2,
                maxLines = 4,
            )
            Text(
                text = stringResource(R.string.goal_sheet_objective_example),
                modifier = Modifier.padding(
                    horizontal = UiConsts.Space4,
                    vertical = UiConsts.Space2,
                ),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
            )
            CodexButton(
                text = stringResource(R.string.goal_sheet_start),
                enabled = objective.isNotBlank(),
                onClick = {
                    onSet(objective.trim())
                    onDismiss()
                },
                modifier = Modifier.padding(top = UiConsts.Space4),
            )
        } else {
            val usage = stringResource(
                R.string.goal_sheet_usage,
                formatTokens(goal.tokensUsed),
                formatGoalDuration(goal.timeUsedSeconds),
            )
            Row(
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GoalStatusChip(status = goal.status)
                Spacer(Modifier.width(UiConsts.Space8))
                Text(
                    text = usage,
                    fontSize = UiType.RowDetail,
                    lineHeight = UiType.RowDetailLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Text(
                text = goal.objective,
                modifier = Modifier.padding(
                    horizontal = UiConsts.Space4,
                    vertical = UiConsts.Space4,
                ),
                fontSize = UiType.SheetBody,
                lineHeight = UiType.SheetBodyLine,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.goal_sheet_progress_hint),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
            )
            CodexButton(
                text = stringResource(R.string.goal_sheet_clear),
                role = ButtonRole.Secondary,
                onClick = {
                    onClear()
                    onDismiss()
                },
                modifier = Modifier.padding(top = UiConsts.Space4),
            )
        }
    }
}

/** Status pill of a running goal, coloured by how the run ended or whether it is still going. */
@Composable
internal fun GoalStatusChip(status: GoalStatus) {
    val accent = goalStatusColor(status)
    Text(
        text = status.label(),
        modifier = Modifier
            .clip(RoundedCornerShape(UiConsts.PillCorner))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space3),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 1,
    )
}

/** Active goals run, paused ones wait, budget-limited ones need a decision, complete ones are done. */
@Composable
internal fun goalStatusColor(status: GoalStatus): Color = when (status) {
    GoalStatus.Active -> MiuixTheme.colorScheme.primary
    GoalStatus.Paused -> warningColor()
    GoalStatus.Blocked -> warningColor()
    GoalStatus.UsageLimited -> MiuixTheme.colorScheme.error
    GoalStatus.BudgetLimited -> MiuixTheme.colorScheme.error
    GoalStatus.Complete -> successColor()
}

/**
 * Elapsed goal time in *seconds*: `200` becomes `3 分 20 秒`, `3900` becomes `1 小时 5 分`.
 *
 * Named for its unit because `chatwidget/cell/ExecCell.kt` has a formatter for the same shape of
 * value measured in milliseconds. Both used to be called `formatDuration`, which is a 1000x error
 * waiting for whoever imports the wrong one.
 */
@Composable
@ReadOnlyComposable
internal fun formatGoalDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remaining = total % 60
    return when {
        hours > 0 -> if (minutes > 0) {
            stringResource(R.string.goal_sheet_duration_hours_minutes, hours, minutes)
        } else {
            stringResource(R.string.goal_sheet_duration_hours, hours)
        }

        minutes > 0 -> if (remaining > 0) {
            stringResource(R.string.goal_sheet_duration_minutes_seconds, minutes, remaining)
        } else {
            stringResource(R.string.goal_sheet_duration_minutes, minutes)
        }

        else -> stringResource(R.string.goal_sheet_duration_seconds, remaining)
    }
}
