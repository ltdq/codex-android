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
        projects = "Projects",
        pluginMenu = List(6) { "plugin-$it" },
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
            assertTrue(rows(section).isNotEmpty(), "$section opened an empty menu")
        }
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
    }

    @Test
    fun `home menu opens a session and then the project tree`() {
        val project = SidebarProject("p1", "Box", "/tmp/box", listOf(SidebarSession("t1", "Fix", "now")))
        val collapsed = rows(NavSection.Home, projects = listOf(project))

        assertEquals(DestinationCatalog.Id.New, (collapsed.first() as NavMenuRow.Entry).id)
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
        assertEquals(NavSection.Settings, sectionOf(Surface.SettingsDetail(SettingsSection.Model)))
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
        assertEquals(permissions, navRowIdOf(Surface.SettingsDetail(SettingsSection.Permissions)))
        assertEquals(DestinationCatalog.Id.Mcp, navRowIdOf(Surface.McpToolbox("server")))
        assertNull(navRowIdOf(Surface.Chat))
    }
}
