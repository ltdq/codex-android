package com.cy.codex.external_agent_config_migration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValue
import com.cy.codex.ImportProgress
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportHistory
import com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem
import com.cy.codex.raisedSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Bringing another agent's configuration into Codex, mirroring the
 * `codex-rs/tui/src/external_agent_config_migration/` module: detect, choose, import, kept on one
 * page because each step is input to the next. Detection is a read; import is the only write; the
 * page loads its own data on the way in.
 */
@Composable
fun ExternalAgentImportScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val items = catalog.externalAgentConfig
    val histories = catalog.externalAgentImportHistories
    // No per-item id on the wire: selection is keyed by type, scope and description.
    val selection =
        remember(items) {
            mutableStateMapOf<String, Boolean>().apply {
                items.forEach { put(it.selectionKey(), true) }
            }
        }
    val selected = items.filter { selection[it.selectionKey()] == true }

    // One event covers both reads; a failed detection still leaves the history on screen.
    LaunchedEffect(Unit) { onEvent(AppEvent.ReloadExternalAgentConfig) }

    CodexPage(
        title = stringResource(R.string.migration_screen_title),
        description = stringResource(R.string.migration_screen_subtitle, items.size),
        onBack = onBack,
        modifier = modifier,
    ) {
        MigrationDetectSection(onEvent = onEvent)
        MigrationSelectionSection(
            items = items,
            selected = selection,
            selectedCount = selected.size,
            onToggle = { key, checked -> selection[key] = checked },
            onSelectAll = { value -> items.forEach { selection[it.selectionKey()] = value } },
            onImport = { onEvent(AppEvent.ImportExternalAgentConfig(selected)) },
        )
        val progress = catalog.externalAgentImport
        if (progress != null) {
            MigrationProgressSection(progress = progress)
        }
        MigrationHistorySection(histories = histories)
    }
}

/** Item identity within the page; the description is part of the key — a directory can produce
 * several items of the same type (one `SKILLS` row per skill). */
private fun ExternalAgentConfigMigrationItem.selectionKey(): String =
    "$itemType\u0000${cwd.orEmpty()}\u0000$description"

/** The detect step; its button is secondary — a read that writes nothing must not compete with
 * the import for the page's one filled pill. */
@Composable
private fun MigrationDetectSection(onEvent: (AppEvent) -> Unit) {
    val colors = MiuixTheme.colorScheme
    CodexSection(stringResource(R.string.migration_detect_section)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                    )
                    .clip(remember { RoundedCornerShape(UiConsts.CornerRow) })
                    .background(codeSurface())
                    .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space7),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconInline),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(UiConsts.Space7))
            Text(
                text = stringResource(R.string.migration_detect_note),
                modifier = Modifier.weight(1f),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Button(
            onClick = { onEvent(AppEvent.DetectExternalAgentConfig) },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space10,
                        bottom = UiConsts.Space8,
                    ),
            colors = ButtonDefaults.buttonColors(),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.migration_detect_action),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The choose step; draft selection lives here, not in [CatalogState] — a half-made choice is not
 * server state, and the progress card below vanishes with the run. */
@Composable
private fun MigrationSelectionSection(
    items: List<ExternalAgentConfigMigrationItem>,
    selected: Map<String, Boolean>,
    selectedCount: Int,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        CodexGroupTitle(stringResource(R.string.migration_selection_section))
        if (items.isEmpty()) {
            CodexSection {
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
                            imageVector = MiuixIcons.ConvertFile,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.migration_selection_empty),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = stringResource(R.string.migration_selection_empty_detail),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = UiConsts.Space6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onSelectAll(true) },
                    enabled = items.any { selected[it.selectionKey()] != true },
                    colors = ButtonDefaults.buttonColors(),
                    cornerRadius = UiConsts.ButtonHeightCompact / 2,
                    minHeight = UiConsts.ButtonHeightCompact,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.migration_selection_select_all),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(UiConsts.Space6))
                Button(
                    onClick = { onSelectAll(false) },
                    enabled = items.any { selected[it.selectionKey()] == true },
                    colors = ButtonDefaults.buttonColors(),
                    cornerRadius = UiConsts.ButtonHeightCompact / 2,
                    minHeight = UiConsts.ButtonHeightCompact,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.migration_selection_select_none),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.migration_selection_count, selectedCount),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
            CodexCardGrid(count = items.size) { index ->
                val item = items[index]
                MigrationItemCard(
                    item = item,
                    checked = selected[item.selectionKey()] == true,
                    onToggle = { onToggle(item.selectionKey(), it) },
                )
            }
        }
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().padding(top = UiConsts.SectionGap),
            enabled = selectedCount > 0,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.migration_selection_import),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One detected item as a catalogue card: what it is, where it came from, and its pick. */
@Composable
private fun MigrationItemCard(
    item: ExternalAgentConfigMigrationItem,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val kind = migrationItemTypeLabel(item.itemType)
    val subtitle =
        when {
            item.description.isBlank() -> item.cwd ?: kind
            item.cwd == null -> "$kind · ${item.description}"
            else -> "$kind · ${item.description} · ${item.cwd}"
        }
    CodexCatalogCard(
        title = item.description.ifBlank { kind },
        description = subtitle,
        icon = MiuixIcons.ConvertFile,
        onClick = { onToggle(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onToggle)
        },
    )
}

@Composable
@ReadOnlyComposable
private fun migrationItemTypeLabel(type: String): String =
    stringResource(
        when (type) {
            "AGENTS_MD" -> R.string.migration_kind_agents_md
            "CONFIG" -> R.string.migration_kind_config
            "SKILLS" -> R.string.migration_kind_skills
            "PLUGINS" -> R.string.migration_kind_plugins
            "MCP_SERVER_CONFIG" -> R.string.migration_kind_mcp_server_config
            "SUBAGENTS" -> R.string.migration_kind_subagents
            "HOOKS" -> R.string.migration_kind_hooks
            "COMMANDS" -> R.string.migration_kind_commands
            "MEMORY" -> R.string.migration_kind_memory
            "SESSIONS" -> R.string.migration_kind_sessions
            else -> R.string.migration_kind_unknown
        }
    )

/** Composed only while an import runs, so the section's presence *is* the running state. */
@Composable
private fun MigrationProgressSection(progress: ImportProgress) {
    val colors = MiuixTheme.colorScheme
    // Zero total = unknown size; clamp instead of divide, or the bar draws NaN.
    val fraction =
        if (progress.total > 0) {
            (progress.imported.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    CodexSection(stringResource(R.string.migration_progress_section)) {
        CodexRow(
            title = progress.label,
            endAction = {
                CodexValue(
                    stringResource(
                        R.string.migration_progress_count,
                        progress.imported,
                        progress.total,
                    )
                )
            },
        )
        LinearProgressIndicator(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                        bottom = UiConsts.Space8,
                    ),
            progress = fraction,
            colors =
                ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = colors.primary,
                    backgroundColor = colors.onSurface.copy(alpha = 0.08f),
                ),
            height = UiConsts.ProgressHeight,
        )
    }
}

/** The audit trail, newest first; read-only — history is the server's record of a completed import. */
@Composable
private fun MigrationHistorySection(histories: List<ExternalAgentConfigImportHistory>) {
    val ordered = remember(histories) { histories.sortedByDescending { it.completedAtMs } }
    CodexSection(stringResource(R.string.migration_history_section)) {
        if (ordered.isEmpty()) {
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
                    text = stringResource(R.string.migration_history_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.migration_history_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        ordered.forEachIndexed { index, history ->
            if (index > 0) CodexRowDivider()
            MigrationHistoryRow(history = history)
        }
    }
}

/** One recorded import as a read-only row; the timestamp renders in the user's locale, not as
 * the epoch ms it arrives as. */
@Composable
private fun MigrationHistoryRow(history: ExternalAgentConfigImportHistory) {
    val stamp = migrationTimeLabel(history.completedAtMs)
    CodexRow(
        title = history.providerId ?: history.importId,
        summary =
            stringResource(
                R.string.migration_history_counts,
                history.successes.size,
                history.failures.size,
            ),
        endAction =
            if (stamp == null) {
                null
            } else {
                { CodexValue(stamp, monospace = false) }
            },
    )
}

/** Null for `0` — the wire default, which would print 1970. */
@Composable
@ReadOnlyComposable
private fun migrationTimeLabel(at: Long): String? =
    if (at <= 0L) {
        null
    } else {
        SimpleDateFormat(
                stringResource(R.string.migration_history_time_format),
                Locale.getDefault(),
            )
            .format(Date(at))
    }
