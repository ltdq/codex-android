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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import com.cy.codex.chatwidget.SidebarProject
import com.cy.codex.chatwidget.SidebarSession
import com.cy.codex.theme.RoundedIndication
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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
    enabled: Boolean = true,
) {
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
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

/**
 * A card that carries a page's empty state: the glyph, what is missing, and the action that would
 * fill it when the app has one.
 */
@Composable
fun CodexEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    CodexSection(modifier = modifier) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(UiConsts.IconBoxLarge)
                        .squircleBackground(
                            color = raisedSurface(),
                            cornerRadius = UiConsts.CornerCard,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconHeader),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.Space12))
            Text(
                text = title,
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            Text(
                text = description,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(UiConsts.Space16))
                Button(
                    onClick = { onAction?.invoke() },
                    enabled = onAction != null,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = actionLabel, maxLines = 1)
                }
            }
        }
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
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = squircleShape(UiConsts.CornerControl)
    val fill =
        when {
            !enabled -> Color.Transparent
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
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    }
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
            tint =
                when {
                    !enabled -> colors.disabledOnSurface
                    selected -> colors.primary
                    else -> colors.onSurfaceSecondary
                },
        )
        Spacer(Modifier.width(UiConsts.Space10))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color =
                when {
                    !enabled -> colors.disabledOnSurface
                    selected -> colors.onBackground
                    else -> colors.onBackgroundVariant
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(UiConsts.Space6))
            trailing()
        }
    }
}

/**
 * A line of text where a group of the menu has nothing to list.
 *
 * Sits on the menu rows' own rail rather than the cards': the note belongs to the group title above
 * it, and a card would read as a row that can be pressed.
 */
@Composable
fun CodexMenuNoteRow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = UiConsts.MenuRowInset + UiConsts.MenuRowPadding + UiConsts.RowIndent,
                    end = UiConsts.MenuRowInset + UiConsts.MenuRowPadding,
                    top = UiConsts.Space2,
                    bottom = UiConsts.Space4,
                ),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
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

/**
 * The rows the desktop app's settings pages are built from.
 *
 * A desktop settings row is one of a few controls: a switch, a picker, a segmented pick, a field or
 * a button. [CodexRow] and its siblings carry the switch and the page-opening row; the four below
 * carry the rest, so a ported page reads as its copy and nothing else.
 */

/**
 * A row that reports one value and opens the list it is picked from.
 *
 * [CodexValueRow] is the reading: this one is a control, which is what the trailing chevron says.
 * [value] is what the row shows, so a picker whose options are ids can report a display name.
 */
@Composable
fun CodexSelectRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    value: String = options.getOrElse(selected) { "" },
    summary: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = if (enabled) { { open = true } } else null,
        endAction = {
            CodexValue(value, monospace = false)
            Spacer(Modifier.width(UiConsts.Space4))
            Icon(
                imageVector = MiuixIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconChevron),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
    )
    if (open) {
        CodexOptionsSheet(
            title = title,
            options = options,
            selected = selected,
            onSelect = {
                onSelect(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** The choices of a [CodexSelectRow], as a sheet: the row's own title over one row per option. */
@Composable
fun CodexOptionsSheet(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        title = title,
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(max = LocalWindowInfo.current.containerDpSize.height * UiConsts.SheetHeightFraction)
                    .verticalScroll(rememberScrollState())
        ) {
            options.forEachIndexed { index, option ->
                RadioButtonPreference(
                    title = option,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/**
 * A row whose trailing control is a segmented picker.
 *
 * Every option is on screen, so a two- or three-way choice shows what is not chosen as well; a set
 * too wide for the row belongs in a [CodexSelectRow] instead.
 */
@Composable
fun CodexSegmentedRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        endAction = {
            Row(
                modifier =
                    Modifier.clip(squircleShape(UiConsts.CornerChip))
                        .background(colors.surfaceContainerHigh)
                        .padding(UiConsts.Space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { index, option ->
                    val picked = index == selected
                    Box(
                        modifier =
                            Modifier.clip(squircleShape(UiConsts.CornerChip))
                                .then(if (picked) Modifier.background(raisedSurface()) else Modifier)
                                .then(
                                    if (enabled) {
                                        Modifier.clickable(role = Role.RadioButton) { onSelect(index) }
                                    } else {
                                        Modifier
                                    }
                                )
                                .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space4),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            fontSize = UiType.Chip,
                            lineHeight = UiType.ChipLine,
                            color =
                                when {
                                    !enabled -> colors.disabledOnSurface
                                    picked -> colors.primary
                                    else -> colors.onSurfaceVariantSummary
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

/** A row whose trailing control is a button: an action that is neither a switch nor a choice. */
@Composable
fun CodexButtonRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        endAction = {
            Button(
                onClick = onAction,
                enabled = enabled,
                colors =
                    if (destructive) {
                        ButtonDefaults.buttonColors(
                            color = Color.Transparent,
                            contentColor = colors.error,
                        )
                    } else {
                        ButtonDefaults.buttonColorsPrimary()
                    },
            ) {
                Text(text = actionLabel, fontSize = UiType.Action, maxLines = 1)
            }
        },
    )
}

/**
 * A row that reports a value the user types, and opens the field for it.
 *
 * The row keeps the value on its trailing edge and the sheet keeps the keyboard, so a page of
 * settings never has a text field competing with the rows around it for focus.
 */
@Composable
fun CodexFieldRow(
    title: String,
    value: String,
    onCommit: (String) -> Unit,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    CodexRow(
        title = title,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        onClick = if (enabled) { { open = true } } else null,
        endAction = {
            CodexValue(value.ifEmpty { placeholder.orEmpty() }, monospace = false)
            Spacer(Modifier.width(UiConsts.Space4))
            Icon(
                imageVector = MiuixIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconChevron),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
    )
    if (open) {
        CodexFieldSheet(
            title = title,
            initial = value,
            placeholder = placeholder.orEmpty(),
            confirmLabel = confirmLabel,
            onSubmit = {
                onCommit(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** The one-field editor a [CodexFieldRow] opens. */
@Composable
fun CodexFieldSheet(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        title = title,
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = placeholder,
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
            )
            Spacer(Modifier.height(UiConsts.Space12))
            Button(
                onClick = { onSubmit(text.trim()) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = confirmLabel, maxLines = 1)
            }
        }
    }
}

