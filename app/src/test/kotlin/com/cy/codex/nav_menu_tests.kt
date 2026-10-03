package com.cy.codex

import com.cy.codex.chatwidget.SettingsSection
import com.cy.codex.chatwidget.SidebarProject
import com.cy.codex.chatwidget.SidebarSession
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rail -> menu -> page mapping the shell is built on; every row must name a page that exists. */
class NavMenuTest {

    private val labels = NavMenuLabels(
        newSession = "New session",
        homeGroup = "Session",
        projects = "Projects",
        conversationsGroup = "Conversations",
        allConversations = "All conversations",
        sessionToolsGroup = "Current task",
        sessionTools = DestinationCatalog.SessionTools.map { "tool-$it" },
        archivedGroup = "Archived",
        archivedConversations = "Archived chats",
        pluginsExtensionsGroup = "Extensions",
        pluginsSharesGroup = "Sharing",
        pluginMenu = List(6) { "plugin-$it" },
        settingsGroup = "General",
        settingsSections = SettingsSection.entries.map { it.name },
        settingsPages = List(8) { "page-$it" },
        accountGroup = "Account and data",
    )

    private fun rows(
        section: NavSection,
        projects: List<SidebarProject> = emptyList(),
        expandedProjects: Set<String> = emptySet(),
        selectedThreadId: String = "",
        selectedRowId: String? = null,
    ): List<NavMenuRow> =
        navMenuRows(section, labels, projects, expandedProjects, selectedThreadId, selectedRowId)

    @Test
    fun `every rail item opens a menu`() {
        NavSection.entries.forEach { section ->
            assertTrue(
                rows(
                    section,
                    projects = listOf(SidebarProject("p1", "Box", "/tmp/box", emptyList())),
                ).isNotEmpty(),
                "$section opened an empty menu",
            )
        }
        // Projects is the one menu whose rows are the tree itself: with no projects it holds the
        // group title and the action that adds the first workspace, then the library.
        val empty = rows(NavSection.Projects)
        assertEquals(DestinationCatalog.Id.Workspace, (empty.first() as NavMenuRow.Header).action)
        assertTrue(empty.none { it is NavMenuRow.Project || it is NavMenuRow.Session })
    }

    @Test
    fun `every menu names its rows under a group title`() {
        NavSection.entries.forEach { section ->
            val rows = rows(section, projects = listOf(SidebarProject("p1", "Box", "/tmp/box", emptyList())))
            assertEquals(
                NavMenuRow.Header::class,
                rows.first()::class,
                "$section does not open with a group title",
            )
            rows.forEachIndexed { index, row ->
                if (row is NavMenuRow.Header) {
                    // A title that ends the list has to carry its own action; otherwise it names
                    // rows that are not there.
                    if (index == rows.lastIndex) {
                        assertTrue(row.action != null, "$section ends with a group title")
                    } else {
                        assertTrue(
                            rows[index + 1] !is NavMenuRow.Header,
                            "$section has two group titles in a row",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the project group carries the action that adds a workspace`() {
        NavSection.entries.forEach { section ->
            val group = rows(section).filterIsInstance<NavMenuRow.Header>().firstOrNull {
                it.title == labels.projects
            }
            if (section == NavSection.Home || section == NavSection.Projects) {
                assertEquals(
                    DestinationCatalog.Id.Workspace,
                    group?.action,
                    "$section does not offer the workspace action on its project group",
                )
            } else {
                assertNull(group, "$section lists projects")
            }
        }
    }

    @Test
    fun `the chat menu lists the whole library and the session tools`() {
        val rows = rows(NavSection.Home)
        val ids = rows.filterIsInstance<NavMenuRow.Entry>().map { it.id }
        assertEquals(listOf(DestinationCatalog.Id.New), ids.take(1))
        assertEquals(DestinationCatalog.Id.Sessions, ids[1])
        assertEquals(
            DestinationCatalog.SessionTools,
            ids.drop(2).toSet(),
            "the tools that act on the open session are not the chat menu's rows",
        )
        // 全部对话 stands under the project tree, and the tools under a group of their own.
        val projectsAt = rows.indexOfFirst { it is NavMenuRow.Header && it.title == labels.projects }
        val conversationsAt = rows.indexOfFirst { it is NavMenuRow.Header && it.title == labels.conversationsGroup }
        val toolsAt = rows.indexOfFirst { it is NavMenuRow.Header && it.title == labels.sessionToolsGroup }
        assertTrue(projectsAt in 0 until conversationsAt, "全部对话 is not under 项目")
        assertTrue(conversationsAt < toolsAt, "the session tools are not the last group")
    }

    @Test
    fun `the archived chats are a page of the settings menu`() {
        val settings = rows(NavSection.Settings)
        val archived =
            settings.filterIsInstance<NavMenuRow.Entry>().single { it.id == DestinationCatalog.Id.Archived }
        assertEquals(labels.archivedConversations, archived.title)
        assertEquals(
            NavSection.Settings,
            sectionOf(Surface.Archived),
            "the archived page does not belong to the section that lists it",
        )
        assertEquals(DestinationCatalog.Id.Archived, navRowIdOf(Surface.Archived, SettingsSection.Model))
    }

    @Test
    fun `plugins and settings menus cover the pages the catalog owns`() {
        val plugins =
            rows(NavSection.Plugins).filterIsInstance<NavMenuRow.Entry>().map { it.id }.toSet()
        assertEquals(DestinationCatalog.PluginsMenu, plugins)

        val settings = rows(NavSection.Settings).filterIsInstance<NavMenuRow.Entry>()
        assertEquals(
            SettingsSection.entries,
            settings.mapNotNull { settingsSectionOfRow(it.id) },
        )
        assertEquals(
            DestinationCatalog.SettingsMenu,
            settings.filter { settingsSectionOfRow(it.id) == null }.map { it.id }.toSet(),
        )
        assertEquals(
            DestinationCatalog.HomeMenu,
            rows(NavSection.Home).filterIsInstance<NavMenuRow.Entry>().map { it.id }
                .filter { it == DestinationCatalog.Id.New || it == DestinationCatalog.Id.Sessions }
                .toSet(),
        )
    }

    @Test
    fun `home menu opens a session and then the project tree`() {
        val project = SidebarProject("p1", "Box", "/tmp/box", listOf(SidebarSession("t1", "Fix", "now")))
        val collapsed = rows(NavSection.Home, projects = listOf(project))

        assertEquals(
            DestinationCatalog.Id.New,
            collapsed.filterIsInstance<NavMenuRow.Entry>()
                .single { it.id == DestinationCatalog.Id.New }
                .id,
        )
        assertEquals(
            listOf("project-p1"),
            collapsed.filterIsInstance<NavMenuRow.Project>().map { it.key },
        )
        assertTrue(collapsed.none { it is NavMenuRow.Session })
    }

    @Test
    fun `an expanded project lists its sessions and marks the open one`() {
        val project = SidebarProject(
            "p1",
            "Box",
            "/tmp/box",
            listOf(SidebarSession("t1", "Fix", "now"), SidebarSession("t2", "Ship", "2m ago")),
        )

        val expanded =
            rows(
                NavSection.Home,
                projects = listOf(project),
                expandedProjects = setOf("p1"),
                selectedThreadId = "t2",
            ).filterIsInstance<NavMenuRow.Session>()

        assertEquals(listOf("t1", "t2"), expanded.map { it.session.id })
        assertEquals(listOf(false, true), expanded.map { it.selected })
    }

    @Test
    fun `settings rows round trip to their section`() {
        SettingsSection.entries.forEach { section ->
            assertEquals(section, settingsSectionOfRow(settingsRowId(section)))
        }
        assertNull(settingsSectionOfRow(DestinationCatalog.Id.Account))
    }

    @Test
    fun `a page belongs to the section that owns it`() {
        NavSection.entries.forEach { section ->
            assertEquals(section, sectionOf(sectionRoot(section)))
        }
        assertEquals(NavSection.Plugins, sectionOf(Surface.McpServers))
        assertEquals(NavSection.Plugins, sectionOf(Surface.McpToolbox("server")))
        assertEquals(NavSection.Projects, sectionOf(Surface.EnvironmentDetail("env")))
        assertEquals(NavSection.Settings, sectionOf(Surface.Settings))
        // Session-scoped pages stay under the chat, wherever they were opened from.
        assertEquals(NavSection.Home, sectionOf(Surface.FileBrowser("/tmp")))
        assertEquals(NavSection.Home, sectionOf(Surface.Diff))
    }

    @Test
    fun `the open page marks its menu row`() {
        val permissions = settingsRowId(SettingsSection.Permissions)
        val marked =
            rows(NavSection.Settings, selectedRowId = permissions)
                .filterIsInstance<NavMenuRow.Entry>()
                .single { it.selected }
        assertEquals(permissions, marked.id)
        assertEquals(
            permissions,
            navRowIdOf(Surface.Settings, SettingsSection.Permissions),
        )
        assertEquals(DestinationCatalog.Id.Mcp, navRowIdOf(Surface.McpToolbox("server"), SettingsSection.Model))
        assertNull(navRowIdOf(Surface.Chat, SettingsSection.Model))
    }

    @Test
    fun `a search keeps the rows that match and the titles above them`() {
        val project = SidebarProject(
            "p1",
            "Box",
            "/tmp/box",
            listOf(SidebarSession("t1", "Fix parser", "now"), SidebarSession("t2", "Ship", "2m ago")),
        )
        val rows =
            rows(
                NavSection.Home,
                projects = listOf(project),
                expandedProjects = setOf("p1"),
                selectedThreadId = "t2",
            )

        val matched = filterMenuRows(rows, "parser")
        assertEquals(NavMenuRow.Header::class, matched.first()::class)
        assertEquals(
            listOf("t1"),
            matched.filterIsInstance<NavMenuRow.Session>().map { it.session.id },
        )
        // A matching session brings its project, unfolded, and keeps the selection it had.
        assertEquals(
            listOf(true),
            matched.filterIsInstance<NavMenuRow.Project>().map { it.expanded },
        )
        assertTrue(matched.filterIsInstance<NavMenuRow.Entry>().isEmpty())

        assertTrue(filterMenuRows(rows, "nothing here").isEmpty())
        assertEquals(rows, filterMenuRows(rows, "  "))
    }

    @Test
    fun `a search narrows an option menu without its group titles`() {
        val rows = rows(NavSection.Settings)
        val matched = filterMenuRows(rows, labels.settingsPages[3])

        assertEquals(
            listOf(DestinationCatalog.SettingsMenu.elementAt(3)),
            matched.filterIsInstance<NavMenuRow.Entry>().map { it.id },
        )
        assertEquals(1, matched.count { it is NavMenuRow.Header })
    }

    @Test
    fun `a page of the open section swaps the top in place`() {
        assertEquals(
            listOf(Surface.Chat, Surface.Hooks),
            sectionPageSwap(listOf(Surface.Chat, Surface.Skills), Surface.Hooks),
        )
        assertEquals(
            listOf(Surface.Chat, Surface.Settings, Surface.RemoteControl),
            sectionPageSwap(listOf(Surface.Chat, Surface.Settings, Surface.Account), Surface.RemoteControl),
        )
    }

    @Test
    fun `a page already in the stack comes back and drops what was pushed over it`() {
        // The settings body's own links push, so the section's page can stand under them.
        assertEquals(
            listOf(Surface.Chat, Surface.Settings),
            sectionPageSwap(listOf(Surface.Chat, Surface.Settings, Surface.Account), Surface.Settings),
        )
        assertEquals(
            listOf(Surface.Chat, Surface.Settings),
            sectionPageSwap(listOf(Surface.Chat, Surface.Settings, Surface.WorkspacePicker), Surface.Settings),
        )
    }

    @Test
    fun `a page of another section stacks and never leaves a route twice`() {
        assertNull(sectionPageSwap(listOf(Surface.Chat, Surface.Settings), Surface.Sessions))
        assertNull(sectionPageSwap(listOf(Surface.Chat), Surface.Skills))
        assertNull(sectionPageSwap(emptyList(), Surface.Settings))

        val swapped =
            sectionPageSwap(
                listOf(Surface.Chat, Surface.Settings, Surface.Account),
                Surface.Settings,
            ).orEmpty()
        assertEquals(swapped.distinct(), swapped)
    }
}
