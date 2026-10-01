package com.cy.codex

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.chatwidget.ActionRow
import com.cy.codex.chatwidget.ProjectRow
import com.cy.codex.chatwidget.SessionRow
import com.cy.codex.chatwidget.SidebarEntry
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The shell's first segment: the rail that never hides, and whose selected item toggles its menu.
 * Which sections it holds is the shell's decision; its looks are [CodexNavigationRail]'s. With no
 * menu to float, [showTooltips] names the item the pointer is on instead.
 */
@Composable
internal fun NavRail(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    onHover: (NavSection, Boolean) -> Unit,
    onLongPress: (NavSection) -> Unit,
    showTooltips: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = UiConsts.NavRailWidth,
    itemSize: Dp = UiConsts.NavRailItemSize,
    itemGap: Dp = UiConsts.Space8,
) {
    val showMenuLabel = stringResource(R.string.nav_rail_show_menu)
    CodexNavigationRail(
        modifier = modifier,
        width = width,
        itemSize = itemSize,
        itemGap = itemGap,
    ) {
        NavSection.entries.filterNot { it == NavSection.Settings }.forEach { section ->
            NavRailItem(
                section = section,
                selected = section == selected,
                onSelect = onSelect,
                onHover = onHover,
                onLongPress = onLongPress,
                showTooltips = showTooltips,
                longClickLabel = showMenuLabel,
                itemSize = itemSize,
            )
        }
        // Settings configures what the sections above show, so it stands on the rail's floor.
        Spacer(Modifier.weight(1f))
        NavRailItem(
            section = NavSection.Settings,
            selected = selected == NavSection.Settings,
            onSelect = onSelect,
            onHover = onHover,
            onLongPress = onLongPress,
            showTooltips = showTooltips,
            longClickLabel = showMenuLabel,
            itemSize = itemSize,
        )
    }
}

@Composable
private fun NavRailItem(
    section: NavSection,
    selected: Boolean,
    onSelect: (NavSection) -> Unit,
    onHover: (NavSection, Boolean) -> Unit,
    onLongPress: (NavSection) -> Unit,
    showTooltips: Boolean,
    longClickLabel: String,
    itemSize: Dp,
) {
    val title = stringResource(section.titleRes)
    // Where the menu is pinned, the rail cannot float it, so the pointer resting on an item and a
    // finger holding one name the item instead; the tooltip brings both gestures with it.
    TooltipBox(
        text = title,
        enabled = showTooltips,
        positioning = TooltipAnchorPosition.End,
    ) {
        CodexNavigationRailItem(
            selected = selected,
            onClick = { onSelect(section) },
            icon = section.icon,
            contentDescription = title,
            size = itemSize,
            longClickLabel = longClickLabel,
            // The tooltip presses the same gesture when it is the one that answers.
            onLongClick = if (showTooltips) null else { { onLongPress(section) } },
            onHoverChanged = { hovered -> onHover(section, hovered) },
        )
    }
}

/**
 * The shell's second segment: one menu, floating over the page or pinned as the screen's first
 * segment. [settle] is how far it has joined the screen and [reveal] how much width has unfolded,
 * the rows keeping the width they end up with. [onCardTap] pins the menu on a tap no row took.
 */
@Composable
internal fun NavMenuPanel(
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    onCardTap: () -> Unit,
    settle: Float,
    reveal: Float,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    listPadding: PaddingValues = PaddingValues(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
    itemGap: Dp = UiConsts.Space2,
) {
    val colors = MiuixTheme.colorScheme
    val railGap = UiConsts.NavMenuFloatGap * (1f - settle)
    val edgeGap = UiConsts.NavMenuFloatMargin * (1f - settle)
    // A pinned menu is the screen's first segment, so it takes the screen's fill; a floating card
    // keeps the panel's own.
    val fill = lerp(panelColor(), colors.background, settle)
    val cardTap by rememberUpdatedState(onCardTap)
    Box(
        modifier =
            modifier
                .width(width * reveal)
                .fillMaxHeight()
                .padding(start = railGap, top = edgeGap, end = edgeGap, bottom = edgeGap),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { cardTap() } },
            shape = squircleShape(navMenuCorner(settle)),
            color = fill,
            shadowElevation = UiConsts.PanelElevation * (1f - settle),
        ) {
            Column(
                modifier =
                    Modifier
                        // The card's own rectangle, whatever it has unfolded to, so the rows keep
                        // the width they end up with and the unfold reveals them instead of
                        // reflowing them.
                        .requiredWidth(width - railGap - edgeGap)
                        .fillMaxHeight()
                        .padding(
                            // Whatever the card gives up, the rows take back: a pin moves the
                            // chrome, not the text.
                            start = UiConsts.NavMenuFloatGap - railGap,
                            top = UiConsts.NavMenuFloatMargin - edgeGap,
                            end = UiConsts.NavMenuFloatMargin - edgeGap,
                            bottom = UiConsts.NavMenuFloatMargin - edgeGap,
                        ),
            ) {
                Spacer(Modifier.height(topInset + UiConsts.ScreenMargin))
                // Only the large title: the bar is the panel's header, and the list scrolls beside
                // it rather than under it, so there is no collapsed step for a second title to name.
                TopAppBar(
                    title = "",
                    largeTitle = title,
                    color = fill,
                    defaultWindowInsetsPadding = false,
                )
                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(itemGap),
                ) {
                    items(rows.size, key = { rows[it].key }) { index ->
                        when (val row = rows[index]) {
                            is NavMenuRow.Header -> SmallTitle(text = row.title)

                            is NavMenuRow.Entry ->
                                ActionRow(
                                    entry = SidebarEntry(row.id, row.title, row.icon),
                                    selected = row.selected,
                                    onClick = { onRow(row) },
                                )

                            is NavMenuRow.Project ->
                                ProjectRow(
                                    project = row.project,
                                    expanded = row.expanded,
                                    onClick = { onRow(row) },
                                )

                            is NavMenuRow.Session ->
                                SessionRow(
                                    session = row.session,
                                    selected = row.selected,
                                    onClick = { onRow(row) },
                                )
                        }
                    }
                }
                Spacer(Modifier.height(bottomInset + UiConsts.ScreenMargin))
            }
        }
        // The menu's right edge is the boundary the two segments share: one hairline there, since
        // both stand on the screen's fill. A floating card has an edge of its own instead.
        VerticalDivider(
            modifier = Modifier.align(Alignment.TopEnd).offset(x = -edgeGap),
            thickness = UiConsts.DividerThickness,
            color = colors.dividerLine.copy(alpha = settle * reveal),
        )
    }
}

/**
 * The menu's own corners: the panel's card while a pointer floats it, and none once it is pinned,
 * where the screen's clip rounds its left corners into the panel's.
 */
internal fun navMenuCorner(settle: Float): Dp = UiConsts.PanelCorner * (1f - settle)

