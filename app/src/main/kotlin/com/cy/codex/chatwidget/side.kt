package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.SlashCommands
import com.cy.codex.ThreadListState
import com.cy.codex.protocol.protocol.v2.Thread

/**
 * Session model, mirroring the TUI's side panel (`codex-rs/tui/src/chatwidget/side.rs` and
 * `app/side.rs`). Built from `thread/list` through [ThreadListState.grouped]; groups derive from
 * each thread's working directory.
 */
data class SidebarSession(
    val id: String,
    val title: String,
    val date: String,
    val archived: Boolean = false,
    val running: Boolean = false,
)

data class SidebarProject(
    val id: String,
    val name: String,
    val path: String,
    val sessions: List<SidebarSession>,
)

object SidebarModel {

    @Composable
    @ReadOnlyComposable
    fun projects(threads: ThreadListState, includeArchived: Boolean): List<SidebarProject> =
        threads.grouped().mapNotNull { group ->
            val sessions = group.threads
                .filter { includeArchived || it.id !in threads.archivedIds }
                .map { thread -> sessionOf(thread, thread.id in threads.archivedIds) }
            if (sessions.isEmpty()) {
                null
            } else {
                SidebarProject(group.id, group.name, group.path.orEmpty(), sessions)
            }
        }

    /**
     * The thread library by recency, for the home menu's 最近 group.
     *
     * The grouping is dropped on purpose: 最近 is where a thread is reopened, so a thread filed under
     * a project is listed here as well, and only `updatedAt` orders the two.
     */
    @Composable
    @ReadOnlyComposable
    fun recent(threads: ThreadListState, includeArchived: Boolean): List<SidebarSession> =
        threads.threads
            .filter { includeArchived || it.id !in threads.archivedIds }
            .sortedByDescending { it.updatedAt }
            .map { thread -> sessionOf(thread, thread.id in threads.archivedIds) }

    @Composable
    @ReadOnlyComposable
    private fun sessionOf(thread: Thread, archived: Boolean): SidebarSession =
        SidebarSession(
            id = thread.id,
            title = thread.name ?: thread.preview.ifBlank { thread.id.takeLast(6) },
            date = relativeTime(thread.updatedAt),
            archived = archived,
            running = thread.status is com.cy.codex.protocol.protocol.v2.ThreadStatus.Active,
        )

    /** Coarse relative time in the TUI's session-picker buckets. */
    @Composable
    @ReadOnlyComposable
    fun relativeTime(epochMillis: Long): String {
        val now = System.currentTimeMillis()
        val delta = (now - epochMillis).coerceAtLeast(0)
        val minutes = delta / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 2 -> stringResource(R.string.sidebar_time_now)
            minutes < 60 -> stringResource(R.string.sidebar_time_minutes, minutes)
            hours < 24 -> stringResource(R.string.sidebar_time_hours, hours)
            days < 7 -> stringResource(R.string.sidebar_time_days, days)
            else -> {
                val weeks = days / 7
                stringResource(R.string.sidebar_time_weeks, weeks)
            }
        }
    }

    /**
     * Slash-command suggestions built from [SlashCommands.All], the same table submission
     * recognizes; [planAvailable] mirrors the upstream feature gate on `SlashCommand::Plan`.
     */
    @Composable
    fun slashSuggestions(
        query: String = "/",
        planAvailable: Boolean = true,
        usageAvailable: Boolean = true,
        appsAvailable: Boolean = true,
    ): List<SlashCommand> =
        SlashCommands.filter(query, usageAvailable, appsAvailable)
            .filter { spec -> planAvailable || spec.name != "plan" }
            .map { spec ->
                SlashCommand(
                    command = "/" + spec.name,
                    description = stringResource(spec.descriptionRes),
                    takesArgument = spec.takesArgument,
                )
            }
}

data class SlashCommand(val command: String, val description: String, val takesArgument: Boolean = false)
