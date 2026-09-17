package com.cy.codexui.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.v2.ModelPreset
import com.cy.codexui.protocol.protocol.v2.ReasoningEffort
import com.cy.codexui.label
import com.cy.codexui.status.formatTokens
import com.cy.codexui.ModalSheet
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.pressableRow
import com.cy.codexui.raisedSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Model and reasoning-effort pickers.
 *
 * Mirrors `chatwidget/model_popups.rs`: the model list comes from `model/list`, and the effort row
 * only offers the levels the *selected* model actually supports, plus the level it defaults to.
 * [EffortSheet] is the same control reduced to a single question, so the status card can reuse it
 * without going through the model list.
 */

/** `model/list` row: name, what it is good at, the context window and its default effort. */
@Composable
fun ModelSheet(
    models: List<ModelPreset>,
    selectedModel: String,
    effort: ReasoningEffort,
    onModel: (String) -> Unit,
    onEffort: (ReasoningEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val selected = models.firstOrNull { it.isSelectedBy(selectedModel) }
    val efforts = selected?.supportedReasoningEfforts?.takeIf { it.isNotEmpty() }
        ?: ReasoningEffort.entries.toList()
    val current = selected?.displayName ?: selectedModel
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.model_sheet_title),
        subtitle = stringResource(R.string.model_sheet_subtitle, current, effort.label()),
    ) {
        if (models.isEmpty()) {
            Text(
                text = stringResource(R.string.model_sheet_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = UiConsts.Space20),
                fontSize = UiType.Subtitle,
                lineHeight = UiType.SubtitleLine,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            models.forEach { preset ->
                ModelRow(
                    preset = preset,
                    selected = preset.isSelectedBy(selectedModel),
                    onClick = { onModel(preset.model) },
                )
            }
        }
        Spacer(Modifier.size(UiConsts.Space4))
        Text(
            text = stringResource(R.string.model_sheet_effort_section),
            modifier = Modifier.padding(start = UiConsts.Space4, top = UiConsts.Space4),
            fontSize = UiType.Subtitle,
            lineHeight = UiType.SubtitleLine,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariantSummary,
        )
        EffortChips(
            efforts = efforts,
            selected = effort,
            onPick = onEffort,
            modifier = Modifier.padding(top = UiConsts.Space2),
        )
        val modelName = selected?.displayName ?: stringResource(R.string.model_sheet_this_model)
        val defaultLevel = (selected?.defaultReasoningEffort ?: effort).label()
        Text(
            text = stringResource(
                R.string.model_sheet_effort_default_and_hint,
                modelName,
                defaultLevel,
                effort.hint(),
            ),
            modifier = Modifier.padding(start = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * Single-question effort picker: the levels a model supports, nothing else. Used by the status
 * card's effort row.
 */
@Composable
fun EffortSheet(
    efforts: List<ReasoningEffort>,
    selected: ReasoningEffort,
    onPick: (ReasoningEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val levels = efforts.takeIf { it.isNotEmpty() } ?: ReasoningEffort.entries.toList()
    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.model_sheet_effort_section),
        subtitle = stringResource(R.string.model_sheet_effort_sheet_subtitle),
    ) {
        levels.forEach { level ->
            EffortRow(
                effort = level,
                selected = level == selected,
                onClick = { onPick(level) },
            )
        }
        Text(
            text = stringResource(
                R.string.model_sheet_effort_current,
                selected.label(),
                selected.hint(),
            ),
            modifier = Modifier.padding(start = UiConsts.Space4, top = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

/** Horizontal chip strip of the efforts a model supports. */
@Composable
internal fun EffortChips(
    efforts: List<ReasoningEffort>,
    selected: ReasoningEffort,
    onPick: (ReasoningEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.PillCorner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        efforts.forEach { level ->
            val active = level == selected
            Text(
                text = level.label(),
                modifier = Modifier
                    .pressableRow(
                        shape = shape,
                        container = if (active) colors.primary else raisedSurface(),
                        onClick = { onPick(level) },
                    )
                    .padding(horizontal = UiConsts.Space14, vertical = UiConsts.Space8),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = if (active) colors.onPrimary else colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ModelRow(
    preset: ModelPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = shape,
                container = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
            )
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = preset.displayName,
                    fontSize = UiType.SheetRowTitle,
                    lineHeight = UiType.SheetRowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (preset.isDefault) {
                    Spacer(Modifier.width(UiConsts.Space6))
                    Text(
                        text = stringResource(R.string.model_sheet_default_badge),
                        modifier = Modifier
                            .clip(RoundedCornerShape(UiConsts.BadgeCorner))
                            .background(colors.primary.copy(alpha = 0.14f))
                            .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
                        fontSize = UiType.Badge,
                        lineHeight = UiType.BadgeLine,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        maxLines = 1,
                    )
                }
            }
            if (preset.description.isNotBlank()) {
                Text(
                    text = preset.description,
                    modifier = Modifier.padding(top = UiConsts.Space2),
                    fontSize = UiType.RowDetail,
                    lineHeight = UiType.RowDetailLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    R.string.model_sheet_context,
                    preset.defaultReasoningEffort.label(),
                ),
                modifier = Modifier.padding(top = UiConsts.Space2),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space8))
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.model_sheet_selected_model),
                modifier = Modifier.size(UiConsts.IconRow),
                tint = colors.primary,
            )
        }
    }
}

@Composable
private fun EffortRow(
    effort: ReasoningEffort,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = shape,
                container = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
            )
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = effort.label(),
                fontSize = UiType.SheetRowTitle,
                lineHeight = UiType.SheetRowTitleLine,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = colors.onSurface,
                maxLines = 1,
            )
            Text(
                text = effort.hint(),
                modifier = Modifier.padding(top = UiConsts.Space1),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space8))
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.model_sheet_selected_effort),
                modifier = Modifier.size(UiConsts.IconRow),
                tint = colors.primary,
            )
        }
    }
}

/** One sentence on what an effort level buys, mirroring the effort status line. */
@Composable
@ReadOnlyComposable
internal fun ReasoningEffort.hint(): String = when (this) {
    ReasoningEffort.None -> stringResource(R.string.reasoning_effort_none)
    ReasoningEffort.Minimal -> stringResource(R.string.model_sheet_hint_minimal)
    ReasoningEffort.Low -> stringResource(R.string.model_sheet_hint_low)
    ReasoningEffort.Medium -> stringResource(R.string.model_sheet_hint_medium)
    ReasoningEffort.High -> stringResource(R.string.model_sheet_hint_high)
    ReasoningEffort.XHigh -> stringResource(R.string.model_sheet_hint_xhigh)
    ReasoningEffort.Max -> stringResource(R.string.reasoning_effort_max)
    ReasoningEffort.Ultra -> stringResource(R.string.reasoning_effort_ultra)
}

/** A preset answers to both its catalog id and its wire model name. */
private fun ModelPreset.isSelectedBy(selected: String): Boolean =
    selected.isNotBlank() && (model == selected || id == selected)
