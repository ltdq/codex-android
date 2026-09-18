package com.cy.codexui

import org.junit.Test
import kotlin.test.assertEquals

class SlashCommandsTest {
    @Test
    fun `bare slash lists the whole catalog in declared order`() {
        assertEquals(SlashCommands.All, SlashCommands.filter("/"))
    }

    @Test
    fun `clicking a suggestion matches a recognized command`() {
        assertEquals(
            SlashCommands.All.map { it.name }.toSet(),
            CodexApp.ComposerCommands,
        )
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
}
