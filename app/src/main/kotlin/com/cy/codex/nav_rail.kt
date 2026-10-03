package com.cy.codex

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The shell's first segment: the rail that never hides, and whose selected item toggles its menu.
 * Which sections it holds is the shell's decision; its looks are [CodexNavigationRail]'s. With no
 * menu to float, [showTooltips] names the item the pointer is on instead. The app's mark heads the
 * column above the sections, in a block of its own rather than in the items' own spacing.
 */
@Composable
internal fun NavRail(
    selected: NavSection,
    onSelect: (NavSection) -> Unit,
    onHover: (NavSection, Boolean) -> Unit,
    onLongPress: (NavSection) -> Unit,
    showTooltips: Boolean,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
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
        header = { CodexRailLogo() },
        topInset = topInset,
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
 * that clips there is what hides it. [onCardTap] pins the card on a tap no row took. [listState] is
 * the caller's: the card leaves the composition when it is put away, and the reader's place in the
 * rows has to outlive it. [query] narrows the rows through [filterMenuRows]; the field that sets it
 * stays on the card, above the list.
 */
@Composable
internal fun NavMenuPanel(
    title: String,
    rows: List<NavMenuRow>,
    onRow: (NavMenuRow) -> Unit,
    onCardTap: () -> Unit,
    slide: Float,
    settled: Float,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    width: Dp = UiConsts.NavMenuWidth,
    itemGap: Dp = UiConsts.Space2,
) {
    val fill = MiuixTheme.colorScheme.background
    val cardTap by rememberUpdatedState(onCardTap)
    val shown = if (query.isBlank()) rows else filterMenuRows(rows, query)
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
                // The card's own head: the section's name on the rail the option rows' content
                // stands on, so the title, the search field and the labels share one left edge.
                Text(
                    text = title,
                    modifier =
                        Modifier.padding(
                            start = UiConsts.MenuRowInset + UiConsts.MenuRowPadding,
                            end = UiConsts.MenuRowInset + UiConsts.MenuRowPadding,
                            top = UiConsts.Space2,
                            bottom = UiConsts.Space8,
                        ),
                    fontSize = UiType.Title,
                    lineHeight = UiType.TitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CodexSearchField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.nav_menu_search),
                    modifier =
                        Modifier.padding(
                            start = UiConsts.MenuRowInset,
                            end = UiConsts.MenuRowInset,
                            bottom = UiConsts.Space4,
                        ),
                )
                LazyColumn(
                    state = listState,
                    // The list takes the whole card: its own gutter at the top, and the window's
                    // bottom inset inside the scroll range, so the last row is reachable rather
                    // than sitting under a gap the card reserved for it.
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding =
                        PaddingValues(
                            start = UiConsts.MenuRowInset,
                            end = UiConsts.MenuRowInset,
                            top = UiConsts.Space4,
                            bottom = UiConsts.MenuRowInset + bottomInset,
                        ),
                    verticalArrangement = Arrangement.spacedBy(itemGap),
                ) {
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text =
                                    stringResource(
                                        if (query.isBlank()) {
                                            R.string.nav_menu_empty
                                        } else {
                                            R.string.nav_menu_no_matches
                                        }
                                    ),
                                modifier =
                                    Modifier.padding(
                                        start = UiConsts.MenuRowInset + UiConsts.MenuRowPadding,
                                        top = UiConsts.MenuGroupTop,
                                    ),
                                fontSize = UiType.Body,
                                lineHeight = UiType.BodyLine,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    items(shown.size, key = { shown[it].key }) { index ->
                        when (val row = shown[index]) {
                            is NavMenuRow.Header ->
                                CodexMenuGroupTitle(
                                    text = row.title,
                                    trailing =
                                        row.action?.let { id ->
                                            {
                                                MenuGroupAction(
                                                    id = id,
                                                    onClick = { onRow(row) },
                                                )
                                            }
                                        },
                                )

                            is NavMenuRow.Entry ->
                                CodexMenuRow(
                                    title = row.title,
                                    icon = row.icon,
                                    selected = row.selected,
                                    onClick = { onRow(row) },
                                )

                            is NavMenuRow.Project ->
                                CodexMenuProjectRow(
                                    project = row.project,
                                    expanded = row.expanded,
                                    onClick = { onRow(row) },
                                )

                            is NavMenuRow.Session ->
                                CodexMenuSessionRow(
                                    session = row.session,
                                    selected = row.selected,
                                    onClick = { onRow(row) },
                                )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The action a menu group carries on its title row: the group's own destination, so the row that
 * names the projects is also where a workspace is added.
 */
@Composable
private fun MenuGroupAction(id: String, onClick: () -> Unit) {
    val description =
        when (id) {
            DestinationCatalog.Id.Workspace -> stringResource(R.string.sidebar_add_workspace)
            else -> id
        }
    IconButton(
        onClick = onClick,
        minWidth = UiConsts.IconButtonCompact,
        minHeight = UiConsts.IconButtonCompact,
    ) {
        Icon(
            imageVector = MiuixIcons.AddFolder,
            contentDescription = description,
            modifier = Modifier.size(UiConsts.IconRow),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

/**
 * The page card's left edge: the rail's own edge while the menu is shut, and the far side of the
 * menu's column once the menu card stands pinned beside it. The card takes the column back as the
 * menu card goes into the rail, so the two keep one gap while they move; the page stands at
 * [pageColumnStart] instead, which the card moves around.
 */
internal fun pageCardStart(railWidth: Dp, menuWidth: Dp, menuRoom: Float): Dp =
    railWidth + menuWidth * menuRoom

/**
 * The page column's left edge: the rail's own edge, plus the menu's column where the window has room
 * for one. The menu card's motion is not part of it, so pinning the menu moves the card and leaves
 * the page the width it was laid out at, instead of reflowing every row it shows.
 */
internal fun pageColumnStart(railWidth: Dp, menuWidth: Dp, roomForMenu: Boolean): Dp =
    railWidth + if (roomForMenu) menuWidth else 0.dp

/** Width of the page column: the window less the column's own edge and the gutter the page keeps. */
internal fun pageColumnWidth(windowWidth: Dp, columnStart: Dp): Dp =
    (windowWidth - columnStart - UiConsts.ScreenMargin * 2).coerceAtLeast(0.dp)

/**
 * Where the page column stands inside the card: centred, so a card that has taken the menu's column
 * back splits what it has left evenly instead of pushing the page to one side. The card's own right
 * inset is [UiConsts.ScreenInset], and its left edge is [cardStart].
 */
internal fun pageColumnOffset(windowWidth: Dp, cardStart: Dp, pageWidth: Dp): Dp =
    ((windowWidth - cardStart - UiConsts.ScreenInset - pageWidth) / 2).coerceAtLeast(0.dp)

/**
 * Where the rail's head starts: the window's own top inset plus the shell's margin, so the mark is
 * clear of a camera cutout and never crowds the window's edge the way an item's own inset does.
 */
internal fun railHeadTop(topInset: Dp): Dp = topInset + UiConsts.ScreenMargin
