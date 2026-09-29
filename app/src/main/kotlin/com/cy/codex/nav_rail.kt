package com.cy.codex

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.chatwidget.ActionRow
import com.cy.codex.chatwidget.ProjectRow
import com.cy.codex.chatwidget.SectionHeader
import com.cy.codex.chatwidget.SessionRow
import com.cy.codex.chatwidget.SidebarEntry
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The shell's first segment: the rail that never hides, and whose selected item toggles its menu.
 */
@Composable
internal fun NavRail(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavRailWidth,
    itemSize: Dp = UiConsts.NavRailItemSize,
    itemGap: Dp = UiConsts.Space8,
) {
    val colors = MiuixTheme.colorScheme
    // The edge gap is the item's own inset in the rail, so an icon's centre sits as far from the top
    // and bottom edges as it does from the side: one step, not two.
    val edgeGap = ((width - itemSize) / 2).coerceAtLeast(0.dp)
    Surface(modifier = modifier.width(width).fillMaxHeight(), color = colors.surface) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemGap),
        ) {
            Spacer(Modifier.height(topInset + edgeGap))
            NavSection.entries.filterNot { it == NavSection.Settings }.forEach { section ->
                RailItem(
                    section = section,
                    selected = section == selected,
                    onClick = { onSelect(section) },
                    size = itemSize,
                )
            }
            Spacer(Modifier.weight(1f))
            RailItem(
                section = NavSection.Settings,
                selected = selected == NavSection.Settings,
                onClick = { onSelect(NavSection.Settings) },
                size = itemSize,
            )
            Spacer(Modifier.height(bottomInset.coerceAtLeast(edgeGap)))
        }
    }
}

/** One rail item: the icon and nothing else, so the rail costs the page no label column. */
@Composable
private fun RailItem(section: NavSection, selected: Boolean, onClick: () -> Unit, size: Dp) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier =
            Modifier.size(size)
                .then(
                    if (selected) {
                        Modifier.squircleBackground(
                            color = colors.surfaceContainerHigh,
                            cornerRadius = UiConsts.ChipCorner,
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = section.icon,
            // The label is gone from the rail, so the icon is what names the section.
            contentDescription = stringResource(section.titleRes),
            modifier = Modifier.size(UiConsts.IconLeading),
            tint = if (selected) colors.primary else colors.onSurfaceSecondary,
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
                        is NavMenuRow.Header ->
                            SectionHeader(
                                title = row.title,
                                collapsed = false,
                                collapsible = false,
                                onClick = {},
                            )

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
