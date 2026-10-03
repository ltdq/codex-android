package com.cy.codex.chatwidget

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.BuildConfig
import com.cy.codex.CatalogState
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexPage
import com.cy.codex.CodexRadioRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValue
import com.cy.codex.CodexValueRow
import com.cy.codex.DestinationCatalog
import com.cy.codex.PermissionProfileDisabled
import com.cy.codex.PermissionProfileRowModel
import com.cy.codex.PermissionSelectionFailure
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.UiConsts
import com.cy.codex.app.RecapSettings
import com.cy.codex.description
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The settings body: one page of the desktop app's settings at a time (see [SettingsSection]).
 *
 * The rail's settings item opens the body and the menu swaps which page it holds, so this screen has
 * no page list and no back chevron of its own.
 */
@Composable
fun SettingsScreen(
    catalog: CatalogState,
    session: SessionState,
    configPath: String,
    section: SettingsSection,
    onEvent: (AppEvent) -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenShortcuts: () -> Unit,
    ordinaryUsageRecovered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context =
        SettingsPageContext(
            catalog = catalog,
            session = session,
            configPath = configPath,
            ordinaryUsageRecovered = ordinaryUsageRecovered,
            onEvent = onEvent,
            onOpenWorkspacePicker = onOpenWorkspacePicker,
            onOpenEntry = onOpenEntry,
            onOpenShortcuts = onOpenShortcuts,
        )
    CodexPage(
        title = section.title(),
        description = section.description(),
        modifier = modifier,
    ) {
        settingsPage(context, section)
    }
}

@Composable
internal fun SettingsWorkspaceSection(
    cwd: String,
    roots: List<String>,
    onOpenWorkspacePicker: () -> Unit,
) {
    CodexSection(stringResource(R.string.settings_group_workspace)) {
        CodexNavRow(
            title = stringResource(R.string.settings_screen_current_directory),
            summary = cwd.ifEmpty { stringResource(R.string.settings_screen_no_directory) },
            onClick = onOpenWorkspacePicker,
        )
        if (roots.isEmpty()) {
            CodexRowDivider()
            CodexRow(
                title = stringResource(R.string.settings_screen_no_writable_roots),
                enabled = false,
            )
        } else {
            roots.forEach { root ->
                CodexRowDivider()
                CodexValueRow(
                    title = root,
                    value = stringResource(R.string.settings_screen_writable),
                )
            }
        }
    }
}

/** Which layer each effective value came from (`config/read`'s `layers` + `origins`). */
@Composable
internal fun SettingsConfigSourcesSection(catalog: CatalogState, configPath: String) {
    val response = catalog.config
    val layers = response.layers.orEmpty()

    CodexSection(stringResource(R.string.settings_group_config_sources)) {
        CodexValueRow(title = "CODEX_HOME/config.toml", value = configPath)
        if (layers.isEmpty()) {
            CodexRowDivider()
            CodexRow(
                title = stringResource(R.string.settings_screen_no_config_layers),
                enabled = false,
            )
            return@CodexSection
        }

        // Highest precedence first — the order the "why is this value what it is" answer is read in.
        layers.asReversed().forEach { layer ->
            CodexRowDivider()
            CodexRow(
                title = layer.name.label(),
                summary =
                    listOfNotNull(
                            layer.disabledReason ?: layer.name.description(),
                            layer.sourcePath,
                            layer.version.ifEmpty { null },
                        )
                        .joinToString(" · "),
                endAction = {
                    CodexValue(
                        if (layer.disabledReason == null) {
                            stringResource(R.string.settings_screen_config_active)
                        } else {
                            stringResource(R.string.settings_screen_config_disabled)
                        },
                        monospace = false,
                    )
                },
            )
        }

        // One row per rendered key, naming the layer that wins it.
        val origins = response.origins
        CatalogState.RenderedConfigKeys.forEach { key ->
            val value = response.displayValue(key) ?: return@forEach
            CodexRowDivider()
            CodexRow(
                title = key,
                summary =
                    buildString {
                        append(originLabel(origins[key]?.name))
                        append(" · ")
                        append(origins[key]?.version.orEmpty())
                    },
                endAction = { CodexValue(value) },
            )
        }
    }
}

@Composable
internal fun originLabel(source: com.cy.codex.protocol.protocol.v2.ConfigLayerSource?): String =
    source?.label() ?: stringResource(R.string.config_layer_unknown)



/** Android value of tui.auto_recap; a client-side toggle — the server has no recap method. */
@Composable
internal fun SettingsRecapSection() {
    val context = LocalContext.current
    CodexSection(stringResource(R.string.settings_group_recap)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_auto_recap),
            summary = stringResource(R.string.settings_auto_recap_summary),
            checked = RecapSettings.autoRecap,
            onCheckedChange = { RecapSettings.setAutoRecap(context, it) },
        )
    }
}

/** Version from [BuildConfig]; the same value initializes the app-server client. */
@Composable
internal fun SettingsAboutSection() {
    CodexSection(stringResource(R.string.settings_group_about)) {
        CodexValueRow(
            title = stringResource(R.string.settings_app_version),
            value = BuildConfig.VERSION_NAME,
        )
    }
}

/** Memory policy is 个性化's own rows now, which carry the desktop's copy for the same two keys. */
@Composable
internal fun SettingsShortcutsSection(onOpenShortcuts: () -> Unit) {
    CodexSection(stringResource(R.string.settings_group_shortcuts)) {
        CodexNavRow(
            title = stringResource(R.string.shortcuts_overlay_title),
            summary = stringResource(R.string.settings_shortcuts_summary),
            onClick = onOpenShortcuts,
        )
    }
}


internal data class SettingsLinkSpec(val id: String, val titleRes: Int, val icon: ImageVector)


@Composable
internal fun SettingsDataSection(onOpenEntry: (String) -> Unit) {
    val links =
        listOf(
            SettingsLinkSpec(DestinationCatalog.Id.Account, R.string.sidebar_library_account, MiuixIcons.Info),
            SettingsLinkSpec(DestinationCatalog.Id.Memories, R.string.sidebar_library_memories, MiuixIcons.Notes),
            SettingsLinkSpec(DestinationCatalog.Id.Migration, R.string.sidebar_library_migration, MiuixIcons.ConvertFile),
            SettingsLinkSpec(DestinationCatalog.Id.RemoteControl, R.string.sidebar_library_remote, MiuixIcons.Link),
            SettingsLinkSpec(DestinationCatalog.Id.Verification, R.string.sidebar_library_verification, MiuixIcons.Lock),
            SettingsLinkSpec(DestinationCatalog.Id.Sandbox, R.string.sidebar_library_sandbox, MiuixIcons.Tune),
            SettingsLinkSpec(DestinationCatalog.Id.Diagnostics, R.string.sidebar_library_diagnostics, MiuixIcons.Search),
        )
    SettingsLinksGroup(stringResource(R.string.settings_group_data), links, onOpenEntry)
}

@Composable
internal fun SettingsLinksGroup(
    title: String,
    links: List<SettingsLinkSpec>,
    onOpenEntry: (String) -> Unit,
) {
    CodexSection(title) {
        links.forEachIndexed { index, link ->
            if (index > 0) CodexRowDivider()
            CodexNavRow(
                title = stringResource(link.titleRes),
                startAction = {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconPreference),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
                },
                onClick = { onOpenEntry(link.id) },
            )
        }
    }
}

private enum class ThemeOption(@StringRes val labelRes: Int, val mode: ColorSchemeMode) {
    System(R.string.settings_theme_system, ColorSchemeMode.System),
    Light(R.string.settings_theme_light, ColorSchemeMode.Light),
    Dark(R.string.settings_theme_dark, ColorSchemeMode.Dark),
}

/** `experimentalFeature/list`, written back through `SetExperimentalFeature`. */
@Composable
internal fun SettingsExperimentalSection(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
    // The Android runtime fixes these features off, regardless of the persisted config.
    val features =
        catalog.experimentalFeatures.filterNot {
            it.id == "shell_snapshot" || it.id == "shell_zsh_fork"
        }
    CodexSection(stringResource(R.string.settings_group_experimental)) {
        if (features.isEmpty()) {
            CodexRow(
                title = stringResource(R.string.settings_screen_experimental_empty),
                enabled = false,
            )
            return@CodexSection
        }
        features.forEachIndexed { index, feature ->
            if (index > 0) CodexRowDivider()
            CodexSwitchRow(
                title = feature.name,
                summary =
                    listOf(feature.stage, feature.description)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                checked = feature.enabled,
                onCheckedChange = { onEvent(AppEvent.SetExperimentalFeature(feature.id, it)) },
            )
        }
    }
}



/**
 * The per-class switches behind 逐项审批.
 *
 * Shown, not switched: there is no write path for the per-class policy, and a switch that silently
 * does nothing is worse than one that says it cannot move.
 */
@Composable
internal fun SettingsGranularApprovalSection(config: ThreadSessionState) {
    if (config.approvalPolicy != AskForApproval.Granular) return
    val granular = config.granularApproval
    CodexSection(stringResource(R.string.settings_group_granular)) {
        listOf(
                stringResource(R.string.settings_screen_granular_sandbox) to granular.sandboxApproval,
                stringResource(R.string.settings_screen_granular_rules) to granular.rules,
                stringResource(R.string.settings_screen_granular_skills) to granular.skillApproval,
                stringResource(R.string.settings_screen_granular_permissions) to
                    granular.requestPermissions,
                stringResource(R.string.settings_screen_granular_mcp) to granular.mcpElicitations,
            )
            .forEachIndexed { index, (label, asks) ->
                if (index > 0) CodexRowDivider()
                CodexSwitchRow(
                    title = label,
                    summary =
                        if (asks) {
                            stringResource(R.string.settings_screen_granular_ask)
                        } else {
                            stringResource(R.string.settings_screen_granular_auto)
                        },
                    checked = asks,
                    // The no-op keeps the switch from reporting a change it is not allowed to make.
                    onCheckedChange = {},
                    enabled = false,
                )
            }
    }
}

/**
 * Named permission profiles of `permissionProfile/list`, one radio row each. Android-only scope:
 * built-in sandbox modes stay on the rows above; the TUI preset matrix
 * (codex-rs/tui/src/chatwidget/permissions_menu.rs) is not ported.
 */
@Composable
internal fun SettingsPermissionProfilesGroup(
    config: ThreadSessionState,
    catalog: CatalogState,
    selectionError: PermissionSelectionFailure?,
    onEvent: (AppEvent) -> Unit,
) {
    val rows = catalog.permissionProfileRows(config.activePermissionProfile)
    CodexSection(stringResource(R.string.settings_group_permissions)) {
        when {
            catalog.permissionDiscoveryUnsupported -> CodexRow(
                title = stringResource(R.string.settings_permission_discovery_unsupported),
                enabled = false,
            )

            rows.isEmpty() -> CodexRow(
                title = stringResource(R.string.settings_screen_permissions_empty),
                enabled = false,
            )

            else -> rows.forEachIndexed { index, row ->
                if (index > 0) CodexRowDivider()
                PermissionProfileRow(row, onEvent)
            }
        }
        selectionError?.let { error ->
            val message = when (error) {
                PermissionSelectionFailure.RequiresNewerServer ->
                    stringResource(R.string.settings_permission_select_requires_newer_server)

                is PermissionSelectionFailure.Failed ->
                    stringResource(R.string.settings_permission_select_failed, error.detail.orEmpty())
            }
            CodexRowDivider()
            CodexRow(title = message, enabled = false)
        }
    }
}

/** One profile row; a disabled row states the upstream `disabled_reason` wording as its summary. */
@Composable
internal fun PermissionProfileRow(
    row: PermissionProfileRowModel,
    onEvent: (AppEvent) -> Unit,
) {
    val reason = when (row.disabledReason) {
        PermissionProfileDisabled.ByRequirements ->
            stringResource(R.string.settings_permission_disabled_requirements)

        PermissionProfileDisabled.NotOnServer ->
            stringResource(R.string.settings_permission_disabled_unavailable)

        null -> null
    }
    CodexRadioRow(
        title = row.id,
        summary = listOfNotNull(row.description?.takeIf { it.isNotBlank() }, reason).joinToString(" · "),
        selected = row.selected,
        enabled = row.disabledReason == null,
        onClick = { onEvent(AppEvent.SetPermissionProfile(row.id)) },
    )
}