package com.cy.codex

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.cy.codex.chatwidget.SettingsGroup
import com.cy.codex.chatwidget.SettingsSection
import com.cy.codex.chatwidget.SidebarProject
import com.cy.codex.chatwidget.SidebarSession
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Th1
import top.yukonga.miuix.kmp.icon.extended.Timer

/**
 * What the rail offers, top to bottom; settings comes last, being chrome rather than a page.
 *
 * The items are the desktop app's five: 主页, 定时任务, 自定义, 项目, and 设置 on the rail's floor.
 */
enum class NavSection(@StringRes val titleRes: Int, val icon: ImageVector) {
    Home(R.string.nav_rail_home, MiuixIcons.Home),
    Scheduled(R.string.nav_rail_scheduled, MiuixIcons.Alarm),
    Customize(R.string.nav_rail_customize, MiuixIcons.Community),
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

    Surface.Scheduled -> NavSection.Scheduled

    Surface.Skills,
    Surface.McpServers,
    is Surface.McpToolbox,
    Surface.Plugins,
    Surface.Apps,
    Surface.PluginShares,
    -> NavSection.Customize

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
    Surface.Hooks,
    -> NavSection.Settings

    // Everything else acts on the open session, so it belongs to the chat.
    else -> NavSection.Home
}

/** Page a rail item opens; the menu lists what else its section holds. */
fun sectionRoot(section: NavSection): Surface = when (section) {
    NavSection.Home -> Surface.Chat
    NavSection.Scheduled -> Surface.Scheduled
    NavSection.Customize -> Surface.Plugins
    NavSection.Projects -> Surface.Projects
    NavSection.Settings -> Surface.Settings
}

/** The menu row that marks [surface], or `null` when no row names it. */
fun navRowIdOf(surface: Surface, settingsSection: SettingsSection): String? = when (surface) {
    Surface.Plugins -> DestinationCatalog.Id.Plugins
    Surface.Skills -> DestinationCatalog.Id.Skills
    Surface.McpServers, is Surface.McpToolbox -> DestinationCatalog.Id.Mcp
    Surface.Apps -> DestinationCatalog.Id.Apps
    Surface.PluginShares -> DestinationCatalog.Id.PluginShares
    Surface.Scheduled -> DestinationCatalog.Id.Scheduled
    // The settings body carries its section in the shell, not in the route.
    Surface.Settings -> settingsRowId(settingsSection)
    Surface.Sessions -> DestinationCatalog.Id.Sessions
    // The page that lists the archived chats owns the list they are read in.
    Surface.Archived -> settingsRowId(SettingsSection.ArchivedChats)
    Surface.Hooks -> settingsRowId(SettingsSection.Hooks)
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

/** The row a settings page answers to; [settingsSectionOfRow] reads it back. */
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
        val enabled: Boolean = true,
    ) : NavMenuRow {
        override val key: String = "entry-$id"
    }

    data class Project(val project: SidebarProject, val expanded: Boolean) : NavMenuRow {
        override val key: String = "project-${project.id}"
    }

    data class Session(val session: SidebarSession, val selected: Boolean) : NavMenuRow {
        override val key: String = "session-${session.id}"
    }

    /**
     * The row a truncated list ends with, which is the desktop's 展开显示: the rest of the list is
     * behind it rather than in the card.
     */
    data class ShowMore(val id: String, val title: String) : NavMenuRow {
        override val key: String = "more-$id"
    }

    /** A line of text where a group has nothing to list; not pressable, so it is not an [Entry]. */
    data class Note(val text: String) : NavMenuRow {
        override val key: String = "note-$text"
    }
}

/** Row titles the menu needs; resolved in the composition so [navMenuRows] stays testable. */
data class NavMenuLabels(
    val newChat: String,
    val projects: String,
    val recentGroup: String,
    val showMore: String,
    val sessionToolsGroup: String,
    val sessionTools: List<String>,
    val allSessions: String,
    val newTask: String,
    val scheduledGroup: String,
    val scheduledEmpty: String,
    val customize: List<String>,
    val settingsGroups: List<String>,
    val settingsPages: List<List<String>>,
)

@Composable
@ReadOnlyComposable
fun navMenuLabels(): NavMenuLabels = NavMenuLabels(
    newChat = stringResource(R.string.nav_menu_new_chat),
    projects = stringResource(R.string.sidebar_projects_header),
    recentGroup = stringResource(R.string.nav_menu_recent_group),
    showMore = stringResource(R.string.nav_menu_show_more),
    sessionToolsGroup = stringResource(R.string.sidebar_session_tools_header),
    sessionTools = SessionToolSpecs.map { stringResource(it.titleRes) },
    allSessions = stringResource(R.string.sidebar_library_all_sessions),
    newTask = stringResource(R.string.nav_menu_new_task),
    scheduledGroup = stringResource(R.string.nav_menu_scheduled_group),
    scheduledEmpty = stringResource(R.string.nav_menu_scheduled_empty),
    customize = CustomizeMenuSpecs.map { stringResource(it.titleRes) },
    settingsGroups = SettingsGroup.entries.map { stringResource(it.titleRes) },
    settingsPages =
        SettingsGroup.entries.map { group ->
            SettingsSection.of(group).map { stringResource(it.titleRes) }
        },
)

/**
 * The tools that act on the open session, listed under the chat's own menu.
 *
 * The desktop's sidebar has no such group — it reaches these from the composer and the tab bar —
 * and the app's session has no other entry to the filesystem, the exec page or the background
 * terminals, so the port keeps them as a group of the home menu rather than losing them.
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

/** What the rail's 自定义 item opens: the desktop's plugins pane lists 插件 and 技能 first. */
private val CustomizeMenuSpecs = listOf(
    MenuSpec(DestinationCatalog.Id.Plugins, R.string.settings_page_plugins, MiuixIcons.Store),
    MenuSpec(DestinationCatalog.Id.Skills, R.string.sidebar_library_skills, MiuixIcons.Tasks),
    MenuSpec(DestinationCatalog.Id.Mcp, R.string.sidebar_library_mcp_servers, MiuixIcons.Link),
    MenuSpec(DestinationCatalog.Id.Apps, R.string.sidebar_library_apps, MiuixIcons.Community),
    MenuSpec(DestinationCatalog.Id.PluginShares, R.string.sidebar_library_shares, MiuixIcons.Share),
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

/** How many threads the 最近 group lists before it hands the rest to 展开显示. */
private const val RecentSessionLimit = 5

/**
 * Rows the rail item's menu shows; [selectedRowId] marks the row that names the open page.
 *
 * The home menu is the desktop's sidebar: 新聊天, the project tree, and the recent threads under
 * 展开显示. [recent] is the thread library by recency, which the app folds itself.
 */
internal fun navMenuRows(
    section: NavSection,
    labels: NavMenuLabels,
    projects: List<SidebarProject>,
    expandedProjects: Set<String>,
    selectedThreadId: String,
    selectedRowId: String?,
    recent: List<SidebarSession> = emptyList(),
): List<NavMenuRow> = buildList {
    when (section) {
        NavSection.Home -> {
            add(
                NavMenuRow.Entry(
                    id = DestinationCatalog.Id.New,
                    title = labels.newChat,
                    icon = MiuixIcons.Create,
                )
            )
            addProjectTree(labels.projects, projects, expandedProjects, selectedThreadId)
            addRecent(labels, recent, selectedThreadId)
            addSessionTools(labels, selectedRowId)
        }

        NavSection.Scheduled -> {
            add(
                NavMenuRow.Entry(
                    id = DestinationCatalog.Id.NewTask,
                    title = labels.newTask,
                    icon = MiuixIcons.Create,
                    enabled = false,
                )
            )
            add(NavMenuRow.Header(labels.scheduledGroup))
            add(NavMenuRow.Note(labels.scheduledEmpty))
        }

        NavSection.Customize ->
            CustomizeMenuSpecs.forEachIndexed { index, spec ->
                add(
                    NavMenuRow.Entry(
                        id = spec.id,
                        title = labels.customize.getOrElse(index) { spec.id },
                        icon = spec.icon,
                        selected = selectedRowId == spec.id,
                    )
                )
            }

        NavSection.Projects ->
            addProjectTree(labels.projects, projects, expandedProjects, selectedThreadId)

        NavSection.Settings -> {
            // The archived chats are the fourth group's only page, so the groups carry every row and
            // a second header for them would repeat both the title and the row.
            SettingsGroup.entries.forEachIndexed { index, group ->
                add(NavMenuRow.Header(labels.settingsGroups.getOrElse(index) { group.name }))
                SettingsSection.of(group).forEachIndexed { pageIndex, page ->
                    val id = settingsRowId(page)
                    add(
                        NavMenuRow.Entry(
                            id = id,
                            title = labels.settingsPages.getOrNull(index)?.getOrElse(pageIndex) { page.name }
                                ?: page.name,
                            icon = page.icon,
                            selected = selectedRowId == id,
                        )
                    )
                }
            }
        }
    }
}

/**
 * The recent threads, truncated to [RecentSessionLimit].
 *
 * The list is the library by recency, so a thread that is also under its project is listed here as
 * well: the desktop's 最近 group is where a thread is reopened, and the tree is where it is filed.
 */
private fun MutableList<NavMenuRow>.addRecent(
    labels: NavMenuLabels,
    recent: List<SidebarSession>,
    selectedThreadId: String,
) {
    add(NavMenuRow.Header(labels.recentGroup, action = DestinationCatalog.Id.Sessions))
    recent.take(RecentSessionLimit).forEach { add(NavMenuRow.Session(it, it.id == selectedThreadId)) }
    if (recent.size > RecentSessionLimit) {
        add(NavMenuRow.ShowMore(DestinationCatalog.Id.Sessions, labels.showMore))
    }
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

                is NavMenuRow.Note ->
                    if (row.text.contains(needle, ignoreCase = true)) {
                        header?.let { add(it) }
                        header = null
                        add(row)
                    }

                is NavMenuRow.ShowMore ->
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