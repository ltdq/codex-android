package com.cy.codex.chatwidget

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexButtonRow
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.DestinationCatalog
import com.cy.codex.R

/**
 * 插件: the desktop page's tabs (插件 / MCP / 技能) over the app's own catalogs.
 *
 * The app has one page per catalog rather than tabs inside one, so the hub is five rows that open
 * them; the two header actions the desktop shows beside its search field have no counterpart.
 */
@Composable
internal fun SettingsPluginsPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_plugins_catalog_group)) {
        PluginCatalogSpecs.forEachIndexed { index, spec ->
            if (index > 0) CodexRowDivider()
            CodexNavRow(
                title = stringResource(spec.titleRes),
                onClick = { context.onOpenEntry(spec.id) },
            )
        }
    }

    CodexSection(stringResource(R.string.settings_plugins_header_group)) {
        CodexButtonRow(
            title = stringResource(R.string.settings_plugins_browse),
            actionLabel = stringResource(R.string.settings_plugins_browse_action),
            enabled = false,
            onAction = {},
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_plugins_add),
            actionLabel = stringResource(R.string.settings_plugins_add_action),
            enabled = false,
            onAction = {},
        )
    }
}

/** The catalogs the rail's 自定义 item opens, in the order that menu lists them. */
private val PluginCatalogSpecs = listOf(
    PluginCatalogSpec(DestinationCatalog.Id.Plugins, R.string.sidebar_library_plugins),
    PluginCatalogSpec(DestinationCatalog.Id.Skills, R.string.sidebar_library_skills),
    PluginCatalogSpec(DestinationCatalog.Id.Mcp, R.string.sidebar_library_mcp_servers),
    PluginCatalogSpec(DestinationCatalog.Id.Apps, R.string.sidebar_library_apps),
    PluginCatalogSpec(DestinationCatalog.Id.PluginShares, R.string.sidebar_library_shares),
)

private class PluginCatalogSpec(val id: String, @StringRes val titleRes: Int)

/** 电脑操控: no desktop capture covers the page, so one row says where the setting lives. */
@Composable
internal fun SettingsComputerPage(context: SettingsPageContext) {
    CodexEmptyRow(stringResource(R.string.settings_computer_unavailable))
}

/** 应用快照: no desktop capture covers the page, so one row says where the setting lives. */
@Composable
internal fun SettingsSnapshotsPage(context: SettingsPageContext) {
    CodexEmptyRow(stringResource(R.string.settings_snapshots_unavailable))
}

/** 浏览器: no capture covers this page, so one row says where the setting lives. */
@Composable
internal fun SettingsBrowserPage(context: SettingsPageContext) {
    CodexEmptyRow(stringResource(R.string.settings_browser_unavailable))
}
