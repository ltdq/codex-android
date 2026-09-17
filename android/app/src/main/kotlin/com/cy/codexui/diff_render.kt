package com.cy.codexui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.DiffFileKind
import com.cy.codexui.DiffLine
import com.cy.codexui.DiffLineKind
import com.cy.codexui.DiffPalette
import com.cy.codexui.FileDiff
import com.cy.codexui.codeSurface
import com.cy.codexui.pressableRow
import com.cy.codexui.successColor
import com.cy.codexui.warningColor
import com.cy.codexui.UiType
import com.cy.codexui.UiConsts
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** How many more diff lines one tap on the expander reveals. */
private const val DiffExpanderChunk = 200

/**
 * Diff rendering shared by the transcript's patch cells and the status card's diff pane.
 *
 * Mirrors `codex-rs/tui/src/diff_render.rs`: two gutters, per-kind tinting and a tail row when the
 * body is longer than the cell is willing to show.
 *
 * The body is a plain Column bounded by [maxLines] and grown in chunks on demand. A lazy list
 * would nest a second vertical scrollable inside the transcript's LazyColumn, and an unbounded
 * Column would make one transcript item lay out a whole file.
 */
@Composable
fun DiffBody(
    lines: List<DiffLine>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    showGutters: Boolean = true,
    verticalPadding: Dp = 4.dp,
    omittedStartPadding: Dp = 16.dp,
    omittedTopPadding: Dp = 6.dp,
    omittedBottomPadding: Dp = 2.dp,
    omittedFontSize: TextUnit = UiType.Caption,
    omittedLineHeight: TextUnit = UiType.CardTitle,
) {
    val palette = diffPalette()
    val hunkTextColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val signAdded = stringResource(R.string.blocks_sign_added)
    val signRemoved = stringResource(R.string.blocks_sign_removed)
    // Rows are folded once per body and re-folded only when the diff, the palette or the locale
    // changes, so recomposing a row does no string, Color or TextStyle construction.
    val rows = remember(lines, palette, hunkTextColor, signAdded, signRemoved) {
        buildDiffRows(lines, palette, hunkTextColor, signAdded, signRemoved)
    }
    // Disclosure state for the truncated tail; keyed on the diff so a new payload opens at its
    // start instead of at a stale offset.
    val visible = remember(lines, maxLines) {
        mutableIntStateOf(maxLines.coerceIn(0, lines.size))
    }
    val shown = visible.intValue.coerceAtMost(lines.size)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = verticalPadding),
    ) {
        for (index in 0 until shown) {
            DiffRow(model = rows[index], gutterColor = palette.gutter, showGutters = showGutters)
        }
        if (shown < lines.size) {
            DiffExpander(
                label = stringResource(R.string.blocks_show_more_lines, lines.size - shown),
                onClick = {
                    visible.intValue = (shown + DiffExpanderChunk).coerceAtMost(lines.size)
                },
                startPadding = omittedStartPadding,
                topPadding = omittedTopPadding,
                bottomPadding = omittedBottomPadding,
                fontSize = omittedFontSize,
                lineHeight = omittedLineHeight,
            )
        }
    }
}

/**
 * One diff row with everything the layout needs already resolved.
 *
 * Immutable and comparable, which is what lets Compose skip a row whose line did not change while
 * an unrelated file of the same turn grows.
 */
@Immutable
internal data class DiffRowModel(
    val oldLine: String,
    val newLine: String,
    val sign: String,
    val text: String,
    val background: Color,
    val textColor: Color,
)

/** Fold parsed lines into row models. Pure, so a body pays for it once rather than per frame. */
internal fun buildDiffRows(
    lines: List<DiffLine>,
    palette: DiffPalette,
    hunkTextColor: Color,
    signAdded: String,
    signRemoved: String,
): List<DiffRowModel> {
    val rows = ArrayList<DiffRowModel>(lines.size)
    for (line in lines) {
        val background = when (line.kind) {
            DiffLineKind.Add -> palette.addSurface
            DiffLineKind.Remove -> palette.removeSurface
            DiffLineKind.Hunk -> palette.hunkSurface
            DiffLineKind.Context -> Color.Transparent
        }
        val textColor = when (line.kind) {
            DiffLineKind.Add -> palette.addText
            DiffLineKind.Remove -> palette.removeText
            DiffLineKind.Hunk -> hunkTextColor
            DiffLineKind.Context -> palette.context
        }
        val sign = when (line.kind) {
            DiffLineKind.Add -> signAdded
            DiffLineKind.Remove -> signRemoved
            DiffLineKind.Hunk, DiffLineKind.Context -> ""
        }
        rows += DiffRowModel(
            oldLine = line.oldLine?.toString().orEmpty(),
            newLine = line.newLine?.toString().orEmpty(),
            sign = sign,
            text = line.text,
            background = background,
            textColor = textColor,
        )
    }
    return rows
}

/** One diff line. Every value is precomputed in [DiffRowModel]; nothing is built here. */
@Composable
internal fun DiffRow(
    model: DiffRowModel,
    gutterColor: Color,
    showGutters: Boolean = true,
    verticalPadding: Dp = 1.dp,
    signWidth: Dp = 15.dp,
    endPadding: Dp = 18.dp,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.CaptionLine,
) {
    Row(
        modifier = Modifier
            .background(model.background)
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showGutters) {
            DiffLineNumber(text = model.oldLine, color = gutterColor)
            DiffLineNumber(text = model.newLine, color = gutterColor)
        }
        Text(
            text = model.sign,
            modifier = Modifier.width(signWidth),
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Monospace,
            color = model.textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = model.text,
            modifier = Modifier.padding(end = endPadding),
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Monospace,
            color = model.textColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The row that stands in for the hidden tail of a long diff.
 *
 * A tap reveals the next chunk rather than repeating the omitted count: a diff the caller
 * truncated is usually still worth reading, and the old note left no way to read it without
 * opening the status pane.
 */
@Composable
private fun DiffExpander(
    label: String,
    onClick: () -> Unit,
    startPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = startPadding, top = topPadding, bottom = bottomPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
fun DiffLineNumber(
    text: String,
    color: Color,
    width: Dp = 32.dp,
    endPadding: Dp = 8.dp,
    fontSize: TextUnit = UiType.Caption,
    lineHeight: TextUnit = UiType.CaptionLine,
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(end = endPadding),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * Diff colours for the current theme.
 *
 * The dark and light values have no tonal equivalent in the miuix palette, so they are fixed here
 * the way `codex-rs/tui/src/color.rs` fixes the terminal's diff colours.
 */
@Composable
fun diffPalette(): DiffPalette {
    val dark = isSystemInDarkTheme()
    val context = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    return remember(dark, context) {
        if (dark) {
            DiffPalette(
                addText = Color(0xFF7EE787),
                addSurface = Color(0x1F7EE787),
                removeText = Color(0xFFFF9A9A),
                removeSurface = Color(0x1FFF9A9A),
                hunkText = Color(0xFF8AB4F8),
                hunkSurface = Color(0x1F8AB4F8),
                gutter = Color(0x66FFFFFF),
                context = context,
            )
        } else {
            DiffPalette(
                addText = Color(0xFF1A7F37),
                addSurface = Color(0x1F1A7F37),
                removeText = Color(0xFFB3261E),
                removeSurface = Color(0x1FB3261E),
                hunkText = Color(0xFF1A5FB4),
                hunkSurface = Color(0x1F1A5FB4),
                gutter = Color(0x66000000),
                context = context,
            )
        }
    }
}

/** Letter badge for one changed file: `A` / `M` / `D`. */
@Composable
fun FileKindBadge(
    file: FileDiff,
    modifier: Modifier = Modifier,
    size: Dp = 21.dp,
    corner: Dp = UiConsts.CornerChip,
    fontSize: TextUnit = UiType.Caption,
    lineHeight: TextUnit = UiType.SheetRowTitle,
) {
    val color = fileKindColor(file.kind)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = file.letter,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
fun fileKindColor(kind: DiffFileKind): Color {
    val colors = MiuixTheme.colorScheme
    return when (kind) {
        DiffFileKind.Added -> successColor()
        DiffFileKind.Modified -> warningColor()
        DiffFileKind.Deleted -> colors.error
    }
}

@Composable
fun FileStatText(
    additions: Int,
    removals: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = UiType.Caption,
    lineHeight: TextUnit = UiType.CardTitle,
    spacing: Dp = 5.dp,
) {
    val palette = diffPalette()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (additions > 0) {
            Text(
                text = stringResource(R.string.blocks_stat_added, additions),
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontFamily = FontFamily.Monospace,
                color = palette.addText,
                maxLines = 1,
            )
        }
        if (removals > 0) {
            Spacer(Modifier.width(spacing))
            Text(
                text = stringResource(R.string.blocks_stat_removed, removals),
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontFamily = FontFamily.Monospace,
                color = palette.removeText,
                maxLines = 1,
            )
        }
    }
}

/** One expandable file inside a patch cell: header row plus the diff body when open. */
@Composable
fun FileDiffRow(
    file: FileDiff,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    bodyMaxLines: Int = 400,
    corner: Dp = UiConsts.CornerControl,
    horizontalPadding: Dp = 6.dp,
    verticalPadding: Dp = 5.dp,
    badgeSpacing: Dp = 9.dp,
    titleFontSize: TextUnit = UiType.Body,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    parentFontSize: TextUnit = UiType.Caption,
    parentLineHeight: TextUnit = UiType.SheetRowTitle,
    statSpacing: Dp = 6.dp,
    chevronSpacing: Dp = 4.dp,
    chevronSize: Dp = 12.dp,
    bodyStartPadding: Dp = 6.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressableRow(
                    shape = shape,
                    container = if (expanded) colors.primary.copy(alpha = 0.08f) else Color.Transparent,
                    onClick = onToggle,
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileKindBadge(file)
            Spacer(Modifier.width(badgeSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = if (expanded) FontWeight.Medium else FontWeight.Normal,
                    color = if (expanded) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (file.parentPath.isNotEmpty()) {
                    Text(
                        text = file.parentPath,
                        fontSize = parentFontSize,
                        lineHeight = parentLineHeight,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(statSpacing))
            FileStatText(file.additions, file.removals)
            Spacer(Modifier.width(chevronSpacing))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (expanded) {
                    stringResource(R.string.blocks_collapse_diff)
                } else {
                    stringResource(R.string.blocks_view_diff)
                },
                modifier = Modifier
                    .size(chevronSize)
                    .rotate(if (expanded) 90f else 0f),
                tint = colors.onSurfaceVariantSummary,
            )
        }
        if (expanded) {
            DiffBody(
                lines = file.lines,
                maxLines = bodyMaxLines,
                modifier = Modifier.padding(start = bodyStartPadding),
            )
        }
    }
}

/**
 * Card chrome used by every tool cell: an icon, a title line, an optional trailing slot and a
 * body. Mirrors the shared shape of the TUI's `history_cell/{exec,mcp,patches,search}.rs` cells.
 */
@Composable
fun ToolCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color = MiuixTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    corner: Dp = UiConsts.CornerRow,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    iconSize: Dp = 15.dp,
    iconSpacing: Dp = 8.dp,
    titleFontSize: TextUnit = UiType.Body,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    subtitleFontSize: TextUnit = UiType.Caption,
    subtitleLineHeight: TextUnit = UiType.CardTitle,
    trailingSpacing: Dp = 8.dp,
    bodySpacing: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(codeSurface())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = accent,
            )
            Spacer(Modifier.width(iconSpacing))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = subtitleFontSize,
                        lineHeight = subtitleLineHeight,
                        color = colors.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(trailingSpacing))
                trailing()
            }
        }
        Spacer(Modifier.height(bodySpacing))
        content()
    }
}

/** Collapsible section inside a transcript cell, used for reasoning and long tool output. */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    verticalPadding: Dp = 4.dp,
    chevronSize: Dp = 13.dp,
    chevronSpacing: Dp = 7.dp,
    titleFontSize: TextUnit = UiType.Body,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    subtitleSpacing: Dp = 8.dp,
    subtitleFontSize: TextUnit = UiType.Caption,
    subtitleLineHeight: TextUnit = UiType.Message,
    contentStartPadding: Dp = 20.dp,
    contentTopPadding: Dp = 2.dp,
    contentSpacing: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = if (expanded) {
                    stringResource(R.string.blocks_collapse, title)
                } else {
                    stringResource(R.string.blocks_expand, title)
                },
                modifier = Modifier
                    .size(chevronSize)
                    .rotate(if (expanded) 90f else 0f),
                tint = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(chevronSpacing))
            Text(
                text = title,
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(Modifier.width(subtitleSpacing))
                Text(
                    text = subtitle,
                    modifier = Modifier.weight(1f),
                    fontSize = subtitleFontSize,
                    lineHeight = subtitleLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = contentStartPadding, top = contentTopPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                content = content,
            )
        }
    }
}

/** Small state memory for a cell that owns its own disclosure state. */
@Composable
fun rememberExpanded(initial: Boolean): Pair<Boolean, () -> Unit> {
    var expanded by remember { mutableStateOf(initial) }
    return expanded to { expanded = !expanded }
}
