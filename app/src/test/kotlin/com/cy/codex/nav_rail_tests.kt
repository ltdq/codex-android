package com.cy.codex

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Where the page card stands while the rail's menu card is shut, pinned beside it, or unfolding out
 * of the rail: the menu keeps the card it floats as, so the page is the one that moves.
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
}
