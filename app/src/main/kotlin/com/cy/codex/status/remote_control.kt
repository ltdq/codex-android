package com.cy.codex.status

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.RemoteControlClient
import com.cy.codex.protocol.protocol.v2.RemoteControlConnectionStatus
import com.cy.codex.protocol.protocol.v2.RemoteControlStatus
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Relay, pairing and paired devices — the TUI's `status/remote_connection.rs` and
 * `app/daemon_menu.rs` on one page. No local state: reads from [CatalogState],
 * writes as [AppEvent]. */
@Composable
fun RemoteControlScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val status = catalog.remoteControl
    val relayLabel = status?.status?.label() ?: stringResource(R.string.remote_control_page_unread)

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.remote_control_page_title),
            summary = relayLabel,
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.remote_control_page_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadRemoteControl) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.remote_control_page_refresh),
                        modifier = Modifier.size(UiConsts.IconRefresh),
                        tint = colors.primary,
                    )
                }
            },
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            ConnectionCard(status = status, onEvent = onEvent)
            PairingCard(
                code = catalog.remoteControlPairingCode,
                claimed = catalog.remoteControlPairingClaimed,
                onEvent = onEvent,
            )
            PairedDevicesCard(clients = catalog.remoteControlClients, onEvent = onEvent)
        }
    }
}

/** Relay state plus the switch that turns it on; unknown state shows an empty card,
 * because an unchecked switch would report the machine as off. */
@Composable
private fun ConnectionCard(
    status: RemoteControlStatus?,
    onEvent: (AppEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.remote_control_connection),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.ScreenMirroring,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        if (status == null) {
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
                        imageVector = MiuixIcons.ScreenMirroring,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.remote_control_unread),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.remote_control_unread_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space16))
                Button(
                    onClick = { onEvent(AppEvent.ReloadRemoteControl) },
                    modifier = Modifier,
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.remote_control_page_retry), maxLines = 1)
                }
            }
        } else {
            BasicComponent(
                title = stringResource(R.string.remote_control_status_label),
                endActions = {
                    Text(
                        text = status.status.label().ifEmpty { "—" },
                        color =
                            statusDotColor(status.status.tone())
                                ?: MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.remote_control_server),
                endActions = {
                    Text(
                        text = status.serverName.ifEmpty { "—" },
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.remote_control_installation),
                endActions = {
                    Text(
                        text = status.installationId.ifEmpty { "—" },
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.remote_control_environment),
                endActions = {
                    Text(
                        text = status.environmentId.orEmpty().ifEmpty { "—" },
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            SwitchPreference(
                checked = status.status != RemoteControlConnectionStatus.Disabled,
                onCheckedChange = { onEvent(AppEvent.SetRemoteControlEnabled(it)) },
                title = stringResource(R.string.remote_control_relay),
                summary = stringResource(R.string.remote_control_relay_detail),
            )
        }
    }
}

/** Mint, poll, re-mint — the three protocol actions. The poll is a button: nothing pushes
 * the claim, and a self-polling page would have to pick an interval. */
@Composable
private fun PairingCard(
    code: String?,
    claimed: Boolean?,
    onEvent: (AppEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.remote_control_pairing),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        if (code == null) {
            RemoteControlNote(stringResource(R.string.remote_control_pairing_detail))
            Button(
                onClick = { onEvent(AppEvent.StartRemoteControlPairing) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.Space4),
                enabled = true,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = stringResource(R.string.remote_control_pairing_start), maxLines = 1)
            }
        } else {
            RemoteControlNote(stringResource(R.string.remote_control_pairing_hint))
            BasicComponent(
                title = stringResource(R.string.remote_control_pairing_code),
                endActions = {
                    Text(
                        text = code.ifEmpty { "—" },
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.remote_control_pairing_state),
                endActions = {
                    Text(
                        text =
                            if (claimed == true) {
                                    stringResource(R.string.remote_control_pairing_claimed)
                                } else {
                                    stringResource(R.string.remote_control_pairing_waiting)
                                }
                                .ifEmpty { "—" },
                        color =
                            statusDotColor(
                                if (claimed == true) ThreadStatusTone.Done
                                else ThreadStatusTone.Waiting
                            ) ?: MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                },
            )
            Button(
                onClick = { onEvent(AppEvent.PollRemoteControlPairing) },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                enabled = claimed != true,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = stringResource(R.string.remote_control_pairing_check), maxLines = 1)
            }
            Button(
                onClick = { onEvent(AppEvent.StartRemoteControlPairing) },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.Space4)
                        .padding(bottom = UiConsts.Space8),
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.remote_control_pairing_restart), maxLines = 1)
            }
        }
    }
}

/** Tap expands instead of opening a sheet: the one action per device is destructive. The
 * list is keyed by `environmentId`; a link without one skips the read. */
@Composable
private fun PairedDevicesCard(
    clients: List<RemoteControlClient>,
    onEvent: (AppEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.remote_control_devices),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Phone,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = clients.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (clients.isEmpty()) {
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
                        imageVector = MiuixIcons.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.remote_control_devices_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.remote_control_devices_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        clients.forEachIndexed { index, client ->
            if (index > 0)
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            PairedDeviceRow(
                client = client,
                expanded = expanded == client.clientId,
                onToggle = {
                    expanded = if (expanded == client.clientId) null else client.clientId
                },
                onRevoke = { onEvent(AppEvent.RevokeRemoteControlClient(client.clientId)) },
            )
        }
    }
}

/** Title falls back to the client id: `displayName` is optional on the wire, and a row
 * with no title leaves the user guessing which device to cut off. */
@Composable
private fun PairedDeviceRow(
    client: RemoteControlClient,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = client.displayName?.takeIf { it.isNotBlank() } ?: client.clientId,
            summary = deviceSummary(client),
            endActions = {
                Text(
                    text = lastSeenAge(client.lastSeenAt),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                )
            },
            onClick = onToggle,
        )
        if (expanded) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = UiConsts.Space4,
                            end = UiConsts.Space4,
                            bottom = UiConsts.Space8,
                        ),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onRevoke,
                    modifier = Modifier,
                    enabled = true,
                    colors =
                        ButtonDefaults.buttonColors(
                            color = Color.Transparent,
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                ) {
                    Text(text = stringResource(R.string.remote_control_device_revoke), maxLines = 1)
                }
            }
        }
    }
}

/** Optional wire fields are joined as-is; a hole would read as "the server does not know
 * the model" rather than "the client never told us". */
@Composable
private fun deviceSummary(client: RemoteControlClient): String? {
    val parts =
        listOfNotNull(
            client.platform?.takeIf { it.isNotBlank() },
            client.deviceModel?.takeIf { it.isNotBlank() },
            client.osVersion?.takeIf { it.isNotBlank() },
        )
    return parts
        .takeIf { it.isNotEmpty() }
        ?.joinToString(stringResource(R.string.remote_control_device_separator))
}

/** `DateUtils`'s relative age: the platform carries the duration ladder in every language.
 * A device that never checked in says so instead of leaving the column empty. */
@Composable
private fun lastSeenAge(lastSeenAt: Long?): String =
    lastSeenAt?.let { seen ->
        DateUtils.getRelativeTimeSpanString(
                seen,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
            .toString()
    } ?: stringResource(R.string.remote_control_device_never_seen)

/** One composable so every note shares size and colour; protocol claims stay string
 * resources. */
@Composable
private fun RemoteControlNote(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/** Page-local mapping: which protocol state means which tone; the shared palette must not
 * re-colour another page's state. */
private fun RemoteControlConnectionStatus.tone(): ThreadStatusTone =
    when (this) {
        RemoteControlConnectionStatus.Connected -> ThreadStatusTone.Done
        RemoteControlConnectionStatus.Connecting -> ThreadStatusTone.Running
        RemoteControlConnectionStatus.Disabled -> ThreadStatusTone.Idle
        RemoteControlConnectionStatus.Errored -> ThreadStatusTone.Failed
    }
