package com.cy.codexui

import androidx.compose.ui.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Pins the row precompute the diff body relies on.
 *
 * The renderer folds colours, gutters and signs into [DiffRowModel] once per body so a
 * recomposition only lays a row out. Equality is the other half of that contract: Compose can skip
 * a row only when two folds of unchanged lines compare equal.
 */
class DiffRowModelTest {

    private val palette = DiffPalette(
        addText = Color(0xFF00FF00),
        addSurface = Color(0x1100FF00),
        removeText = Color(0xFFFF0000),
        removeSurface = Color(0x11FF0000),
        hunkText = Color(0xFF0000FF),
        hunkSurface = Color(0x110000FF),
        gutter = Color(0x66000000),
        context = Color(0xFF222222),
    )

    private val hunkTextColor = Color(0xFF123456)

    private fun rowsFor(diff: String) = buildDiffRows(
        lines = parseUnifiedDiff(diff),
        palette = palette,
        hunkTextColor = hunkTextColor,
        signAdded = "+",
        signRemoved = "−",
    )

    @Test
    fun foldsGuttersSignsAndColorsPerKind() {
        val rows = rowsFor("@@ -1,2 +1,2 @@\n-old\n+new\n context")
        assertEquals(4, rows.size)

        assertEquals("", rows[0].oldLine)
        assertEquals("", rows[0].newLine)
        assertEquals("", rows[0].sign)
        assertEquals(palette.hunkSurface, rows[0].background)
        assertEquals(hunkTextColor, rows[0].textColor)

        assertEquals("1", rows[1].oldLine)
        assertEquals("", rows[1].newLine)
        assertEquals("−", rows[1].sign)
        assertEquals("old", rows[1].text)
        assertEquals(palette.removeSurface, rows[1].background)
        assertEquals(palette.removeText, rows[1].textColor)

        assertEquals("", rows[2].oldLine)
        assertEquals("1", rows[2].newLine)
        assertEquals("+", rows[2].sign)
        assertEquals("new", rows[2].text)
        assertEquals(palette.addSurface, rows[2].background)
        assertEquals(palette.addText, rows[2].textColor)

        assertEquals("2", rows[3].oldLine)
        assertEquals("2", rows[3].newLine)
        assertEquals("", rows[3].sign)
        assertEquals(Color.Transparent, rows[3].background)
        assertEquals(palette.context, rows[3].textColor)
    }

    @Test
    fun foldingTheSameLinesTwiceProducesEqualRows() {
        assertEquals(rowsFor("@@ -1 +1 @@\n-old\n+new"), rowsFor("@@ -1 +1 @@\n-old\n+new"))
    }
}
