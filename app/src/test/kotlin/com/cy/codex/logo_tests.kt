package com.cy.codex

import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rail's mark: the prompt's own geometry, which has to stay inside the tile it is cut from at
 * every size the rail asks for, and the head it stands in. The tile's fill and its corner are the
 * shell's, and are not what this holds.
 */
class LogoTest {

    /** How much of the tile the prompt keeps clear of its edge, caps included. */
    private val margin = 0.15f

    /** An item's own inset off the rail's side edge, which the head is measured against. */
    private val itemInset = (UiConsts.NavRailWidth - UiConsts.NavRailItemSize) / 2

    private class Extent(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** The prompt's outer edge: round caps and joins reach half a stroke past every point. */
    private fun extent(side: Float): Extent {
        val glyph = railLogoGlyph(side)
        val half = glyph.strokeWidth / 2f
        val points = glyph.chevron + glyph.cursor
        return Extent(
            left = points.minOf { it.x } - half,
            top = points.minOf { it.y } - half,
            right = points.maxOf { it.x } + half,
            bottom = points.maxOf { it.y } + half,
        )
    }

    @Test
    fun `the prompt keeps clear of the tile's edge at every size`() {
        listOf(1f, UiConsts.NavRailLogoSize.value, 64f).forEach { side ->
            val prompt = extent(side)

            assertTrue(prompt.left >= margin * side, "left ${prompt.left} of $side")
            assertTrue(prompt.top >= margin * side, "top ${prompt.top} of $side")
            assertTrue(side - prompt.right >= margin * side, "right ${prompt.right} of $side")
            assertTrue(side - prompt.bottom >= margin * side, "bottom ${prompt.bottom} of $side")
        }
    }

    @Test
    fun `the cursor sits on the chevron's baseline`() {
        val glyph = railLogoGlyph(side = 32f)

        assertEquals(glyph.chevron.last().y, glyph.cursor.first().y)
        assertEquals(glyph.cursor.first().y, glyph.cursor.last().y)
    }

    @Test
    fun `the prompt is centred in its tile`() {
        val side = 32f
        val prompt = extent(side)

        assertTrue(abs((prompt.left + prompt.right) / 2f - side / 2f) <= 0.02f * side)
    }

    @Test
    fun `the mark is the same at every size`() {
        val small = railLogoGlyph(side = 32f)
        val large = railLogoGlyph(side = 64f)

        assertEquals(small.chevron.map { it * 2f }, large.chevron)
        assertEquals(small.cursor.map { it * 2f }, large.cursor)
        assertEquals(small.strokeWidth * 2f, large.strokeWidth)
    }

    /** The head is a block of its own, so the mark is never an item's own gap from the window's edge. */
    @Test
    fun `the mark's head keeps the shell's margin off the window's top edge`() {
        assertEquals(UiConsts.ScreenMargin, railHeadTop(topInset = 0.dp))
        assertEquals(UiConsts.ScreenMargin + 24.dp, railHeadTop(topInset = 24.dp))
        assertTrue(railHeadTop(topInset = 0.dp) > itemInset)
    }

    @Test
    fun `the head block is taller than the mark it holds`() {
        assertTrue(UiConsts.NavRailHeadHeight > UiConsts.NavRailLogoSize)
    }
}
