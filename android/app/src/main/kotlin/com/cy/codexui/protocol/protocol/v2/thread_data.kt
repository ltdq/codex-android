package com.cy.codexui.protocol.protocol.v2

import com.cy.codexui.protocol.protocol.item.ThreadItem

/** One row of `thread/list`: metadata only, no transcript. */
data class Thread(
    val id: String,
    val name: String? = null,
    val preview: String? = null,
    val modelProvider: String = "openai",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val cwd: String = "",
    val status: ThreadStatus = ThreadStatus.Idle,
    val archived: Boolean = false,
    val sectionId: String? = null,
    val forkedFromId: String? = null,
    val gitBranch: String? = null,
)

sealed interface ThreadStatus {
    data object Idle : ThreadStatus

    data object NotLoaded : ThreadStatus

    data class Active(val activeFlags: List<ThreadActiveFlag> = emptyList()) : ThreadStatus

    data class SystemError(val message: String) : ThreadStatus
}

enum class ThreadActiveFlag(val wire: String) {
    WaitingOnApproval("waitingOnApproval"),
    WaitingOnUserInput("waitingOnUserInput"),
}

/** `thread/read` response: the full item list of one thread. */
data class ThreadReadResponse(
    val thread: Thread,
    val items: List<ThreadItem> = emptyList(),
    val turns: List<Turn> = emptyList(),
)

/** One agent turn: a user message plus everything the agent did in response. */
data class Turn(
    val id: String,
    val items: List<ThreadItem> = emptyList(),
    val status: TurnStatus = TurnStatus.Completed,
    val startedAt: Long = 0L,
    val completedAt: Long? = null,
    val usage: ThreadTokenUsage? = null,
)

enum class TurnStatus(val wire: String) {
    InProgress("inProgress"),
    Completed("completed"),
    Interrupted("interrupted"),
    Failed("failed"),
    ;

    companion object {
        fun fromWire(value: String): TurnStatus =
            entries.firstOrNull { it.wire == value } ?: Completed
    }
}

/**
 * Token accounting for one thread, straight from `thread/tokenUsage/updated`.
 *
 * Field names follow `schema/typescript/v2/ThreadTokenUsage.ts`.
 */
data class ThreadTokenUsage(
    val totalTokens: Int = 0,
    val inputTokens: Int = 0,
    val cachedInputTokens: Int = 0,
    val outputTokens: Int = 0,
    val reasoningOutputTokens: Int = 0,
    val modelContextWindow: Int? = null,
) {
    val usedFraction: Float
        get() = modelContextWindow?.takeIf { it > 0 }?.let {
            (totalTokens.toFloat() / it.toFloat()).coerceIn(0f, 1f)
        } ?: 0f
}

/** `threadSection/…`: a user-defined group of threads in the sidebar. */
data class ThreadSection(
    val id: String,
    val name: String,
    val position: Int = 0,
)

/** `model/list` entry. */
data class ModelPreset(
    val id: String,
    val model: String,
    val displayName: String,
    val description: String = "",
    val defaultReasoningEffort: ReasoningEffort = ReasoningEffort.Medium,
    val supportedReasoningEfforts: List<ReasoningEffort> = ReasoningEffort.entries,
    val isDefault: Boolean = false,
    val contextWindow: Int = 256_000,
)

/** `permissionProfile/list` entry. */
data class PermissionProfileEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val active: Boolean = false,
)

/** `experimentalFeature/list` entry. */
data class ExperimentalFeatureEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val enabled: Boolean = false,
    val stage: String = "beta",
)

/** `mcpServerStatus/list` entry. */
data class McpServerStatusEntry(
    val name: String,
    val status: McpServerConnectionStatus = McpServerConnectionStatus.Ready,
    val tools: Int = 0,
    val resources: Int = 0,
    val error: String? = null,
)

enum class McpServerConnectionStatus(val wire: String) {
    Starting("starting"),
    Ready("ready"),
    Failed("failed"),
    Disabled("disabled"),
}

/** `skills/list` entry. */
data class SkillEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val path: String = "",
    val enabled: Boolean = true,
    val scope: SkillScope = SkillScope.User,
)

enum class SkillScope(val wire: String) {
    User("user"),
    Project("project"),
    System("system"),
}

/** `plugin/list` entry: the parts of `PluginSummary` this client renders. */
data class PluginEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val installed: Boolean = false,
    val version: String = "",
    val marketplace: String = "",
    /** Backend remote plugin identifier, when the plugin service published one. */
    val remotePluginId: String? = null,
    /** Remote sharing context, when this account has shared the plugin. */
    val shareContext: PluginShareContext? = null,
)

/** `app/list` entry — connectors exposed by the account. */
data class AppInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val installed: Boolean = false,
)

/**
 * One hook configured for a working directory.
 *
 * Mirrors `v2::HookMetadata`. The handler is a flattened tagged union upstream (`handlerType` plus
 * that variant's fields), so the variant fields are carried as optionals here and [handlerType]
 * says which are meaningful.
 */
data class HookMetadata(
    /** Stable identity of the hook inside its config source. */
    val key: String,
    val eventName: String,
    /** `command`, `mcpTool`, `prompt` or `agent`. */
    val handlerType: String = "",
    val command: String? = null,
    val async: Boolean = false,
    val server: String? = null,
    val tool: String? = null,
    val matcher: String? = null,
    val timeoutSec: Long = 0L,
    val statusMessage: String? = null,
    val additionalContextLimit: Int? = null,
    val sourcePath: String = "",
    val source: String = "",
    val pluginId: String? = null,
    val displayOrder: Long = 0L,
    val enabled: Boolean = true,
    val isManaged: Boolean = false,
    val currentHash: String = "",
    /** `managed`, `untrusted`, `trusted` or `modified`. */
    val trustStatus: String = "",
)

/** One `hooks/list` entry: the hooks discovered under one working directory. */
data class HooksListEntry(
    val cwd: String = "",
    val hooks: List<HookMetadata> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<HookErrorInfo> = emptyList(),
)

data class HookErrorInfo(val path: String = "", val message: String = "")

/** `account/read` response. */
data class AccountInfo(
    val email: String? = null,
    val planType: String? = null,
    val organization: String? = null,
    val loggedIn: Boolean = false,
)

/** `account/rateLimits/read` response. */
data class RateLimits(
    val primary: RateLimitWindow? = null,
    val secondary: RateLimitWindow? = null,
    val credits: Int? = null,
)

data class RateLimitWindow(
    val label: String,
    val usedPercent: Float,
    val resetsAt: Long? = null,
)

/** `account/usage/read` response. */
data class AccountUsage(
    val dailyBuckets: List<UsageBucket> = emptyList(),
    val totalTokens: Int = 0,
)

data class UsageBucket(val day: String, val tokens: Int)

/**
 * `fs/getMetadata` response, and the shape `fs/readDirectory` rows are folded into.
 *
 * The real `fs/readDirectory` returns `FsReadDirectoryEntry { fileName, isDirectory, isFile,
 * isSymlink }` — a name, not a path. The picker works in paths (it navigates by joining), so the
 * entry is widened to a path here and [isFile]/[isSymlink] are carried through for the row icon.
 */
data class FileMetadata(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedAt: Long = 0L,
    val isFile: Boolean = !isDirectory,
    val isSymlink: Boolean = false,
    val createdAt: Long = 0L,
) {
    /** `fs/readDirectory` returns entries carrying the same metadata shape. */
    val name: String get() = path.trimEnd('/').substringAfterLast('/')

    companion object {
        /** Fold one `fs/readDirectory` entry into the path-addressed shape the picker navigates by. */
        fun of(directory: String, entry: FsReadDirectoryEntry): FileMetadata = FileMetadata(
            path = directory.trimEnd('/') + "/" + entry.fileName,
            isDirectory = entry.isDirectory,
            isFile = entry.isFile,
            isSymlink = entry.isSymlink,
        )
    }
}
