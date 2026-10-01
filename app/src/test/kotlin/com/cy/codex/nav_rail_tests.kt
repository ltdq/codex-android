package com.cy.codex

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals

/**
 * What shape the rail's menu is drawn with as it joins the screen the pages stand on: a floating
 * menu is the panel's card, a pinned one leaves its left corners to the screen's clip.
 */
class NavRailTest {

    @Test
    fun `a menu the pointer floats is the panel's card`() {
        assertEquals(30.dp, navMenuCorner(settle = 0f))
    }

    @Test
    fun `a pinned menu leaves its corners to the screen's clip`() {
        assertEquals(0.dp, navMenuCorner(settle = 1f))
    }

    @Test
    fun `the corners come off as the menu settles`() {
        assertEquals(15.dp, navMenuCorner(settle = 0.5f))
    }
}
