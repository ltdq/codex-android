package com.cy.codex.chatwidget

import com.cy.codex.bottom_pane.formatElapsedCompact
import com.cy.codex.protocol.protocol.item.ContextCompactionItem

/**
 * A compaction this client watched start and has not yet seen complete; the activity row shows its
 * timer. Mirrors `ActiveCompaction` (codex-rs/tui/src/chatwidget/compaction.rs).
 */
internal data class ActiveCompaction(val id: String, val startedAtMs: Long)

/** Id of the header `/compact` raises itself, before the server names the compaction it started. */
internal const val PendingCompactionId = "pending-compaction"

/**
 * The compaction's duration in seconds, or null when this client did not see it start.
 *
 * Mirrors `on_context_compaction_completed`, which counts only the completion whose id matched the
 * live compaction (codex-rs/tui/src/chatwidget/compaction.rs).
 */
internal fun compactionElapsedSeconds(item: ContextCompactionItem): Long? {
    val startedAtMs = item.startedAtMs ?: return null
    val completedAtMs = item.completedAtMs ?: return null
    return (completedAtMs - startedAtMs).coerceAtLeast(0L) / 1000
}

/** The completion message, with ` · ` and the elapsed time appended once one is known. */
internal fun compactionLabel(compacted: String, elapsedSeconds: Long?): String =
    elapsedSeconds?.let { "$compacted · ${formatElapsedCompact(it)}" } ?: compacted
