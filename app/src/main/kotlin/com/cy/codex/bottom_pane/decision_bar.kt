package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared decision pills for the approval dialog (codex-rs/tui/src/bottom_pane/approval_overlay.rs). */
enum class DecisionRole {
    Primary,
    Secondary,
    Destructive,
}

/** The protocol label and hierarchy of one approval choice. */
data class DecisionAction(
    val label: String,
    val role: DecisionRole = DecisionRole.Secondary,
    val onClick: () -> Unit,
)

/** Wrapping pill row; wraps instead of scrolling so the destructive escape hatch never goes off-screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecisionRow(
    decisions: List<DecisionAction>,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        decisions.forEach { action ->
            Button(
                onClick = action.onClick,
                modifier =
                    Modifier.then(
                        if (action.role == DecisionRole.Destructive)
                            Modifier.squircleBorder(
                                UiConsts.OutlineThickness,
                                if (!busy) MiuixTheme.colorScheme.error.copy(alpha = 0.5f)
                                else MiuixTheme.colorScheme.outline.copy(alpha = 0.18f),
                                UiConsts.ButtonHeight / 2,
                            )
                        else Modifier
                    ),
                enabled = !busy,
                colors =
                    when (action.role) {
                        DecisionRole.Primary -> ButtonDefaults.buttonColorsPrimary()
                        DecisionRole.Secondary -> ButtonDefaults.buttonColors()
                        DecisionRole.Destructive ->
                            ButtonColors(
                                color = Color.Transparent,
                                disabledColor =
                                    MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                                contentColor = MiuixTheme.colorScheme.error,
                                disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                            )
                    },
                cornerRadius = UiConsts.ButtonHeight / 2,
                minWidth = UiConsts.ButtonMinWidth,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = action.label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Remaining-queue footer, muted because the queue is a fact, not a warning. */
@Composable
internal fun RemainingQueueLine(remainingQueue: Int) {
    if (remainingQueue <= 0) return
    Spacer(Modifier.height(UiConsts.Space10))
    Text(
        text = stringResource(R.string.decision_bar_remaining_queue, remainingQueue),
        modifier = Modifier.fillMaxWidth(),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
    )
}

/** Confirm + cancel row for the two form dialogs; they cannot use [DecisionRow] because their primary action has a disabled-visible invalid state. The buttons split the width evenly. */
@Composable
internal fun FormButtons(
    confirmLabel: String,
    enabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.decision_bar_cancel),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = confirmLabel,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun DialogSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}
