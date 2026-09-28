package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SlashCommandsTest {
    @Test
    fun `bare slash lists the whole catalog in declared order`() {
        assertEquals(SlashCommands.All, SlashCommands.filter("/"))
    }

    @Test
    fun `clicking a suggestion matches a recognized command`() {
        assertEquals(
            SlashCommands.Known,
            CodexApp.ComposerCommands,
        )
    }

    @Test
    fun `aliases resolve to their command and stay out of the bare list`() {
        assertEquals("stop", SlashCommands.find("clean")?.name)
        assertEquals("cd", SlashCommands.find("cwd")?.name)
        assertEquals("cd", SlashCommands.find("pwd")?.name)
        assertEquals(listOf("stop"), SlashCommands.filter("/clean").map { it.name })
        assertEquals(
            SlashCommands.All,
            SlashCommands.filter("/"),
        )
    }

    @Test
    fun `an alias prefix does not offer the hidden alias`() {
        assertEquals(listOf("clear"), SlashCommands.filter("/cle").map { it.name })
    }

    @Test
    fun `prefix filtering preserves catalog order`() {
        assertEquals(
            listOf("model", "mcp", "memories"),
            SlashCommands.filter("/m").map { it.name },
        )
    }

    @Test
    fun `filtering is case insensitive`() {
        assertEquals(listOf("model"), SlashCommands.filter("/MODEL").map { it.name })
    }

    @Test
    fun `account-gated commands stay out of suggestions`() {
        val names = SlashCommands.filter("/", usageAvailable = false, appsAvailable = false).map { it.name }
        assertFalse("usage" in names)
        assertFalse("apps" in names)
        assertEquals(emptyList(), SlashCommands.filter("/usage", usageAvailable = false))
        assertEquals(emptyList(), SlashCommands.filter("/apps", appsAvailable = false))
    }
}
