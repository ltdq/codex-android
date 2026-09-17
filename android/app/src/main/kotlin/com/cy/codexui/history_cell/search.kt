package com.cy.codexui.history_cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.item.ImageGenerationItem
import com.cy.codexui.protocol.protocol.item.ImageViewItem
import com.cy.codexui.protocol.protocol.item.SleepItem
import com.cy.codexui.protocol.protocol.item.WebSearchItem
import com.cy.codexui.protocol.protocol.item.WebSearchResult
import com.cy.codexui.ToolCard
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.statusDotColor
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Web search, image viewing and generation, and sleeps.
 *
 * Mirrors `codex-rs/tui/src/history_cell/search.rs` and the image helpers in `patches.rs`: a search
 * is a card because its results are the payload, everything else is one compact line because it
 * only narrates what the turn is doing.
 */

@Composable
fun WebSearchCell(
    item: WebSearchItem,
    modifier: Modifier = Modifier,
    resultSpacing: Dp = 9.dp,
    emptyFontSize: TextUnit = UiType.Subtitle,
    emptyLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    ToolCard(
        icon = MiuixIcons.Basic.Search,
        title = stringResource(R.string.search_cell_title, item.query),
        subtitle = stringResource(R.string.search_cell_result_count, item.results.size),
        modifier = modifier,
    ) {
        if (item.results.isEmpty()) {
            Text(
                text = stringResource(R.string.search_cell_no_results),
                fontSize = emptyFontSize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(resultSpacing)) {
                item.results.forEach { result -> SearchResultRow(result) }
            }
        }
    }
}

@Composable
fun ImageViewCell(item: ImageViewItem, modifier: Modifier = Modifier) {
    CompactLine(
        icon = MiuixIcons.Image,
        text = stringResource(R.string.search_cell_view_image),
        detail = item.path,
        modifier = modifier,
    )
}

@Composable
fun ImageGenerationCell(item: ImageGenerationItem, modifier: Modifier = Modifier) {
    val tone = dynamicTone(item.status)
    CompactLine(
        icon = MiuixIcons.Photos,
        text = stringResource(R.string.search_cell_image_generation),
        detail = item.prompt.ifBlank { null },
        modifier = modifier,
        tone = tone,
        trailing = { StatusChip(label = dynamicLabel(item.status), tone = tone) },
    )
}

@Composable
fun SleepCell(item: SleepItem, modifier: Modifier = Modifier) {
    CompactLine(
        icon = MiuixIcons.Stopwatch,
        text = stringResource(R.string.search_cell_sleep),
        detail = formatToolDuration(item.durationMs),
        modifier = modifier,
    )
}

@Composable
private fun SearchResultRow(
    result: WebSearchResult,
    titleFontSize: TextUnit = UiType.SheetRowTitle,
    titleLineHeight: TextUnit = UiType.SheetRowTitleLine,
    urlFontSize: TextUnit = UiType.Meta,
    urlLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = result.title.ifBlank { result.url },
            fontSize = titleFontSize,
            lineHeight = titleLineHeight,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.url,
            fontSize = urlFontSize,
            lineHeight = urlLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One-line cell body shared by the narrating items: icon, label, detail and an optional trailing
 * slot (usually a status chip).
 */
@Composable
internal fun CompactLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: ThreadStatusTone? = null,
    trailing: @Composable (() -> Unit)? = null,
    verticalPadding: Dp = 3.dp,
    iconSize: Dp = 14.dp,
    iconSpacing: Dp = 8.dp,
    fontSize: TextUnit = UiType.RowTitle,
    lineHeight: TextUnit = UiType.RowTitleLine,
    detailSpacing: Dp = 6.dp,
    detailFontSize: TextUnit = UiType.Meta,
    detailLineHeight: TextUnit = UiType.Composer,
    trailingSpacing: Dp = 8.dp,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = tone?.let { statusDotColor(it) } ?: colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(iconSpacing))
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = colors.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.width(detailSpacing))
        if (detail == null) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                text = detail,
                modifier = Modifier.weight(1f),
                fontSize = detailFontSize,
                lineHeight = detailLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(trailingSpacing))
            trailing()
        }
    }
}
