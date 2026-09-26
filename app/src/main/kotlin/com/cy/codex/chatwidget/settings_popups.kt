package com.cy.codex.chatwidget

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.BuildConfig
import com.cy.codex.CatalogState
import com.cy.codex.DestinationCatalog
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.UiConsts
import com.cy.codex.UiType
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
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Community
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Full-screen settings page: the TUI's bottom-pane pickers (bottom_pane/model_popups.rs,
 * permission_popups.rs, experimental_features_view.rs) become one two-level page of
 * miuix-preference rows sending the same events.
 */
@Composable
fun SettingsScreen(
    catalog: CatalogState,
    session: SessionState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    onOpenWorkspacePicker: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenShortcuts: () -> Unit,
    configPath: String,
    modifier: Modifier = Modifier,
) {
    val config = session.config
    val preset =
        catalog.modelPreset(config.model)
            ?: catalog.models.firstOrNull { it.isDefault && !it.hidden }
    val backStack = remember { mutableStateListOf<NavKey>(SettingsRoute.Home) }

    NavDisplay(
        backStack = backStack,
        modifier =
            modifier
                .fillMaxSize(),
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull() else onBack()
        },
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(enableCornerClip = false, dimAmount = 0f),
    ) {
        entry<SettingsRoute.Home>(swipeDismiss = NavSwipeDirection.None) {
            SettingsPage(
                title = stringResource(R.string.settings_screen_title),
                summary = stringResource(R.string.settings_home_subtitle),
                onBack = onBack,
            ) {
                SettingsHome(
                    catalog = catalog,
                    session = session,
                    onSelect = { backStack.add(SettingsRoute.Detail(it)) },
                )
            }
        }
        entry<SettingsRoute.Detail>(swipeDismiss = NavSwipeDirection.None) { route ->
            SettingsPage(
                title = stringResource(route.section.titleRes),
                summary = stringResource(route.section.descriptionRes),
                onBack = { backStack.removeLastOrNull() },
            ) {
                when (route.section) {
                    SettingsSection.Model -> {
                        SettingsModelSection(
                            catalog,
                            preset,
                            config.model,
                            config.reasoningEffort,
                            onEvent,
                        )
                        SettingsMemorySection(catalog, onEvent)
                        SettingsExperimentalSection(catalog, onEvent)
                    }

                    SettingsSection.Permissions ->
                        SettingsApprovalSection(config, catalog.autoReviewAvailable, onEvent)

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
    }
}

private sealed interface SettingsRoute : NavKey {
    data object Home : SettingsRoute
    data class Detail(val section: SettingsSection) : SettingsRoute
}

@Composable
private fun SettingsPage(
    title: String,
    summary: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
    ) {
        BasicComponent(
            title = title,
            summary = summary,
            startAction = { SettingsBackButton(onBack = onBack) },
            insideMargin = PaddingValues(14.dp, 10.dp),
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(top = UiConsts.Space4, bottom = UiConsts.PageBottomInset)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape, Key.Back -> {
                                onBack()
                                true
                            }

                            Key.Tab -> {
                                focusManager.moveFocus(
                                    if (event.isShiftPressed) {
                                        FocusDirection.Previous
                                    } else {
                                        FocusDirection.Next
                                    },
                                )
                                true
                            }

                            else -> false
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            content()
        }
    }
}

private enum class SettingsSection(
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
private fun SettingsHome(
    catalog: CatalogState,
    session: SessionState,
    onSelect: (SettingsSection) -> Unit,
) {
    val config = session.config
    SettingsGroup(stringResource(R.string.settings_home_group)) {
        SettingsSection.entries.forEach { section ->
            ArrowPreference(
                title = stringResource(section.titleRes),
                summary =
                    when (section) {
                        SettingsSection.Model ->
                            catalog.modelPreset(config.model)?.displayName
                                ?: stringResource(R.string.settings_screen_models_empty)

                        SettingsSection.Permissions -> config.approvalPolicy.label()
                        SettingsSection.Workspace ->
                            config.cwd.ifEmpty { stringResource(R.string.settings_screen_no_directory) }

                        SettingsSection.Appearance ->
                            stringResource(
                                when (Appearance.themeMode) {
                                    ColorSchemeMode.System -> R.string.settings_theme_system
                                    ColorSchemeMode.Light -> R.string.settings_theme_light
                                    ColorSchemeMode.Dark -> R.string.settings_theme_dark
                                    else -> R.string.settings_theme_system
                                },
                            )

                        SettingsSection.Notifications ->
                            if (NotificationSettings.enabled) {
                                stringResource(R.string.settings_notifications_enabled)
                            } else {
                                stringResource(R.string.settings_notifications_disabled)
                            }

                        SettingsSection.Extensions ->
                            stringResource(R.string.settings_nav_extensions_summary)

                        SettingsSection.Data -> stringResource(R.string.settings_nav_data_summary)
                        SettingsSection.System -> stringResource(R.string.settings_nav_system_summary)
                    },
                startAction = {
                    Icon(
                        section.icon,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        MiuixTheme.colorScheme.primary,
                    )
                },
                onClick = { onSelect(section) },
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = UiConsts.SectionCorner,
            insideMargin = CardDefaults.InsideMargin,
            content = { content() },
        )
    }
}

@Composable
private fun SettingsWorkspaceSection(
    cwd: String,
    roots: List<String>,
    onOpenWorkspacePicker: () -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_workspace)) {
        ArrowPreference(
            title = stringResource(R.string.settings_screen_current_directory),
            summary = cwd.ifEmpty { stringResource(R.string.settings_screen_no_directory) },
            startAction = {
                Icon(
                    MiuixIcons.FolderFill,
                    null,
                    Modifier.size(UiConsts.IconPreference),
                    MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onOpenWorkspacePicker,
        )
        if (roots.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_no_writable_roots),
                startAction = {
                    Icon(
                        MiuixIcons.FolderFill,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                enabled = false,
            )
        } else {
            roots.forEach { root ->
                BasicComponent(
                    title = root,
                    startAction = {
                        Icon(
                            MiuixIcons.FolderFill,
                            null,
                            Modifier.size(UiConsts.IconPreference),
                            MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    endActions = { MonoValue(stringResource(R.string.settings_screen_writable)) },
                )
            }
        }
    }
}

/** Which layer each effective value came from (`config/read`'s `layers` + `origins`). */
@Composable
private fun SettingsConfigSourcesSection(catalog: CatalogState, configPath: String) {
    val colors = MiuixTheme.colorScheme
    val response = catalog.config
    val layers = response.layers.orEmpty()

    SettingsGroup(stringResource(R.string.settings_group_config_sources)) {
        BasicComponent(title = "CODEX_HOME/config.toml", summary = configPath)
        if (layers.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_no_config_layers),
                startAction = {
                    Icon(
                        MiuixIcons.ConvertFile,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        colors.onSurfaceVariantSummary,
                    )
                },
                enabled = false,
            )
            return@SettingsGroup
        }

        // Highest precedence first — the order the "why is this value what it is" answer is read in.
        layers.asReversed().forEach { layer ->
            BasicComponent(
                title = layer.name.label(),
                summary =
                    listOfNotNull(
                            layer.disabledReason ?: layer.name.description(),
                            layer.sourcePath,
                            layer.version.ifEmpty { null },
                        )
                        .joinToString(" · "),
                startAction = {
                    Icon(
                        MiuixIcons.ConvertFile,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        if (layer.disabledReason == null) colors.primary
                        else colors.disabledOnSurface,
                    )
                },
                endActions = {
                    MonoValue(
                        if (layer.disabledReason == null) {
                            stringResource(R.string.settings_screen_config_active)
                        } else {
                            stringResource(R.string.settings_screen_config_disabled)
                        }
                    )
                },
            )
        }

        // One row per rendered key, naming the layer that wins it.
        val origins = response.origins
        CatalogState.RenderedConfigKeys.forEach { key ->
            val value = response.displayValue(key) ?: return@forEach
            val origin = origins[key]
            BasicComponent(
                title = key,
                summary =
                    buildString {
                        append(
                            origin?.name?.label() ?: stringResource(R.string.config_layer_unknown)
                        )
                        append(" · ")
                        append(origin?.version.orEmpty())
                    },
                startAction = {
                    Icon(
                        MiuixIcons.Tune,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        colors.onSurfaceVariantSummary,
                    )
                },
                endActions = { MonoValue(value) },
            )
        }
    }
}

/** Theme and motion — client-side choices the app-server has no opinion on; written straight into [Appearance]. */
@Composable
private fun SettingsAppearanceSection() {
    val context = LocalContext.current
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ThemeOption.entries.forEach { option ->
            RadioButtonPreference(
                title = stringResource(option.labelRes),
                selected = Appearance.themeMode == option.mode,
                onClick = { Appearance.setThemeMode(context, option.mode) },
            )
        }
        SwitchPreference(
            title = stringResource(R.string.settings_reduce_motion),
            summary = stringResource(R.string.settings_reduce_motion_summary),
            checked = Appearance.reduceMotion,
            onCheckedChange = { Appearance.setReduceMotion(context, it) },
        )
        SwitchPreference(
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
    SettingsGroup(stringResource(R.string.settings_group_notifications)) {
        SwitchPreference(
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
            BasicComponent(
                title = stringResource(R.string.settings_notifications_permission_denied),
                enabled = false,
            )
        }
        AgentNotification.entries.forEach { type ->
            SwitchPreference(
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
    SettingsGroup(stringResource(R.string.settings_group_recap)) {
        SwitchPreference(
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
    SettingsGroup(stringResource(R.string.settings_group_about)) {
        BasicComponent(
            title = stringResource(R.string.settings_app_version),
            endActions = { MonoValue(BuildConfig.VERSION_NAME) },
        )
    }
}

/** Memory policy is a preference; the memory page remains a read-only store browser and reset tool. */
@Composable
private fun SettingsMemorySection(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
    val snapshot = catalog.configSnapshot
    SettingsGroup(stringResource(R.string.settings_group_memory)) {
        SwitchPreference(
            title = stringResource(R.string.memories_screen_use),
            summary = stringResource(R.string.memories_screen_use_detail),
            checked = snapshot.useMemories ?: true,
            onCheckedChange = {
                onEvent(AppEvent.SetMemorySettings(it, snapshot.generateMemories ?: true))
            },
        )
        SwitchPreference(
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
    SettingsGroup(stringResource(R.string.settings_group_shortcuts)) {
        ArrowPreference(
            title = stringResource(R.string.shortcuts_overlay_title),
            summary = stringResource(R.string.settings_shortcuts_summary),
            startAction = {
                Icon(
                    MiuixIcons.Search,
                    null,
                    Modifier.size(UiConsts.IconPreference),
                    MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onOpenShortcuts,
        )
    }
}

@Composable
private fun SettingsSessionLink(config: ThreadSessionState, onOpenEntry: (String) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_session)) {
        ArrowPreference(
            title = stringResource(R.string.settings_tab_session),
            summary =
                listOfNotNull(
                        config.threadId.ifEmpty { null },
                        config.gitBranch,
                    )
                    .joinToString(" · ")
                    .ifEmpty { stringResource(R.string.settings_screen_session_not_started) },
            startAction = {
                Icon(
                    MiuixIcons.Info,
                    null,
                    Modifier.size(UiConsts.IconPreference),
                    MiuixTheme.colorScheme.primary,
                )
            },
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
            SettingsLinkSpec(DestinationCatalog.Id.Bedrock, R.string.sidebar_library_bedrock, MiuixIcons.Store),
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
    SettingsGroup(title) {
        links.forEach { link ->
            ArrowPreference(
                title = stringResource(link.titleRes),
                startAction = {
                    Icon(
                        link.icon,
                        null,
                        Modifier.size(UiConsts.IconPreference),
                        MiuixTheme.colorScheme.onSurfaceSecondary,
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
    SettingsGroup(stringResource(R.string.settings_group_experimental)) {
        if (features.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_experimental_empty),
                enabled = false,
            )
            return@SettingsGroup
        }
        features.forEach { feature ->
            SwitchPreference(
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
    effort: ReasoningEffort,
    onEvent: (AppEvent) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_model)) {
        if (catalog.models.isEmpty()) {
            BasicComponent(
                title = stringResource(R.string.settings_screen_models_empty),
                enabled = false,
            )
            return@SettingsGroup
        }
        catalog.models.filterNot { it.hidden }.forEach { model ->
            RadioButtonPreference(
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
        SettingsGroup(stringResource(R.string.settings_group_oss_provider)) {
            providers.forEach { (id, labels) ->
                RadioButtonPreference(
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

    val efforts = preset?.supportedReasoningEfforts.orEmpty()
    if (efforts.isNotEmpty()) {
        val selectedIndex = efforts.indexOf(effort).coerceAtLeast(0)
        SettingsGroup(stringResource(R.string.settings_group_effort)) {
            OverlaySpinnerPreference(
                items = efforts.map { option -> DropdownItem(text = option.label()) },
                selectedIndex = selectedIndex,
                title = stringResource(R.string.settings_screen_effort_title),
                summary = stringResource(R.string.settings_screen_effort_summary),
                onSelectedIndexChange = {
                    onEvent(
                        AppEvent.WriteConfigValue(
                            "model_reasoning_effort",
                            JsonPrimitive(efforts[it].wire),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun SettingsApprovalSection(
    config: ThreadSessionState,
    autoReviewAvailable: Boolean,
    onEvent: (AppEvent) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_approval)) {
        AskForApproval.entries.forEach { option ->
            RadioButtonPreference(
                title = option.label(),
                summary = option.description(),
                selected = option == config.approvalPolicy,
                onClick = {
                    onEvent(AppEvent.WriteConfigValue("approval_policy", JsonPrimitive(option.wire)))
                },
            )
        }
        BasicComponent(
            title = stringResource(R.string.runtime_android_sandbox),
            summary = stringResource(R.string.runtime_android_sandbox_detail),
            endActions = { MonoValue(stringResource(R.string.runtime_fixed)) },
        )
        BasicComponent(
            title = stringResource(R.string.settings_screen_network_access),
            endActions = {
                MonoValue(stringResource(R.string.settings_screen_network_allowed))
            },
        )
    }

    // A reviewer decides *who* answers a request, not *whether* it is raised. AutoReview is
    // offered only when guardian_approval is on and configRequirements/read allows it.
    SettingsGroup(stringResource(R.string.settings_group_reviewer)) {
        ApprovalsReviewer.entries
            .filter { it != ApprovalsReviewer.AutoReview || autoReviewAvailable }
            .forEach { option ->
                RadioButtonPreference(
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

    if (config.approvalPolicy == AskForApproval.Granular) {
        val granular = config.granularApproval
        SettingsGroup(stringResource(R.string.settings_group_granular)) {
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
                .forEach { (label, asks) ->
                    SwitchPreference(
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

/** Monospace by convention so ids, paths and counts line up. */
@Composable
private fun MonoValue(text: String) {
    Text(
        text = text,
        fontSize = UiType.Body,
        lineHeight = UiType.BodyLine,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            MiuixIcons.ChevronBackward,
            stringResource(R.string.settings_screen_back),
            Modifier.size(UiConsts.IconHeader),
            MiuixTheme.colorScheme.primary,
        )
    }
}
