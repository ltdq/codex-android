package com.cy.codexui.chatwidget

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codexui.ActionRow
import com.cy.codexui.AppEvent
import com.cy.codexui.ButtonRole
import com.cy.codexui.CatalogState
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexButtonSize
import com.cy.codexui.CodexDivider
import com.cy.codexui.CodexSwitchRow
import com.cy.codexui.CodexTextField
import com.cy.codexui.ModalSheet
import com.cy.codexui.R
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.ValueRow
import com.cy.codexui.label
import com.cy.codexui.pressableRow
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.protocol.v2.PluginDetail
import com.cy.codexui.protocol.protocol.v2.PluginEntry
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/plugins` output as a page.
 *
 * Mirrors `plugin/list` and `bottom_pane/plugin_catalog`: entries are split the way the catalog
 * presents them — what is already installed, and what the marketplace still offers. Every row's
 * action round-trips: `plugin/install` and `plugin/uninstall` are emitted as [AppEvent]s, and the
 * catalog is re-read from the server afterwards rather than patched locally.
 */
@Composable
fun PluginsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // A search narrows the list with the *server's* index rather than filtering the copy on screen:
    // `plugin/list` returns at most one marketplace's worth of entries, so a local filter would
    // silently fail to find a plugin that is installed but not in the visible catalog.
    var term by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PluginEntry>?>(null) }
    // `plugin/installed` is a different question from `plugin/list`: it answers with what is
    // installed regardless of which marketplace it came from, and drops the rest.
    var installedOnly by remember { mutableStateOf(false) }
    var installedEntries by remember { mutableStateOf<List<PluginEntry>?>(null) }
    var detail by remember { mutableStateOf<PluginDetail?>(null) }
    var addingMarketplace by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val plugins = results ?: installedEntries ?: catalog.plugins
    val installed = plugins.filter { it.installed }
    val marketplace = plugins.filterNot { it.installed }

    fun search(query: String) {
        term = query
        if (query.isBlank()) {
            results = null
            return
        }
        scope.launch {
            client.searchPlugins(query)
                .onSuccess { if (term == query) results = it }
                .onFailure { if (term == query) error = it.message }
        }
    }

    fun setInstalledOnly(value: Boolean) {
        installedOnly = value
        if (!value) {
            installedEntries = null
            return
        }
        scope.launch {
            client.listInstalledPlugins().onSuccess { response ->
                installedEntries = response.marketplaces.flatMap { it.plugins }
            }.onFailure { error = it.message }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.plugins_screen_title),
            subtitle = stringResource(R.string.plugins_screen_subtitle, installed.size, marketplace.size),
            leading = { PluginsBackButton(onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.Space10),
        ) {
            CodexTextField(
                value = term,
                onValueChange = ::search,
                placeholder = stringResource(R.string.plugins_screen_search),
            )
            CodexSwitchRow(
                title = stringResource(R.string.plugins_screen_installed_only),
                subtitle = stringResource(R.string.plugins_screen_installed_only_detail),
                checked = installedOnly,
                onCheckedChange = ::setInstalledOnly,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            error?.let { Text(it, color = colors.error, fontSize = UiType.Meta) }
            MarketplacesCard(catalog.marketplaces, onEvent, onAdd = { addingMarketplace = true })
            if (plugins.isEmpty()) {
                SectionCard(title = stringResource(R.string.plugins_screen_title), icon = MiuixIcons.Store) {
                    Text(
                        text = stringResource(R.string.plugins_screen_empty),
                        modifier = Modifier.padding(vertical = UiConsts.Space4),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.disabledOnSurface,
                    )
                }
            } else {
                PluginGroupCard(
                    title = stringResource(R.string.plugins_screen_installed),
                    emptyText = stringResource(R.string.plugins_screen_installed_empty),
                    onEvent = onEvent,
                    entries = installed,
                    onOpen = { plugin ->
                        scope.launch {
                            client.readPlugin(plugin.name, plugin.marketplace.ifEmpty { null })
                                .onSuccess { detail = it }
                                .onFailure { error = it.message }
                        }
                    },
                )
                PluginGroupCard(
                    title = stringResource(R.string.plugins_screen_marketplace),
                    emptyText = stringResource(R.string.plugins_screen_marketplace_empty),
                    onEvent = onEvent,
                    entries = marketplace,
                    onOpen = { plugin ->
                        scope.launch {
                            client.readPlugin(plugin.name, plugin.marketplace.ifEmpty { null })
                                .onSuccess { detail = it }
                                .onFailure { error = it.message }
                        }
                    },
                )
            }
        }
    }

    detail?.let { open ->
        PluginDetailSheet(detail = open, client = client, onDismiss = { detail = null })
    }
    if (addingMarketplace) {
        MarketplaceFormSheet(
            onDismiss = { addingMarketplace = false },
            onSubmit = { source, ref ->
                onEvent(AppEvent.AddMarketplace(source, ref))
                addingMarketplace = false
            },
        )
    }
}

@Composable
private fun PluginGroupCard(
    title: String,
    emptyText: String,
    onEvent: (AppEvent) -> Unit,
    entries: List<PluginEntry>,
    onOpen: (PluginEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = title, icon = MiuixIcons.Store, trailing = entries.size.toString()) {
        if (entries.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.padding(vertical = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.disabledOnSurface,
            )
        } else {
            entries.forEachIndexed { index, plugin ->
                if (index > 0) PluginsDivider()
                PluginRow(plugin, onEvent, onOpen)
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: PluginEntry,
    onEvent: (AppEvent) -> Unit,
    onOpen: (PluginEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = remember { RoundedCornerShape(UiConsts.RowCorner) },
                container = Color.Transparent,
                onClick = { onOpen(plugin) },
                onClickLabel = plugin.name,
            )
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plugin.name,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (plugin.description.isNotEmpty()) {
                Text(
                    text = plugin.description,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.Space3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.version.ifEmpty { stringResource(R.string.plugins_screen_no_version) },
                    fontSize = UiType.Caption,
                    lineHeight = UiType.CaptionLine,
                    fontFamily = FontFamily.Monospace,
                    color = colors.disabledOnSurface,
                )
                Spacer(Modifier.width(UiConsts.Space6))
                PluginsChip(
                    text = plugin.marketplace.ifEmpty { stringResource(R.string.plugins_screen_local) },
                    tint = colors.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(UiConsts.Space10))
        // Installing is the one thing this page can do for a plugin, so the marketplace chip is the
        // accent pill and "Installed" — a state, not an action — is the outlined one.
        CodexButton(
            text = if (plugin.installed) {
                stringResource(R.string.plugins_screen_installed)
            } else {
                stringResource(R.string.plugins_screen_install)
            },
            onClick = {
                if (plugin.installed) {
                    onEvent(AppEvent.UninstallPlugin(plugin.id))
                } else {
                    onEvent(AppEvent.InstallPlugin(plugin.name, plugin.marketplace.ifEmpty { null }))
                }
            },
            role = if (plugin.installed) ButtonRole.Secondary else ButtonRole.Primary,
            size = CodexButtonSize.Compact,
        )
    }
}

@Composable
private fun PluginsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.plugins_screen_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PluginsChip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(UiConsts.BadgeCorner))
            .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
            .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space1),
    ) {
        Text(
            text = text,
            fontSize = UiType.Badge,
            lineHeight = UiType.BadgeLine,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun PluginsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UiConsts.Space1)
            .height(UiConsts.DividerThickness)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}

/**
 * What one plugin actually contributes.
 *
 * `plugin/read` answers with the manifest — skills, MCP servers and apps the plugin installs — and
 * `plugin/skill/read` fetches one skill's body. Both are reads the catalog list cannot answer: the
 * list only knows a plugin's name and whether it is installed, so a user deciding whether to install
 * one has nothing to decide on until this sheet opens.
 */
@Composable
private fun PluginDetailSheet(
    detail: PluginDetail,
    client: AppServerClient,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var openSkill by remember(detail.id) { mutableStateOf<String?>(null) }
    var skillBody by remember(detail.id) { mutableStateOf<String?>(null) }
    var skillFailed by remember(detail.id) { mutableStateOf(false) }

    ModalSheet(
        show = true,
        onDismiss = onDismiss,
        onDismissFinished = onDismiss,
        title = detail.name,
        subtitle = detail.version.ifEmpty { detail.marketplace },
    ) {
        if (detail.description.isNotEmpty()) {
            Text(
                text = detail.description,
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        SectionCard(title = stringResource(R.string.plugins_detail_facts), icon = MiuixIcons.Info) {
            ValueRow(label = stringResource(R.string.plugins_detail_id), value = detail.id, monospace = true)
            CodexDivider()
            ValueRow(
                label = stringResource(R.string.plugins_detail_marketplace),
                value = detail.marketplace,
            )
            if (detail.author != null) {
                CodexDivider()
                ValueRow(label = stringResource(R.string.plugins_detail_author), value = detail.author)
            }
            if (detail.homepage != null) {
                CodexDivider()
                ValueRow(
                    label = stringResource(R.string.plugins_detail_homepage),
                    value = detail.homepage,
                    monospace = true,
                )
            }
        }
        if (detail.skills.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.plugins_detail_skills),
                icon = MiuixIcons.Tasks,
                trailing = detail.skills.size.toString(),
            ) {
                detail.skills.forEachIndexed { index, skill ->
                    if (index > 0) CodexDivider()
                    ActionRow(
                        title = skill.name,
                        subtitle = skill.description.ifEmpty { null },
                        onClick = {
                            if (openSkill == skill.name) {
                                openSkill = null
                                return@ActionRow
                            }
                            openSkill = skill.name
                            skillBody = null
                            skillFailed = false
                            scope.launch {
                                client.readPluginSkill(
                                    marketplace = detail.marketplace,
                                    pluginId = detail.id,
                                    skillName = skill.name,
                                ).onSuccess { skillBody = it }
                                    .onFailure { skillFailed = true }
                            }
                        },
                    )
                    if (openSkill == skill.name) {
                        Text(
                            text = skillBody ?: if (skillFailed) {
                                stringResource(R.string.plugins_detail_skill_failed)
                            } else {
                                stringResource(R.string.plugins_detail_skill_loading)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
                            fontSize = UiType.Code,
                            lineHeight = UiType.CodeLine,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        if (detail.mcpServers.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.plugins_detail_mcp),
                icon = MiuixIcons.Link,
                trailing = detail.mcpServers.size.toString(),
            ) {
                detail.mcpServers.forEach { server ->
                    ValueRow(label = server.name, value = server.status.label())
                }
            }
        }
        if (detail.apps.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.plugins_detail_apps),
                icon = MiuixIcons.Community,
                trailing = detail.apps.size.toString(),
            ) {
                detail.apps.forEach { app ->
                    ValueRow(label = app.name, value = app.id, monospace = true)
                }
            }
        }
    }
}
