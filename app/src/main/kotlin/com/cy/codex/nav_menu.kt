package com.cy.codex

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cy.codex.chatwidget.SettingsSection
import com.cy.codex.chatwidget.SidebarProject
import com.cy.codex.chatwidget.SidebarSession
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Tune

/**
 * What the rail offers, top to bottom; settings comes last, being chrome rather than a page.
 */
enum class NavSection(@StringRes val titleRes: Int, val icon: ImageVector) {
    Home(R.string.nav_rail_home, MiuixIcons.Messages),
    Plugins(R.string.nav_rail_plugins, MiuixIcons.Store),
    Projects(R.string.nav_rail_projects, MiuixIcons.Folder),
    Settings(R.string.nav_rail_settings, MiuixIcons.Settings),
}

/**
 * The rail item a page belongs to; the rail highlights where the page lives, not the item that was
 * last tapped.
 */
fun sectionOf(surface: Surface): NavSection = when (surface) {
    Surface.Chat -> NavSection.Home
    Surface.Skills,
    Surface.McpServers,
    is Surface.McpToolbox,
    Surface.Plugins,
    Surface.Apps,
    Surface.Hooks,
    Surface.PluginShares,
    -> NavSection.Plugins

    Surface.Projects,
    is Surface.EnvironmentDetail,
    -> NavSection.Projects

    Surface.Settings,
    is Surface.SettingsDetail,
    Surface.Account,
    Surface.Memories,
    Surface.ExternalAgentImport,
    Surface.RemoteControl,
    Surface.UserVerification,
    Surface.WindowsSandbox,
    Surface.Diagnostics,
    Surface.SessionStatus,
    -> NavSection.Settings

    // Everything else acts on the open session, so it belongs to the chat.
    else -> NavSection.Home
}

/** Page a rail item opens; the menu lists what else its section holds. */
fun sectionRoot(section: NavSection): Surface = when (section) {
    NavSection.Home -> Surface.Chat
    NavSection.Plugins -> Surface.Skills
    NavSection.Projects -> Surface.Projects
    NavSection.Settings -> Surface.Settings
}

/** The menu row that marks [surface], or `null` when no row names it. */
fun navRowIdOf(surface: Surface): String? = when (surface) {
    Surface.Skills -> DestinationCatalog.Id.Skills
    Surface.McpServers, is Surface.McpToolbox -> DestinationCatalog.Id.Mcp
    Surface.Plugins -> DestinationCatalog.Id.Plugins
    Surface.Apps -> DestinationCatalog.Id.Apps
    Surface.Hooks -> DestinationCatalog.Id.Hooks
    Surface.PluginShares -> DestinationCatalog.Id.PluginShares
    is Surface.SettingsDetail -> settingsRowId(surface.section)
    Surface.Account -> DestinationCatalog.Id.Account
    Surface.Memories -> DestinationCatalog.Id.Memories
    Surface.ExternalAgentImport -> DestinationCatalog.Id.Migration
    Surface.RemoteControl -> DestinationCatalog.Id.RemoteControl
    Surface.UserVerification -> DestinationCatalog.Id.Verification
    Surface.WindowsSandbox -> DestinationCatalog.Id.Sandbox
    Surface.Diagnostics -> DestinationCatalog.Id.Diagnostics
    Surface.SessionStatus -> DestinationCatalog.Id.Status
    else -> null
}

/** The row a settings section answers to; [settingsSectionOfRow] reads it back. */
internal fun settingsRowId(section: SettingsSection): String = "settings:${section.name}"

internal fun settingsSectionOfRow(id: String): SettingsSection? {
    val name = id.removePrefix(SettingsRowPrefix).takeIf { it != id } ?: return null
    return SettingsSection.entries.firstOrNull { it.name == name }
}

private const val SettingsRowPrefix = "settings:"

/** One row of the shell's second segment. */
sealed interface NavMenuRow {
    val key: String

    data class Header(val title: String) : NavMenuRow {
        override val key: String = "header-$title"
    }

    data class Entry(
        val id: String,
        val title: String,
        val icon: ImageVector,
        val selected: Boolean = false,
    ) : NavMenuRow {
        override val key: String = "entry-$id"
    }

    data class Project(val project: SidebarProject, val expanded: Boolean) : NavMenuRow {
        override val key: String = "project-${project.id}"
    }

    data class Session(val session: SidebarSession, val selected: Boolean) : NavMenuRow {
        override val key: String = "session-${session.id}"
    }
}

/** Row titles the menu needs; resolved in the composition so [navMenuRows] stays testable. */
data class NavMenuLabels(
    val newSession: String,
    val projects: String,
    val pluginMenu: List<String>,
    val settingsSections: List<String>,
    val settingsPages: List<String>,
    val accountGroup: String,
)

@Composable
@ReadOnlyComposable
fun navMenuLabels(): NavMenuLabels = NavMenuLabels(
    newSession = stringResource(R.string.runtime_new_thread),
    projects = stringResource(R.string.sidebar_projects_header),
    pluginMenu = PluginsMenuSpecs.map { stringResource(it.titleRes) },
    settingsSections = SettingsSection.entries.map { stringResource(it.titleRes) },
    settingsPages = SettingsPageSpecs.map { stringResource(it.titleRes) },
    accountGroup = stringResource(R.string.nav_menu_account_group),
)

/** What the rail's plugins item opens: the extension pages, skills and MCP first. */
private val PluginsMenuSpecs = listOf(
    MenuSpec(DestinationCatalog.Id.Skills, R.string.sidebar_library_skills, MiuixIcons.Tasks),
    MenuSpec(DestinationCatalog.Id.Mcp, R.string.sidebar_library_mcp_servers, MiuixIcons.Link),
    MenuSpec(DestinationCatalog.Id.Plugins, R.string.sidebar_library_plugins, MiuixIcons.Store),
    MenuSpec(DestinationCatalog.Id.Apps, R.string.sidebar_library_apps, MiuixIcons.Community),
    MenuSpec(DestinationCatalog.Id.Hooks, R.string.sidebar_library_hooks, MiuixIcons.Refresh),
    MenuSpec(DestinationCatalog.Id.PluginShares, R.string.sidebar_library_shares, MiuixIcons.Share),
)

/** Pages that configure the account rather than the open session; they hang off the settings menu. */
private val SettingsPageSpecs = listOf(
    MenuSpec(DestinationCatalog.Id.Account, R.string.sidebar_library_account, MiuixIcons.Info),
    MenuSpec(DestinationCatalog.Id.Memories, R.string.sidebar_library_memories, MiuixIcons.Notes),
    MenuSpec(DestinationCatalog.Id.Migration, R.string.sidebar_library_migration, MiuixIcons.ConvertFile),
    MenuSpec(DestinationCatalog.Id.RemoteControl, R.string.sidebar_library_remote, MiuixIcons.Link),
    MenuSpec(DestinationCatalog.Id.Verification, R.string.sidebar_library_verification, MiuixIcons.Lock),
    MenuSpec(DestinationCatalog.Id.Sandbox, R.string.sidebar_library_sandbox, MiuixIcons.Tune),
    MenuSpec(DestinationCatalog.Id.Diagnostics, R.string.sidebar_library_diagnostics, MiuixIcons.Search),
    MenuSpec(DestinationCatalog.Id.Status, R.string.session_status_title, MiuixIcons.Tasks),
)

private class MenuSpec(val id: String, @StringRes val titleRes: Int, val icon: ImageVector)

/**
 * The shell's second segment: the menu a click pinned, and the section a pointer floats over it.
 * The two are exclusive; [floating] outlives [onPointer] by the panel's exit animation.
 */
internal data class RailMenu(
    val pinned: Boolean = false,
    /** Section a pointer put up; the rows the panel lists for as long as it is drawn. */
    val floating: NavSection? = null,
    /** Whether the pointer is on the rail or the menu, that is, whether the pick is still live. */
    val onPointer: Boolean = false,
    /**
     * Whether a click has spent the hover the pointer stands on, which is the case after a column
     * is closed under it: the rail floats nothing until the pointer has been clear and come back.
     */
    val hoverSpent: Boolean = false,
) {
    /** Whether the menu is drawn at all. */
    val shown: Boolean get() = pinned || onPointer

    /** Whether the menu floats over the page rather than standing as the window's second column. */
    val detached: Boolean get() = onPointer && !pinned

    /** The section the menu lists; [openPage] is the section the page on screen belongs to. */
    fun section(openPage: NavSection): NavSection = if (pinned) openPage else floating ?: openPage

    /**
     * The pointer rested on a rail item; a pinned column is what it answers to, not the pointer,
     * and a hover a click spent comes back only by [leave].
     */
    fun hover(section: NavSection): RailMenu =
        if (pinned || hoverSpent) this else copy(floating = section, onPointer = true)

    /**
     * A long press asks for the floating menu, and a pinned column already answers for the section;
     * the rail names the item it pressed instead, which is what [pinned] tells it to do. The gesture
     * is the pointer's own, so the hover it stands on lives again.
     */
    fun press(section: NavSection): RailMenu =
        if (pinned) this else copy(floating = section, onPointer = true, hoverSpent = false)

    /**
     * The pointer is clear of the rail and the menu: the pick is spent, the panel's rows are not,
     * and the rail floats a section again the next time the pointer rests on it.
     */
    fun leave(): RailMenu = copy(onPointer = false, hoverSpent = false)

    /** A click pinned the menu, and what is left to list is the section the page belongs to. */
    fun pin(): RailMenu = copy(pinned = true, floating = null, onPointer = false)

    /**
     * The click that closes the column again; the page takes back the width it gave up, and the
     * pointer that made the click stays where it is, so the item under it floats nothing.
     */
    fun unpin(): RailMenu = copy(pinned = false, hoverSpent = true)
}

/**
 * Rows the rail item's menu shows; [selectedRowId] marks the row that names the open page.
 */
internal fun navMenuRows(
    section: NavSection,
    labels: NavMenuLabels,
    projects: List<SidebarProject>,
    expandedProjects: Set<String>,
    selectedThreadId: String,
    selectedRowId: String?,
): List<NavMenuRow> = buildList {
    when (section) {
        NavSection.Home -> {
            add(NavMenuRow.Entry(DestinationCatalog.Id.New, labels.newSession, MiuixIcons.Messages))
            add(NavMenuRow.Header(labels.projects))
            addProjectTree(projects, expandedProjects, selectedThreadId)
        }

        NavSection.Plugins ->
            PluginsMenuSpecs.forEachIndexed { index, spec ->
                add(
                    NavMenuRow.Entry(
                        id = spec.id,
                        title = labels.pluginMenu.getOrElse(index) { spec.id },
                        icon = spec.icon,
                        selected = selectedRowId == spec.id,
                    )
                )
            }

        NavSection.Projects -> {
            add(NavMenuRow.Header(labels.projects))
            addProjectTree(projects, expandedProjects, selectedThreadId)
        }

        NavSection.Settings -> {
            SettingsSection.entries.forEachIndexed { index, settings ->
                val id = settingsRowId(settings)
                add(
                    NavMenuRow.Entry(
                        id = id,
                        title = labels.settingsSections.getOrElse(index) { settings.name },
                        icon = settings.icon,
                        selected = selectedRowId == id,
                    )
                )
            }
            add(NavMenuRow.Header(labels.accountGroup))
            SettingsPageSpecs.forEachIndexed { index, spec ->
                add(
                    NavMenuRow.Entry(
                        id = spec.id,
                        title = labels.settingsPages.getOrElse(index) { spec.id },
                        icon = spec.icon,
                        selected = selectedRowId == spec.id,
                    )
                )
            }
        }
    }
}

private fun MutableList<NavMenuRow>.addProjectTree(
    projects: List<SidebarProject>,
    expandedProjects: Set<String>,
    selectedThreadId: String,
) {
    projects.forEach { project ->
        val expanded = project.id in expandedProjects
        add(NavMenuRow.Project(project, expanded))
        if (expanded) {
            project.sessions.forEach { add(NavMenuRow.Session(it, it.id == selectedThreadId)) }
        }
    }
}
