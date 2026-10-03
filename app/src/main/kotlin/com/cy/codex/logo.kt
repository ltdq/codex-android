package com.cy.codex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The app's mark: a filled tile carrying the terminal prompt the app runs on, drawn rather than
 * imported so it follows the scheme's accent and stays sharp at any size. It heads the rail, the
 * column's one solid tile, so the shell has an anchor above its icon-only items.
 *
 * Decorative: the mark names the app, which every screen the user is on already says.
 */
@Composable
fun CodexRailLogo(modifier: Modifier = Modifier, size: Dp = UiConsts.NavRailLogoSize) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier =
            modifier
                .size(size)
                .background(color = colors.primary, shape = squircleShape(UiConsts.CornerChip)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glyph = railLogoGlyph(side = this.size.minDimension)
            drawPath(
                path =
                    Path().apply {
                        moveTo(glyph.chevron[0].x, glyph.chevron[0].y)
                        lineTo(glyph.chevron[1].x, glyph.chevron[1].y)
                        lineTo(glyph.chevron[2].x, glyph.chevron[2].y)
                    },
                color = colors.onPrimary,
                style =
                    Stroke(
                        width = glyph.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawLine(
                color = colors.onPrimary,
                start = glyph.cursor[0],
                end = glyph.cursor[1],
                strokeWidth = glyph.strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** [railLogoGlyph]'s prompt: the chevron's points, the cursor's two, and the stroke they share. */
internal class LogoGlyph(
    val chevron: List<Offset>,
    val cursor: List<Offset>,
    val strokeWidth: Float,
)

/**
 * The prompt in a square of [side] px: a chevron and the cursor bar on its baseline, as fractions of
 * the square, so the mark is the same at every size the rail asks for. The fractions leave the tile
 * a margin all round, which is what a glyph on a solid tile needs to stay a mark rather than a fill.
 */
internal fun railLogoGlyph(side: Float): LogoGlyph =
    LogoGlyph(
        chevron = listOf(Offset(0.28f, 0.32f), Offset(0.45f, 0.50f), Offset(0.28f, 0.68f)).scaled(side),
        cursor = listOf(Offset(0.55f, 0.68f), Offset(0.74f, 0.68f)).scaled(side),
        strokeWidth = side * PromptStroke,
    )

private fun List<Offset>.scaled(side: Float): List<Offset> = map { it * side }

/** Stroke of the prompt, as a fraction of the tile: chunky enough to carry a tile at the rail's size. */
private const val PromptStroke = 0.112f
