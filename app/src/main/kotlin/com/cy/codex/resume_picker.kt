package com.cy.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.chatwidget.SidebarModel
import com.cy.codex.chatwidget.StatusChip
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadSection
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.status.BackChevron
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** How many lines an expanded card's transcript preview shows; the upstream cap is six. */
internal const val ResumePreviewLineLimit = 6

/** One line of an expanded card's recent transcript, with who said it. */
internal data class ResumePreviewLine(val speaker: ResumePreviewSpeaker, val text: String)

/** Who spoke one [ResumePreviewLine]. */
internal enum class ResumePreviewSpeaker {
    User,
    Assistant,
}

/** `recencyAt` when the server sent it, else [Thread.updatedAt]. */
internal fun threadRecency(thread: Thread): Long = thread.recencyAt ?: thread.updatedAt

/** Case-insensitive match over name, preview, id, branch and cwd, per `Row::matches_query` (codex-rs/tui/src/resume_picker.rs). */
internal fun threadMatchesQuery(thread: Thread, query: String): Boolean {
    if (query.isEmpty()) return true
    return listOfNotNull(thread.name, thread.preview, thread.id, thread.gitInfo?.branch, thread.cwd)
        .any { it.contains(query, ignoreCase = true) }
}

/** Rows the picker shows: query matched locally, newest activity first; stable sort keeps the server's order for ties (ThreadSortKey::UpdatedAt). */
internal fun resumeThreads(threads: List<Thread>, query: String): List<Thread> =
    threads.filter { threadMatchesQuery(it, query) }.sortedByDescending { threadRecency(it) }

/** Newest user/assistant messages, first line each, capped at [limit], per `resume_picker_transcript_preview.rs`. */
internal fun transcriptPreviewLines(
    items: List<ThreadItem>,
    limit: Int = ResumePreviewLineLimit,
): List<ResumePreviewLine> {
    if (limit <= 0) return emptyList()
    return items
        .mapNotNull { item ->
            when (item) {
                is UserMessageItem ->
                    item.content
                        .filterIsInstance<UserInput.Text>()
                        .joinToString(" ") { it.text }
                        .let { previewLine(ResumePreviewSpeaker.User, it) }

                is AgentMessageItem -> previewLine(ResumePreviewSpeaker.Assistant, item.text)
                else -> null
            }
        }
        .takeLast(limit)
}

private fun previewLine(speaker: ResumePreviewSpeaker, text: String): ResumePreviewLine? =
    text
        .lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.let { ResumePreviewLine(speaker, it) }

/** One expanded card's preview read: in flight, the latest answer, or why it has none. */
private sealed interface ResumePreviewState {
    data object Loading : ResumePreviewState

    data object Failed : ResumePreviewState

    data class Loaded(val lines: List<ResumePreviewLine>) : ResumePreviewState
}

/** Session picker and lifecycle actions, mirroring `codex-rs/tui/src/resume_picker.rs` and `app/session_picker.rs`. */
@Composable
fun SessionListScreen(
    app: CodexApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    bottomPadding: Dp = 24.dp,
    rowSpacing: Dp = 6.dp,
) {
    val colors = MiuixTheme.colorScheme
    val threads = app.threads
    val showArchived = threads.includeArchived
    val scope = rememberCoroutineScope()
    var renamed by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var renamingSection by remember { mutableStateOf<String?>(null) }
    var creatingSection by remember { mutableStateOf(false) }

    // The search filters rows already loaded; a keystroke never repeats `thread/list`.
    var query by remember { mutableStateOf("") }

    // The preview cache outlives the expansion: reopening a card must not read the same thread twice.
    var expandedThreadId by remember { mutableStateOf<String?>(null) }
    val previews = remember { mutableStateMapOf<String, ResumePreviewState>() }

    val visible =
        remember(threads.threads, threads.archivedIds, showArchived, query) {
            resumeThreads(
                threads.threads.filter { showArchived || it.id !in threads.archivedIds },
                query,
            )
        }

    fun toggleExpanded(threadId: String) {
        if (expandedThreadId == threadId) {
            expandedThreadId = null
            return
        }
        expandedThreadId = threadId
        if (previews.containsKey(threadId)) return
        previews[threadId] = ResumePreviewState.Loading
        scope.launch {
            previews[threadId] =
                app.client
                    .readThread(ThreadReadParams(threadId))
                    .fold(
                        onSuccess = { ResumePreviewState.Loaded(transcriptPreviewLines(it.items)) },
                        onFailure = { ResumePreviewState.Failed },
                    )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BasicComponent(
            title = stringResource(R.string.session_list_title),
            summary = stringResource(R.string.session_list_subtitle, visible.size),
            startAction = {
                BackChevron(
                    onClick = onBack,
                    description = stringResource(R.string.session_list_back),
                )
            },
            endActions = {
                // The chip is a toggle, not a status tone: it names which half of the list is on screen.
                Button(
                    onClick = { app.onAppEvent(AppEvent.SetThreadListScope(!showArchived)) },
                    modifier = Modifier,
                    enabled = true,
                    colors =
                        if (showArchived) ButtonDefaults.buttonColorsPrimary()
                        else ButtonDefaults.buttonColors(),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (showArchived) {
                                    R.string.session_list_filter_archived
                                } else {
                                    R.string.session_list_filter_active
                                }
                            ),
                        maxLines = 1,
                    )
                }
            },
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            modifier =
                Modifier.padding(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = UiConsts.Space8,
                        bottom = UiConsts.Space6,
                    )
                    .fillMaxWidth(),
            label = stringResource(R.string.resume_picker_search_hint),
            useLabelAsPlaceholder = true,
            singleLine = true,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = bottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            item(key = "sections") {
                SectionsCard(
                    sections = threads.sections,
                    onEvent = app::onAppEvent,
                    renaming = renamingSection,
                    onRenamingChange = { renamingSection = it },
                    onCreate = { creatingSection = true },
                )
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(UiConsts.Space12)) {
                        Text(
                            stringResource(R.string.runtime_ready),
                            color = colors.onSurfaceVariantSummary,
                        )
                        Button(
                            onClick = { app.onAppEvent(AppEvent.NewThread()) },
                            modifier = Modifier,
                            enabled = true,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(text = stringResource(R.string.runtime_new_thread), maxLines = 1)
                        }
                    }
                }
            }
            items(visible.size, key = { visible[it].id }) { index ->
                val thread = visible[index]
                SessionCard(
                    title = thread.name ?: thread.id.takeLast(8),
                    preview =
                        thread.preview.ifBlank { stringResource(R.string.session_list_no_preview) },
                    cwd = thread.cwd,
                    branch = thread.gitInfo?.branch,
                    updatedAt = thread.updatedAt,
                    archived = thread.id in threads.archivedIds,
                    selected = thread.id == app.widget.state.threadId,
                    renameDraft = if (renamed == thread.id) renameDraft else null,
                    onRenameDraft = { renameDraft = it },
                    onOpen = {
                        app.openThread(thread.id)
                        onBack()
                    },
                    onFork = { app.onAppEvent(AppEvent.ForkThread(thread.id)) },
                    onRenameStart = {
                        renamed = thread.id
                        renameDraft = thread.name.orEmpty()
                    },
                    onRenameCommit = {
                        app.onAppEvent(AppEvent.RenameThread(thread.id, renameDraft))
                        renamed = null
                    },
                    onArchiveToggle = {
                        app.onAppEvent(
                            AppEvent.ArchiveThread(thread.id, thread.id !in threads.archivedIds)
                        )
                    },
                    onDelete = { app.onAppEvent(AppEvent.DeleteThread(thread.id)) },
                    sections = threads.sections,
                    onMoveToSection = { sectionId ->
                        app.onAppEvent(AppEvent.MoveThreadToSection(thread.id, sectionId))
                    },
                    expanded = expandedThreadId == thread.id,
                    previewState = previews[thread.id],
                    onToggleExpand = { toggleExpanded(thread.id) },
                )
            }
        }
    }

    if (creatingSection) {
        FormSheet(
            title = stringResource(R.string.session_list_section_new),
            fields =
                listOf(
                    FormField(
                        key = "name",
                        label = stringResource(R.string.session_list_section_name),
                    )
                ),
            confirmLabel = stringResource(R.string.session_list_section_create),
            onDismiss = { creatingSection = false },
            onSubmit = { values ->
                app.onAppEvent(AppEvent.CreateSection(values["name"].orEmpty().trim()))
                creatingSection = false
            },
        )
    }
}

@Composable
private fun SessionCard(
    title: String,
    preview: String,
    cwd: String,
    branch: String?,
    updatedAt: Long,
    archived: Boolean,
    selected: Boolean,
    renameDraft: String?,
    onRenameDraft: (String) -> Unit,
    onOpen: () -> Unit,
    onFork: () -> Unit,
    onRenameStart: () -> Unit,
    onRenameCommit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    sections: List<ThreadSection>,
    onMoveToSection: (String?) -> Unit,
    expanded: Boolean,
    previewState: ResumePreviewState?,
    onToggleExpand: () -> Unit,
    corner: Dp = UiConsts.CornerRow,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 12.dp,
    statusDotSize: Dp = 7.dp,
    statusDotSpacing: Dp = 9.dp,
    titleFontSize: TextUnit = UiType.CardTitle,
    titleLineHeight: TextUnit = UiType.CardTitleLine,
    chipSpacing: Dp = 8.dp,
    titlePreviewSpacing: Dp = 6.dp,
    previewFontSize: TextUnit = UiType.Body,
    previewLineHeight: TextUnit = UiType.SheetTitle,
    previewMetaSpacing: Dp = 8.dp,
    metaFontSize: TextUnit = UiType.Caption,
    metaLineHeight: TextUnit = UiType.SheetRowTitle,
    metaActionsSpacing: Dp = 10.dp,
    actionSpacing: Dp = 7.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember(corner) { RoundedCornerShape(corner) }
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(raisedSurface())
                .clickable(onClick = onOpen)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.size(statusDotSize)
                        .clip(CircleShape)
                        .background(
                            if (archived) colors.onSurfaceVariantSummary.copy(alpha = 0.5f)
                            else statusDotColor(ThreadStatusTone.Idle)
                        )
            )
            Spacer(Modifier.width(statusDotSpacing))
            if (renameDraft != null) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = renameDraft,
                    onValueChange = onRenameDraft,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = titleFontSize,
                    lineHeight = titleLineHeight,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(Modifier.width(chipSpacing))
                StatusChip(
                    label = stringResource(R.string.session_list_current),
                    tone = ThreadStatusTone.Running,
                )
            }
            if (archived) {
                Spacer(Modifier.width(chipSpacing))
                StatusChip(
                    label = stringResource(R.string.session_list_archived),
                    tone = ThreadStatusTone.Idle,
                )
            }
        }
        Spacer(Modifier.height(titlePreviewSpacing))
        Text(
            text = preview,
            fontSize = previewFontSize,
            lineHeight = previewLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(previewMetaSpacing))
        Text(
            text =
                listOfNotNull(
                        cwd,
                        branch?.let { stringResource(R.string.session_list_branch, it) },
                        SidebarModel.relativeTime(updatedAt),
                    )
                    .joinToString(stringResource(R.string.session_list_meta_separator)),
            fontSize = metaFontSize,
            lineHeight = metaLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(metaActionsSpacing))
        Row(horizontalArrangement = Arrangement.spacedBy(actionSpacing)) {
            if (renameDraft != null) {
                SessionAction(stringResource(R.string.session_list_save), onRenameCommit)
            } else {
                SessionAction(stringResource(R.string.session_list_rename), onRenameStart)
            }
            SessionAction(stringResource(R.string.session_list_fork), onFork)
            SessionAction(
                label =
                    stringResource(
                        if (archived) R.string.session_list_unarchive
                        else R.string.session_list_archive
                    ),
                onClick = onArchiveToggle,
            )
            SessionAction(
                label =
                    stringResource(
                        if (expanded) R.string.resume_picker_collapse
                        else R.string.resume_picker_expand
                    ),
                onClick = onToggleExpand,
            )
        }
        if (sections.isNotEmpty()) {
            Spacer(Modifier.height(metaActionsSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(actionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.session_list_section_move),
                    fontSize = metaFontSize,
                    lineHeight = metaLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                sections.forEach { section ->
                    SessionAction(label = section.name, onClick = { onMoveToSection(section.id) })
                }
                SessionAction(
                    label = stringResource(R.string.session_list_section_none),
                    onClick = { onMoveToSection(null) },
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(metaActionsSpacing))
            TranscriptPreview(previewState)
        }
    }
}

@Composable
private fun TranscriptPreview(state: ResumePreviewState?) {
    when (state) {
        null,
        ResumePreviewState.Loading ->
            PreviewLine(text = stringResource(R.string.resume_picker_preview_loading))

        ResumePreviewState.Failed ->
            PreviewLine(
                text = stringResource(R.string.resume_picker_preview_failed),
                error = true,
            )

        is ResumePreviewState.Loaded ->
            if (state.lines.isEmpty()) {
                PreviewLine(text = stringResource(R.string.resume_picker_preview_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(UiConsts.Space2)) {
                    state.lines.forEach { line ->
                        PreviewLine(
                            text =
                                when (line.speaker) {
                                    ResumePreviewSpeaker.User ->
                                        stringResource(
                                            R.string.resume_picker_preview_user,
                                            line.text,
                                        )

                                    ResumePreviewSpeaker.Assistant ->
                                        stringResource(
                                            R.string.resume_picker_preview_assistant,
                                            line.text,
                                        )
                                }
                        )
                    }
                }
            }
    }
}

@Composable
private fun PreviewLine(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        color = if (error) colors.error else colors.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** `threadSection/…` grouping, distinct from the sidebar's cwd-derived directory grouping. */
@Composable
private fun SectionsCard(
    sections: List<ThreadSection>,
    onEvent: (AppEvent) -> Unit,
    renaming: String?,
    onRenamingChange: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var draft by remember(renaming) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.session_list_sections),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = sections.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        sections.forEachIndexed { index, section ->
            if (index > 0)
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            if (renaming == section.id) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    SessionAction(
                        label = stringResource(R.string.session_list_save),
                        onClick = {
                            onEvent(AppEvent.RenameSection(section.id, draft.trim()))
                            onRenamingChange(null)
                        },
                    )
                    SessionAction(
                        label = stringResource(R.string.session_list_section_cancel),
                        onClick = { onRenamingChange(null) },
                    )
                }
            } else {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = section.name,
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SessionAction(
                        label = stringResource(R.string.session_list_rename),
                        onClick = {
                            draft = section.name
                            onRenamingChange(section.id)
                        },
                    )
                    SessionAction(
                        label = stringResource(R.string.session_list_delete),
                        onClick = { onEvent(AppEvent.DeleteSection(section.id)) },
                        destructive = true,
                    )
                }
            }
        }
        ArrowPreference(
            title = stringResource(R.string.session_list_section_new),
            summary = stringResource(R.string.session_list_section_new_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint =
                        if (true) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.disabledOnSurface,
                )
            },
            onClick = onCreate,
        )
    }
}

/** The label is the whole pill: the button has no icon slot. */
@Composable
private fun SessionAction(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier,
        enabled = true,
        colors =
            if (destructive)
                ButtonDefaults.buttonColors(
                    color = Color.Transparent,
                    contentColor = MiuixTheme.colorScheme.error,
                )
            else ButtonDefaults.buttonColors(),
    ) {
        Text(text = label, maxLines = 1)
    }
}
