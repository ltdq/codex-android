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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
 * The shell's second segment: one menu, floating over the page or pinned beside it. [settle] is how
 * far it has filled out (0 card, 1 column); the same amount comes back as the rows' padding, so the
 * card grows out around text that never moves, and a pinned menu keeps its scroll and open project.
 */
@Composable
internal fun NavMenuPanel(
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    settle: Float,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    listPadding: PaddingValues = PaddingValues(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
    itemGap: Dp = UiConsts.Space2,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val railGap = UiConsts.NavMenuFloatGap * (1f - settle)
    val edgeGap = UiConsts.NavMenuFloatMargin * (1f - settle)
    Box(
        modifier =
            modifier
                .width(width)
                .fillMaxHeight()
                .padding(start = railGap, top = edgeGap, end = edgeGap, bottom = edgeGap),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().consumePointerInput(),
            shape = RoundedCornerShape(UiConsts.PanelCorner * (1f - settle)),
            color = panelColor(),
            shadowElevation = UiConsts.PanelElevation,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            // Whatever the card gives up, the rows take back: their rectangle is the
                            // same one at both ends of the fill, so a pin moves the chrome, not the text.
                            start = UiConsts.NavMenuFloatGap - railGap,
                            top = UiConsts.NavMenuFloatMargin - edgeGap,
                            end = UiConsts.NavMenuFloatMargin - edgeGap,
                            bottom = UiConsts.NavMenuFloatMargin - edgeGap,
                        ),
            ) {
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
}

/**
 * Swallows every pointer event that lands on the card, so a tap on the card's own padding belongs
 * to the menu rather than to the page under it; the rows are children and take their taps first.
 */
private fun Modifier.consumePointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    }

