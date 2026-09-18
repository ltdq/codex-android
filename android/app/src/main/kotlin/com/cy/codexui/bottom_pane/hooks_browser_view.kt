package com.cy.codexui.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codexui.AppEvent
import com.cy.codexui.CatalogState
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexButtonSize
import com.cy.codexui.R
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.codeSurface
import com.cy.codexui.protocol.protocol.v2.HookMetadata
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/hooks` output as a page.
 *
 * Mirrors `hooks/list` and `bottom_pane/hooks_browser_view.rs`: hooks are grouped by lifecycle
 * event, each row shows its handler, and a hook that still needs review blocks the enable switch
 * until it is trusted. Trust and enablement are `config/batchWrite` upserts into `hooks.state`, the
 * same table the TUI writes (`hooks_rpc.rs`), so both clients pin the same hash.
 */
@Composable
fun HooksScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val hooks = catalog.hooks
    val enabled = hooks.count { it.enabled }
    val review = hooks.count { it.needsReview }
    // First-seen event order: the server's display order, not the alphabet.
    val groups = hooks.groupBy { it.eventName }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.hooks_screen_title),
            subtitle = stringResource(R.string.hooks_screen_subtitle, hooks.size, enabled, review),
            leading = { HooksBackButton(onBack) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            if (hooks.isEmpty()) {
                SectionCard(
                    title = stringResource(R.string.hooks_screen_section),
                    icon = MiuixIcons.ConvertFile,
                ) {
                    Text(
                        text = stringResource(R.string.hooks_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                }
            } else {
                groups.forEach { (event, eventHooks) ->
                    SectionCard(
                        title = event,
                        icon = MiuixIcons.ConvertFile,
                        trailing = eventHooks.size.toString(),
                    ) {
                        eventHooks.forEachIndexed { index, hook ->
                            if (index > 0) HooksDivider()
                            HooksRow(hook, onEvent)
                        }
                    }
                }
            }
            SectionCard(
                title = stringResource(R.string.hooks_screen_section),
                icon = MiuixIcons.ConvertFile,
            ) {
                Text(
                    text = stringResource(R.string.hooks_screen_note),
                    modifier = Modifier.padding(vertical = UiConsts.Space6),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.disabledOnSurface,
                )
            }
        }
    }
}

/** A hook that has not been pinned to the reviewed bytes yet. */
internal val HookMetadata.needsReview: Boolean
    get() = trustStatus.equals("untrusted", ignoreCase = true) || trustStatus.equals("modified", ignoreCase = true)

private val HookMetadata.trusted: Boolean
    get() = trustStatus.equals("trusted", ignoreCase = true)

@Composable
private fun HooksRow(hook: HookMetadata, onEvent: (AppEvent) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val blocked = hook.isManaged || hook.needsReview
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hook.key,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(UiConsts.Space6))
                if (hook.isManaged) {
                    HooksChip(stringResource(R.string.hooks_screen_trust_managed), colors.disabledOnSurface)
                } else if (hook.needsReview) {
                    HooksChip(stringResource(R.string.hooks_screen_trust_review), colors.error)
                } else if (hook.trusted) {
                    HooksChip(stringResource(R.string.hooks_screen_trust_trusted), colors.primary)
                }
            }
            Spacer(Modifier.height(UiConsts.Space5))
            Text(
                text = hook.handlerSummary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HooksRowShape)
                    .background(codeSurface())
                    .padding(horizontal = UiConsts.Space7, vertical = UiConsts.Space5),
                fontSize = UiType.Code,
                lineHeight = UiType.CodeLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = hook.detailSummary()
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(UiConsts.Space5))
                Text(
                    text = detail,
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                hook.isManaged -> {
                    Spacer(Modifier.height(UiConsts.Space5))
                    Text(
                        text = stringResource(R.string.hooks_screen_managed_note),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.disabledOnSurface,
                    )
                }

                hook.needsReview && hook.currentHash.isNotBlank() -> {
                    Spacer(Modifier.height(UiConsts.Space7))
                    CodexButton(
                        text = stringResource(R.string.hooks_screen_trust_action),
                        onClick = { onEvent(AppEvent.SetHookTrust(hook.key, hook.currentHash)) },
                        size = CodexButtonSize.Compact,
                    )
                }
            }
        }
        Spacer(Modifier.width(UiConsts.Space10))
        Switch(
            checked = hook.enabled,
            enabled = !blocked,
            // The write lands asynchronously and re-reads `hooks/list`; the row keeps showing the
            // server's answer rather than a local guess.
            onCheckedChange = { enabled -> onEvent(AppEvent.SetHookEnabled(hook.key, enabled)) },
        )
    }
}

/**
 * The handler line under a hook's key.
 *
 * Three shapes share one field on the wire — a shell command, an MCP tool, or a prompt/agent
 * handler with no payload — so the row renders whichever is meaningful and never shows an empty
 * code block for the variants that carry nothing.
 */
private fun HookMetadata.handlerSummary(): String = when {
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
}.joinToString(" · ")

@Composable
private fun HooksBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.hooks_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun HooksChip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(UiConsts.BadgeCorner))
            .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
            .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2),
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

@Composable
private fun HooksDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space1)
            .height(UiConsts.DividerThickness)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

private val HooksRowShape = RoundedCornerShape(UiConsts.RowCorner)
