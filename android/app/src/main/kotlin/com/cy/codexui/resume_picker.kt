package com.cy.codexui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.ActionRow
import com.cy.codexui.AppEvent
import com.cy.codexui.ButtonRole
import com.cy.codexui.CodexApp
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexButtonSize
import com.cy.codexui.CodexDivider
import com.cy.codexui.R
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.app.FormField
import com.cy.codexui.app.FormSheet
import com.cy.codexui.chatwidget.SidebarModel
import com.cy.codexui.chatwidget.StatusChip
import com.cy.codexui.pressableRow
import com.cy.codexui.protocol.protocol.v2.ThreadSection
import com.cy.codexui.raisedSurface
import com.cy.codexui.status.BackChevron
import com.cy.codexui.statusDotColor
import com.cy.codexui.tone
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Session picker and lifecycle actions.
 *
 * Mirrors `codex-rs/tui/src/resume_picker.rs` and `app/session_picker.rs`: the list of threads the
 * server knows about, with the per-session actions the protocol exposes
 * (`thread/fork`, `thread/archive`, `thread/unarchive`, `thread/name/set`, `thread/delete`).
 */
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
    var renamed by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    // Which section row is being renamed, and what a new section should be called. Both are page
    // state rather than catalog state: they describe an edit in progress, not the server's answer.
    var renamingSection by remember { mutableStateOf<String?>(null) }
    var creatingSection by remember { mutableStateOf(false) }

    val visible = remember(threads.threads, showArchived) {
        threads.threads.filter { showArchived || !it.archived }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SurfaceHeader(
            title = stringResource(R.string.session_list_title),
            subtitle = stringResource(R.string.session_list_subtitle, visible.size),
            leading = {
                BackChevron(
                    onClick = onBack,
                    description = stringResource(R.string.session_list_back),
                )
            },
            trailing = {
                // The chip is a toggle, not a tone: it says which half of the list is on screen, so
                // it takes the button roles instead of a status colour and its dot.
                CodexButton(
                    text = stringResource(
                        if (showArchived) {
                            R.string.session_list_filter_archived
                        } else {
                            R.string.session_list_filter_active
                        },
                    ),
                    onClick = { app.onAppEvent(AppEvent.SetThreadListScope(!showArchived)) },
                    role = if (showArchived) ButtonRole.Primary else ButtonRole.Secondary,
                    size = CodexButtonSize.Compact,
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
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
                        Text(stringResource(R.string.runtime_ready), color = colors.onSurfaceVariantSummary)
                        CodexButton(stringResource(R.string.runtime_new_thread), { app.onAppEvent(AppEvent.NewThread()) })
                    }
                }
            }
            items(visible.size, key = { visible[it].id }) { index ->
                val thread = visible[index]
                SessionCard(
                    title = thread.name ?: thread.id.takeLast(8),
                    preview = thread.preview ?: stringResource(R.string.session_list_no_preview),
                    cwd = thread.cwd,
                    branch = thread.gitBranch,
                    updatedAt = thread.updatedAt,
                    archived = thread.archived,
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
                        app.onAppEvent(AppEvent.ArchiveThread(thread.id, !thread.archived))
                    },
                    onDelete = { app.onAppEvent(AppEvent.DeleteThread(thread.id)) },
                    sections = threads.sections,
                    onMoveToSection = { sectionId ->
                        app.onAppEvent(AppEvent.MoveThreadToSection(thread.id, sectionId))
                    },
                )
            }
        }
    }

    if (creatingSection) {
        FormSheet(
            title = stringResource(R.string.session_list_section_new),
            fields = listOf(
                FormField(
                    key = "name",
                    label = stringResource(R.string.session_list_section_name),
                ),
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(raisedSurface())
            .clickable(onClick = onOpen)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(statusDotSize)
                    .clip(CircleShape)
                    .background(if (archived) colors.onSurfaceVariantSummary.copy(alpha = 0.5f) else statusDotColor(ThreadStatusTone.Idle)),
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
            text = listOfNotNull(
                cwd,
                branch?.let { stringResource(R.string.session_list_branch, it) },
                SidebarModel.relativeTime(updatedAt),
            ).joinToString(stringResource(R.string.session_list_meta_separator)),
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
                label = stringResource(
                    if (archived) R.string.session_list_unarchive else R.string.session_list_archive,
                ),
                onClick = onArchiveToggle,
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
                sections.sortedBy { it.position }.forEach { section ->
                    SessionAction(label = section.name, onClick = { onMoveToSection(section.id) })
                }
                SessionAction(
                    label = stringResource(R.string.session_list_section_none),
                    onClick = { onMoveToSection(null) },
                )
            }
        }
    }
}

/**
 * The sidebar's sections, as a card above the session list.
 *
 * `threadSection/…` is the protocol's own grouping, separate from the directory grouping the
 * sidebar derives from `cwd`: a section is something the *user* filed a thread into, and the two
 * coexist because neither can be computed from the other.
 */
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

    SectionCard(
        title = stringResource(R.string.session_list_sections),
        icon = MiuixIcons.GridView,
        trailing = sections.size.toString(),
    ) {
        sections.sortedBy { it.position }.forEachIndexed { index, section ->
            if (index > 0) CodexDivider()
            if (renaming == section.id) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    modifier = Modifier
                        .fillMaxWidth()
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
        if (sections.isNotEmpty()) CodexDivider()
        ActionRow(
            title = stringResource(R.string.session_list_section_new),
            subtitle = stringResource(R.string.session_list_section_new_detail),
            icon = MiuixIcons.Add,
            onClick = onCreate,
        )
    }
}

/**
 * One lifecycle action of a session card.
 *
 * The label is the whole pill: [CodexButton] has no icon slot, so the four actions say what they do
 * in words instead of carrying a glyph each.
 */
@Composable
private fun SessionAction(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    CodexButton(
        text = label,
        onClick = onClick,
        role = if (destructive) ButtonRole.Destructive else ButtonRole.Secondary,
        size = CodexButtonSize.Compact,
    )
}
