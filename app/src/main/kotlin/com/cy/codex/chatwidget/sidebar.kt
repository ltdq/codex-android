package com.cy.codex.chatwidget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.panelColor
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Toggle for [SessionToolsPanel]; it is the status chip's twin, so it takes the same size, corner
 * and press feedback.
 */
@Composable
fun SessionPanelButton(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOverlay by
        animateColorAsState(
            targetValue =
                if (pressed) colors.onBackground.copy(alpha = 0.12f) else Color.Transparent,
            animationSpec = Motion.Tint,
            label = "sessionPanelPress",
        )
    Surface(
        onClick = onClick,
        modifier = modifier.size(UiConsts.ChipSize),
        shape = RoundedCornerShape(UiConsts.ChipCorner),
        color =
            pressOverlay.compositeOver(
                if (open) colors.primary.copy(alpha = 0.92f) else panelColor()
            ),
        shadowElevation = elevation,
        interactionSource = interactionSource,
        indication = null,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = MiuixIcons.Sidebar,
                contentDescription =
                    if (open) {
                        stringResource(R.string.session_tools_close)
                    } else {
                        stringResource(R.string.session_tools_open)
                    },
                modifier = Modifier.size(UiConsts.ChipIcon),
                tint = if (open) colors.onPrimary else colors.primary,
            )
        }
    }
}

/**
 * The floating panel on the right of the page column: the thread library, then the tools that act
 * on the open thread. It floats over the page rather than taking a fourth segment of its own.
 */
@Composable
fun SessionToolsPanel(
    title: String,
    library: List<SidebarEntry>,
    tools: List<SidebarEntry>,
    onAction: (SidebarEntry) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = UiConsts.SidebarWidthCap,
    maxHeight: Dp = 420.dp,
    listPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
    listItemGap: Dp = 2.dp,
    sectionGap: Dp = 6.dp,
    listBottomGap: Dp = 8.dp,
) {
    val libraryTitle = stringResource(R.string.sidebar_library_header)
    val toolsTitle = stringResource(R.string.sidebar_session_tools_header)
    val rows =
        remember(library, tools, libraryTitle, toolsTitle) {
            buildList {
                add(SessionPanelRow.Header(libraryTitle))
                library.forEach { add(SessionPanelRow.Action(it)) }
                add(SessionPanelRow.Gap)
                add(SessionPanelRow.Header(toolsTitle))
                tools.forEach { add(SessionPanelRow.Action(it)) }
            }
        }
    Surface(
        modifier = modifier.width(width).heightIn(max = maxHeight),
        shape = RoundedCornerShape(UiConsts.OverlayCorner),
        color = panelColor(),
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                fontSize = UiType.SheetTitle,
                lineHeight = UiType.SheetTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(listItemGap),
            ) {
                items(rows.size, key = { rows[it].key }) { index ->
                    when (val row = rows[index]) {
                        is SessionPanelRow.Header ->
                            SectionHeader(
                                title = row.title,
                                collapsed = false,
                                collapsible = false,
                                onClick = {},
                            )

                        SessionPanelRow.Gap -> Spacer(Modifier.height(sectionGap))

                        is SessionPanelRow.Action ->
                            ActionRow(entry = row.entry, onClick = { onAction(row.entry) })
                    }
                }
            }
            Spacer(Modifier.height(listBottomGap))
        }
    }
}

private sealed interface SessionPanelRow {
    val key: String

    data class Header(val title: String) : SessionPanelRow {
        override val key: String = "header-$title"
    }

    data class Action(val entry: SidebarEntry) : SessionPanelRow {
        override val key: String = "action-${entry.id}"
    }

    data object Gap : SessionPanelRow {
        override val key: String = "gap"
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    collapsed: Boolean,
    collapsible: Boolean = true,
    onClick: () -> Unit,
    corner: Dp = UiConsts.CornerControl,
    horizontalPadding: Dp = 6.dp,
    contentPadding: PaddingValues =
        PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    titleSize: TextUnit = UiType.Subtitle,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    chevronSize: Dp = 14.dp,
    pressInDurationMs: Int = Motion.PressMs,
    pressOutDurationMs: Int = Motion.TintMs,
    chevronDurationMs: Int = Motion.ContentEnterMs,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOverlay by
        animateColorAsState(
            targetValue =
                if (pressed) colors.onBackground.copy(alpha = 0.08f) else Color.Transparent,
            animationSpec =
                tween(durationMillis = if (pressed) pressInDurationMs else pressOutDurationMs),
            label = "sectionPress",
        )
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (collapsed) 90f else -90f,
            animationSpec = tween(durationMillis = chevronDurationMs),
            label = "sectionChevron",
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .clip(RoundedCornerShape(corner))
                .then(
                    if (collapsible) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .background(pressOverlay, RoundedCornerShape(corner))
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            color = colors.onSurfaceVariantSummary,
        )
        if (collapsible) {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription =
                    if (collapsed) {
                        stringResource(R.string.sidebar_expand_section, title)
                    } else {
                        stringResource(R.string.sidebar_collapse_section, title)
                    },
                modifier = Modifier.size(chevronSize).graphicsLayer { rotationZ = chevronRotation },
                tint = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** A menu or panel row; [selected] marks the row that names the open page. */
@Composable
internal fun ActionRow(
    entry: SidebarEntry,
    onClick: () -> Unit,
    selected: Boolean = false,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    iconSize: Dp = UiConsts.IconLeading,
    iconGap: Dp = UiConsts.Space14,
    titleSize: TextUnit = UiType.Message,
    titleLineHeight: TextUnit = UiType.ComposerLine,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .squircleSurface(
                    color = if (selected) raisedSurface() else Color.Transparent,
                    cornerRadius = corner,
                )
                .combinedClickable(onClick = onClick)
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = if (selected) colors.primary else colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(iconGap))
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ProjectRow(
    project: SidebarProject,
    expanded: Boolean,
    onClick: () -> Unit,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    iconSize: Dp = UiConsts.IconLeading,
    iconGap: Dp = UiConsts.Space14,
    nameSize: TextUnit = UiType.Message,
    nameLineHeight: TextUnit = UiType.ComposerLine,
    pathSize: TextUnit = UiType.Chip,
    pathLineHeight: TextUnit = UiType.SheetRowTitle,
    chevronSize: Dp = 15.dp,
    chevronDurationMs: Int = Motion.DisclosureMs,
) {
    val colors = MiuixTheme.colorScheme
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            animationSpec = tween(durationMillis = chevronDurationMs),
            label = "projectChevron",
        )
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .squircleSurface(color = Color.Transparent, cornerRadius = corner)
                .combinedClickable(onClick = onClick)
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.FolderFill,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color(0xFFFFC24B),
        )
        Spacer(Modifier.width(iconGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                fontSize = nameSize,
                lineHeight = nameLineHeight,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = project.path,
                fontSize = pathSize,
                lineHeight = pathLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription =
                if (expanded) {
                    stringResource(R.string.sidebar_collapse_project, project.name)
                } else {
                    stringResource(R.string.sidebar_expand_project, project.name)
                },
            modifier = Modifier.size(chevronSize).graphicsLayer { rotationZ = chevronRotation },
            tint = colors.onSurfaceVariantActions,
        )
    }
}

@Composable
internal fun SessionRow(
    session: SidebarSession,
    selected: Boolean,
    onClick: () -> Unit,
    startIndent: Dp = UiConsts.RowIndent,
    corner: Dp = UiConsts.CornerRow,
    contentPadding: PaddingValues =
        PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    dotSize: Dp = 7.dp,
    dotGap: Dp = 12.dp,
    titleSize: TextUnit = UiType.CardTitle,
    titleLineHeight: TextUnit = UiType.Title,
    dateGap: Dp = 8.dp,
    dateSize: TextUnit = UiType.RowDetail,
    dateLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = startIndent)
                .squircleSurface(
                    color = if (selected) raisedSurface() else Color.Transparent,
                    cornerRadius = corner,
                )
                .combinedClickable(onClick = onClick)
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(dotSize)
                    .clip(CircleShape)
                    .background(
                        when {
                            session.running -> statusDotColor(com.cy.codex.ThreadStatusTone.Running)
                            session.archived -> colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
                            else -> colors.onSurfaceVariantSummary
                        }
                    )
        )
        Spacer(Modifier.width(dotGap))
        Text(
            text = session.title,
            modifier = Modifier.weight(1f),
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(dateGap))
        Text(
            text = session.date,
            fontSize = dateSize,
            lineHeight = dateLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}
