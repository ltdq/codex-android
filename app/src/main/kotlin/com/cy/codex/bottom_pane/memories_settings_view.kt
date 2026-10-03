package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.MemoryStatusResponse
import com.cy.codex.raisedSurface
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.successColor
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The memory store: ready flag and consolidated count, the whole of `memory/…`
 * (codex-rs/tui/src/bottom_pane/memories_settings_view.rs); never rendered as an empty list,
 * which would claim the store holds nothing.
 *
 * @param catalog read for [CatalogState.memories]; `null` is distinct from a zero count.
 */
@Composable
fun MemoriesScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val memories = catalog.memories
    var resetting by remember { mutableStateOf(false) }

    CodexPage(
        title = stringResource(R.string.memories_screen_title),
        description = memoriesSubtitle(memories),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { onEvent(AppEvent.ReloadMemories) },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(R.string.memories_screen_refresh),
                    modifier = Modifier.size(UiConsts.IconRefresh),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) {
        MemoriesStatusSection(memories = memories, onRead = { onEvent(AppEvent.ReloadMemories) })
        MemoriesResetSection(onReset = { resetting = true })
    }

    if (resetting) {
        ResetMemorySheet(
            onDismiss = { resetting = false },
            onConfirm = {
                onEvent(AppEvent.ResetMemory)
                resetting = false
            },
        )
    }
}

@Composable
private fun memoriesSubtitle(memories: MemoryStatusResponse?): String =
    when {
        memories == null -> stringResource(R.string.memories_screen_subtitle_unknown)
        memories.v2Ready ->
            stringResource(
                R.string.memories_screen_subtitle_ready,
                memories.v2ConsolidatedThreads,
            )

        else -> stringResource(R.string.memories_screen_subtitle_not_ready)
    }

/** What the store reported; "not asked yet" stays apart from "store is empty". */
@Composable
private fun MemoriesStatusSection(
    memories: MemoryStatusResponse?,
    onRead: () -> Unit,
) {
    CodexSection(stringResource(R.string.memories_screen_section_status)) {
        if (memories == null) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier.size(UiConsts.IconBoxLarge)
                            .squircleBackground(
                                color = raisedSurface(),
                                cornerRadius = UiConsts.CornerCard,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.memories_screen_not_asked),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.memories_screen_not_asked_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space16))
                Button(
                    onClick = onRead,
                    colors = ButtonDefaults.buttonColors(),
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minWidth = 0.dp,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontal,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.memories_screen_check),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            return@CodexSection
        }

        CodexRow(
            title = stringResource(R.string.memories_screen_store),
            endAction = {
                Text(
                    text =
                        if (memories.v2Ready) {
                            stringResource(R.string.memories_screen_store_ready)
                        } else {
                            stringResource(R.string.memories_screen_store_not_ready)
                        },
                    fontSize = UiType.Value,
                    lineHeight = UiType.ValueLine,
                    color = if (memories.v2Ready) successColor() else warningColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.memories_screen_consolidated),
            value = memories.v2ConsolidatedThreads.toString(),
        )
        if (memories.v2ConsolidatedThreads == 0) {
            MemoriesNote(stringResource(R.string.memories_screen_store_empty))
        }
        MemoriesNote(stringResource(R.string.memories_screen_count_only))
    }
}

/** Reset opens a confirmation sheet and is not gated on a successful read — it is the way out of a wedged store. */
@Composable
private fun MemoriesResetSection(onReset: () -> Unit) {
    CodexSection(stringResource(R.string.memories_screen_reset_section)) {
        MemoriesNote(stringResource(R.string.memories_screen_reset_note))
        Button(
            onClick = onReset,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space12,
                        bottom = UiConsts.RowInset,
                    )
                    .squircleBorder(
                        UiConsts.OutlineThickness,
                        MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                        UiConsts.ButtonHeight / 2,
                    ),
            colors =
                ButtonColors(
                    color = Color.Transparent,
                    disabledColor = MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                    contentColor = MiuixTheme.colorScheme.error,
                    disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                ),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.memories_screen_reset_action),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Footnote on a card's inner rail; always the page's own copy, never a value off the wire. */
@Composable
private fun MemoriesNote(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.padding(
                start = UiConsts.RowInset,
                end = UiConsts.RowInset,
                top = UiConsts.Space8,
                bottom = UiConsts.Space8,
            ),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** Confirmation in front of [AppEvent.ResetMemory]; [onDismiss] is both refusal and the end of the exit animation. */
@Composable
private fun ResetMemorySheet(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.memories_screen_reset_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
        dragHandleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = stringResource(R.string.memories_screen_reset_subtitle),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Text(
                    text = stringResource(R.string.memories_screen_reset_body),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space4),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.memories_screen_reset_cancel),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier =
                            Modifier.weight(1f)
                                .squircleBorder(
                                    UiConsts.OutlineThickness,
                                    MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                    UiConsts.ButtonHeight / 2,
                                ),
                        colors =
                            ButtonColors(
                                color = Color.Transparent,
                                disabledColor =
                                    MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                                contentColor = MiuixTheme.colorScheme.error,
                                disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                            ),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minWidth = 0.dp,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.memories_screen_reset_confirm),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
