package com.cy.codex.chatwidget

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.panelColor
import com.cy.codex.statusDotColor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Toggle for [SessionMenuPopup]; it is the status chip's twin, so it takes the same size, corner
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
 * The session menu: the thread library, then the tools that act on the open thread, drawn as a
 * miuix list popup by the root Scaffold's popup host so it hangs off its chip, above the composer.
 */
@Composable
fun SessionMenuPopup(
    show: Boolean,
    library: List<SidebarEntry>,
    tools: List<SidebarEntry>,
    onAction: (SidebarEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 420.dp,
) {
    val libraryTitle = stringResource(R.string.sidebar_library_header)
    val toolsTitle = stringResource(R.string.sidebar_session_tools_header)
    OverlayListPopup(
        show = show,
        popupModifier = modifier,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        maxHeight = maxHeight,
    ) {
        ListPopupColumn {
            SmallTitle(text = libraryTitle)
            library.forEach { entry ->
                SessionMenuRow(entry = entry, onClick = { onAction(entry) })
            }
            SmallTitle(text = toolsTitle)
            tools.forEach { entry ->
                SessionMenuRow(entry = entry, onClick = { onAction(entry) })
            }
        }
    }
}

/**
 * One entry of the session menu; not a `BasicComponent`, because [ListPopupColumn] sizes rows
 * through intrinsic measurement and the library's component overflows the constraint arithmetic
 * there (`Component.kt:227`).
 */
@Composable
private fun SessionMenuRow(entry: SidebarEntry, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            modifier = Modifier.size(UiConsts.IconLeading),
            tint = colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(UiConsts.Space14))
        Text(
            text = entry.title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A menu or panel row; [selected] marks the row that names the open page. */
@Composable
internal fun ActionRow(
    entry: SidebarEntry,
    onClick: () -> Unit,
    selected: Boolean = false,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    iconSize: Dp = UiConsts.IconLeading,
) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        title = entry.title,
        // The row's own fill is the menu's; the selected entry is marked by its tint, like a card's
        // highlighted row.
        titleColor =
            BasicComponentDefaults.titleColor(
                color = if (selected) colors.primary else colors.onBackground,
            ),
        startAction = {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = if (selected) colors.primary else colors.onSurfaceSecondary,
            )
        },
        insideMargin = insideMargin,
        onClick = onClick,
    )
}

@Composable
internal fun ProjectRow(
    project: SidebarProject,
    expanded: Boolean,
    onClick: () -> Unit,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    iconSize: Dp = UiConsts.IconLeading,
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
    BasicComponent(
        title = project.name,
        summary = project.path,
        startAction = {
            Icon(
                imageVector = MiuixIcons.FolderFill,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = Color(0xFFFFC24B),
            )
        },
        endActions = {
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
        },
        insideMargin = insideMargin,
        onClick = onClick,
    )
}

@Composable
internal fun SessionRow(
    session: SidebarSession,
    selected: Boolean,
    onClick: () -> Unit,
    startIndent: Dp = UiConsts.RowIndent,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    dotSize: Dp = 7.dp,
    dateSize: TextUnit = UiType.RowDetail,
    dateLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        modifier = Modifier.padding(start = startIndent),
        title = session.title,
        titleColor =
            BasicComponentDefaults.titleColor(
                color = if (selected) colors.primary else colors.onBackground,
            ),
        startAction = {
            Box(
                modifier =
                    Modifier.size(dotSize)
                        .clip(CircleShape)
                        .background(
                            when {
                                session.running ->
                                    statusDotColor(com.cy.codex.ThreadStatusTone.Running)

                                session.archived -> colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
                                else -> colors.onSurfaceVariantSummary
                            }
                        )
            )
        },
        endActions = {
            Text(
                text = session.date,
                fontSize = dateSize,
                lineHeight = dateLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        },
        insideMargin = insideMargin,
        onClick = onClick,
    )
}
