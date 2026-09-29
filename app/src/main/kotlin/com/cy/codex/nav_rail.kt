package com.cy.codex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.chatwidget.ActionRow
import com.cy.codex.chatwidget.ProjectRow
import com.cy.codex.chatwidget.SessionRow
import com.cy.codex.chatwidget.SidebarEntry
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.TooltipState
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * The shell's first segment: the rail that never hides, and whose selected item toggles its menu.
 * Which sections it holds is the shell's decision; its looks are [CodexNavigationRail]'s.
 * [onHover] and [onLongPress] only say which item the pointer is on; [RailMenu] decides the rest.
 */
@Composable
internal fun NavRail(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    onHover: (NavSection, Boolean) -> Unit,
    onLongPress: (NavSection) -> Unit,
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
    longClickLabel: String,
    itemSize: Dp,
) {
    CodexNavigationRailItem(
        selected = selected,
        onClick = { onSelect(section) },
        icon = section.icon,
        contentDescription = stringResource(section.titleRes),
        size = itemSize,
        longClickLabel = longClickLabel,
        onLongClick = { onLongPress(section) },
        onHoverChanged = { hovered -> onHover(section, hovered) },
    )
}

/**
 * The shell's second segment: the menu behind the selected rail item, at a width fixed by the shell
 * so the page column is measured once. The title collapses as the list scrolls under it, and the
 * same panel is drawn in the layout while pinned and floating through [NavMenuFlyout] as a card.
 */
@Composable
internal fun NavMenuPanel(
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    shape: Shape = RectangleShape,
    listPadding: PaddingValues = PaddingValues(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
    itemGap: Dp = UiConsts.Space2,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        shape = shape,
        color = panelColor(),
        shadowElevation = UiConsts.PanelElevation,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topInset + UiConsts.ScreenMargin))
            TopAppBar(
                title = title,
                largeTitle = title,
                color = panelColor(),
                defaultWindowInsetsPadding = false,
                scrollBehavior = scrollBehavior,
            )
            LazyColumn(
                state = rememberLazyListState(),
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
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

/**
 * The card the menu becomes while it floats: the app's overlay corner, so a menu put up by the
 * pointer reads as an overlay of the page rather than as the shell's second column.
 */
private val NavMenuCardShape = RoundedCornerShape(UiConsts.PanelCorner)

/**
 * Room the floating card keeps inside its popup: the gap from the rail on the left, and the stage
 * its shadow is drawn on everywhere else, since a popup window is exactly as big as what it holds.
 */
private val NavMenuCardInset = PaddingValues(
    start = UiConsts.Space8 + UiConsts.Space16,
    top = UiConsts.Space16,
    end = UiConsts.Space16,
    bottom = UiConsts.Space16,
)

/**
 * The rail with the menu's floating panel hanging off it. The panel is a miuix tooltip, deliberately
 * not focusable: a focusable popup would consume the outside tap that usually belongs to the rail
 * item or the page behind it, and the shell drives the tooltip's state itself.
 */
@Composable
internal fun NavMenuFlyout(
    state: TooltipState,
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        // The popup starts on the rail's own edge, so the pointer travelling from an item into the
        // menu crosses nothing that belongs to neither; the gap the eye reads is the card's inset.
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Right,
                spacingBetweenTooltipAndAnchor = 0.dp,
            ),
        tooltip = {
            // A panel as tall as the rail holds the popup to the window's top: miuix centres a
            // tooltip on its anchor and the window clamps it.
            Box(modifier = Modifier.height(height).padding(NavMenuCardInset)) {
                NavMenuPanel(
                    title = title,
                    rows = rows,
                    onRow = onRow,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    width = width,
                    shape = NavMenuCardShape,
                )
            }
        },
        state = state,
        modifier = modifier,
        focusable = false,
        enableUserInput = false,
        content = content,
    )
}
