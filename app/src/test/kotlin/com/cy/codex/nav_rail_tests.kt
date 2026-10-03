package com.cy.codex

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Where the page card stands while the rail's menu card is shut, pinned beside it, or unfolding out
 * of the rail: the menu keeps the card it floats as, so the page is the one that moves. The page's
 * own column belongs to the layout instead — the rail, plus the menu's column where the window has
 * room for one — so the card's motion never re-measures it.
 */
class NavRailTest {

    private val railWidth = UiConsts.NavRailWidth
    private val menuWidth = UiConsts.NavMenuWidth

    @Test
    fun `a shut menu leaves the page card at the rail's edge`() {
        assertEquals(railWidth, pageCardStart(railWidth, menuWidth, menuRoom = 0f))
    }

    @Test
    fun `a pinned menu card takes its column out of the page`() {
        assertEquals(railWidth + menuWidth, pageCardStart(railWidth, menuWidth, menuRoom = 1f))
    }

    @Test
    fun `the page card follows the menu card across`() {
        assertEquals(railWidth + 112.dp, pageCardStart(railWidth, menuWidth, menuRoom = 0.5f))
    }

    @Test
    fun `a pinned menu card leaves the page one screen inset away`() {
        val cardEnd = UiConsts.ScreenInset + (menuWidth - UiConsts.ScreenInset * 2)
        assertEquals(
            railWidth + cardEnd + UiConsts.ScreenInset,
            pageCardStart(railWidth, menuWidth, menuRoom = 1f),
        )
    }

    @Test
    fun `a menu that floats over the page takes no column`() {
        assertEquals(railWidth, pageColumnStart(railWidth, menuWidth, roomForMenu = false))
    }

    @Test
    fun `a menu with a column beside the page takes it out of the window`() {
        assertEquals(railWidth + menuWidth, pageColumnStart(railWidth, menuWidth, roomForMenu = true))
    }

    /** The card rides the menu; the page does not, so putting the card away reflows nothing. */
    @Test
    fun `putting the menu card away moves the card and not the page column`() {
        val column = pageColumnStart(railWidth, menuWidth, roomForMenu = true)

        assertEquals(pageCardStart(railWidth, menuWidth, menuRoom = 1f), column)
        assertNotEquals(pageCardStart(railWidth, menuWidth, menuRoom = 0f), column)
    }

    /** The 776dp phone the wide breakpoint puts on the three-column layout (docs/TODO.md). */
    @Test
    fun `a page keeps the width the layout gives it with the menu card put away`() {
        val window = 776.dp
        val column = pageColumnStart(railWidth, menuWidth, roomForMenu = true)

        assertEquals(460.dp, pageColumnWidth(window, column))
    }

    /** The card's leftover splits evenly: the page is as far from one edge as from the other. */
    @Test
    fun `a page is centred in the card, menu out or put away`() {
        val window = 776.dp
        val pageWidth = pageColumnWidth(window, pageColumnStart(railWidth, menuWidth, roomForMenu = true))

        listOf(0f, 1f).forEach { room ->
            val cardStart = pageCardStart(railWidth, menuWidth, menuRoom = room)
            val left = pageColumnOffset(window, cardStart, pageWidth)
            val right = window - cardStart - UiConsts.ScreenInset - pageWidth - left

            assertEquals(left, right, "menuRoom=$room")
        }

        val shut = pageCardStart(railWidth, menuWidth, menuRoom = 0f)
        assertEquals(123.dp, pageColumnOffset(window, shut, pageWidth))
    }
}
