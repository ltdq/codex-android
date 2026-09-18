package com.cy.codexui.protocol.protocol.v2

/**
 * Policies and profiles that decide how much the agent may do without asking.
 *
 * Mirrors `AskForApproval`, `SandboxMode`, `CollaborationMode`, `Personality` and
 * `ActivePermissionProfile` from the generated bindings.
 */
enum class AskForApproval(val wire: String) {
    UnlessTrusted(wire = "untrusted"),
    OnRequest(wire = "on-request"),
    Granular(wire = "granular"),
    Never(wire = "never"),
    ;

    companion object {
        fun fromWire(value: String): AskForApproval =
            entries.firstOrNull { it.wire == value } ?: OnRequest
    }
}

/** Fine-grained switches behind [AskForApproval.Granular]. */
data class GranularApprovalConfig(
    val sandboxApproval: Boolean = true,
    val rules: Boolean = true,
    val skillApproval: Boolean = false,
    val requestPermissions: Boolean = true,
    val mcpElicitations: Boolean = true,
)

/**
 * Who an approval request is routed to for review.
 *
 * Mirrors upstream `ApprovalsReviewer`: `auto_review` hands the request to the review subagent
 * instead of showing it to the user. The legacy wire value `guardian_subagent` is the same mode and
 * is folded into [AutoReview] on read.
 */
enum class ApprovalsReviewer(val wire: String) {
    User("user"),
    AutoReview("auto_review"),
    ;

    companion object {
        fun fromWire(value: String?): ApprovalsReviewer =
            when (value) {
                "auto_review", "guardian_subagent" -> AutoReview
                else -> User
            }
    }
}

enum class SandboxMode(val wire: String) {
    ReadOnly("read-only"),
    WorkspaceWrite("workspace-write"),
    DangerFullAccess("danger-full-access"),
    ;

    companion object {
        fun fromWire(value: String): SandboxMode =
            entries.firstOrNull { it.wire == value } ?: WorkspaceWrite
    }
}

data class SandboxPolicy(
    val mode: SandboxMode = SandboxMode.WorkspaceWrite,
    val writableRoots: List<String> = emptyList(),
    val networkAccess: Boolean = false,
    val excludeTmpdirEnvVar: Boolean = false,
    val excludeSlashTmp: Boolean = false,
)

enum class CollaborationMode(val wire: String) {
    Default("default"),
    Plan("plan"),
    Goal("goal"),
    ;

    companion object {
        fun fromWire(value: String?): CollaborationMode =
            entries.firstOrNull { it.wire == value } ?: Default
    }
}

enum class Personality(val wire: String) {
    Pragmatic("pragmatic"),
    Friendly("friendly"),
    None("none"),
}

/**
 * Everything the status card and the settings page need to describe one session.
 *
 * Mirrors `ThreadSessionState` in `codex-rs/tui/src/session_state.rs`: the single internal shape
 * that app orchestration and widgets read from, filled from app-server responses.
 */
data class ThreadSessionState(
    val threadId: String,
    val forkedFromId: String? = null,
    val threadName: String? = null,
    val model: String = "",
    val modelDisplayName: String = "",
    val modelProviderId: String = "openai",
    val reasoningEffort: ReasoningEffort = ReasoningEffort.High,
    val approvalPolicy: AskForApproval = AskForApproval.OnRequest,
    val approvalsReviewer: ApprovalsReviewer = ApprovalsReviewer.User,
    val granularApproval: GranularApprovalConfig = GranularApprovalConfig(),
    val sandboxPolicy: SandboxPolicy = SandboxPolicy(),
    val activePermissionProfile: PermissionProfileEntry? = null,
    val collaborationMode: CollaborationMode = CollaborationMode.Default,
    val personality: Personality = Personality.None,
    /** Effective `serviceTier`; `null` means the server is on its default. */
    val serviceTier: String? = null,
    val cwd: String = "",
    val workspaceRoots: List<String> = emptyList(),
    val instructionSourcePaths: List<String> = emptyList(),
    val gitBranch: String? = null,
    val rolloutPath: String? = null,
    /** Set when this thread is a sub-agent of another thread. */
    val parentThreadId: String? = null,
    /** Whether the server accepts direct turn input; `null` when the capability is unavailable. */
    val canAcceptDirectInput: Boolean? = null,
    /**
     * Cursor for `thread/items/list` with `sortDirection: desc`, when the resume response offered
     * one. Its first page includes the item the cursor names.
     */
    val itemsBackwardsCursor: String? = null,
) {
    val displayName: String get() = threadName ?: cwd.substringAfterLast('/').ifEmpty { threadId }

    /**
     * Whether the composer must refuse direct input.
     *
     * Mirrors `set_parent_owned_thread` upstream: a sub-agent thread, or one the server says does
     * not accept direct input, is viewable but not writable from the composer.
     */
    val blocksDirectInput: Boolean get() = parentThreadId != null || canAcceptDirectInput == false
}
