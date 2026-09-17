package com.cy.codexui.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import com.cy.codexui.protocol.protocol.v2.McpServerConnectionStatus
import com.cy.codexui.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codexui.CatalogState
import com.cy.codexui.label
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.statusDotColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/mcp` output as a page: one row per configured server with its connection state.
 *
 * Mirrors `mcpServerStatus/list` and the startup banner the TUI prints (`mcp_startup`): the dot is
 * the same status tone the transcript uses, so a failed server looks the same in both places.
 */
@Composable
fun McpScreen(
    catalog: CatalogState,
    onBack: () -> Unit,
    onOpenServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val servers = catalog.mcpServers
    val ready = servers.count { it.status == McpServerConnectionStatus.Ready }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.mcp_screen_title),
            subtitle = stringResource(R.string.mcp_screen_subtitle, servers.size, ready),
            leading = { McpBackButton(onBack) },
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
                title = stringResource(R.string.mcp_screen_section_servers),
                icon = MiuixIcons.Community,
                trailing = servers.size.toString(),
            ) {
                if (servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.mcp_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                } else {
                    servers.forEachIndexed { index, server ->
                        if (index > 0) McpDivider()
                        McpServerRow(server, onClick = { onOpenServer(server.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServerRow(server: McpServerStatusEntry, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val tone = mcpTone(server.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(UiConsts.DotSize)
                    .clip(CircleShape)
                    .background(statusDotColor(tone)),
            )
            Spacer(Modifier.width(UiConsts.Space8))
            Text(
                text = server.name,
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            McpChip(text = server.status.label(), tint = statusDotColor(tone))
        }
        Spacer(Modifier.height(UiConsts.Space4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.mcp_screen_tools_resources, server.tools, server.resources),
                modifier = Modifier.weight(1f),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Text(
                text = server.status.wire,
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                fontFamily = FontFamily.Monospace,
                color = colors.disabledOnSurface,
            )
        }
        if (!server.error.isNullOrBlank()) {
            Spacer(Modifier.height(UiConsts.Space6))
            Text(
                text = server.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(McpRowShape)
                    .background(colors.error.copy(alpha = 0.12f))
                    .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space6),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.error,
            )
        }
    }
}

/** `mcp add` opens a config editor the phone does not have yet, so the row stays inert. */
@Composable
private fun McpAddServerRow() {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.AddCircle,
            contentDescription = null,
            modifier = Modifier.size(UiConsts.IconInline),
            tint = colors.disabledOnSurface,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(
            text = stringResource(R.string.mcp_screen_add_server),
            modifier = Modifier.weight(1f),
            fontSize = UiType.Subtitle,
            lineHeight = UiType.SubtitleLine,
            color = colors.disabledOnSurface,
        )
        Text(
            text = stringResource(R.string.mcp_screen_not_connected),
            fontSize = UiType.Caption,
            lineHeight = UiType.CaptionLine,
            color = colors.disabledOnSurface,
        )
    }
}

@Composable
private fun McpBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.mcp_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun McpChip(text: String, tint: Color) {
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
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun McpDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space1)
            .height(UiConsts.DividerThickness)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

private val McpRowShape = RoundedCornerShape(UiConsts.RowCorner)

/** Connection state → the same four tones the transcript's status dots use. */
private fun mcpTone(status: McpServerConnectionStatus): ThreadStatusTone = when (status) {
    McpServerConnectionStatus.Ready -> ThreadStatusTone.Done
    McpServerConnectionStatus.Starting -> ThreadStatusTone.Waiting
    McpServerConnectionStatus.Failed -> ThreadStatusTone.Failed
    McpServerConnectionStatus.Disabled -> ThreadStatusTone.Idle
}
