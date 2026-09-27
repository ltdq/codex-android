package com.cy.codex.history_cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.ImageViewItem
import com.cy.codex.protocol.protocol.item.SleepItem
import com.cy.codex.protocol.protocol.item.WebSearchAction
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.item.WebSearchResult
import com.cy.codex.ToolCard
import com.cy.codex.ThreadStatusTone
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Web search, image viewing/generation and sleeps; mirrors `codex-rs/tui/src/history_cell/search.rs`
 * and the image helpers in `patches.rs`. */
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
        title = webSearchTitle(item),
        // No count without results: a payload-less server is not a search that found nothing.
        subtitle = item.results?.takeIf { it.isNotEmpty() }
            ?.let { stringResource(R.string.search_cell_result_count, it.size) },
        modifier = modifier,
    ) {
        when (val body = projectWebSearchBody(item.results)) {
            // `null` is a missing payload (older endpoints, codex-rs/codex-api/src/endpoint/search.rs), not "found nothing".
            WebSearchBody.NoPayload -> Unit
            WebSearchBody.NoResults -> Text(
                text = stringResource(R.string.search_cell_no_results),
                fontSize = emptyFontSize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )

            is WebSearchBody.Rows -> Column(verticalArrangement = Arrangement.spacedBy(resultSpacing)) {
                body.rows.forEach { row -> SearchResultRow(row) }
            }
        }
    }
}

/** What a search cell's body draws: no payload, no results, or one row per result. */
sealed interface WebSearchBody {
    data object NoPayload : WebSearchBody
    data object NoResults : WebSearchBody
    data class Rows(val rows: List<SearchResultRowView>) : WebSearchBody
}

/** Display projection of one web search result row. */
data class SearchResultRowView(
    val title: String,
    val url: String,
    val snippet: String?,
    val typeBadge: String?,
)

/** The only result kind upstream fixtures name (codex-rs/app-server/tests/suite/v2/web_search.rs); the norm earns no badge. */
private const val TextResultType = "text_result"

/**
 * Projects one result element (codex-rs/ext/items/src/web_search.rs) for the row UI; the ref id is
 * the last-resort title so a row kept by ref id alone is never blank.
 */
fun projectSearchResultRow(result: WebSearchResult): SearchResultRowView = SearchResultRowView(
    title = result.title.ifBlank { result.url }.ifBlank { result.refId.orEmpty() },
    url = result.url,
    snippet = result.snippet?.takeIf { it.isNotBlank() },
    typeBadge = result.type?.takeIf { it.isNotBlank() && it != TextResultType },
)

fun projectWebSearchBody(results: List<WebSearchResult>?): WebSearchBody = when {
    results == null -> WebSearchBody.NoPayload
    results.isEmpty() -> WebSearchBody.NoResults
    else -> WebSearchBody.Rows(results.map(::projectSearchResultRow))
}

/** Mirrors `web_search_action_detail` and `WebSearchCell::summary` in
 * `codex-rs/tui/src/history_cell/search.rs`. */
@Composable
@ReadOnlyComposable
private fun webSearchTitle(item: WebSearchItem): String = when (val action = item.action) {
    is WebSearchAction.OpenPage -> action.url?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.search_cell_opened, it) }
        ?: stringResource(R.string.search_cell_opened_page)

    is WebSearchAction.FindInPage -> {
        val pattern = action.pattern?.takeIf { it.isNotBlank() }
        val url = action.url?.takeIf { it.isNotBlank() }
        when {
            pattern != null && url != null ->
                stringResource(R.string.search_cell_find_in_page, pattern, url)
            pattern != null -> stringResource(R.string.search_cell_searched_for, pattern)
            url != null -> stringResource(R.string.search_cell_searched_page, url)
            else -> stringResource(R.string.search_cell_searched_page_no_url)
        }
    }

    is WebSearchAction.Search -> {
        val detail = action.query?.takeIf { it.isNotBlank() }
            ?: action.queries?.joinToString(", ").orEmpty()
        if (detail.isBlank()) {
            stringResource(R.string.search_cell_searched_web)
        } else {
            stringResource(R.string.search_cell_searched_web_for, detail)
        }
    }

    is WebSearchAction.Other, null -> {
        if (item.query.isBlank()) {
            stringResource(R.string.search_cell_searched_web)
        } else {
            stringResource(R.string.search_cell_searched_web_for, item.query)
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
    val tone = imageGenerationTone(item.status)
    CompactLine(
        icon = MiuixIcons.Photos,
        text =
            stringResource(
                if (item.failed) {
                    R.string.search_cell_image_generation_failed
                } else {
                    R.string.search_cell_image_generation
                }
            ),
        detail = item.revisedPrompt?.takeIf { it.isNotBlank() },
        modifier = modifier,
        tone = tone,
        trailing = { StatusChip(label = imageGenerationLabel(item.status), tone = tone) },
    )
    if (item.savedPath != null) {
        CompactLine(
            icon = MiuixIcons.Photos,
            text = stringResource(R.string.search_cell_image_saved_to),
            detail = item.savedPath,
            modifier = modifier,
        )
    }
}

/** Unknown statuses read as running: the server only guarantees `completed` and `failed`
 * (`ext/items/src/image_generation.rs`). */
internal fun imageGenerationTone(status: String): ThreadStatusTone = when (status) {
    ImageGenerationItem.CompletedStatus -> ThreadStatusTone.Done
    ImageGenerationItem.FailedStatus -> ThreadStatusTone.Failed
    else -> ThreadStatusTone.Running
}

@Composable
@ReadOnlyComposable
internal fun imageGenerationLabel(status: String): String = when (status) {
    ImageGenerationItem.CompletedStatus ->
        stringResource(R.string.mcp_cell_dynamic_status_completed)

    ImageGenerationItem.FailedStatus ->
        stringResource(R.string.mcp_cell_dynamic_status_failed)

    else -> stringResource(R.string.mcp_cell_dynamic_status_calling)
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
    row: SearchResultRowView,
    titleFontSize: TextUnit = UiType.SheetRowTitle,
    titleLineHeight: TextUnit = UiType.SheetRowTitleLine,
    urlFontSize: TextUnit = UiType.Meta,
    urlLineHeight: TextUnit = UiType.Message,
    badgeSpacing: Dp = 6.dp,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = row.title,
                modifier = Modifier.weight(1f),
                fontSize = titleFontSize,
                lineHeight = titleLineHeight,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.typeBadge?.let { type ->
                Spacer(Modifier.width(badgeSpacing))
                // The raw discriminator is shown as-is; never prettify a value this client cannot interpret.
                StatusChip(
                    label = stringResource(R.string.search_cell_result_type_unknown, type),
                    tone = ThreadStatusTone.Idle,
                )
            }
        }
        // Blank urls (ref-id-only rows) stay out rather than leaving an empty line.
        if (row.url.isNotBlank()) {
            Text(
                text = row.url,
                fontSize = urlFontSize,
                lineHeight = urlLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.snippet?.let { snippet ->
            Text(
                text = snippet,
                fontSize = urlFontSize,
                lineHeight = urlLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
