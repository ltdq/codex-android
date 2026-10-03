package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationCatalogTest {
    @Test
    fun `every destination has exactly one owner`() {
        assertEquals(emptySet(), DestinationCatalog.duplicateIds())
    }

    @Test
    fun `legacy sidebar and settings destinations are all rehomed`() {
        val expected = setOf(
            "settings", "new", "workspace", "sessions", "archived", "projects",
            "scheduled", "new_task",
            "history", "files", "exec", "terminals", "review", "worktree", "diff", "goal", "realtime",
            "mcp", "skills", "plugins", "apps", "hooks", "plugin_shares",
            "account", "memories", "migration", "remote_control", "verification", "bedrock", "sandbox",
            "diagnostics", "status",
        )
        assertEquals(expected, DestinationCatalog.Owned)
    }

    @Test
    fun `session tools are rows of the chat's own menu`() {
        assertTrue(
            DestinationCatalog.SessionTools.intersect(DestinationCatalog.CustomizeMenu).isEmpty(),
            "a session tool is also a plugin page",
        )
        assertTrue(
            DestinationCatalog.SessionTools.intersect(DestinationCatalog.SettingsMenu).isEmpty(),
            "a session tool is also a settings page",
        )
        assertTrue(
            DestinationCatalog.SessionTools.intersect(DestinationCatalog.HomeMenu).isEmpty(),
            "the tools and the chat's own entries share an id",
        )
    }

    @Test
    fun `the rail roots are not rows of the menus that open them`() {
        assertTrue(
            DestinationCatalog.RailPages.intersect(DestinationCatalog.ProjectsMenu).isEmpty(),
            "the project page is also a row of its own menu",
        )
        assertTrue(
            DestinationCatalog.RailPages.intersect(DestinationCatalog.ScheduledMenu).isEmpty(),
            "the scheduled page is also the new-task action",
        )
    }
}
