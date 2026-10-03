package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.McpAuthStatus
import com.cy.codex.protocol.protocol.v2.McpServerConnectionStatus
import com.cy.codex.protocol.protocol.v2.McpServerStartupState
import com.cy.codex.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** `/mcp` as a page (mcpServerStatus/list, `mcp_startup`): the dot matches the transcript's status tone. */
@Composable
fun McpScreen(
    catalog: CatalogState,
    onBack: (() -> Unit)?,
    onOpenServer: (String) -> Unit,
    onEvent: (AppEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val servers = catalog.mcpServers
    val ready = servers.count { it.status == McpServerConnectionStatus.Connected }
    // Failed first, then still-starting: a server that needs attention sits above one that is merely slow.
    val startup = catalog.mcpStartup.values.sortedBy { it.status != McpServerStartupState.Failed }

    CodexPage(
        title = stringResource(R.string.mcp_screen_title),
        description = stringResource(R.string.mcp_screen_subtitle, servers.size, ready),
        onBack = onBack,
        modifier = modifier,
    ) {
        if (startup.isNotEmpty()) {
            CodexSection(stringResource(R.string.mcp_screen_startup_section)) {
                startup.forEachIndexed { index, update ->
                    if (index > 0) CodexRowDivider()
                    McpStartupRow(update)
                }
            }
        }
        if (servers.isEmpty()) {
            CodexEmptyRow(stringResource(R.string.mcp_screen_empty))
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                CodexGroupTitle(stringResource(R.string.mcp_screen_section_servers))
                CodexCardGrid(count = servers.size) { index ->
                    val server = servers[index]
                    McpServerCard(
                        server = server,
                        onClick = { onOpenServer(server.name) },
                        onLogin = { onEvent(AppEvent.McpLogin(server.name)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun McpStartupRow(update: com.cy.codex.protocol.protocol.v2.McpStartupStatusUpdated) {
    val failed = update.status == McpServerStartupState.Failed
    val detail =
        update.error
            ?: if (failed && update.failureReason == "reauthenticationRequired") {
                stringResource(R.string.mcp_screen_startup_reauth)
            } else {
                null
            }
    CodexRow(
        title =
            if (failed) {
                stringResource(R.string.mcp_screen_startup_failed, update.serverName)
            } else {
                stringResource(R.string.mcp_screen_startup_starting, update.serverName)
            },
        summary = detail,
        enabled = false,
    )
}

/** One server as a catalogue card: its state, what it exposes, and the login it may still need. */
@Composable
private fun McpServerCard(
    server: McpServerStatusEntry,
    onClick: () -> Unit,
    onLogin: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val tone = mcpTone(server.status)
    val needsLogin =
        server.authStatus == McpAuthStatus.NotLoggedIn ||
            server.status == McpServerConnectionStatus.AuthenticationRequired
    CodexCatalogCard(
        title = server.name,
        description =
            stringResource(R.string.mcp_screen_tools_resources, server.tools, server.resources),
        icon = MiuixIcons.Community,
        enabled =
            server.status != McpServerConnectionStatus.Failed &&
                server.status != McpServerConnectionStatus.Disabled,
        onClick = onClick,
        trailing = { McpChip(text = server.status.label(), tint = statusDotColor(tone)) },
        footer = {
            when {
                !server.error.isNullOrBlank() -> Text(
                    text = server.error,
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(McpRowShape)
                            .background(colors.error.copy(alpha = 0.12f))
                            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space6),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.error,
                )

                // The login row exists for `authStatus`: without it the server stays disconnected,
                // the reason only a status word.
                needsLogin -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    McpChip(
                        text = stringResource(R.string.mcp_screen_auth_not_logged_in),
                        tint = colors.error,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onLogin,
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
                            text = stringResource(R.string.mcp_screen_auth_login),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                else -> Unit
            }
        },
    )
}

@Composable
private fun McpChip(text: String, tint: Color) {
    Box(
        modifier =
            Modifier.clip(McpRowShape)
                .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
                .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2)
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

/** Corner of the chips and error boxes inside a card. */
private val McpRowShape = RoundedCornerShape(UiConsts.BadgeCorner)

/** Connection state → the same four tones the transcript's status dots use. */
private fun mcpTone(status: McpServerConnectionStatus): ThreadStatusTone =
    when (status) {
        McpServerConnectionStatus.Connected -> ThreadStatusTone.Done
        McpServerConnectionStatus.NotStarted,
        McpServerConnectionStatus.Starting,
        McpServerConnectionStatus.AuthenticationRequired -> ThreadStatusTone.Waiting

        McpServerConnectionStatus.Failed -> ThreadStatusTone.Failed
        McpServerConnectionStatus.Cancelled,
        McpServerConnectionStatus.Disabled -> ThreadStatusTone.Idle
    }
