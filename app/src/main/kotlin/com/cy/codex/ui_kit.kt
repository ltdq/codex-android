package com.cy.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.cy.codex.chatwidget.SidebarProject
import com.cy.codex.chatwidget.SidebarSession
import com.cy.codex.theme.RoundedIndication
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The list grid every page and menu is drawn on.
 *
 * One frame, one section, one row, one option row: the shell's second segment and every page it
 * opens share these, so nothing carries a margin or a type step of its own. The rails are
 * [UiConsts.PageGutter] for the header, the group titles and the cards, and [UiConsts.RowInset] for
 * the content inside a row.
 */

/**
 * The page frame: a fixed header on the left rail, and the sections scrolling under it.
 *
 * [scroll] is the body's own scrolling. A page whose body is a lazy list has to say false: the
 * list is measured with an infinite height inside a scrolling column, which throws.
 */
@Composable
fun CodexPage(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.PageGutter,
                        end = UiConsts.PageGutter,
                        top = UiConsts.PageHeaderTop,
                        bottom = UiConsts.PageHeaderBottom,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                CodexBackButton(onBack)
                Spacer(Modifier.width(UiConsts.Space10))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = UiType.Title,
                    lineHeight = UiType.TitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = description,
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = UiConsts.PageGutter)
                    .padding(bottom = UiConsts.PageBottomInset)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape, Key.Back -> {
                                onBack?.invoke()
                                true
                            }

                            Key.Tab -> {
                                focusManager.moveFocus(
                                    if (event.isShiftPressed) {
                                        FocusDirection.Previous
                                    } else {
                                        FocusDirection.Next
                                    },
                                )
                                true
                            }

                            else -> false
                        }
                    },
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
            content = content,
        )
    }
}

/** The page header's back chevron; a page the chat pushed carries one, a rail page does not. */
@Composable
fun CodexBackButton(onBack: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            Modifier.size(UiConsts.IconButtonSize)
                .clip(squircleShape(UiConsts.CornerControl))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = stringResource(R.string.page_back),
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = colors.primary,
        )
    }
}

/** A group of rows under its title; the title sits on the page's rail, the rows inside the card. */
@Composable
fun CodexSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            CodexGroupTitle(title)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = UiConsts.SectionCorner,
            insideMargin = PaddingValues(0.dp),
            colors =
                CardDefaults.defaultColors(
                    color = raisedSurface(),
                    contentColor = MiuixTheme.colorScheme.onSurface,
                ),
            content = content,
        )
    }
}

/** The title over a group of rows, on the same rail as the card it names. */
@Composable
fun CodexGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(bottom = UiConsts.GroupTitleBottom, start = UiConsts.Space4),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The hairline between two rows of one card, inset to the rows' own content. */
@Composable
fun CodexRowDivider(startInset: Dp = UiConsts.RowInset) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = startInset)
                .height(UiConsts.OutlineThickness)
                .background(MiuixTheme.colorScheme.dividerLine)
    )
}

/**
 * One row of a card: its title and summary on the inner rail, an optional leading icon and an
 * optional trailing control at the card's edges.
 */
@Composable
fun CodexRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val indication =
        remember(colors.onBackground) {
            RoundedIndication(color = colors.onBackground, radius = UiConsts.CornerRow, inset = 0.dp)
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = indication,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = UiConsts.RowMinHeight)
                .padding(horizontal = UiConsts.RowInset, vertical = UiConsts.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startAction != null) {
            startAction()
            Spacer(Modifier.width(UiConsts.RowGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = UiType.SheetRowTitle,
                lineHeight = UiType.SheetRowTitleLine,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onSurface else colors.disabledOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Spacer(Modifier.height(UiConsts.Space3))
                Text(
                    text = summary,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color =
                        if (enabled) colors.onSurfaceVariantSummary else colors.disabledOnSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (endAction != null) {
            Spacer(Modifier.width(UiConsts.RowGap))
            Row(verticalAlignment = Alignment.CenterVertically, content = endAction)
        }
    }
}

/** A row whose trailing control is a switch; the whole row toggles with it. */
@Composable
fun CodexSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        endAction = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
    )
}

/** A row that picks one of a group; the trailing radio carries the mark. */
@Composable
fun CodexRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    CodexRow(
        title = title,
        modifier = modifier.semantics { this.selected = selected },
        summary = summary,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        endAction = {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        },
    )
}

/** A row that opens another page. */
@Composable
fun CodexNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    startAction: (@Composable () -> Unit)? = null,
) {
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        startAction = startAction,
        endAction = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconChevron),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
    )
}

/** A row that only reports a value: no press feedback, the value on the trailing edge. */
@Composable
fun CodexValueRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    monospace: Boolean = true,
) {
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        endAction = { CodexValue(value, monospace = monospace) },
    )
}

/** A trailing value; monospace by convention so ids, paths and counts line up. */
@Composable
fun CodexValue(text: String, monospace: Boolean = true) {
    Text(
        text = text,
        fontSize = UiType.Value,
        lineHeight = UiType.ValueLine,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A card with one line of text and nothing to act on: what a section says when it is empty. */
@Composable
fun CodexEmptyRow(text: String, modifier: Modifier = Modifier) {
    CodexSection(modifier = modifier) {
        CodexRow(title = text, enabled = false)
    }
}

/** The search field the second segment carries above its rows. */
@Composable
fun CodexSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val shape = squircleShape(UiConsts.CornerChip)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(UiConsts.MenuSearchHeight)
                .clip(shape)
                .background(colors.surfaceContainerHigh),
        singleLine = true,
        textStyle =
            TextStyle(
                color = colors.onBackground,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
            ),
        cursorBrush = SolidColor(colors.primary),
        decorationBox = { field ->
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = UiConsts.Space10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(UiConsts.Space8))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = UiType.Body,
                            lineHeight = UiType.BodyLine,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        modifier =
                            Modifier.size(UiConsts.IconRow)
                                .clickable { onValueChange("") },
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        },
    )
}

/**
 * One option of the second segment: its icon and label inside a rounded row, which the selected
 * option fills and the pointer lifts.
 */
@Composable
fun CodexMenuRow(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = squircleShape(UiConsts.CornerControl)
    val fill =
        when {
            selected -> colors.surfaceContainerHigh
            hovered -> colors.onBackground.copy(alpha = 0.05f)
            else -> Color.Transparent
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = UiConsts.MenuRowInset)
                .clip(shape)
                .background(fill)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .height(UiConsts.MenuRowHeight)
                .padding(horizontal = UiConsts.MenuRowPadding)
                .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiConsts.IconRow),
            tint = if (selected) colors.primary else colors.onSurfaceSecondary,
        )
        Spacer(Modifier.width(UiConsts.Space10))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.onBackground else colors.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(UiConsts.Space6))
            trailing()
        }
    }
}

/** A project of the home menu: its folder, its name, and the sessions under it once unfolded. */
@Composable
fun CodexMenuProjectRow(
    project: SidebarProject,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (expanded) 90f else 0f,
            animationSpec = tween(durationMillis = Motion.DisclosureMs),
            label = "projectChevron",
        )
    CodexMenuRow(
        title = project.name,
        icon = MiuixIcons.FolderFill,
        selected = false,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription =
                    if (expanded) {
                        stringResource(R.string.sidebar_collapse_project, project.name)
                    } else {
                        stringResource(R.string.sidebar_expand_project, project.name)
                    },
                modifier = Modifier.size(UiConsts.IconChevron).graphicsLayer {
                    rotationZ = chevronRotation
                },
                tint = colors.onSurfaceVariantActions,
            )
        },
    )
}

/** A session of the home menu: a status dot and the thread, indented under its project. */
@Composable
fun CodexMenuSessionRow(
    session: SidebarSession,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = squircleShape(UiConsts.CornerControl)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = UiConsts.MenuRowInset + UiConsts.RowIndent, end = UiConsts.MenuRowInset)
                .clip(shape)
                .background(
                    when {
                        selected -> colors.surfaceContainerHigh
                        hovered -> colors.onBackground.copy(alpha = 0.05f)
                        else -> Color.Transparent
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .height(UiConsts.MenuRowHeight)
                .padding(horizontal = UiConsts.MenuRowPadding)
                .semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(UiConsts.Space6)
                    .clip(CircleShape)
                    .background(
                        when {
                            session.running -> statusDotColor(ThreadStatusTone.Running)
                            session.archived -> colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
                            else -> colors.onSurfaceVariantSummary
                        }
                    )
        )
        Spacer(Modifier.width(UiConsts.Space10))
        Text(
            text = session.title,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.onBackground else colors.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(
            text = session.date,
            fontSize = UiType.Caption,
            lineHeight = UiType.CaptionLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

/** The title over a group of menu options, with the group's own action on its trailing edge. */
@Composable
fun CodexMenuGroupTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = UiConsts.MenuRowInset + UiConsts.MenuRowPadding,
                    end = UiConsts.MenuRowInset + UiConsts.Space4,
                    top = UiConsts.MenuGroupTop,
                    bottom = UiConsts.MenuGroupBottom,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/**
 * Lays [count] cells out in as many columns as [minCellWidth] allows, so the entries of a catalogue
 * are cards side by side rather than one row per line.
 */
@Composable
fun CodexCardGrid(
    count: Int,
    modifier: Modifier = Modifier,
    minCellWidth: Dp = UiConsts.CatalogMinWidth,
    gap: Dp = UiConsts.CatalogGap,
    cell: @Composable (Int) -> Unit,
) {
    if (count <= 0) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns =
            (((maxWidth + gap) / (minCellWidth + gap)).toInt()).coerceIn(1, count)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            var index = 0
            while (index < count) {
                val rowStart = index
                val inRow = minOf(columns, count - rowStart)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (column in 0 until inRow) {
                        Box(modifier = Modifier.weight(1f)) { cell(rowStart + column) }
                    }
                    // A last row that is not full keeps its cells at the width of the rows above.
                    for (missing in inRow until columns) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                index += inRow
            }
        }
    }
}

/** One catalogue entry: its mark, its name over its description, and its own control. */
@Composable
fun CodexCatalogCard(
    title: String,
    description: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val indication =
        remember(colors.onSurface) {
            RoundedIndication(color = colors.onSurface, radius = UiConsts.CornerRow, inset = 0.dp)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(squircleShape(UiConsts.SectionCorner))
                .background(raisedSurface())
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = indication,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(UiConsts.CatalogPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(UiConsts.CatalogIconBox)
                        .clip(squircleShape(UiConsts.CornerControl))
                        .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = if (enabled) colors.primary else colors.disabledOnSurface,
                )
            }
            Spacer(Modifier.width(UiConsts.RowGap))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = UiType.CardTitle,
                lineHeight = UiType.CardTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) colors.onSurface else colors.disabledOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Spacer(Modifier.width(UiConsts.Space8))
                trailing()
            }
        }
        if (description != null) {
            Spacer(Modifier.height(UiConsts.Space8))
            Text(
                text = description,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color =
                    if (enabled) colors.onSurfaceVariantSummary else colors.disabledOnSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (footer != null) {
            Spacer(Modifier.height(UiConsts.Space8))
            footer()
        }
    }
}

