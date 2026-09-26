package com.cy.codex.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The agent roster and the agent picker; the roster is derived from the parent transcript,
 * as subagents have no thread of their own.
 */

enum class AgentRole {
    Main,
    Sub;

    /** Collab-side tag; composable because the roster rows that read it are composables. */
    val tag: String
        @Composable
        @ReadOnlyComposable
        get() =
            when (this) {
                Main -> stringResource(R.string.agents_overview_role_main)
                Sub -> stringResource(R.string.agents_overview_role_sub)
            }
}

data class AgentRosterEntry(
    val threadId: String,
    val name: String,
    val role: AgentRole,
    val status: AgentRunStatus?,
    val activity: SubAgentActivityKind?,
    val task: String?,
    val model: String?,
    val effort: ReasoningEffort?,
    val tokens: Int = 0,
    val itemId: String?,
    val threadStatus: ThreadStatus? = null,
)

/** Roster fold: main first, subagents in first-appearance order, later items winning per field. */
fun deriveAgentRoster(
    items: List<ThreadItem>,
    mainThreadId: String,
    mainLabel: String,
    subAgentNameFormat: String,
): List<AgentRosterEntry> {
    val subagents = LinkedHashMap<String, AgentRosterEntry>()
    items.forEach { item ->
        when (item) {
            is CollabAgentToolCallItem ->
                item.receiverThreadIds.forEach { threadId ->
                    if (threadId == mainThreadId) return@forEach
                    val previous = subagents[threadId]
                    val state = item.agentsStates[threadId]
                    subagents[threadId] =
                        AgentRosterEntry(
                            threadId = threadId,
                            name =
                                previous?.name ?: defaultSubAgentName(threadId, subAgentNameFormat),
                            role = AgentRole.Sub,
                            status = state?.status ?: previous?.status,
                            activity = previous?.activity,
                            task = item.prompt ?: previous?.task,
                            model = item.model ?: previous?.model,
                            effort = item.reasoningEffort ?: previous?.effort,
                            tokens = previous?.tokens ?: 0,
                            itemId = item.id,
                        )
                }

            is SubAgentActivityItem -> {
                val threadId = item.agentThreadId
                if (threadId != mainThreadId) {
                    val previous = subagents[threadId]
                    subagents[threadId] =
                        AgentRosterEntry(
                            threadId = threadId,
                            name =
                                subAgentName(item.agentPath, subAgentNameFormat)
                                    ?: previous?.name
                                    ?: defaultSubAgentName(threadId, subAgentNameFormat),
                            role = AgentRole.Sub,
                            status = previous?.status,
                            activity = item.kind,
                            task = previous?.task,
                            model = previous?.model,
                            effort = previous?.effort,
                            tokens = previous?.tokens ?: 0,
                            itemId = item.id,
                        )
                }
            }

            else -> Unit
        }
    }
    val main =
        AgentRosterEntry(
            threadId = mainThreadId,
            name = mainLabel,
            role = AgentRole.Main,
            status = null,
            activity = null,
            task = null,
            model = null,
            effort = null,
            tokens = 0,
            itemId = null,
        )
    return listOf(main) + subagents.values
}

private fun subAgentName(agentPath: String, format: String): String? {
    val leaf = agentPath.trim().trimEnd('/').substringAfterLast('/').trim()
    return leaf.takeIf { it.isNotEmpty() }?.let { format.format(it) }
}

private fun defaultSubAgentName(threadId: String, format: String): String =
    threadId.takeLast(4).takeIf { it.isNotEmpty() }?.let { format.format(it) }
        ?: format.substringBefore('%').trim()

/** Modal picker with filter box, mirroring `codex-rs/.../bottom_pane/agent_picker.rs`. */
@Composable
fun AgentPickerSheet(
    show: Boolean,
    roster: List<AgentRosterEntry>,
    selectedThreadId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(roster, query) {
            val needle = query.trim()
            if (needle.isEmpty()) roster else roster.filter { it.matches(needle) }
        }
    WindowBottomSheet(
        show = show,
        // Both called: the chosen thread opens behind the sheet, so don't wait for its exit.
        onDismissRequest = {
            onDismiss()
            onDismissFinished()
        },
        onDismissFinished = onDismissFinished,
        title = stringResource(R.string.agent_picker_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        val close = LocalDismissState.current
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = stringResource(R.string.agent_picker_subtitle, roster.size),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = DpSize(UiConsts.Space12, UiConsts.Space8),
                    label = stringResource(R.string.agent_picker_filter_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    leadingIcon = {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = null,
                            modifier =
                                Modifier.padding(start = UiConsts.Space12).size(UiConsts.IconRow),
                            tint = colors.onSurfaceVariantSummary,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.SearchCleanup,
                                    contentDescription =
                                        stringResource(R.string.agent_picker_filter_clear),
                                    modifier = Modifier.size(UiConsts.IconRow),
                                    tint = colors.onSurfaceVariantSummary,
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(UiConsts.Space8))
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.agent_picker_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space20),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    filtered.forEach { entry ->
                        AgentRosterRow(
                            entry = entry,
                            selected = entry.threadId == selectedThreadId,
                            onClick = {
                                onSelect(entry.threadId)
                                close?.invoke()
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun AgentRosterEntry.matches(needle: String): Boolean =
    name.contains(needle, true) ||
        task?.contains(needle, true) == true ||
        threadId.contains(needle, true)

/** Folded roster, recalculated at most once per [SessionState.itemsRevision]. */
@Composable
internal fun rememberAgentRoster(
    session: SessionState,
    mainAgentLabel: String,
    subAgentNameFormat: String,
): List<AgentRosterEntry> {
    val state =
        remember(session, mainAgentLabel, subAgentNameFormat) {
            AgentRosterMemo(session, mainAgentLabel, subAgentNameFormat)
        }
    return state.roster
}

private class AgentRosterMemo(
    private val session: SessionState,
    private val mainAgentLabel: String,
    private val subAgentNameFormat: String,
) {
    private var revision = -1
    private var cached: List<AgentRosterEntry> = emptyList()
    private val state = derivedStateOf {
        val current = session.itemsRevision
        if (current != revision) {
            revision = current
            // Read without observation: the revision is the only dependency, so the fold runs once per revision.
            cached = Snapshot.withoutReadObservation {
                deriveAgentRoster(
                    session.items,
                    session.threadId,
                    mainAgentLabel,
                    subAgentNameFormat,
                )
            }
        }
        cached
    }

    val roster: List<AgentRosterEntry>
        get() = state.value
}
