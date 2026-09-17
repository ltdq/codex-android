package com.cy.codexui.bottom_pane

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.UiType
import com.cy.codexui.fileName
import com.cy.codexui.parentPath
import com.cy.codexui.pressableRow
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.raisedSurface
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `@`-mention file search popup.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/file_search_popup.rs`: candidates arrive already ranked by
 * the server and are only narrowed here, so the popup shows the file name and its folder — the two
 * halves of a path a phone screen can actually fit.
 */

/** Room the empty state keeps inside the popup. */
private val EmptyPaddingHorizontal = 11.dp
private val EmptyPaddingVertical = 12.dp
private val EmptyFontSize = UiType.Body
private val EmptyLineHeight = UiType.MetaLine

/** Room one candidate row keeps inside itself. */
private val RowPaddingHorizontal = 11.dp
private val RowPaddingVertical = 6.dp

/** Type of the file name, the gap before its folder, and the type of the folder. */
private val NameFontSize = UiType.Subtitle
private val NameLineHeight = UiType.RowTitleLine
private val ParentGap = 8.dp
private val ParentFontSize = UiType.Meta
private val ParentLineHeight = UiType.MetaLine

/**
 * A server-side fuzzy search, as the three calls the protocol spreads it over.
 *
 * `fuzzyFileSearch/sessionStart`, `sessionUpdate` and `sessionStop` are one logical operation whose
 * pieces have to be kept together: a session that is never stopped keeps the server indexing for a
 * popup that is gone, and an update for a session that was never started is an unknown-id no-op that
 * looks exactly like a search that found nothing. Bundling them means a caller cannot get the order
 * wrong.
 */
class FuzzySearchSession(
    val client: AppServerClient,
    val sessionId: String,
    /** Directories the search is rooted at; empty means the session's own working directory. */
    val roots: List<String> = emptyList(),
)

/**
 * `@`-mention file search, backed by the server when a [session] is supplied.
 *
 * Without one it narrows [candidates] locally, which is what the mention popup wants: that list
 * arrived with the thread. With one, the ranking is the server's — it is the only side that can see
 * the filesystem — and the local filter is skipped rather than layered on top, because two rankers
 * disagreeing produces a list that is neither.
 */
@Composable
fun FileSearchPopup(
    query: String,
    candidates: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 6,
    session: FuzzySearchSession? = null,
) {
    var remote by remember(session) { mutableStateOf<List<String>?>(null) }
    val local = remember(query, candidates, maxRows) {
        filterPaths(query, candidates).take(maxRows.coerceAtLeast(1))
    }
    if (session != null) {
        // One effect owns the session's whole life. The `finally` is what closes it: a plain
        // `DisposableEffect` cannot, because the scope it would launch on is cancelled at exactly
        // the moment the popup leaves, so the stop would never reach the server. `NonCancellable`
        // is what lets the closing call finish inside a cancelled coroutine.
        LaunchedEffect(session.sessionId) {
            session.client.startFuzzySearchSession(session.sessionId, session.roots)
            try {
                // The one-shot call fills the popup before the user types: an update needs a query,
                // and an empty popup while the index warms up reads as "no files here".
                session.client.fuzzyFileSearch("", session.roots).onSuccess { hits ->
                    remote = hits.map { it.path }
                }
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    session.client.stopFuzzySearchSession(session.sessionId)
                }
            }
        }
        LaunchedEffect(session.sessionId, query) {
            session.client.updateFuzzySearchSession(session.sessionId, query)
        }
    }
    val matches = (remote ?: local).take(maxRows.coerceAtLeast(1))
    val emptyPrompt = if (session != null) {
        stringResource(R.string.file_search_popup_searching)
    } else if (query.isBlank()) {
        stringResource(R.string.file_search_popup_prompt)
    } else {
        stringResource(R.string.file_search_popup_empty)
    }
    PopupShell(modifier = modifier) {
        if (matches.isEmpty()) {
            Text(
                text = emptyPrompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = EmptyPaddingHorizontal,
                        vertical = EmptyPaddingVertical,
                    ),
                fontSize = EmptyFontSize,
                lineHeight = EmptyLineHeight,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        matches.forEachIndexed { index, path ->
            val rowShape = RoundedCornerShape(PopupRowCorner)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressableRow(
                        shape = rowShape,
                        container = if (index == 0) {
                            MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            raisedSurface()
                        },
                        onClick = { onPick(path) },
                    )
                    .padding(
                        horizontal = RowPaddingHorizontal,
                        vertical = RowPaddingVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName(path),
                    modifier = Modifier.weight(1f),
                    fontSize = NameFontSize,
                    lineHeight = NameLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val parent = parentPath(path)
                if (parent.isNotEmpty()) {
                    Spacer(Modifier.width(ParentGap))
                    Text(
                        text = parent,
                        modifier = Modifier.weight(1f),
                        fontSize = ParentFontSize,
                        lineHeight = ParentLineHeight,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Narrow [candidates] by [query].
 *
 * A subsequence hit (`cs` matches `CodexScreen.kt`) is what the TUI's fuzzy matcher rewards, so
 * those come first; a plain case-insensitive `contains` pass follows so a query whose characters
 * are not in order still finds something instead of showing an empty popup.
 */
internal fun filterPaths(query: String, candidates: List<String>): List<String> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return candidates
    val subsequence = mutableListOf<String>()
    val contains = mutableListOf<String>()
    for (candidate in candidates) {
        val haystack = candidate.lowercase()
        when {
            isSubsequence(needle, haystack) -> subsequence += candidate
            haystack.contains(needle) -> contains += candidate
        }
    }
    return subsequence + contains
}

private fun isSubsequence(needle: String, haystack: String): Boolean {
    var index = 0
    for (char in haystack) {
        if (index < needle.length && char == needle[index]) index++
        if (index == needle.length) return true
    }
    return index == needle.length
}
