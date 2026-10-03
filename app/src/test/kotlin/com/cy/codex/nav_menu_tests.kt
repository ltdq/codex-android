package com.cy.codex

import com.cy.codex.chatwidget.SettingsGroup
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
        newChat = "New chat",
        projects = "Projects",
        recentGroup = "Recent",
        showMore = "Show more",
        sessionToolsGroup = "Current task",
        sessionTools = DestinationCatalog.SessionTools.map { "tool-$it" },
        allSessions = "All sessions",
        newTask = "New task",
        scheduledGroup = "Upcoming",
        scheduledEmpty = "Nothing scheduled yet",
        customize = DestinationCatalog.CustomizeMenu.map { "customize-$it" },
        settingsGroups = SettingsGroup.entries.map { it.name },
        settingsPages = SettingsGroup.entries.map { group -> SettingsSection.of(group).map { it.name } },
    )

    private fun rows(
        section: NavSection,
        projects: List<SidebarProject> = emptyList(),
        expandedProjects: Set<String> = emptySet(),
        selectedThreadId: String = "",
        selectedRowId: String? = null,
        recent: List<SidebarSession> = emptyList(),
    ): List<NavMenuRow> =
        navMenuRows(
            section,
            labels,
            projects,
            expandedProjects,
            selectedThreadId,
            selectedRowId,
            recent,
        )

    @Test
    fun `the rail is the desktop app's five items, settings last`() {
        assertEquals(
            listOf(
                NavSection.Home,
                NavSection.Scheduled,
                NavSection.Customize,
                NavSection.Projects,
                NavSection.Settings,
            ),
            NavSection.entries,
        )
    }

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
    }

    @Test
    fun `the home menu is 新聊天, the project tree, the recent threads and the session tools`() {
        val project = SidebarProject("p1", "Box", "/tmp/box", listOf(SidebarSession("t1", "Fix", "now")))
        val recent = listOf(SidebarSession("t9", "Recent", "now"))
        val menu = rows(NavSection.Home, projects = listOf(project), recent = recent)

        assertEquals(
            DestinationCatalog.Id.New,
            menu.filterIsInstance<NavMenuRow.Entry>().first().id,
        )
        val titles = menu.filterIsInstance<NavMenuRow.Header>().map { it.title }
        assertEquals(listOf(labels.projects, labels.recentGroup, labels.sessionToolsGroup), titles)
        assertEquals(
            DestinationCatalog.Id.Workspace,
            menu.filterIsInstance<NavMenuRow.Header>().first().action,
            "the project group does not carry the workspace action",
        )
        assertEquals(
            DestinationCatalog.Id.Sessions,
            menu.filterIsInstance<NavMenuRow.Header>()
                .single { it.title == labels.recentGroup }
                .action,
            "the recent group does not carry the action that opens the whole library",
        )
        assertEquals(
            DestinationCatalog.SessionTools,
            menu.filterIsInstance<NavMenuRow.Entry>().map { it.id }.filter {
                it != DestinationCatalog.Id.New
            }.toSet(),
            "the tools that act on the open session are not the chat menu's rows",
        )
    }

    @Test
    fun `the recent group lists the library by recency and truncates it under 展开显示`() {
        val recent = List(8) { SidebarSession("t$it", "Thread $it", "now") }
        val menu = rows(NavSection.Home, recent = recent)

        val listed = menu.filterIsInstance<NavMenuRow.Session>().map { it.session.id }
        assertTrue(listed.size < recent.size, "the recent group is not truncated")
        assertEquals(recent.take(listed.size).map { it.id }, listed)
        val more = menu.filterIsInstance<NavMenuRow.ShowMore>().single()
        assertEquals(DestinationCatalog.Id.Sessions, more.id)
        assertEquals(labels.showMore, more.title)

        // A list that fits keeps no 展开显示 row.
        assertTrue(rows(NavSection.Home, recent = recent.take(2)).none { it is NavMenuRow.ShowMore })
    }

    @Test
    fun `the settings menu is four groups of pages and the archived chats`() {
        val menu = rows(NavSection.Settings)
        val entries = menu.filterIsInstance<NavMenuRow.Entry>()

        assertEquals(
            SettingsSection.entries,
            entries.mapNotNull { settingsSectionOfRow(it.id) },
            "the settings menu does not list every page of the catalog, in catalog order",
        )
        assertEquals(
            SettingsGroup.entries.map { it.name },
            menu.filterIsInstance<NavMenuRow.Header>().map { it.title },
            "the page groups are not the desktop's four",
        )
        assertEquals(
            settingsRowId(SettingsSection.ArchivedChats),
            entries.last().id,
            "the archived chats are not the last row of the last group",
        )
    }

    @Test
    fun `no two rows of a menu share a key`() {
        val home = rows(NavSection.Home, recent = listOf(SidebarSession("t1", "Fix", "now")))
        val menus = NavSection.entries.associateWith { section ->
            if (section == NavSection.Home) home else rows(section)
        }

        menus.forEach { (section, menu) ->
            val keys = menu.map { it.key }
            assertEquals(keys.size, keys.toSet().size, "$section lists a duplicate row key: $keys")
        }
    }

    @Test
    fun `every settings page belongs to exactly one group`() {
        val grouped = SettingsGroup.entries.flatMap { SettingsSection.of(it) }
        assertEquals(SettingsSection.entries.toSet(), grouped.toSet())
        assertEquals(SettingsSection.entries.size, grouped.size)
        assertEquals(
            SettingsSection.entries.size,
            SettingsGroup.entries.sumOf { SettingsSection.of(it).size },
        )
    }

    @Test
    fun `the customize menu covers the catalogs its rail item owns`() {
        val rows = rows(NavSection.Customize).filterIsInstance<NavMenuRow.Entry>().map { it.id }.toSet()
        assertEquals(DestinationCatalog.CustomizeMenu, rows)
    }

    @Test
    fun `the scheduled menu offers a task that cannot be created yet`() {
        val menu = rows(NavSection.Scheduled)
        val entry = menu.filterIsInstance<NavMenuRow.Entry>().single()
        assertEquals(DestinationCatalog.Id.NewTask, entry.id)
        assertTrue(!entry.enabled, "新建任务 is offered as if it worked")
        assertEquals(labels.scheduledGroup, menu.filterIsInstance<NavMenuRow.Header>().single().title)
        assertEquals(labels.scheduledEmpty, menu.filterIsInstance<NavMenuRow.Note>().single().text)
    }

    @Test
    fun `the archived chats are a page of the settings menu`() {
        val settings = rows(NavSection.Settings)
        val archived =
            settings.filterIsInstance<NavMenuRow.Entry>()
                .single { it.id == settingsRowId(SettingsSection.ArchivedChats) }
        assertEquals(SettingsSection.ArchivedChats.name, archived.title)
        assertEquals(
            NavSection.Settings,
            sectionOf(Surface.Archived),
            "the archived page does not belong to the section that lists it",
        )
        assertEquals(
            settingsRowId(SettingsSection.ArchivedChats),
            navRowIdOf(Surface.Archived, SettingsSection.General),
            "the page that lists the archived chats does not mark the row that opened it",
        )
        assertEquals(SettingsSection.ArchivedChats, settingsSectionOfRow(archived.id))
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

        // No recent threads are passed, so the only session rows are the project's own.
        assertEquals(listOf("t1", "t2"), expanded.map { it.session.id })
        assertEquals(listOf(false, true), expanded.map { it.selected })
    }

    @Test
    fun `settings rows round trip to their page`() {
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
        assertEquals(NavSection.Customize, sectionOf(Surface.McpServers))
        assertEquals(NavSection.Customize, sectionOf(Surface.McpToolbox("server")))
        assertEquals(NavSection.Customize, sectionOf(Surface.Plugins))
        assertEquals(NavSection.Projects, sectionOf(Surface.EnvironmentDetail("env")))
        assertEquals(NavSection.Scheduled, sectionOf(Surface.Scheduled))
        assertEquals(NavSection.Settings, sectionOf(Surface.Settings))
        assertEquals(NavSection.Settings, sectionOf(Surface.Hooks))
        // Session-scoped pages stay under the chat, wherever they were opened from.
        assertEquals(NavSection.Home, sectionOf(Surface.FileBrowser("/tmp")))
        assertEquals(NavSection.Home, sectionOf(Surface.Diff))
    }

    @Test
    fun `the open page marks its menu row`() {
        val general = settingsRowId(SettingsSection.General)
        val marked =
            rows(NavSection.Settings, selectedRowId = general)
                .filterIsInstance<NavMenuRow.Entry>()
                .single { it.selected }
        assertEquals(general, marked.id)
        assertEquals(general, navRowIdOf(Surface.Settings, SettingsSection.General))
        assertEquals(
            DestinationCatalog.Id.Mcp,
            navRowIdOf(Surface.McpToolbox("server"), SettingsSection.General),
        )
        assertEquals(
            settingsRowId(SettingsSection.Hooks),
            navRowIdOf(Surface.Hooks, SettingsSection.General),
            "the hooks page is a settings page, so it marks its own row",
        )
        assertNull(navRowIdOf(Surface.Chat, SettingsSection.General))
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
        val matched = filterMenuRows(rows, labels.settingsPages[0][2])

        assertEquals(
            listOf(settingsRowId(SettingsSection.Appearance)),
            matched.filterIsInstance<NavMenuRow.Entry>().map { it.id },
        )
        assertEquals(1, matched.count { it is NavMenuRow.Header })
    }

    @Test
    fun `a page of the open section swaps the top in place`() {
        // 插件 and MCP are two pages of the same rail item, so each replaces the other.
        assertEquals(
            listOf(Surface.Chat, Surface.McpServers),
            sectionPageSwap(listOf(Surface.Chat, Surface.Skills), Surface.McpServers),
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
