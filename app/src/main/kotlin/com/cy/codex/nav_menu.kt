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
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Timer
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
    Surface.Chat,
    Surface.Sessions,
    -> NavSection.Home
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
    Surface.Account,
    Surface.Archived,
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
fun navRowIdOf(surface: Surface, settingsSection: SettingsSection): String? = when (surface) {
    Surface.Skills -> DestinationCatalog.Id.Skills
    Surface.McpServers, is Surface.McpToolbox -> DestinationCatalog.Id.Mcp
    Surface.Plugins -> DestinationCatalog.Id.Plugins
    Surface.Apps -> DestinationCatalog.Id.Apps
    Surface.Hooks -> DestinationCatalog.Id.Hooks
    Surface.PluginShares -> DestinationCatalog.Id.PluginShares
    // The settings body carries its section in the shell, not in the route.
    Surface.Settings -> settingsRowId(settingsSection)
    Surface.Sessions -> DestinationCatalog.Id.Sessions
    Surface.Archived -> DestinationCatalog.Id.Archived
    // The session tools are rows of the chat's own menu, so the open tool marks its row there.
    Surface.ThreadHistory -> DestinationCatalog.Id.History
    is Surface.FileBrowser -> DestinationCatalog.Id.Files
    Surface.ExecCommand -> DestinationCatalog.Id.Exec
    Surface.BackgroundTerminals -> DestinationCatalog.Id.Terminals
    Surface.Review -> DestinationCatalog.Id.Review
    Surface.Worktrees -> DestinationCatalog.Id.Worktree
    Surface.Diff -> DestinationCatalog.Id.Diff
    Surface.Realtime -> DestinationCatalog.Id.Realtime
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

    /**
     * A group title; [action] names a destination the group offers beside its title, which is how
     * the project tree carries the action that adds a workspace.
     */
    data class Header(val title: String, val action: String? = null) : NavMenuRow {
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
    val homeGroup: String,
    val projects: String,
    val conversationsGroup: String,
    val allConversations: String,
    val sessionToolsGroup: String,
    val sessionTools: List<String>,
    val archivedGroup: String,
    val archivedConversations: String,
    val pluginsExtensionsGroup: String,
    val pluginsSharesGroup: String,
    val pluginMenu: List<String>,
    val settingsGroup: String,
    val settingsSections: List<String>,
    val settingsPages: List<String>,
    val accountGroup: String,
)

@Composable
@ReadOnlyComposable
fun navMenuLabels(): NavMenuLabels = NavMenuLabels(
    newSession = stringResource(R.string.runtime_new_thread),
    homeGroup = stringResource(R.string.nav_menu_home_group),
    projects = stringResource(R.string.sidebar_projects_header),
    conversationsGroup = stringResource(R.string.nav_menu_conversations_group),
    allConversations = stringResource(R.string.nav_menu_all_conversations),
    sessionToolsGroup = stringResource(R.string.sidebar_session_tools_header),
    sessionTools = SessionToolSpecs.map { stringResource(it.titleRes) },
    archivedGroup = stringResource(R.string.nav_menu_archived_group),
    archivedConversations = stringResource(R.string.nav_menu_archived_conversations),
    pluginsExtensionsGroup = stringResource(R.string.nav_menu_extensions_group),
    pluginsSharesGroup = stringResource(R.string.nav_menu_shares_group),
    pluginMenu = PluginsMenuSpecs.map { stringResource(it.titleRes) },
    settingsGroup = stringResource(R.string.nav_menu_settings_group),
    settingsSections = SettingsSection.entries.map { stringResource(it.titleRes) },
    settingsPages = SettingsPageSpecs.map { stringResource(it.titleRes) },
    accountGroup = stringResource(R.string.nav_menu_account_group),
)

/**
 * The tools that act on the open session, listed under the chat's own menu.
 *
 * They used to be a popup hanging off a chip in the page's corner; the menu beside the page is the
 * same list on the shell's own grid, so the chip is gone and these are its rows.
 */
private val SessionToolSpecs = listOf(
    MenuSpec(DestinationCatalog.Id.History, R.string.sidebar_library_history, MiuixIcons.Refresh),
    MenuSpec(DestinationCatalog.Id.Files, R.string.sidebar_library_files, MiuixIcons.File),
    MenuSpec(DestinationCatalog.Id.Exec, R.string.sidebar_library_exec, MiuixIcons.Th1),
    MenuSpec(DestinationCatalog.Id.Terminals, R.string.sidebar_library_terminals, MiuixIcons.Timer),
    MenuSpec(DestinationCatalog.Id.Review, R.string.sidebar_library_review, MiuixIcons.Search),
    MenuSpec(DestinationCatalog.Id.Worktree, R.string.worktrees_title, MiuixIcons.Folder),
    MenuSpec(DestinationCatalog.Id.Diff, R.string.git_diff_screen_title, MiuixIcons.File),
    MenuSpec(DestinationCatalog.Id.Goal, R.string.goal_sheet_title, MiuixIcons.Tasks),
    MenuSpec(DestinationCatalog.Id.Realtime, R.string.sidebar_library_realtime, MiuixIcons.Mic),
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

/** The plugin rows that stay under the extension group; the rest hang off the share group. */
private const val PluginExtensionCount = 5

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

    /** Whether the menu floats over the page rather than standing pinned beside it. */
    val detached: Boolean get() = onPointer && !pinned

    /** The section the menu lists; [openPage] is the section the page on screen belongs to. */
    fun section(openPage: NavSection): NavSection = if (pinned) openPage else floating ?: openPage

    /**
     * The pointer rested on a rail item; a pinned card is what it answers to, not the pointer,
     * and a hover a click spent comes back only by [leave].
     */
    fun hover(section: NavSection): RailMenu =
        if (pinned || hoverSpent) this else copy(floating = section, onPointer = true)

    /**
     * A long press asks for the floating menu, and a pinned card already answers for the section;
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
     * The click that takes the menu card down again; the page takes back the width it gave up, and
     * the pointer that made the click stays where it is, so the item under it floats nothing.
     */
    fun unpin(): RailMenu = copy(pinned = false, hoverSpent = true)

    /**
     * A click on a rail item: the item of the section the page is already in only puts that section's
     * menu up or down, so the page keeps the item it was opened on; any other item names the section
     * the page moves to.
     */
    fun click(current: NavSection, tapped: NavSection): RailClick =
        if (tapped == current) RailClick(pinned = !pinned, opens = null)
        else RailClick(pinned = true, opens = tapped)
}

/** What a click on a rail item asks the shell to do: pin or unpin, and the section to open, if any. */
internal data class RailClick(val pinned: Boolean, val opens: NavSection?)

/**
 * The back stack left behind when the second segment shows a page of the open section, or `null`
 * when [next] belongs to another section and stacks the way any other page does.
 *
 * A page of the section swaps the top in place; one that already stands lower in the stack comes
 * back to the top, dropping the pages pushed over it — the settings body's own links push, so the
 * section's page can sit under them. No route may stand twice: `miuix-nav` keys entries by route
 * and rejects the duplicate entry.
 */
internal fun sectionPageSwap(stack: List<Surface>, next: Surface): List<Surface>? {
    val at = stack.indexOf(next)
    if (at >= 0) return stack.take(at + 1)
    val top = stack.lastOrNull() ?: return null
    if (top == Surface.Chat || sectionOf(top) != sectionOf(next)) return null
    return stack.dropLast(1) + next
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
            add(NavMenuRow.Header(labels.homeGroup))
            add(NavMenuRow.Entry(DestinationCatalog.Id.New, labels.newSession, MiuixIcons.Messages))
            addProjectTree(labels.projects, projects, expandedProjects, selectedThreadId)
            addConversations(labels)
            addSessionTools(labels, selectedRowId)
        }

        NavSection.Plugins ->
            PluginsMenuSpecs.forEachIndexed { index, spec ->
                if (index == PluginExtensionCount) add(NavMenuRow.Header(labels.pluginsSharesGroup))
                else if (index == 0) add(NavMenuRow.Header(labels.pluginsExtensionsGroup))
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
            addProjectTree(labels.projects, projects, expandedProjects, selectedThreadId)
            addConversations(labels)
        }

        NavSection.Settings -> {
            add(NavMenuRow.Header(labels.settingsGroup))
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
            // The archived chats are a page of the settings menu, not a mode of the session list.
            add(NavMenuRow.Header(labels.archivedGroup))
            add(
                NavMenuRow.Entry(
                    id = DestinationCatalog.Id.Archived,
                    title = labels.archivedConversations,
                    icon = MiuixIcons.Blocklist,
                    selected = selectedRowId == DestinationCatalog.Id.Archived,
                )
            )
        }
    }
}

/** The whole thread library under the project tree: what the tree's own sessions do not cover. */
private fun MutableList<NavMenuRow>.addConversations(labels: NavMenuLabels) {
    add(NavMenuRow.Header(labels.conversationsGroup))
    add(
        NavMenuRow.Entry(
            id = DestinationCatalog.Id.Sessions,
            title = labels.allConversations,
            icon = MiuixIcons.Messages,
        )
    )
}

/** The tools that act on the open session, under a group of their own. */
private fun MutableList<NavMenuRow>.addSessionTools(labels: NavMenuLabels, selectedRowId: String?) {
    add(NavMenuRow.Header(labels.sessionToolsGroup))
    SessionToolSpecs.forEachIndexed { index, spec ->
        add(
            NavMenuRow.Entry(
                id = spec.id,
                title = labels.sessionTools.getOrElse(index) { spec.id },
                icon = spec.icon,
                selected = selectedRowId == spec.id,
            )
        )
    }
}

/**
 * The menu's rows under a search: an option survives on its own title, a project carries the
 * sessions that match it, and a group title survives only while it still names a row.
 */
internal fun filterMenuRows(rows: List<NavMenuRow>, query: String): List<NavMenuRow> {
    val needle = query.trim()
    if (needle.isEmpty()) return rows
    val selectedSessions =
        rows.filterIsInstance<NavMenuRow.Session>().filter { it.selected }.map { it.session.id }.toSet()
    return buildList {
        var header: NavMenuRow.Header? = null
        rows.forEach { row ->
            when (row) {
                is NavMenuRow.Header -> header = row

                is NavMenuRow.Entry ->
                    if (row.title.contains(needle, ignoreCase = true)) {
                        header?.let { add(it) }
                        header = null
                        add(row)
                    }

                is NavMenuRow.Project -> {
                    val sessions =
                        row.project.sessions.filter { it.title.contains(needle, ignoreCase = true) }
                    if (row.project.name.contains(needle, ignoreCase = true) || sessions.isNotEmpty()) {
                        header?.let { add(it) }
                        header = null
                        add(NavMenuRow.Project(row.project, expanded = true))
                        sessions.forEach { add(NavMenuRow.Session(it, it.id in selectedSessions)) }
                    }
                }

                // A session is only ever listed under its project, which the row above carries.
                is NavMenuRow.Session -> Unit
            }
        }
    }
}

/**
 * The project tree under its own group title.
 *
 * The title stands even with no projects: the action that adds the first workspace hangs off it, so
 * an empty tree is where it is needed most.
 */
private fun MutableList<NavMenuRow>.addProjectTree(
    title: String,
    projects: List<SidebarProject>,
    expandedProjects: Set<String>,
    selectedThreadId: String,
) {
    // The action stands on the group's own row, where the projects it acts on are listed.
    add(NavMenuRow.Header(title, action = DestinationCatalog.Id.Workspace))
    projects.forEach { project ->
        val expanded = project.id in expandedProjects
        add(NavMenuRow.Project(project, expanded))
        if (expanded) {
            project.sessions.forEach { add(NavMenuRow.Session(it, it.id == selectedThreadId)) }
        }
    }
}
