package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a pointer and a click do to the shell's second segment: a click pins, a pointer floats.
 */
class RailMenuTest {

    @Test
    fun `a pointer on an item floats that section over the page`() {
        val menu = RailMenu().hover(NavSection.Plugins)

        assertTrue(menu.shown)
        assertTrue(menu.detached)
        assertEquals(NavSection.Plugins, menu.floating)
        assertEquals(NavSection.Plugins, menu.section(NavSection.Home))
    }

    @Test
    fun `moving along the rail swaps the rows without taking the panel down`() {
        val menu = RailMenu().hover(NavSection.Plugins).hover(NavSection.Projects)

        assertTrue(menu.detached)
        assertEquals(NavSection.Projects, menu.floating)
    }

    @Test
    fun `the pointer leaving the segments spends the pick and leaves the panel its rows`() {
        val menu = RailMenu().hover(NavSection.Plugins).leave()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        // The panel is still fading, so the section it lists outlives the pick that put it up.
        assertEquals(NavSection.Plugins, menu.section(NavSection.Home))
    }

    @Test
    fun `a click pins the pick into the window's second column`() {
        val menu = RailMenu().hover(NavSection.Plugins).pin()

        assertTrue(menu.shown)
        assertFalse(menu.detached)
        assertTrue(menu.pinned)
        // The column lists the page the click opened, not the rows the pointer passed over.
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `a pinned column is not floated by the pointer`() {
        val menu = RailMenu(pinned = true).hover(NavSection.Plugins)

        assertTrue(menu.pinned)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `a long press floats the section it pressed and gives up the pin`() {
        val menu = RailMenu(pinned = true).press(NavSection.Projects)

        assertFalse(menu.pinned)
        assertTrue(menu.detached)
        assertEquals(NavSection.Projects, menu.section(NavSection.Home))
    }

    @Test
    fun `unpinning takes the column down and gives the page its width back`() {
        val menu = RailMenu(pinned = true).unpin()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `nothing is drawn before anything puts the menu up`() {
        val menu = RailMenu()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }
}
