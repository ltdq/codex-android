package com.cy.codex.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class RecapTrigger { Manual, Automatic }

/** `tui.auto_recap` in `codex_ui` prefs; upstream default true (codex-rs/.../config/src/types.rs). */
object RecapSettings {
    private const val FileName = "codex_ui"
    private const val KeyAutoRecap = "auto_recap"

    var autoRecap by mutableStateOf(true)
        private set

    fun load(context: Context) {
        autoRecap = context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
            .getBoolean(KeyAutoRecap, true)
    }

    fun setAutoRecap(context: Context, enabled: Boolean) {
        autoRecap = enabled
        context.getSharedPreferences(FileName, Context.MODE_PRIVATE)
            .edit().putBoolean(KeyAutoRecap, enabled).apply()
    }
}

/**
 * Automatic recap schedule, ported from `codex-rs/.../app/recap.rs` `RecapState`: due at
 * `max(focus lost, last turn finished) + 30 minutes`, gated by completed-turn thresholds.
 */
internal class RecapScheduler {
    var completedTurns = 0
        private set

    /** Bumped on every finished turn, success or not; retries are keyed by it. */
    var turnRevision = 0
        private set

    var inFlightTrigger: RecapTrigger? = null
        private set

    private var unfocusedSinceMs: Long? = null
    private var lastTurnFinishedAtMs: Long? = null
    private var lastRecappedTurnCount: Int? = null
    private var scheduledCheck: Job? = null
    private var retryRevision: Int? = null

    fun noteTurnFinished(status: TurnStatus, nowMs: Long) {
        if (status == TurnStatus.Completed) completedTurns++
        turnRevision++
        lastTurnFinishedAtMs = nowMs
    }

    /** The app moved to the background: the idle clock starts, but never restarts while away. */
    fun noteFocusLost(nowMs: Long) {
        if (unfocusedSinceMs == null) retryRevision = null
        if (unfocusedSinceMs == null) unfocusedSinceMs = nowMs
    }

    fun noteFocusGained() {
        unfocusedSinceMs = null
        scheduledCheck?.cancel()
        scheduledCheck = null
        retryRevision = null
        if (inFlightTrigger == RecapTrigger.Automatic) inFlightTrigger = null
    }

    /** Zero the schedule for a freshly bound thread; the focus clock survives, as upstream. */
    fun resetForNewThread() {
        scheduledCheck?.cancel()
        scheduledCheck = null
        completedTurns = 0
        turnRevision = 0
        lastTurnFinishedAtMs = null
        lastRecappedTurnCount = null
        retryRevision = null
        inFlightTrigger = null
    }

    // lastRecappedTurnCount stays null: reseeding cannot know whether the last recap was recorded.
    fun seedFromTurns(turns: List<Turn>, nowMs: Long) {
        scheduledCheck?.cancel()
        scheduledCheck = null
        completedTurns = turns.count { it.status == TurnStatus.Completed }
        turnRevision = 0
        lastRecappedTurnCount = null
        lastTurnFinishedAtMs = if (completedTurns > 0) nowMs else null
    }

    fun nextCheckDeadlineMs(): Long? {
        val unfocusedSince = unfocusedSinceMs ?: return null
        if (completedTurns < MinCompletedTurns) return null
        val previous = lastRecappedTurnCount
        if (previous != null && completedTurns - previous < MinTurnsBetweenRecaps) return null
        val lastTurnFinished = lastTurnFinishedAtMs ?: return null
        return maxOf(unfocusedSince, lastTurnFinished) + RecapDelayMs
    }

    fun shouldGenerate(nowMs: Long): Boolean = nextCheckDeadlineMs()?.let { nowMs >= it } == true

    /** (Re)arms the one-shot check; every call aborts the previous timer. */
    fun scheduleCheck(
        scope: CoroutineScope,
        threadId: String,
        nowMs: Long,
        enabled: Boolean,
        onCheck: (String) -> Unit,
    ) {
        scheduledCheck?.cancel()
        scheduledCheck = null
        if (!enabled) return
        val deadline = nextCheckDeadlineMs() ?: return
        scheduledCheck = scope.launch {
            delay((deadline - nowMs).coerceAtLeast(0))
            scheduledCheck = null
            onCheck(threadId)
        }
    }

    /** One automatic retry per turn revision, 30 seconds after the failure. */
    fun scheduleRetry(
        scope: CoroutineScope,
        threadId: String,
        revision: Int,
        onCheck: (String) -> Unit,
    ) {
        if (turnRevision != revision || retryRevision == revision) return
        retryRevision = revision
        scope.launch {
            delay(RecapRetryDelayMs)
            onCheck(threadId)
        }
    }

    /** Remember which completed-turn count the last recap covered, for the two-turn cooldown. */
    fun markRecapped(completedTurnCount: Int) {
        lastRecappedTurnCount = completedTurnCount
    }

    fun beginInFlight(trigger: RecapTrigger): Boolean {
        if (inFlightTrigger != null) return false
        inFlightTrigger = trigger
        return true
    }

    fun finishInFlight() {
        inFlightTrigger = null
    }

    companion object {
        /** Upstream `MIN_COMPLETED_TURNS`. */
        const val MinCompletedTurns = 3

        /** Upstream `MIN_TURNS_BETWEEN_RECAPS`. */
        const val MinTurnsBetweenRecaps = 2

        /** Upstream `RECAP_DELAY`; units: ms. */
        const val RecapDelayMs = 30 * 60 * 1000L

        /** Upstream `RECAP_RETRY_DELAY`; units: ms. */
        const val RecapRetryDelayMs = 30_000L
    }
}
