package com.cy.codex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cy.codex.theme.RoundedIndication
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.squircle.SquircleDefaults
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.squircle.isSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The app's own miuix components, built from the library's own surfaces, shapes, colours and
 * semantics so they read as miuix rather than as Compose defaults; the other half of [style.kt],
 * which ports `codex-rs/tui/src/style.rs`.
 */

/**
 * A surface's silhouette in miuix's corner geometry: the continuous corner a circular arc only
 * approximates, drawn as a [squirclePath]. A [Shape] rather than the library's `squircleClip`, which
 * records a surface of the window's own size into a layer per frame.
 */
@Composable
fun squircleShape(radius: Dp): Shape {
    val squircle = isSquircleEnabled()
    return remember(radius, squircle) { SquircleShape(radius, squircle) }
}

/** [addSquircleRect] as a bare path, so a silhouette drawn by hand is the one a clip was cut from. */
private fun squirclePath(
    size: Size,
    radius: Dp,
    density: Density,
    squircle: Boolean,
): Path = Path().apply {
    with(density) {
        addSquircleRect(
            width = size.width,
            height = size.height,
            cornerRadius = radius.toPx(),
            squircleEnabled = squircle,
        )
    }
}

/** [squirclePath] behind a [Shape], one radius for all four corners. */
private class SquircleShape(private val radius: Dp, private val squircle: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(squirclePath(size, radius, density, squircle))
}

/**
 * The shell's screen: the menu and the page stack, floating on the rail's own colour ([railColor],
 * washed by [CodexShellBackdrop]), inset by the rail's width and the window's other three edges and
 * rounded at the panel's corner. It clips what it holds, since a page paints its own background edge
 * to edge.
 */
@Composable
fun CodexShellScreen(
    railWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val squircle = isSquircleEnabled()
    val line = UiConsts.OutlineThickness
    val shape = squircleShape(UiConsts.PanelCorner)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    start = railWidth,
                    top = UiConsts.ScreenInset,
                    end = UiConsts.ScreenInset,
                    bottom = UiConsts.ScreenInset,
                )
                .clip(shape)
                .background(colors.background)
                .drawWithCache {
                    // A hair inside the silhouette, or a rectangle around it squares the corners off.
                    val frame = squirclePath(
                        size = Size(size.width - line.toPx(), size.height - line.toPx()),
                        radius = UiConsts.PanelCorner - line / 2,
                        density = this,
                        squircle = squircle,
                    ).apply { translate(Offset(line.toPx() / 2f, line.toPx() / 2f)) }
                    onDrawWithContent {
                        drawContent()
                        drawPath(frame, color = colors.outline, style = Stroke(line.toPx()))
                    }
                },
        content = content,
    )
}

/** The background's own colours, as the hue they stand on and the swing they turn through. */
private class Os3Hues(
    val hue: Float,
    val swing: Float,
    val saturation: Float,
    val value: Float,
)

/** The demo's own background in a dark scheme: a deep blue that turns violet and back. */
private val Os3Dark = Os3Hues(hue = 250f, swing = 35f, saturation = 0.72f, value = 0.32f)

/** And in a light one, the pastel the same turn takes: a pink through a lavender. */
private val Os3Light = Os3Hues(hue = 292f, swing = 48f, saturation = 0.14f, value = 0.97f)

/** How far that background's gradient slides, as a fraction of the window. */
private const val Os3Flow = 0.06f

/**
 * How much of the window the flow's light is wide, as a fraction of its height, and how bright: a
 * band rather than a glow, since an edge travelling down the rail is what the eye can see move.
 */
private const val Os3Band = 0.16f
private const val Os3BandAlpha = 0.45f

/** [Color.hsv] with the hue wrapped, so a swing that crosses the wheel's seam is still a colour. */
private fun os3Color(hue: Float, saturation: Float, value: Float): Color =
    Color.hsv(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

/** A full turn of the background's swing. */
private val TwoPi = (2.0 * PI).toFloat()

/**
 * The rail's colour, lit: the window's base layer, painted under everything the shell draws, as the
 * OS3 background the miuix demo's blur page turns through, with one band of light running down it.
 * Only the four bands the screen leaves are painted, since the cycle repaints it every frame.
 */
@Composable
fun CodexShellBackdrop(
    railWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val base = railColor()
    val hues = if (darkScheme()) Os3Dark else Os3Light
    // Read by the draw and not by the composition, so a turning base redraws instead of recomposing.
    val drift =
        if (Motion.reduced) {
            null
        } else {
            rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = Motion.DriftMs, easing = LinearEasing)),
            )
        }
    Box(
        modifier =
            modifier
                .drawWithCache {
                    val inset = UiConsts.ScreenInset.toPx()
                    // How far the screen's silhouette reaches out of its corner, which a band must
                    // cover.
                    val tile = UiConsts.PanelCorner.toPx() * SquircleDefaults.Extension
                    val rail = railWidth.toPx()
                    val top = inset + tile
                    val bottom = size.height - top
                    // Disjoint: a pixel two bands reach would take the wash twice, and the seam would
                    // be where they cross.
                    val bands =
                        listOf(
                            Rect(0f, 0f, size.width, top),
                            Rect(0f, bottom, size.width, size.height),
                            Rect(0f, top, rail, bottom),
                            Rect(size.width - inset, top, size.width, bottom),
                        )
                    onDrawBehind {
                        val turn = drift?.value ?: 0f
                        val phase = turn * TwoPi
                        val hue = hues.hue + hues.swing * sin(phase)
                        // The hue's own neighbours, which the demo's background runs through from
                        // one end of the window to the other.
                        val background =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        os3Color(hue - 22f, hues.saturation * 1.1f, hues.value * 0.9f),
                                        os3Color(hue + 6f, hues.saturation * 0.9f, hues.value * 1.15f),
                                        os3Color(hue + 38f, hues.saturation * 1.05f, hues.value * 0.8f),
                                    ),
                                start = Offset(size.width * (0.08f + Os3Flow * cos(phase)), 0f),
                                end = Offset(size.width * (0.92f - Os3Flow * cos(phase)), size.height),
                            )
                        val light = os3Color(hue + 6f, hues.saturation * 0.7f, hues.value * 1.2f)
                        // The light enters and leaves the window, so the cycle's one seam falls off
                        // screen.
                        val sweep = size.height * (1.5f * turn - 0.25f)
                        val flow =
                            Brush.linearGradient(
                                // Fading to its own nothing rather than through transparent black,
                                // which the shader darkens on the way out.
                                colors =
                                    listOf(
                                        light.copy(alpha = 0f),
                                        light.copy(alpha = Os3BandAlpha),
                                        light.copy(alpha = 0f),
                                    ),
                                start = Offset(0f, sweep - size.height * Os3Band),
                                end = Offset(0f, sweep + size.height * Os3Band),
                            )
                        bands.forEach { band -> drawRect(color = base, topLeft = band.topLeft, size = band.size) }
                        bands.forEach { band -> drawRect(brush = background, topLeft = band.topLeft, size = band.size) }
                        bands.forEach { band -> drawRect(brush = flow, topLeft = band.topLeft, size = band.size) }
                    }
                },
    )
}

/**
 * The shell's navigation rail: the window's edge column the sections are switched from. Unlike the
 * library's labelled `NavigationRail` it is icon-only, pinned to the window's edges and never
 * scrolls, and it draws no surface or divider of its own, being the window's base layer.
 */
@Composable
fun CodexNavigationRail(
    modifier: Modifier = Modifier,
    width: Dp = UiConsts.NavRailWidth,
    itemSize: Dp = UiConsts.NavRailItemSize,
    itemGap: Dp = UiConsts.Space8,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The item's own inset is also the column's first and last gap, so an icon's centre sits as far
    // from the top and bottom edges as it does from the side.
    val edgeGap = ((width - itemSize) / 2).coerceAtLeast(0.dp)
    Column(
        modifier = modifier.width(width).fillMaxHeight().selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(edgeGap))
        // Items only: the gaps to the window's edges are the item's inset, not list spacing.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemGap),
            content = content,
        )
        Spacer(Modifier.height(edgeGap))
    }
}

/**
 * One section of [CodexNavigationRail]: the icon and its press highlight, and no label, so the rail
 * costs the page beside it no label column. A press and a long press are one gesture detector, so a
 * long press never also selects the item.
 */
@Composable
fun CodexNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = UiConsts.NavRailItemSize,
    iconSize: Dp = UiConsts.NavRailIconSize,
    longClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onHoverChanged: (Boolean) -> Unit = {},
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    // One source for hover and press, so the highlight and the shell's own state follow one pointer.
    val hovered by interactionSource.collectIsHoveredAsState()
    val reportHover by rememberUpdatedState(onHoverChanged)
    LaunchedEffect(hovered) { reportHover(hovered) }
    val isSelected = selected
    // Press and hover draw on the item's whole square, in the rail's own rounded rectangle: the
    // library's default indication insets its circle, which reads as a dot on an icon-only rail.
    val indication =
        remember(colors.onBackground) {
            RoundedIndication(color = colors.onBackground, radius = UiConsts.CornerRow, inset = 0.dp)
        }
    Box(
        modifier =
            modifier
                .size(size)
                .hoverable(interactionSource)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = longClickLabel,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = indication,
                )
                // The tab role comes from the gesture detector; the selection state does not.
                .semantics { this.selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (isSelected) colors.primary else colors.onSurfaceSecondary,
        )
    }
}
