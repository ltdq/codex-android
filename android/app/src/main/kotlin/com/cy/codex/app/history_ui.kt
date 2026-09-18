package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.ActionRow
import com.cy.codex.ButtonRole
import com.cy.codex.CodexButton
import com.cy.codex.CodexButtonSize
import com.cy.codex.CodexDivider
import com.cy.codex.CodexTextField
import com.cy.codex.EmptyState
import com.cy.codex.R
import com.cy.codex.SectionCard
import com.cy.codex.SurfaceBackButton
import com.cy.codex.SurfaceHeader
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.label
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.OccurrenceMatch
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.ImageViewItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.SleepItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.TimelineEntry
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.UserInput
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Rows per page: small enough that the first one lands while the page is still moving. */
private const val PageLimit = 25

/** Rows asked for the timeline index. See [fetchPage] for why this is the only timeline page. */
private const val TimelineIndexLimit = 100

/**
 * One closed thread's history, as a browser rather than as a transcript.
 *
 * Mirrors `codex-rs/tui/src/app/history_ui.rs` together with `app_server_session/history.rs`. A
 * thread that is no longer loaded has nothing to open: the only way back into its past is
 * `thread/search`, and everything after that is a read of one of the paging families. This page is
 * that sequence, in the order the TUI asks for it — find the thread, search inside it, look at the
 * sparse timeline that backs the scrubber, then page the listing one page at a time.
 *
 * It is deliberately *not* the transcript. The cells in the `history_cell` package fold deltas, own
 * their collapsed state and assume an item is on screen once, while this page shows items that are
 * already complete and may be shown again beside a search hit that points into them. So every row
 * here is one line — an id, a command, a first line of prose — which is the shape a *browser* of
 * history needs and the shape a transcript cell cannot be reduced to.
 *
 * Two things this page cannot do, and therefore does not pretend to do:
 * - A `thread/searchOccurrences` hit is display-only. No call in the protocol scrolls a transcript
 *   to an item id, and there is no transcript here to scroll, so a hit row states the item it was
 *   found in and the offset inside it and stops there.
 * - The timeline is read as a single index page. `thread/timeline/list` answers with entries and no
 *   cursor, so only items and turns can be continued past their first page.
 *
 * @param client the connection the reads go through; nothing is read until the user searches or
 *   picks a thread.
 * @param onBack invoked by the header's back chevron.
 * @param modifier layout modifier for the pushed page.
 */
@Composable
fun ThreadHistoryScreen(
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()

    // The thread search: null means "not searched yet", which is a different page from "no match".
    var term by remember { mutableStateOf("") }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Thread>?>(null) }

    // The thread under inspection. Archived threads are searchable on purpose: a closed thread is
    // exactly what this page is for, and archiving is how a closed thread is usually put away.
    var selected by remember { mutableStateOf<Thread?>(null) }
    val threadId = selected?.id

    var occurrenceTerm by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<OccurrenceMatch>?>(null) }

    var index by remember { mutableStateOf<List<TimelineEntry>?>(null) }

    var tab by remember { mutableStateOf(HistoryTab.Items) }
    var rows by remember { mutableStateOf<List<HistoryPageRow>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // One failure line for the whole page. Every read either clears it or replaces it, so what is
    // shown is always the outcome of the last call the user asked for.
    var failed by remember { mutableStateOf<String?>(null) }

    fun findThreads() {
        val words = term.trim()
        if (words.isEmpty()) return
        scope.launch {
            finding = true
            client.searchThreads(words, includeArchived = true)
                .onSuccess {
                    found = it.threads
                    failed = null
                }
                .onFailure {
                    found = emptyList()
                    failed = it.message
                }
            finding = false
        }
    }

    fun scanSelected() {
        val id = threadId ?: return
        val words = occurrenceTerm.trim()
        if (words.isEmpty()) return
        scope.launch {
            scanning = true
            client.searchThreadOccurrences(id, words)
                .onSuccess {
                    hits = it
                    failed = null
                }
                .onFailure {
                    hits = emptyList()
                    failed = it.message
                }
            scanning = false
        }
    }

    fun loadMore() {
        val id = threadId ?: return
        val next = cursor ?: return
        if (loading) return
        scope.launch {
            loading = true
            fetchPage(client, id, tab, next)
                .onSuccess {
                    rows = rows + it.rows
                    cursor = it.nextCursor
                    failed = null
                }
                .onFailure { failed = it.message }
            loading = false
        }
    }

    // Everything scoped to one thread is cleared and re-read here. One effect rather than two keyed
    // on the same id, because that would be two places to forget a reset — and a hit or an index
    // entry from the previous thread shown under the new one is the kind of wrong that looks right.
    LaunchedEffect(threadId) {
        hits = null
        index = null
        if (threadId == null) return@LaunchedEffect
        client.listThreadTimeline(threadId, null, TimelineIndexLimit)
            .onSuccess {
                index = it
                failed = null
            }
            .onFailure { failed = it.message }
    }

    // Only the chosen tab is read. The first page is read from here rather than from the tab
    // handler because switching tabs and switching threads are the same event for this list, and an
    // effect keyed on both is what keeps a late answer from one listing out of another.
    LaunchedEffect(threadId, tab) {
        rows = emptyList()
        cursor = null
        loaded = false
        if (threadId == null) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        fetchPage(client, threadId, tab, null)
            .onSuccess {
                rows = it.rows
                cursor = it.nextCursor
                loaded = true
                failed = null
            }
            .onFailure { failed = it.message }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SurfaceHeader(
            title = stringResource(R.string.history_ui_title),
            subtitle = selected?.let { it.name ?: it.preview.ifBlank { it.id } }
                ?: stringResource(R.string.history_ui_no_thread),
            leading = { SurfaceBackButton(stringResource(R.string.history_ui_back), onBack) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            if (failed != null) {
                SectionCard(
                    title = stringResource(R.string.history_ui_failed),
                    icon = MiuixIcons.Info,
                ) {
                    Text(
                        text = failed.orEmpty(),
                        modifier = Modifier
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                }
            }
            SectionCard(
                title = stringResource(R.string.history_ui_find),
                icon = MiuixIcons.Search,
                trailing = found?.size?.toString(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CodexTextField(
                        value = term,
                        onValueChange = { term = it },
                        modifier = Modifier.weight(1f),
                        placeholder = stringResource(R.string.history_ui_find_placeholder),
                        onImeAction = { findThreads() },
                    )
                    Spacer(Modifier.width(UiConsts.Space8))
                    CodexButton(
                        text = stringResource(R.string.history_ui_find_action),
                        onClick = { findThreads() },
                        role = ButtonRole.Secondary,
                        size = CodexButtonSize.Compact,
                        enabled = term.isNotBlank() && !finding,
                    )
                }
                val matches = found
                Spacer(Modifier.height(UiConsts.Space4))
                when {
                    matches == null -> EmptyState(
                        icon = MiuixIcons.Search,
                        title = stringResource(R.string.history_ui_no_search),
                        detail = stringResource(R.string.history_ui_no_search_detail),
                    )

                    matches.isEmpty() -> EmptyState(
                        icon = MiuixIcons.Search,
                        title = stringResource(R.string.history_ui_no_results),
                        detail = stringResource(R.string.history_ui_no_results_detail),
                    )

                    else -> matches.forEachIndexed { position, thread ->
                        if (position > 0) CodexDivider()
                        ActionRow(
                            title = thread.name ?: thread.preview.ifBlank { thread.id },
                            subtitle = thread.cwd.ifEmpty { null },
                            trailing = updatedAtLabel(thread.updatedAt),
                            tint = if (thread.id == threadId) colors.primary else null,
                            onClick = { selected = thread },
                        )
                    }
                }
            }
            if (threadId != null) {
                SectionCard(
                    title = stringResource(R.string.history_ui_scan),
                    icon = MiuixIcons.Notes,
                    trailing = hits?.size?.toString(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CodexTextField(
                            value = occurrenceTerm,
                            onValueChange = { occurrenceTerm = it },
                            modifier = Modifier.weight(1f),
                            placeholder = stringResource(R.string.history_ui_scan_placeholder),
                            onImeAction = { scanSelected() },
                        )
                        Spacer(Modifier.width(UiConsts.Space8))
                        CodexButton(
                            text = stringResource(R.string.history_ui_scan_action),
                            onClick = { scanSelected() },
                            role = ButtonRole.Secondary,
                            size = CodexButtonSize.Compact,
                            enabled = occurrenceTerm.isNotBlank() && !scanning,
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_ui_hit_note),
                        modifier = Modifier
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space4),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                    val matches = hits
                    when {
                        matches == null -> EmptyState(
                            icon = MiuixIcons.Notes,
                            title = stringResource(R.string.history_ui_no_scan),
                            detail = stringResource(R.string.history_ui_no_scan_detail),
                        )

                        matches.isEmpty() -> EmptyState(
                            icon = MiuixIcons.Notes,
                            title = stringResource(R.string.history_ui_no_results),
                            detail = stringResource(R.string.history_ui_no_results_detail),
                        )

                        else -> matches.forEachIndexed { position, hit ->
                            if (position > 0) CodexDivider()
                            OccurrenceRow(hit)
                        }
                    }
                }
                SectionCard(
                    title = stringResource(R.string.history_ui_index),
                    icon = MiuixIcons.Timer,
                    trailing = index?.size?.toString(),
                ) {
                    Text(
                        text = stringResource(R.string.history_ui_index_note),
                        modifier = Modifier
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space4),
                        fontSize = UiType.Footnote,
                        lineHeight = UiType.FootnoteLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                    val entries = index
                    when {
                        entries == null -> LoadingRow()

                        entries.isEmpty() -> EmptyState(
                            icon = MiuixIcons.Timer,
                            title = stringResource(R.string.history_ui_index_empty),
                            detail = stringResource(R.string.history_ui_index_empty_detail),
                        )

                        else -> entries.forEachIndexed { position, entry ->
                            if (position > 0) CodexDivider()
                            HistoryRow(
                                summary = timelineSummary(entry),
                                label = entry.label(),
                                detail = timelineDetail(entry),
                            )
                        }
                    }
                }
                SectionCard(
                    title = stringResource(R.string.history_ui_pages),
                    icon = MiuixIcons.Messages,
                    trailing = if (loaded) rows.size.toString() else null,
                ) {
                    HistoryTabs(tab = tab, onSelect = { tab = it })
                    Spacer(Modifier.height(UiConsts.Space8))
                    when {
                        loading && rows.isEmpty() -> LoadingRow()

                        loaded && rows.isEmpty() -> EmptyState(
                            icon = MiuixIcons.Messages,
                            title = stringResource(R.string.history_ui_page_empty),
                            detail = stringResource(R.string.history_ui_page_empty_detail),
                        )

                        else -> rows.forEachIndexed { position, row ->
                            if (position > 0) CodexDivider()
                            when (row) {
                                is HistoryPageRow.Item -> HistoryRow(
                                    summary = itemSummary(row.item),
                                )

                                is HistoryPageRow.TurnRow -> HistoryRow(
                                    summary = row.turn.id,
                                    detail = stringResource(
                                        R.string.history_ui_turn_detail,
                                        row.turn.status.label(),
                                        row.turn.items.size,
                                    ),
                                )

                                is HistoryPageRow.Timeline -> HistoryRow(
                                    summary = timelineSummary(row.entry),
                                    label = row.entry.label(),
                                    detail = timelineDetail(row.entry),
                                )
                            }
                        }
                    }
                    if (loading && rows.isNotEmpty()) {
                        CodexDivider()
                        LoadingRow()
                    }
                    // Offered only while the server named a cursor; a page already in flight
                    // disables it as well, so a second tap cannot append the same page twice.
                    if (cursor != null) {
                        Spacer(Modifier.height(UiConsts.Space8))
                        CodexButton(
                            text = stringResource(R.string.history_ui_load_more),
                            onClick = { loadMore() },
                            modifier = Modifier.fillMaxWidth(),
                            role = ButtonRole.Secondary,
                            enabled = !loading,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which of the three listings the paged card is showing.
 *
 * The three are three different calls, not three filters over one answer, so this choice decides
 * what gets *read* — which is why only the chosen one is ever requested.
 */
private enum class HistoryTab {
    /** One page of `thread/items/list`. */
    Items,

    /** One page of `thread/turns/list`. */
    Turns,

    /** One page of `thread/timeline/list`; the one listing whose answer carries no cursor. */
    Timeline,
}

/** Tab name, resolved where resources are available: the tab row is a composable. */
@Composable
@ReadOnlyComposable
private fun HistoryTab.label(): String = stringResource(
    when (this) {
        HistoryTab.Items -> R.string.history_ui_tab_items
        HistoryTab.Turns -> R.string.history_ui_tab_turns
        HistoryTab.Timeline -> R.string.history_ui_tab_timeline
    },
)

/**
 * One row of a page, still in the shape the protocol sent it.
 *
 * A page keeps records rather than pre-rendered strings because two of the three listings resolve a
 * label out of resources (the timeline kind and the turn status), and a label resolved outside
 * composition would be resolved for the wrong locale.
 */
private sealed interface HistoryPageRow {
    /** One `ThreadItem` from `thread/items/list`. */
    data class Item(val item: ThreadItem) : HistoryPageRow

    /** One `Turn` from `thread/turns/list`. */
    data class TurnRow(val turn: Turn) : HistoryPageRow

    /** One `TimelineEntry` from `thread/timeline/list`. */
    data class Timeline(val entry: TimelineEntry) : HistoryPageRow
}

/**
 * One page of the chosen listing: its rows, plus the cursor the next page starts from.
 *
 * A null cursor means both "this was the last page" and "this family sends no cursor"; the two are
 * one thing to a load-more affordance, and pretending otherwise would put a button on screen that
 * can never be enabled.
 */
private data class HistoryPage(
    val rows: List<HistoryPageRow>,
    val nextCursor: String?,
)

/**
 * Read one page of [tab] and normalise it into a [HistoryPage].
 *
 * One function rather than three call sites because the three families answer in three envelopes —
 * two pages and a bare list — and this is the single place that difference is flattened.
 *
 * `thread/timeline/list` answers with entries only: the client drops the cursor the wire response
 * carries, so a timeline page can never ask for the next one and the normalised cursor is null. The
 * other two families pass their cursor straight through.
 */
private suspend fun fetchPage(
    client: AppServerClient,
    threadId: String,
    tab: HistoryTab,
    cursor: String?,
): Result<HistoryPage> = when (tab) {
    HistoryTab.Items -> client.listThreadItems(com.cy.codex.protocol.protocol.v2.ThreadItemsListParams(threadId, cursor, PageLimit)).map { page ->
        HistoryPage(page.items.map { HistoryPageRow.Item(it) }, page.nextCursor)
    }

    HistoryTab.Turns -> client.listThreadTurns(com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams(threadId, cursor, limit = PageLimit)).map { page ->
        HistoryPage(page.turns.map { HistoryPageRow.TurnRow(it) }, page.nextCursor)
    }

    HistoryTab.Timeline -> client.listThreadTimeline(threadId, cursor, PageLimit).map { entries ->
        HistoryPage(entries.map { HistoryPageRow.Timeline(it) }, null)
    }
}

/**
 * One line of monospace for one item.
 *
 * This is the page's whole contract with an item: what it is and where it starts. What the item
 * *said* is a transcript's job — the cells in `history_cell` fold deltas into it and hold their own
 * expanded state — while a browser needs a line per row that can be scanned and compared, so the
 * summary is the first non-blank line of the item's payload and falls back to its id when the item
 * carries no text of its own.
 *
 * Exhaustive over `ThreadItem` on purpose: a new variant should stop this file from compiling
 * rather than appear as a blank row nobody notices.
 */
@Composable
@ReadOnlyComposable
private fun itemSummary(item: ThreadItem): String = when (item) {
    is UserMessageItem -> item.content.firstNotNullOfOrNull { it.summaryLine() } ?: item.id
    is HookPromptItem -> firstLine(item.fragments.firstOrNull()?.text) ?: item.id
    is AgentMessageItem -> firstLine(item.text) ?: item.id
    is FunctionCallOutputItem -> firstLine(item.output) ?: item.name
    is PlanItem -> firstLine(item.text) ?: item.id
    is ReasoningItem -> firstLine(item.title) ?: item.id
    is CommandExecutionItem -> firstLine(item.command) ?: item.id
    is FileChangeItem -> fileChangeSummary(item)
    is McpToolCallItem -> qualified(item.server, item.tool) ?: item.id
    is DynamicToolCallItem -> qualified(item.namespace, item.tool) ?: item.id
    is CollabAgentToolCallItem -> firstLine(item.prompt)?.let { "${item.tool.label()} $it" }
        ?: item.tool.label()

    is SubAgentActivityItem -> firstLine(item.agentPath) ?: item.id
    is WebSearchItem -> firstLine(item.query) ?: item.id
    is ImageViewItem -> firstLine(item.path) ?: item.id
    is SleepItem -> stringResource(R.string.history_ui_item_sleep, item.durationMs)
    is ImageGenerationItem -> firstLine(item.prompt) ?: item.id
    is EnteredReviewModeItem -> firstLine(item.review) ?: item.id
    is ExitedReviewModeItem -> firstLine(item.review) ?: item.id
    is ContextCompactionItem -> stringResource(R.string.history_ui_item_compaction)
    is com.cy.codex.protocol.protocol.item.TurnSeparatorItem -> item.label
    is com.cy.codex.protocol.protocol.item.RecapItem ->
        item.text ?: stringResource(R.string.recap_cell_title)

    is com.cy.codex.protocol.protocol.item.TipItem -> firstLine(item.text) ?: item.id
}

/**
 * The first file a patch touched, with the count when it touched more than one.
 *
 * A patch is the one item whose payload is a set rather than a string, and a row that showed only
 * the first path would read as a single-file change; the count is what keeps the summary honest.
 */
@Composable
@ReadOnlyComposable
private fun fileChangeSummary(item: FileChangeItem): String {
    val first = firstLine(item.changes.firstOrNull()?.path) ?: return item.id
    return if (item.changes.size > 1) {
        stringResource(R.string.history_ui_item_files, first, item.changes.size)
    } else {
        first
    }
}

/** `namespace/tool` from whichever halves an item named, or null when it named neither. */
private fun qualified(namespace: String?, name: String): String? = listOfNotNull(namespace, name)
    .filter { it.isNotBlank() }
    .joinToString("/")

/**
 * The identifying string of a timeline entry: the item's id, or the turn boundary's turn id.
 *
 * The upstream entry has no id field of its own — the union's variants each name what they are
 * about — so the row's summary is derived from whichever variant arrived.
 */
@Composable
@ReadOnlyComposable
private fun timelineSummary(entry: TimelineEntry): String = when (entry) {
    is TimelineEntry.Item -> itemSummary(entry.item)
    is TimelineEntry.Realtime -> entry.item.id
    is TimelineEntry.TurnStarted -> entry.turnId
    is TimelineEntry.TurnCompleted -> entry.turnId
}

/**
 * The detail line of a timeline entry, or `null` when the variant carries nothing more to say.
 *
 * A transcript turn is the unit a reader scans for, so a completed boundary reports how it ended
 * and how long it took; an in-progress item entry has no such summary and stays a single line.
 */
@Composable
@ReadOnlyComposable
private fun timelineDetail(entry: TimelineEntry): String? = when (entry) {
    is TimelineEntry.Item -> null
    is TimelineEntry.Realtime -> entry.item.text?.takeIf { it.isNotBlank() } ?: entry.item.type
    is TimelineEntry.TurnStarted -> null
    is TimelineEntry.TurnCompleted -> stringResource(
        R.string.history_ui_timeline_turn_detail,
        entry.status.label(),
        entry.durationMs ?: 0L,
    )
}

/** The one string an input carries, or null when it carries none of its own. */
private fun UserInput.summaryLine(): String? = when (this) {
    is UserInput.Text -> text
    is UserInput.Image -> url
    is UserInput.LocalImage -> path
    is UserInput.Audio -> url
    is UserInput.LocalAudio -> path
    is UserInput.Skill -> name
    is UserInput.Mention -> path
}

/**
 * The first non-blank line of [text], trimmed; null when there is nothing to show.
 *
 * A row shows one line, and clipping at the row instead would cut the payload wherever the width
 * happened to fall — usually somewhere inside a path. Taking the first line means the summary is
 * the beginning of what the item said rather than an arbitrary slice of it.
 */
private fun firstLine(text: String?): String? =
    text?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() }

/**
 * A thread's `updatedAt` as a local timestamp, or null when the server sent no timestamp.
 *
 * Zero is the protocol's "unknown", and formatting it would print 1970 in the row, which the user
 * would read as a real date. The pattern lives in a resource because the order of its parts is a
 * locale decision even when every part is numeric.
 */
@Composable
@ReadOnlyComposable
private fun updatedAtLabel(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    val pattern = stringResource(R.string.history_ui_updated_format)
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/**
 * One row of a listing: the payload in monospace, with an optional word in front of it and an
 * optional line under it.
 *
 * The three listings show three different records — an item, a turn, a timeline entry — but they
 * are all "one line of a page" to the reader, and one row is what keeps the three tabs from
 * drifting into three typographic treatments of the same thing. The monospace is not decoration:
 * an id or a command is something the user may have to read character by character, and the code
 * surface behind it is the app's existing mark for exactly that.
 *
 * @param summary the payload, always one line.
 * @param modifier layout modifier for the row.
 * @param label a short word naming the record kind, for listings whose records are not uniform.
 * @param detail a second, quieter line: the server's own caption, or a state the record carries.
 */
@Composable
private fun HistoryRow(
    summary: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    detail: String? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) {
                Text(
                    text = label,
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(UiConsts.Space8))
            }
            Text(
                text = summary,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(UiConsts.CornerChip))
                    .background(codeSurface())
                    .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space3),
                fontSize = UiType.Value,
                lineHeight = UiType.ValueLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (detail != null) {
            Spacer(Modifier.height(UiConsts.Space3))
            Text(
                text = detail,
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One `thread/searchOccurrences` hit.
 *
 * Not an [ActionRow]: every action row in this app ends in a chevron and means "this opens
 * something", and a hit opens nothing — no call in the protocol scrolls a transcript to an item id,
 * and this page has no transcript to scroll. So the row states the three facts the server sent
 * instead: what the match says, how far into the item it was found, and which item that was, in the
 * monospace a future jump would need to address.
 */
@Composable
private fun OccurrenceRow(hit: OccurrenceMatch) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hit.snippet.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.history_ui_hit_no_snippet),
                modifier = Modifier.weight(1f),
                fontSize = UiType.Detail,
                lineHeight = UiType.DetailLine,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(UiConsts.Space8))
            Text(
                text = stringResource(R.string.history_ui_hit_offset, hit.start),
                fontSize = UiType.Caption,
                lineHeight = UiType.CaptionLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(UiConsts.Space3))
        Text(
            text = hit.itemId,
            modifier = Modifier
                .clip(RoundedCornerShape(UiConsts.CornerChip))
                .background(codeSurface())
                .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2),
            fontSize = UiType.Caption,
            lineHeight = UiType.CaptionLine,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The page-in-flight row.
 *
 * No spinner: this list is about to grow, and a rotation would take the place of the rows already
 * on screen. A line that says what is happening keeps the page still and the reading position
 * intact.
 */
@Composable
private fun LoadingRow() {
    Text(
        text = stringResource(R.string.history_ui_loading),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * The segmented choice between the three listings.
 *
 * Three compact pills rather than a tab strip: the app has no tab component, and the pills already
 * carry its selected treatment — a role in the button hierarchy rather than a second set of colours
 * invented here. The chosen tab is the filled one, because filled means "the one that was picked".
 */
@Composable
private fun HistoryTabs(tab: HistoryTab, onSelect: (HistoryTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        HistoryTab.entries.forEach { candidate ->
            CodexButton(
                text = candidate.label(),
                onClick = { onSelect(candidate) },
                modifier = Modifier.weight(1f),
                role = if (candidate == tab) ButtonRole.Primary else ButtonRole.Secondary,
                size = CodexButtonSize.Compact,
            )
        }
    }
}
