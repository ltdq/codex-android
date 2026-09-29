package com.cy.codex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * The shell's first segment: the rail that never hides, and whose selected item toggles its menu.
 * Which sections it holds is the shell's decision; its looks are [CodexNavigationRail]'s.
 */
@Composable
internal fun NavRail(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = UiConsts.NavRailWidth,
    itemSize: Dp = UiConsts.NavRailItemSize,
    itemGap: Dp = UiConsts.Space8,
) {
    CodexNavigationRail(
        modifier = modifier,
        width = width,
        itemSize = itemSize,
        itemGap = itemGap,
    ) {
        NavSection.entries.filterNot { it == NavSection.Settings }.forEach { section ->
            CodexNavigationRailItem(
                selected = section == selected,
                onClick = { onSelect(section) },
                icon = section.icon,
                contentDescription = stringResource(section.titleRes),
                size = itemSize,
            )
        }
        // Settings configures what the sections above show, so it stands on the rail's floor.
        Spacer(Modifier.weight(1f))
        CodexNavigationRailItem(
            selected = selected == NavSection.Settings,
            onClick = { onSelect(NavSection.Settings) },
            icon = NavSection.Settings.icon,
            contentDescription = stringResource(NavSection.Settings.titleRes),
            size = itemSize,
        )
    }
}

/**
 * The shell's second segment: the menu behind the selected rail item, at a width fixed by the shell
 * so the page column is measured once. The title collapses as the list scrolls under it.
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
    listPadding: PaddingValues = PaddingValues(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
    itemGap: Dp = UiConsts.Space2,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
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
