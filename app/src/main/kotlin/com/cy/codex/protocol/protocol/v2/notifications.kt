package com.cy.codex.protocol.protocol.v2

/**
 * Payloads the client reads back out of the 82 notifications.
 *
 * Mirrors `schema/typescript/v2/`. Only fields the UI renders are carried; the rest of the wire
 * message is dropped by the decoder.
 */

data class ItemTextDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
    val summaryIndex: Int = 0,
)

data class CommandExecutionOutputDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

data class TerminalInteraction(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val processId: String,
    val stdin: String,
)

data class FileChangeOutputDelta(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val delta: String,
)

data class McpToolCallProgress(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val message: String,
)

data class TurnPlanUpdated(
    val threadId: String,
    val turnId: String,
    val plan: List<PlanStep>,
)

data class PlanStep(val step: String, val status: PlanStepStatus)

enum class PlanStepStatus(val wire: String) {
    Pending("pending"),
    InProgress("inProgress"),
    Completed("completed"),
    ;

    companion object {
        fun fromWire(value: String): PlanStepStatus =
            entries.firstOrNull { it.wire == value } ?: Pending
    }
}

data class TurnDiffUpdated(
    val threadId: String,
    val turnId: String,
    val diff: String,
)

data class ThreadStatusChanged(
    val threadId: String,
    val status: ThreadStatus,
)

data class ThreadTokenUsageUpdated(
    val threadId: String,
    val turnId: String?,
    val usage: ThreadTokenUsage,
)

data class ThreadNameUpdated(val threadId: String, val name: String?)

data class ThreadSettingsUpdated(
    val threadId: String,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val approvalPolicy: AskForApproval? = null,
    val approvalsReviewer: ApprovalsReviewer? = null,
    val collaborationMode: CollaborationMode? = null,
    val serviceTier: String? = null,
)

/**
 * `thread/queue/changed`: the server's queue for this thread moved.
 *
 * Carries no payload — it is a poke; the client answers with `thread/queue/list` (see [QueuedSubmission]).
 */
data class ThreadQueueChanged(val threadId: String)

/** `thread/goal/updated` and `thread/goal/cleared`. */
data class ThreadGoalUpdated(
    val threadId: String,
    val objective: String,
    val status: GoalStatus = GoalStatus.Active,
    /** Token ceiling for the goal, or null when the goal is unbounded. */
    val tokenBudget: Long? = null,
    val tokensUsed: Int = 0,
    val timeUsedSeconds: Long = 0L,
)

/**
 * `thread/goal/set`: a patch rather than a replacement.
 *
 * Every field the caller omits is left alone (upstream `ThreadGoalSetParams`). [clearTokenBudget]
 * sends the explicit JSON null that removes an existing ceiling, because omitting the field means
 * "keep it".
 */
data class ThreadGoalSetParams(
    val threadId: String,
    val objective: String? = null,
    val status: GoalStatus? = null,
    val tokenBudget: Long? = null,
    val clearTokenBudget: Boolean = false,
)

enum class GoalStatus(val wire: String) {
    Active("active"),
    Paused("paused"),
    Blocked("blocked"),
    UsageLimited("usageLimited"),
    BudgetLimited("budgetLimited"),
    Complete("complete"),
}

data class ThreadReverted(val threadId: String, val itemId: String?)

data class ThreadCompacted(val threadId: String, val summary: String? = null)

// Diagnostics
//
// These are five *separate* notifications on the wire, with different payloads, so they are five
// separate types here. Collapsing them into one "diagnostic" shape loses `willRetry` and
// `path`/`range` (which locate a bad config key).

data class ErrorNotification(
    val error: TurnError,
    val threadId: String,
    val turnId: String,
    /**
     * The server is retrying the turn itself. Kept for wire parity — nothing reads it.
     */
    val willRetry: Boolean = false,
)

/** `error.error`; `codexErrorInfo` is the snake-free wire tag listed by `CodexErrorInfo`. */
data class TurnError(
    val message: String,
    val additionalDetails: String? = null,
    val codexErrorInfo: String? = null,
)

data class WarningNotification(
    val threadId: String,
    val message: String,
)

data class ConfigWarningNotification(
    val summary: String,
    val details: String? = null,
    val path: String? = null,
    val range: TextRange? = null,
)

data class TextRange(val start: Int, val end: Int)

data class GuardianWarningNotification(
    val threadId: String,
    val message: String,
)

data class DeprecationNoticeNotification(
    val summary: String,
    val details: String? = null,
)

data class WorldWritableWarningNotification(
    val samplePaths: List<String> = emptyList(),
    val extraCount: Int = 0,
    /** The scan itself failed, so `samplePaths` is not exhaustive. */
    val failedScan: Boolean = false,
)

enum class DiagnosticSeverity { Info, Warning, Error }

/**
 * `serverRequest/resolved`: the request is settled and every surface should drop it. Carries the
 * thread rather than the method — the client already knows which request it answered.
 */
data class ServerRequestResolved(
    val requestId: String,
    val threadId: String,
)

data class ModelRerouted(
    val threadId: String,
    val fromModel: String,
    val toModel: String,
    val reason: String,
)

/**
 * `account/rateLimits/updated`: a sparse rolling update — fields the server could not supply are
 * absent, not zero, so a reader merges them into the last `account/rateLimits/read` snapshot.
 */
data class RateLimitsUpdated(val rateLimits: RateLimitSnapshot)

data class McpStartupStatusUpdated(
    val serverName: String,
    val status: McpServerStartupState,
    val error: String? = null,
    /** `reauthenticationRequired` when the failure is an expired credential. */
    val failureReason: String? = null,
)

data class CatalogChanged(val reason: String? = null)

data class FsChanged(val path: String, val kind: String)
