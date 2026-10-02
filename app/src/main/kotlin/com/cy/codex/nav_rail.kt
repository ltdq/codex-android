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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.chatwidget.ActionRow
import com.cy.codex.chatwidget.ProjectRow
import com.cy.codex.chatwidget.SessionRow
import com.cy.codex.chatwidget.SidebarEntry
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TopAppBar
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
 * The shell's second segment: the menu card, which the pointer floats over the page and a click
 * pins into the layout. It is the page card's own surface ([CodexShellCard]) at the page card's own
 * inset off the window and the page, and its left edge is the page card's — the rail's own — so the
 * two cards line up while the menu is the only one up. Floating, it carries the card's shadow, since
 * it hangs over the page; [settled] is how far it has come down onto the page, and the shadow is all
 * a pin takes off it, so the card never changes size or place. [slide] is how far it has been drawn
 * out of the rail: the rail's edge is where it comes from and where it goes back to, so a caller
 * that clips there is what hides it. [onCardTap] pins the card on a tap no row took.
 */
@Composable
internal fun NavMenuPanel(
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    onCardTap: () -> Unit,
    slide: Float,
    settled: Float,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    listPadding: PaddingValues = PaddingValues(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
    itemGap: Dp = UiConsts.Space2,
) {
    val fill = MiuixTheme.colorScheme.background
    val cardTap by rememberUpdatedState(onCardTap)
    Box(
        modifier =
            modifier
                .width(width)
                .fillMaxHeight()
                // A card that has not come out yet lies inside the rail's column, where the clip
                // leaves nothing of it, shadow included.
                .offset(x = -width * (1f - slide))
                .padding(top = UiConsts.ScreenInset, end = UiConsts.ScreenInset, bottom = UiConsts.ScreenInset),
    ) {
        CodexShellCard(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { cardTap() } },
            shadowElevation = UiConsts.PanelElevation * (1f - settled),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
    }
}

/**
 * The page card's left edge: the rail's own edge while the menu is shut, and the far side of the
 * menu's column once the menu card stands pinned beside it. The menu card is drawn out of the rail
 * as the page gives the column up, so the two keep one gap while they move.
 */
internal fun pageCardStart(railWidth: Dp, menuWidth: Dp, menuRoom: Float): Dp =
    railWidth + menuWidth * menuRoom

