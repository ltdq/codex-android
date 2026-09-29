package com.cy.codex

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.squircle.squircleBackground
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
 * One section of [CodexNavigationRail]: the icon and its selection fill, and no label, so the rail
 * costs the page beside it no label column. The fill, the icon step and the tab role are the
 * library's collapsed rail item's.
 */
@Composable
fun CodexNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = UiConsts.NavRailItemSize,
    iconSize: Dp = UiConsts.IconLeading,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .size(size)
                .squircleBackground(
                    color = if (selected) colors.surfaceContainerHigh else Color.Transparent,
                    cornerRadius = UiConsts.CornerRow,
                )
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (selected) colors.primary else colors.onSurfaceSecondary,
        )
    }
}
