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
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.v2.HookEntry
import com.cy.codexui.AppEvent
import com.cy.codexui.CatalogState
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.codeSurface
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
 * Mirrors `hooks/list` and `bottom_pane/hooks_browser_view.rs`: the TUI shows the event, whether the
 * hook is active and whether it still needs review. The phone shows the same facts in one card.
 *
 * The switch is visual only: the write path is `hooks/list` plus a config batch write, and neither
 * is implemented in this client yet, so toggling must not pretend to persist.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.hooks_screen_title),
            subtitle = stringResource(R.string.hooks_screen_subtitle, hooks.size, enabled),
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
            SectionCard(
                title = stringResource(R.string.hooks_screen_section),
                icon = MiuixIcons.ConvertFile,
                trailing = hooks.size.toString(),
            ) {
                if (hooks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.hooks_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                } else {
                    hooks.forEachIndexed { index, hook ->
                        if (index > 0) HooksDivider()
                        HooksRow(hook, onEvent)
                    }
                }
                HooksDivider()
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

@Composable
private fun HooksRow(hook: HookEntry, onEvent: (AppEvent) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hook.name,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(UiConsts.Space6))
                HooksChip(
                    text = hook.event,
                    tint = if (hook.enabled) colors.primary else colors.disabledOnSurface,
                )
            }
            Spacer(Modifier.height(UiConsts.Space5))
            Text(
                text = hook.command,
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
        }
        Spacer(Modifier.width(UiConsts.Space10))
        Switch(
            checked = hook.enabled,
            // A hook's on/off lives in the config file, so the toggle is a `config/value/write`
            // against the key the hook was declared under. A write that a managed layer shadows
            // comes back `okOverridden`, which the caller surfaces rather than silently ignoring.
            onCheckedChange = { enabled ->
                onEvent(
                    AppEvent.WriteConfigValue(
                        keyPath = "hooks.${'$'}{hook.id}.enabled",
                        value = kotlinx.serialization.json.JsonPrimitive(enabled),
                    ),
                )
            },
        )
    }
}

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
