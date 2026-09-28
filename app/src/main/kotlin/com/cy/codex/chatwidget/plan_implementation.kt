package com.cy.codex.chatwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.v2.PlanStep
import com.cy.codex.protocol.protocol.v2.PlanStepStatus
import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.raisedSurface
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.successColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The plan timeline, a pure function of [steps] — the whole plan arrives from `turn/plan/updated`.
 * Mirrors `chatwidget/plan_implementation.rs` and the plan section of `status/card.rs`.
 */
@Composable
fun PlanTimeline(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val done = steps.count { it.status == PlanStepStatus.Completed }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.plan_timeline_summary, steps.size, done),
            modifier = Modifier.padding(bottom = UiConsts.Space6),
            fontSize = UiType.Footnote,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        steps.forEachIndexed { index, step ->
            PlanStepRow(
                step = step,
                first = index == 0,
                last = index == steps.lastIndex,
            )
        }
    }
}

@Composable
fun PlanProgressChip(
    steps: List<PlanStep>,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val done = steps.count { it.status == PlanStepStatus.Completed }
    val accent = if (done == steps.size) successColor() else colors.primary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = UiConsts.PillCorner))
            .background(raisedSurface())
            .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.plan_timeline_chip, done, steps.size),
            fontSize = UiType.Footnote,
            lineHeight = UiType.MetaLine,
            fontWeight = FontWeight.Medium,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.width(UiConsts.Space7))
        LinearProgressIndicator(
            modifier = Modifier.width(UiConsts.ProgressWidthCompact),
            progress = (done.toFloat() / steps.size.toFloat()).coerceIn(0f, 1f),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = accent,
                backgroundColor = colors.onSurface.copy(alpha = 0.1f),
            ),
            height = UiConsts.Space3,
        )
    }
}

@Composable
private fun PlanStepRow(
    step: PlanStep,
    first: Boolean,
    last: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val accent = planStepColor(step.status)
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .width(UiConsts.Space20)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .width(UiConsts.Space2)
                    .height(UiConsts.Space4)
                    .background(if (first) Color.Transparent else planRailColor(step.status)),
            )
            PlanNode(status = step.status)
            if (!last) {
                Box(
                    modifier = Modifier
                        .width(UiConsts.Space2)
                        .weight(1f)
                        .background(planRailColor(step.status)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = UiConsts.Space6, bottom = if (last) UiConsts.Space0 else UiConsts.Space10),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space1),
        ) {
            Text(
                text = step.step,
                fontSize = UiType.Subtitle,
                lineHeight = UiType.SubtitleLine,
                color = if (step.status == PlanStepStatus.Completed) {
                    colors.onSurfaceVariantSummary
                } else {
                    colors.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = step.status.label(),
                fontSize = UiType.Footnote,
                lineHeight = UiType.MetaLine,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlanNode(status: PlanStepStatus) {
    val colors = MiuixTheme.colorScheme
    val accent = planStepColor(status)
    Box(modifier = Modifier.size(UiConsts.PlanNodeSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(UiConsts.PlanNodeSize)) {
            val stroke = UiConsts.PlanNodeStroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            when (status) {
                PlanStepStatus.Completed -> drawCircle(color = accent, radius = radius)

                PlanStepStatus.InProgress -> {
                    drawArc(
                        color = accent,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                    )
                    drawCircle(color = accent, radius = radius, style = Stroke(width = stroke))
                }

                PlanStepStatus.Pending -> drawCircle(
                    color = accent,
                    radius = radius,
                    style = Stroke(width = stroke),
                )
            }
        }
        if (status == PlanStepStatus.Completed) {
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.Space9),
                tint = colors.background,
            )
        }
    }
}

@Composable
private fun planStepColor(status: PlanStepStatus): Color = when (status) {
    PlanStepStatus.Completed -> successColor()
    PlanStepStatus.InProgress -> MiuixTheme.colorScheme.primary
    PlanStepStatus.Pending -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

@Composable
private fun planRailColor(status: PlanStepStatus): Color =
    if (status == PlanStepStatus.Completed) {
        successColor().copy(alpha = 0.5f)
    } else {
        MiuixTheme.colorScheme.dividerLine
    }

@Composable
@ReadOnlyComposable
internal fun PlanStepStatus.label(): String = stringResource(
    when (this) {
        PlanStepStatus.Pending -> R.string.plan_timeline_pending
        PlanStepStatus.InProgress -> R.string.plan_timeline_in_progress
        PlanStepStatus.Completed -> R.string.plan_timeline_completed
    },
)

/** The prompt the implement row submits (`PLAN_IMPLEMENTATION_CODING_MESSAGE`). */
internal const val PlanImplementationCodingMessage = "Implement the plan."

/** Prepended to the approved plan by the fresh-context row (`PLAN_IMPLEMENTATION_CLEAR_CONTEXT_PREFIX`). */
internal const val PlanImplementationClearContextPrefix =
    "A previous agent produced the plan below to accomplish the user's task. " +
        "Implement the plan in a fresh context. Treat the plan as the source of " +
        "user intent, re-read files as needed, and carry the work through " +
        "implementation and verification."

/** Why an implementing row cannot run, or `null` when it can. */
internal enum class PlanImplementationBlock { DefaultModeUnavailable, NoApprovedPlan }

/** Availability of the two implementing rows; `null` per row means the row is enabled. */
internal data class PlanImplementationOptions(
    val implement: PlanImplementationBlock? = null,
    val clearContext: PlanImplementationBlock? = null,
)

/** A plan-mode turn that finished with a plan, awaiting the user's choice. */
data class PlanImplementationPrompt(val threadId: String, val planMarkdown: String?)

/**
 * Both rows need the catalogue's Default mode, and the fresh-context row also needs an approved
 * plan (`selection_view_params`, codex-rs/tui/src/chatwidget/plan_implementation.rs).
 */
internal fun planImplementationOptions(
    defaultModeAvailable: Boolean,
    planMarkdown: String?,
): PlanImplementationOptions = PlanImplementationOptions(
    implement = if (defaultModeAvailable) null else PlanImplementationBlock.DefaultModeUnavailable,
    clearContext = when {
        !defaultModeAvailable -> PlanImplementationBlock.DefaultModeUnavailable
        planMarkdown.isNullOrBlank() -> PlanImplementationBlock.NoApprovedPlan
        else -> null
    },
)

/** The fresh thread's first message: the prefix and the approved plan. */
internal fun planImplementationClearContextMessage(planMarkdown: String): String =
    "$PlanImplementationClearContextPrefix\n\n$planMarkdown"

/**
 * Context-used label for the fresh-context row, or `null` for a fresh or unknown window
 * (`plan_implementation_context_usage_label`, codex-rs/tui/src/chatwidget/turn_runtime.rs).
 * [compactTokens] words the fallback the prompt takes when the server reports tokens without a
 * window; upstream returns no label at all when both are unknown.
 */
internal fun planImplementationContextUsageLabel(
    usage: ThreadTokenUsage,
    compactTokens: (Long) -> String,
): String? {
    val remaining = usage.contextRemainingPercent()
    if (remaining != null) {
        val used = 100 - remaining
        return if (used <= 0) null else "$used% used"
    }
    val tokens = usage.total.totalTokens
    return if (tokens > 0) "${compactTokens(tokens)} used" else null
}

/**
 * Confirmation after a plan-mode turn produced a plan (chatwidget/plan_implementation.rs):
 * implement it in this thread, implement it in a fresh one, or keep planning.
 */
@Composable
fun PlanImplementationSheet(
    planMarkdown: String?,
    defaultModeAvailable: Boolean,
    contextUsageLabel: String?,
    onImplement: () -> Unit,
    onClearContext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = planImplementationOptions(defaultModeAvailable, planMarkdown)
    val freshContextDetail = stringResource(R.string.plan_implementation_clear_context_fresh)
    val usedContextDetail = if (contextUsageLabel == null) {
        freshContextDetail
    } else {
        stringResource(R.string.plan_implementation_clear_context_used, contextUsageLabel)
    }
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.plan_implementation_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space2),
        ) {
            PlanImplementationRow(
                title = stringResource(R.string.plan_implementation_yes),
                detail = stringResource(R.string.plan_implementation_yes_detail),
                block = options.implement,
                onSelect = onImplement,
            )
            PlanImplementationRow(
                title = stringResource(R.string.plan_implementation_clear_context),
                detail = usedContextDetail,
                block = options.clearContext,
                onSelect = onClearContext,
            )
            PlanImplementationRow(
                title = stringResource(R.string.plan_implementation_no),
                detail = stringResource(R.string.plan_implementation_no_detail),
                block = null,
                onSelect = onDismiss,
            )
        }
    }
}

@Composable
private fun PlanImplementationRow(
    title: String,
    detail: String,
    block: PlanImplementationBlock?,
    onSelect: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val enabled = block == null
    val detailText = if (block == null) detail else block.reason()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .combinedClickable(enabled = enabled, onClick = onSelect)
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = UiType.SheetRowTitle,
                lineHeight = UiType.SheetRowTitleLine,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariantSummary,
            )
            Text(
                text = detailText,
                modifier = Modifier.padding(top = UiConsts.Space1),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun PlanImplementationBlock.reason(): String = stringResource(
    when (this) {
        PlanImplementationBlock.DefaultModeUnavailable ->
            R.string.plan_implementation_default_unavailable

        PlanImplementationBlock.NoApprovedPlan -> R.string.plan_implementation_no_approved_plan
    },
)
