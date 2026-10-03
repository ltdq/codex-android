package com.cy.codex.chatwidget

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.WorldClock

/** The settings menu's groups, in the order the sidebar lists them. */
enum class SettingsGroup(@StringRes val titleRes: Int) {
    Personal(R.string.settings_group_personal),
    Integrations(R.string.settings_group_integrations),
    Coding(R.string.settings_group_coding),
    Archived(R.string.settings_group_archived),
}

/**
 * One page of the settings body.
 *
 * The desktop app's sidebar is four groups of nineteen pages and the body shows one page at a time,
 * so a section is not a route of its own: the rail's settings item opens [Surface.Settings] and the
 * menu swaps which section it holds (`CodexApp.settingsSection`).
 *
 * [descriptionRes] is the subtitle the desktop prints under the page title, or 0 where it prints
 * none. [icon] is the menu row's glyph.
 */
enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val group: SettingsGroup,
) {
    // ---- 个人 -----------------------------------------------------------------
    General(R.string.settings_page_general, 0, MiuixIcons.Settings, SettingsGroup.Personal),
    Import(
        R.string.settings_page_import,
        R.string.settings_page_import_summary,
        MiuixIcons.Import,
        SettingsGroup.Personal,
    ),
    Appearance(R.string.settings_page_appearance, 0, MiuixIcons.Theme, SettingsGroup.Personal),
    Voice(R.string.settings_page_voice, 0, MiuixIcons.Mic, SettingsGroup.Personal),
    Agent(
        R.string.settings_page_agent,
        R.string.settings_page_agent_summary,
        MiuixIcons.MindMap,
        SettingsGroup.Personal,
    ),
    Personalization(
        R.string.settings_page_personalization,
        0,
        MiuixIcons.Notes,
        SettingsGroup.Personal,
    ),
    Pets(R.string.settings_page_pets, 0, MiuixIcons.Favorites, SettingsGroup.Personal),
    Shortcuts(R.string.settings_page_shortcuts, 0, MiuixIcons.Tune, SettingsGroup.Personal),

    // ---- 集成 -----------------------------------------------------------------
    Plugins(
        R.string.settings_page_plugins,
        R.string.settings_page_plugins_summary,
        MiuixIcons.Store,
        SettingsGroup.Integrations,
    ),
    Computer(
        R.string.settings_page_computer,
        0,
        MiuixIcons.ScreenMirroring,
        SettingsGroup.Integrations,
    ),
    Snapshots(
        R.string.settings_page_snapshots,
        0,
        MiuixIcons.ScreenCapture,
        SettingsGroup.Integrations,
    ),
    Browser(R.string.settings_page_browser, 0, MiuixIcons.WorldClock, SettingsGroup.Integrations),

    // ---- 编码 -----------------------------------------------------------------
    Hooks(
        R.string.settings_page_hooks,
        R.string.settings_page_hooks_summary,
        MiuixIcons.Refresh,
        SettingsGroup.Coding,
    ),
    Connections(
        R.string.settings_page_connections,
        0,
        MiuixIcons.Link,
        SettingsGroup.Coding,
    ),
    Review(R.string.settings_page_review, 0, MiuixIcons.Report, SettingsGroup.Coding),
    Git(R.string.settings_page_git, 0, MiuixIcons.Merge, SettingsGroup.Coding),
    Environment(
        R.string.settings_page_environment,
        R.string.settings_page_environment_summary,
        MiuixIcons.Layers,
        SettingsGroup.Coding,
    ),
    Worktrees(
        R.string.settings_page_worktrees,
        0,
        MiuixIcons.HorizontalSplit,
        SettingsGroup.Coding,
    ),

    // ---- 已归档 ---------------------------------------------------------------
    ArchivedChats(
        R.string.settings_page_archived_chats,
        0,
        MiuixIcons.Blocklist,
        SettingsGroup.Archived,
    ),
    ;

    companion object {
        /** The pages of [group], in catalog order; the menu lists a group as a title and its rows. */
        fun of(group: SettingsGroup): List<SettingsSection> = entries.filter { it.group == group }
    }
}

/** The page's title, resolved in the composition so the catalog stays testable. */
@Composable
@ReadOnlyComposable
fun SettingsSection.title(): String = stringResource(titleRes)

/** The page's subtitle, or `null` where the desktop prints none. */
@Composable
@ReadOnlyComposable
fun SettingsSection.description(): String? =
    if (descriptionRes == 0) null else stringResource(descriptionRes)
