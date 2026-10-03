package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.protocol.protocol.v2.HookMetadata
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/hooks` as a page (hooks/list, bottom_pane/hooks_browser_view.rs); trust and enablement are
 * `config/batchWrite` upserts into `hooks.state`, the table the TUI writes (hooks_rpc.rs).
 */
@Composable
fun HooksScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val hooks = catalog.hooks
    val enabled = hooks.count { it.enabled }
    val review = hooks.count { it.needsReview }
    // First-seen event order: the server's display order, not the alphabet.
    val groups = hooks.groupBy { it.eventName }

    CodexPage(
        title = stringResource(R.string.hooks_screen_title),
        description = stringResource(R.string.hooks_screen_subtitle, hooks.size, enabled, review),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (hooks.isEmpty()) {
            CodexEmptyRow(stringResource(R.string.hooks_screen_empty))
        } else {
            groups.forEach { (event, eventHooks) ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    CodexGroupTitle(event)
                    CodexCardGrid(count = eventHooks.size) { index ->
                        HooksCard(hook = eventHooks[index], onEvent = onEvent)
                    }
                }
            }
        }
        // `hooks/list` reports problems on the same call; without this section a broken hook
        // silently disappears.
        if (catalog.hookWarnings.isNotEmpty() || catalog.hookErrors.isNotEmpty()) {
            CodexSection(stringResource(R.string.hooks_screen_issues)) {
                catalog.hookErrors.forEachIndexed { index, error ->
                    if (index > 0) CodexRowDivider()
                    HooksIssueRow(
                        text = error.message.ifBlank { error.path },
                        path = error.path.takeIf { it.isNotBlank() && it != error.message },
                        tint = colors.error,
                    )
                }
                catalog.hookWarnings.forEachIndexed { index, warning ->
                    if (index > 0 || catalog.hookErrors.isNotEmpty()) CodexRowDivider()
                    HooksIssueRow(
                        text = warning,
                        path = null,
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
        CodexSection(stringResource(R.string.hooks_screen_section)) {
            CodexRow(
                title = stringResource(R.string.hooks_screen_note),
                enabled = false,
            )
        }
    }
}

/** A hook that has not been pinned to the reviewed bytes yet. */
internal val HookMetadata.needsReview: Boolean
    get() =
        trustStatus.equals("untrusted", ignoreCase = true) ||
            trustStatus.equals("modified", ignoreCase = true)

private val HookMetadata.trusted: Boolean
    get() = trustStatus.equals("trusted", ignoreCase = true)

/** One hook as a catalogue card: its key, its handler, its trust, and its switch. */
@Composable
private fun HooksCard(hook: HookMetadata, onEvent: (AppEvent) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val blocked = hook.isManaged || hook.needsReview
    CodexCatalogCard(
        title = hook.key,
        description = hook.detailSummary().ifEmpty { null },
        icon = MiuixIcons.ConvertFile,
        enabled = !blocked,
        trailing = {
            Switch(
                checked = hook.enabled,
                enabled = !blocked,
                // The write lands asynchronously; the card shows the server's answer, not a local guess.
                onCheckedChange = { enabled -> onEvent(AppEvent.SetHookEnabled(hook.key, enabled)) },
            )
        },
        footer = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = hook.handlerSummary(),
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(HooksRowShape)
                            .background(codeSurface())
                            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space5),
                    fontSize = UiType.Code,
                    lineHeight = UiType.CodeLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        hook.isManaged -> HooksChip(
                            stringResource(R.string.hooks_screen_trust_managed),
                            colors.disabledOnSurface,
                        )

                        hook.needsReview -> HooksChip(
                            stringResource(R.string.hooks_screen_trust_review),
                            colors.error,
                        )

                        hook.trusted -> HooksChip(
                            stringResource(R.string.hooks_screen_trust_trusted),
                            colors.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    when {
                        hook.isManaged -> Text(
                            text = stringResource(R.string.hooks_screen_managed_note),
                            fontSize = UiType.Footnote,
                            lineHeight = UiType.FootnoteLine,
                            color = colors.disabledOnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        hook.needsReview && hook.currentHash.isNotBlank() -> Button(
                            onClick = { onEvent(AppEvent.SetHookTrust(hook.key, hook.currentHash)) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            cornerRadius = UiConsts.ButtonHeightCompact / 2,
                            minWidth = 0.dp,
                            minHeight = UiConsts.ButtonHeightCompact,
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                    vertical = 0.dp,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.hooks_screen_trust_action),
                                fontSize = UiType.Action,
                                lineHeight = UiType.ActionLine,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** The handler line under a hook's key: a shell command, an MCP tool, or the bare handler type. */
private fun HookMetadata.handlerSummary(): String =
    when {
        command != null -> command
        server != null && tool != null -> "$server/$tool"
        else -> handlerType
    }

/** Matcher, timeout, origin and async marker, in the order the TUI prints them. */
private fun HookMetadata.detailSummary(): String = buildList {
    matcher?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (timeoutSec > 0) add("${timeoutSec}s")
    if (async) add("async")
    sourcePath.takeIf { it.isNotBlank() }?.let { add(it) }
    pluginId?.takeIf { it.isNotBlank() }?.let { add(it) }
}
    .joinToString(" · ")

@Composable
private fun HooksIssueRow(text: String, path: String?, tint: Color) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.RowInset, vertical = UiConsts.Space7)
    ) {
        Text(
            text = text,
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = tint,
        )
        if (path != null) {
            Spacer(Modifier.height(UiConsts.Space3))
            Text(
                text = path,
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.disabledOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HooksChip(text: String, tint: Color) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
                .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2)
    ) {
        Text(
            text = text,
            fontSize = UiType.Chip,
            lineHeight = UiType.ChipLine,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

private val HooksRowShape = RoundedCornerShape(UiConsts.RowCorner)
