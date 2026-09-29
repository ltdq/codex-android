package com.cy.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.theme.RoundedIndication
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The app's own miuix components, built from the library's own surfaces, shapes, colours and
 * semantics so they read as miuix rather than as Compose defaults; the other half of [style.kt],
 * which ports `codex-rs/tui/src/style.rs`.
 */

/**
 * The shell's navigation rail: the window's edge column the sections are switched from. Unlike the
 * library's labelled `NavigationRail` it is icon-only, pinned to the window's edges and never
 * scrolls, but it keeps the library's surface, selectable item group and divider.
 */
@Composable
fun CodexNavigationRail(
    modifier: Modifier = Modifier,
    width: Dp = UiConsts.NavRailWidth,
    itemSize: Dp = UiConsts.NavRailItemSize,
    itemGap: Dp = UiConsts.Space8,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The item's own inset is also the column's first and last gap, so an icon's centre sits as far
    // from the top and bottom edges as it does from the side.
    val edgeGap = ((width - itemSize) / 2).coerceAtLeast(0.dp)
    Row(modifier = modifier.fillMaxHeight().background(MiuixTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.width(width).fillMaxHeight().selectableGroup(),
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
        if (showDivider) VerticalDivider()
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
