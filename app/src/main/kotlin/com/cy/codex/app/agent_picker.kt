package com.cy.codex.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.SessionState
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadStatus

/**
 * The agent roster, the roster fold and the roster's chat pages. The loaded transcript and the
 * descendant thread listing both contribute agents.
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

/** Include descendants absent from the loaded parent transcript. */
internal fun mergeAgentRoster(
    roster: List<AgentRosterEntry>,
    descendants: List<Thread>,
    subAgentNameFormat: String,
): List<AgentRosterEntry> {
    val entries = roster.associateByTo(mutableMapOf()) { it.threadId }
    val mainThreadId = roster.first().threadId
    val orderedDescendants = descendants
        .filter { it.id != mainThreadId && it.parentThreadId != null }
        .distinctBy { it.id }
        .sortedWith(compareBy<Thread> { it.createdAt }.thenBy { it.id })
    orderedDescendants.forEach { thread ->
        val previous = entries[thread.id]
        entries[thread.id] =
            AgentRosterEntry(
                threadId = thread.id,
                name = thread.name?.takeIf { it.isNotBlank() }
                    ?: previous?.name
                    ?: thread.agentNickname?.takeIf { it.isNotBlank() }
                    ?: defaultSubAgentName(thread.id, subAgentNameFormat),
                role = AgentRole.Sub,
                status = previous?.status,
                activity = previous?.activity,
                task = previous?.task ?: thread.preview.takeIf { it.isNotBlank() },
                model = previous?.model ?: thread.model,
                effort = previous?.effort ?: thread.reasoningEffort,
                tokens = previous?.tokens ?: 0,
                itemId = previous?.itemId,
                threadStatus = thread.status,
            )
    }
    val listedById = orderedDescendants.associateBy { it.id }
    val transcriptIds = roster.mapTo(mutableSetOf()) { it.threadId }
    val visible = roster.drop(1).map { entries.getValue(it.threadId) }.toMutableList()
    orderedDescendants.filter { it.id !in transcriptIds }.forEach { thread ->
        val index = visible.indexOfFirst { agent ->
            listedById[agent.threadId]?.createdAt?.let { it > thread.createdAt } == true
        }
        if (index < 0) visible += entries.getValue(thread.id)
        else visible.add(index, entries.getValue(thread.id))
    }
    return listOf(roster.first()) + visible
}

/**
 * Pages of the chat pager: the open thread first, then its subagents in spawn order. Only the
 * first page is the bound session; the rest are read-only views of a spawned agent's conversation.
 */
fun agentPageThreads(mainThreadId: String, roster: List<AgentRosterEntry>): List<String> =
    listOf(mainThreadId) + roster.filter { it.role == AgentRole.Sub }.map { it.threadId }

private fun subAgentName(agentPath: String, format: String): String? {
    val leaf = agentPath.trim().trimEnd('/').substringAfterLast('/').trim()
    return leaf.takeIf { it.isNotEmpty() }?.let { format.format(it) }
}

private fun defaultSubAgentName(threadId: String, format: String): String =
    threadId.takeLast(4).takeIf { it.isNotEmpty() }?.let { format.format(it) }
        ?: format.substringBefore('%').trim()

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
    descendants: List<Thread> = emptyList(),
): List<AgentRosterEntry> {
    val state =
        remember(session, mainAgentLabel, subAgentNameFormat) {
            AgentRosterMemo(session, mainAgentLabel, subAgentNameFormat)
        }
    val roster = state.roster
    return remember(roster, descendants, subAgentNameFormat) {
        mergeAgentRoster(roster, descendants, subAgentNameFormat)
    }
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
