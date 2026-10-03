package com.cy.codex.chatwidget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.theme.Appearance
import kotlinx.serialization.json.JsonPrimitive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The settings body: the TUI's bottom-pane pickers (bottom_pane/model_popups.rs,
 * permission_popups.rs, experimental_features_view.rs) as option rows on the shell's list grid.
 *
 * One section per page: the rail's settings item opens the body and the menu swaps which section it
 * holds, so this screen has no section list and no back chevron of its own.
 */
@Composable
fun SettingsScreen(
    catalog: CatalogState,
    session: SessionState,
    onEvent: (AppEvent) -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenShortcuts: () -> Unit,
    configPath: String,
    section: SettingsSection,
    /** The account read's recovery verdict; gates the model group to Reserve-only (codex-rs/tui/src/chatwidget/luna_reserve_model.rs). */
    ordinaryUsageRecovered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val config = session.config
    val preset =
        catalog.modelPreset(config.model)
            ?: catalog.models.firstOrNull { it.isDefault && !it.hidden }
    CodexPage(
        title = stringResource(section.titleRes),
        description = stringResource(section.descriptionRes),
        modifier = modifier,
    ) {
        when (section) {
            SettingsSection.Model -> {
                SettingsModelSection(
                    catalog,
                    preset,
                    config.model,
                    config.reasoningEffort,
                    ordinaryUsageRecovered,
                    onEvent,
                )
                SettingsMemorySection(catalog, onEvent)
                SettingsExperimentalSection(catalog, onEvent)
            }

            SettingsSection.Permissions ->
                SettingsApprovalSection(config, catalog, session.permissionSelectionError, onEvent)

            SettingsSection.Workspace -> {
                SettingsWorkspaceSection(config.cwd, config.workspaceRoots, onOpenWorkspacePicker)
                SettingsSessionLink(config, onOpenEntry)
            }

            SettingsSection.Appearance -> {
                SettingsAppearanceSection()
                SettingsShortcutsSection(onOpenShortcuts)
            }

            SettingsSection.Notifications -> {
                SettingsNotificationSection()
                SettingsRecapSection()
            }

            SettingsSection.Extensions -> SettingsExtensionsSection(onOpenEntry)
            SettingsSection.Data -> SettingsDataSection(onOpenEntry)
            SettingsSection.System -> {
                SettingsConfigSourcesSection(catalog, configPath)
                SettingsAboutSection()
            }
        }
    }
}

/** The settings page's two-level nav; [Permissions] is where `/permissions` deep-links. */
enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    Model(R.string.settings_nav_model, R.string.settings_nav_model_summary, MiuixIcons.MindMap),
    Permissions(R.string.settings_nav_permissions, R.string.settings_nav_permissions_summary, MiuixIcons.Lock),
    Workspace(R.string.settings_nav_workspace, R.string.settings_nav_workspace_summary, MiuixIcons.FolderFill),
    Appearance(R.string.settings_nav_appearance, R.string.settings_nav_appearance_summary, MiuixIcons.Tune),
    Notifications(R.string.settings_nav_notifications, R.string.settings_nav_notifications_summary, MiuixIcons.Refresh),
    Extensions(R.string.settings_nav_extensions, R.string.settings_nav_extensions_summary, MiuixIcons.Store),
    Data(R.string.settings_nav_data, R.string.settings_nav_data_summary, MiuixIcons.Notes),
    System(R.string.settings_nav_system, R.string.settings_nav_system_summary, MiuixIcons.Info),
}

@Composable
private fun SettingsWorkspaceSection(
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
private fun SettingsConfigSourcesSection(catalog: CatalogState, configPath: String) {
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
private fun originLabel(source: com.cy.codex.protocol.protocol.v2.ConfigLayerSource?): String =
    source?.label() ?: stringResource(R.string.config_layer_unknown)

/** Theme and motion — client-side choices the app-server has no opinion on; written straight into [Appearance]. */
@Composable
private fun SettingsAppearanceSection() {
    val context = LocalContext.current
    CodexSection(stringResource(R.string.settings_group_appearance)) {
        ThemeOption.entries.forEachIndexed { index, option ->
            if (index > 0) CodexRowDivider()
            CodexRadioRow(
                title = stringResource(option.labelRes),
                selected = Appearance.themeMode == option.mode,
                onClick = { Appearance.setThemeMode(context, option.mode) },
            )
        }
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_reduce_motion),
            summary = stringResource(R.string.settings_reduce_motion_summary),
            checked = Appearance.reduceMotion,
            onCheckedChange = { Appearance.setReduceMotion(context, it) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_show_tooltips),
            summary = stringResource(R.string.settings_show_tooltips_summary),
            checked = Appearance.showTooltips,
            onCheckedChange = { Appearance.setShowTooltips(context, it) },
        )
    }
}

/** Android counterpart of tui.notifications: master switch gates the runtime permission, rows are the wire-keyed whitelist. */
@Composable
private fun SettingsNotificationSection() {
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            NotificationSettings.setEnabled(context, granted)
        }
    CodexSection(stringResource(R.string.settings_group_notifications)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_notifications),
            summary = stringResource(R.string.settings_notifications_summary),
            checked = NotificationSettings.enabled,
            onCheckedChange = { enabled ->
                when {
                    !enabled -> NotificationSettings.setEnabled(context, false)
                    agentNotificationsAllowed(context) ->
                        NotificationSettings.setEnabled(context, true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> NotificationSettings.setEnabled(context, true)
                }
            },
        )
        if (NotificationSettings.enabled && !agentNotificationsAllowed(context)) {
            CodexRowDivider()
            CodexRow(
                title = stringResource(R.string.settings_notifications_permission_denied),
                enabled = false,
            )
        }
        AgentNotification.entries.forEach { type ->
            CodexRowDivider()
            CodexSwitchRow(
                title = stringResource(type.labelRes),
                checked = type in NotificationSettings.types,
                enabled = NotificationSettings.enabled,
                onCheckedChange = { NotificationSettings.setType(context, type, it) },
            )
        }
    }
}

/** Android value of tui.auto_recap; a client-side toggle — the server has no recap method. */
@Composable
private fun SettingsRecapSection() {
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
private fun SettingsAboutSection() {
    CodexSection(stringResource(R.string.settings_group_about)) {
        CodexValueRow(
            title = stringResource(R.string.settings_app_version),
            value = BuildConfig.VERSION_NAME,
        )
    }
}

/** Memory policy is a preference; the memory page remains a read-only store browser and reset tool. */
@Composable
private fun SettingsMemorySection(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
    val snapshot = catalog.configSnapshot
    CodexSection(stringResource(R.string.settings_group_memory)) {
        CodexSwitchRow(
            title = stringResource(R.string.memories_screen_use),
            summary = stringResource(R.string.memories_screen_use_detail),
            checked = snapshot.useMemories ?: true,
            onCheckedChange = {
                onEvent(AppEvent.SetMemorySettings(it, snapshot.generateMemories ?: true))
            },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.memories_screen_generate),
            summary = stringResource(R.string.memories_screen_generate_detail),
            checked = snapshot.generateMemories ?: true,
            onCheckedChange = {
                onEvent(AppEvent.SetMemorySettings(snapshot.useMemories ?: true, it))
            },
        )
    }
}

@Composable
private fun SettingsShortcutsSection(onOpenShortcuts: () -> Unit) {
    CodexSection(stringResource(R.string.settings_group_shortcuts)) {
        CodexNavRow(
            title = stringResource(R.string.shortcuts_overlay_title),
            summary = stringResource(R.string.settings_shortcuts_summary),
            onClick = onOpenShortcuts,
        )
    }
}

@Composable
private fun SettingsSessionLink(config: ThreadSessionState, onOpenEntry: (String) -> Unit) {
    CodexSection(stringResource(R.string.settings_group_session)) {
        CodexNavRow(
            title = stringResource(R.string.settings_tab_session),
            summary =
                listOfNotNull(
                        config.threadId.ifEmpty { null },
                        config.gitBranch,
                    )
                    .joinToString(" · ")
                    .ifEmpty { stringResource(R.string.settings_screen_session_not_started) },
            onClick = { onOpenEntry(DestinationCatalog.Id.Status) },
        )
    }
}

private data class SettingsLinkSpec(val id: String, val titleRes: Int, val icon: ImageVector)

@Composable
private fun SettingsExtensionsSection(onOpenEntry: (String) -> Unit) {
    val links =
        listOf(
            SettingsLinkSpec(DestinationCatalog.Id.Mcp, R.string.sidebar_library_mcp_servers, MiuixIcons.Link),
            SettingsLinkSpec(DestinationCatalog.Id.Skills, R.string.sidebar_library_skills, MiuixIcons.Tasks),
            SettingsLinkSpec(DestinationCatalog.Id.Plugins, R.string.sidebar_library_plugins, MiuixIcons.Store),
            SettingsLinkSpec(DestinationCatalog.Id.Apps, R.string.sidebar_library_apps, MiuixIcons.Community),
            SettingsLinkSpec(DestinationCatalog.Id.Hooks, R.string.sidebar_library_hooks, MiuixIcons.Refresh),
            SettingsLinkSpec(DestinationCatalog.Id.PluginShares, R.string.sidebar_library_shares, MiuixIcons.Share),
        )
    SettingsLinksGroup(stringResource(R.string.settings_group_extensions), links, onOpenEntry)
}

@Composable
private fun SettingsDataSection(onOpenEntry: (String) -> Unit) {
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
private fun SettingsLinksGroup(
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
private fun SettingsExperimentalSection(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
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

@Composable
private fun SettingsModelSection(
    catalog: CatalogState,
    preset: ModelPreset?,
    currentModel: String,
    effort: ReasoningEffort?,
    ordinaryUsageRecovered: Boolean,
    onEvent: (AppEvent) -> Unit,
) {
    // While ordinary usage is blocked the group collapses to one borrowed row
    // (codex-rs/tui/src/chatwidget/luna_reserve_model.rs); Reserve selections stay session-only.
    val mode = modelPickerMode(
        catalog.models,
        currentModel,
        ordinaryUsageRecovered,
        catalog.rateLimits.rateLimitsByLimitId,
    )
    CodexSection(
        stringResource(
            if (mode is ModelPickerMode.Normal) R.string.settings_group_model else R.string.luna_picker_header,
        ),
    ) {
        when (mode) {
            ModelPickerMode.RestrictedUnavailable ->
                CodexRow(
                    title = stringResource(R.string.luna_picker_unavailable),
                    enabled = false,
                )

            is ModelPickerMode.Restricted -> {
                CodexRadioRow(
                    title = mode.row.displayName,
                    summary = mode.row.description.ifEmpty { mode.row.model },
                    selected = true,
                    onClick = { onEvent(AppEvent.SetModel(LUNA_RESERVE_MODEL)) },
                )
                CodexRowDivider()
                CodexRow(
                    title = stringResource(R.string.luna_picker_subtitle),
                    enabled = false,
                )
            }

            is ModelPickerMode.Normal -> {
                if (mode.rows.isEmpty()) {
                    CodexRow(
                        title = stringResource(R.string.settings_screen_models_empty),
                        enabled = false,
                    )
                    return@CodexSection
                }
                mode.rows.forEachIndexed { index, model ->
                    if (index > 0) CodexRowDivider()
                    CodexRadioRow(
                        title = model.displayName,
                        summary =
                            listOfNotNull(
                                    model.description.ifEmpty { model.model },
                                    stringResource(R.string.settings_screen_model_default).takeIf {
                                        model.isDefault
                                    },
                                )
                                .joinToString(" · "),
                        selected = model.model == currentModel,
                        onClick = {
                            onEvent(AppEvent.WriteConfigValue("model", JsonPrimitive(model.model)))
                        },
                    )
                }
            }
        }
    }

    // The oss provider choice, shown only for provider `oss` — where oss_selection.rs offers it.
    if (catalog.config.snapshot.modelProvider == "oss") {
        val providers =
            listOf(
                "lmstudio" to
                    (stringResource(R.string.settings_oss_lmstudio) to
                        stringResource(R.string.settings_oss_lmstudio_summary)),
                "ollama" to
                    (stringResource(R.string.settings_oss_ollama) to
                        stringResource(R.string.settings_oss_ollama_summary)),
            )
        CodexSection(stringResource(R.string.settings_group_oss_provider)) {
            providers.forEachIndexed { index, (id, labels) ->
                if (index > 0) CodexRowDivider()
                CodexRadioRow(
                    title = labels.first,
                    summary = labels.second,
                    selected = catalog.config.snapshot.ossProvider == id,
                    onClick = {
                        onEvent(AppEvent.WriteConfigValue("oss_provider", JsonPrimitive(id)))
                    },
                )
            }
        }
    }

    // The Reserve row borrows the normal model's efforts; an effort pick stays on the thread to
    // keep Reserve routing and the saved return target.
    val efforts = (mode as? ModelPickerMode.Restricted)?.row?.supportedReasoningEfforts
        ?: preset?.supportedReasoningEfforts.orEmpty()
    if (efforts.isNotEmpty()) {
        CodexSection(stringResource(R.string.settings_group_effort)) {
            efforts.forEachIndexed { index, option ->
                if (index > 0) CodexRowDivider()
                CodexRadioRow(
                    title = option.label(),
                    selected = option == effort,
                    onClick = {
                        onEvent(
                            if (mode is ModelPickerMode.Restricted) {
                                AppEvent.SetReasoningEffort(option)
                            } else {
                                AppEvent.WriteConfigValue(
                                    "model_reasoning_effort",
                                    JsonPrimitive(option.wire),
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsApprovalSection(
    config: ThreadSessionState,
    catalog: CatalogState,
    selectionError: PermissionSelectionFailure?,
    onEvent: (AppEvent) -> Unit,
) {
    val autoReviewAvailable = catalog.autoReviewAvailable
    CodexSection(stringResource(R.string.settings_group_approval)) {
        AskForApproval.entries.forEachIndexed { index, option ->
            if (index > 0) CodexRowDivider()
            CodexRadioRow(
                title = option.label(),
                summary = option.description(),
                selected = option == config.approvalPolicy,
                onClick = {
                    onEvent(AppEvent.WriteConfigValue("approval_policy", JsonPrimitive(option.wire)))
                },
            )
        }
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.runtime_android_sandbox),
            summary = stringResource(R.string.runtime_android_sandbox_detail),
            value = stringResource(R.string.runtime_fixed),
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_screen_network_access),
            value = stringResource(R.string.settings_screen_network_allowed),
        )
    }

    // A reviewer decides *who* answers a request, not *whether* it is raised. AutoReview is
    // offered only when guardian_approval is on and configRequirements/read allows it.
    CodexSection(stringResource(R.string.settings_group_reviewer)) {
        ApprovalsReviewer.entries
            .filter { it != ApprovalsReviewer.AutoReview || autoReviewAvailable }
            .forEachIndexed { index, option ->
                if (index > 0) CodexRowDivider()
                CodexRadioRow(
                    title = option.label(),
                    summary = option.description(),
                    selected = option == config.approvalsReviewer,
                    onClick = {
                        onEvent(
                            AppEvent.WriteConfigValue(
                                "approvals_reviewer",
                                JsonPrimitive(option.wire),
                            ),
                        )
                    },
                )
            }
    }

    SettingsPermissionProfilesGroup(config, catalog, selectionError, onEvent)

    if (config.approvalPolicy == AskForApproval.Granular) {
        val granular = config.granularApproval
        CodexSection(stringResource(R.string.settings_group_granular)) {
            // Shown, not switched: there is no write path for the per-class policy, and a switch that
            // silently does nothing is worse than one that says it cannot move.
            listOf(
                    stringResource(R.string.settings_screen_granular_sandbox) to
                        granular.sandboxApproval,
                    stringResource(R.string.settings_screen_granular_rules) to granular.rules,
                    stringResource(R.string.settings_screen_granular_skills) to
                        granular.skillApproval,
                    stringResource(R.string.settings_screen_granular_permissions) to
                        granular.requestPermissions,
                    stringResource(R.string.settings_screen_granular_mcp) to
                        granular.mcpElicitations,
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
                        // The no-op keeps the switch's toggle from reporting a change it is not allowed to make.
                        onCheckedChange = {},
                        enabled = false,
                    )
                }
        }
    }
}

/**
 * Named permission profiles of `permissionProfile/list`, one radio row each. Android-only scope: built-in
 * sandbox modes stay on the rows above; the TUI preset matrix (codex-rs/tui/src/chatwidget/permissions_menu.rs) is not ported.
 */
@Composable
private fun SettingsPermissionProfilesGroup(
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
private fun PermissionProfileRow(
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
