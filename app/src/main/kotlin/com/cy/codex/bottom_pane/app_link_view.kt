package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.AppInfo
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** `/apps` as a page, mirroring `app/list` and codex-rs/tui/src/bottom_pane/app_link_view.rs. */
@Composable
fun AppsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // `app/installed` re-narrows server-side; the catalog list itself comes from `app/list`.
    var installedOnly by remember { mutableStateOf(false) }
    var narrowed by remember { mutableStateOf<List<AppInfo>?>(null) }
    // `app/read` takes ids: after a write, re-read just that row.
    var reread by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val apps = narrowed ?: catalog.apps
    val installed = apps.filter { it.installed }
    val marketplace = apps.filterNot { it.installed }

    fun setInstalledOnly(value: Boolean) {
        installedOnly = value
        if (!value) {
            narrowed = null
            return
        }
        scope.launch { client.listInstalledApps().onSuccess { narrowed = it } }
    }

    val readOne: (String) -> Unit = { id ->
        scope.launch { client.readApps(listOf(id)).onSuccess { reread = it } }
    }

    CodexPage(
        title = stringResource(R.string.apps_screen_title),
        description = stringResource(R.string.apps_screen_subtitle, installed.size, marketplace.size),
        onBack = onBack,
        modifier = modifier,
    ) {
        CodexSection {
            CodexSwitchRow(
                title = stringResource(R.string.apps_screen_installed_only),
                summary = stringResource(R.string.apps_screen_installed_only_detail),
                checked = installedOnly,
                onCheckedChange = ::setInstalledOnly,
            )
        }
        if (reread.isNotEmpty()) {
            CodexSection(stringResource(R.string.apps_screen_reread)) {
                reread.forEachIndexed { index, app ->
                    if (index > 0) CodexRowDivider()
                    CodexValueRow(
                        title = app.name,
                        value =
                            if (app.installed) {
                                stringResource(R.string.apps_screen_installed)
                            } else {
                                stringResource(R.string.apps_screen_not_installed)
                            },
                        monospace = false,
                    )
                }
            }
        }
        if (apps.isEmpty()) {
            CodexEmptyRow(stringResource(R.string.apps_screen_empty))
        } else {
            AppsGroup(
                title = stringResource(R.string.apps_screen_installed),
                emptyText = stringResource(R.string.apps_screen_installed_empty),
                entries = installed,
                onEvent = onEvent,
                onReread = readOne,
            )
            AppsGroup(
                title = stringResource(R.string.apps_screen_marketplace),
                emptyText = stringResource(R.string.apps_screen_marketplace_empty),
                entries = marketplace,
                onEvent = onEvent,
                onReread = readOne,
            )
        }
    }
}

/** One catalogue group: its title, then its entries as cards. */
@Composable
private fun AppsGroup(
    title: String,
    emptyText: String,
    entries: List<AppInfo>,
    onEvent: (AppEvent) -> Unit,
    onReread: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CodexGroupTitle(title)
        if (entries.isEmpty()) {
            Text(
                text = emptyText,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.disabledOnSurface,
            )
        } else {
            CodexCardGrid(count = entries.size) { index ->
                AppCard(app = entries[index], onEvent = onEvent, onReread = onReread)
            }
        }
    }
}

/** One connector as a catalogue card: what it is, its id, and the two calls that act on it. */
@Composable
private fun AppCard(
    app: AppInfo,
    onEvent: (AppEvent) -> Unit,
    onReread: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    CodexCatalogCard(
        title = app.name,
        description = app.description.ifEmpty { null },
        icon = MiuixIcons.Community,
        enabled = app.installed,
        trailing = {
            Button(
                onClick = { onEvent(AppEvent.SetAppInstalled(app.id, !app.installed)) },
                colors =
                    if (app.installed) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColorsPrimary(),
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
                    text =
                        if (app.installed) {
                            stringResource(R.string.apps_screen_installed)
                        } else {
                            stringResource(R.string.apps_screen_install)
                        },
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.id,
                    fontSize = UiType.Caption,
                    lineHeight = UiType.CaptionLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Button(
                    onClick = { onReread(app.id) },
                    colors = ButtonDefaults.buttonColors(),
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
                        text = stringResource(R.string.apps_screen_reread_one),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}
