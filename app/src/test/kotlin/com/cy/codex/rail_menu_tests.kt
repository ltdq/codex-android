package com.cy.codex

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a pointer and a click do to the shell's second segment: a click pins, a pointer floats.
 */
class RailMenuTest {

    @Test
    fun `a pointer on an item floats that section over the page`() {
        val menu = RailMenu().hover(NavSection.Customize)

        assertTrue(menu.shown)
        assertTrue(menu.detached)
        assertEquals(NavSection.Customize, menu.floating)
        assertEquals(NavSection.Customize, menu.section(NavSection.Home))
    }

    @Test
    fun `moving along the rail swaps the rows without taking the panel down`() {
        val menu = RailMenu().hover(NavSection.Customize).hover(NavSection.Projects)

        assertTrue(menu.detached)
        assertEquals(NavSection.Projects, menu.floating)
    }

    @Test
    fun `the pointer leaving the segments spends the pick and leaves the panel its rows`() {
        val menu = RailMenu().hover(NavSection.Customize).leave()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        // The panel is still fading, so the section it lists outlives the pick that put it up.
        assertEquals(NavSection.Customize, menu.section(NavSection.Home))
    }

    @Test
    fun `a click pins the pick into the window's second column`() {
        val menu = RailMenu().hover(NavSection.Customize).pin()

        assertTrue(menu.shown)
        assertFalse(menu.detached)
        assertTrue(menu.pinned)
        // The column lists the page the click opened, not the rows the pointer passed over.
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `a pinned column is not floated by the pointer`() {
        val menu = RailMenu(pinned = true).hover(NavSection.Customize)

        assertTrue(menu.pinned)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `a long press floats the section it pressed while nothing is pinned`() {
        val menu = RailMenu().press(NavSection.Projects)

        assertTrue(menu.detached)
        assertEquals(NavSection.Projects, menu.section(NavSection.Home))
    }

    @Test
    fun `a long press leaves the pinned column up instead of floating over it`() {
        val menu = RailMenu(pinned = true).press(NavSection.Projects)

        assertTrue(menu.pinned)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    @Test
    fun `unpinning takes the column down and leaves the page its own column`() {
        val menu = RailMenu(pinned = true).unpin()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    /** The shell keys the list's state on this section, so a card put away must list the same one. */
    @Test
    fun `a card put away and pinned again lists the section it was pinned on`() {
        val pinned = RailMenu(pinned = true)
        val shut = pinned.unpin()
        val again = shut.pin()

        assertEquals(pinned.section(NavSection.Home), shut.section(NavSection.Home))
        assertEquals(pinned.section(NavSection.Home), again.section(NavSection.Home))
    }

    @Test
    fun `the item the column was closed under floats nothing while the pointer stays on it`() {
        val menu = RailMenu(pinned = true).unpin().hover(NavSection.Home)

        assertFalse(menu.shown)
        assertFalse(menu.detached)
    }

    @Test
    fun `the pointer coming back to the rail floats again once it has been clear`() {
        val menu = RailMenu(pinned = true).unpin().leave().hover(NavSection.Customize)

        assertTrue(menu.detached)
        assertEquals(NavSection.Customize, menu.floating)
    }

    @Test
    fun `a long press floats over the hover a click spent`() {
        val menu = RailMenu(pinned = true).unpin().press(NavSection.Projects)

        assertTrue(menu.detached)
        assertEquals(NavSection.Projects, menu.section(NavSection.Home))
    }

    @Test
    fun `nothing is drawn before anything puts the menu up`() {
        val menu = RailMenu()

        assertFalse(menu.shown)
        assertFalse(menu.detached)
        assertEquals(NavSection.Home, menu.section(NavSection.Home))
    }

    /** Clicking the item of the section the page is in puts the menu up or down, nothing else. */
    @Test
    fun `a click on the open section's item only puts its menu up or down`() {
        val shut =
            RailMenu(pinned = true).click(current = NavSection.Customize, tapped = NavSection.Customize)
        assertFalse(shut.pinned)
        assertNull(shut.opens)

        val up =
            RailMenu(pinned = false).click(current = NavSection.Customize, tapped = NavSection.Customize)
        assertTrue(up.pinned)
        assertNull(up.opens)
    }

    @Test
    fun `a click on another rail item moves the page to that section`() {
        val click =
            RailMenu(pinned = false).click(current = NavSection.Customize, tapped = NavSection.Projects)

        assertTrue(click.pinned)
        assertEquals(NavSection.Projects, click.opens)
    }
}
