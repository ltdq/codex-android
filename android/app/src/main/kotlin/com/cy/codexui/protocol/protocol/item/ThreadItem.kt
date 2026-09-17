package com.cy.codexui.protocol.protocol.item

import com.cy.codexui.protocol.protocol.v2.AsyncUserInputQuestion
import com.cy.codexui.protocol.protocol.v2.CollabAgentState
import com.cy.codexui.protocol.protocol.v2.CollabAgentTool
import com.cy.codexui.protocol.protocol.v2.CollabAgentToolCallStatus
import com.cy.codexui.protocol.protocol.v2.CommandAction
import com.cy.codexui.protocol.protocol.v2.CommandExecutionSource
import com.cy.codexui.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codexui.protocol.protocol.v2.DynamicToolCallStatus
import com.cy.codexui.protocol.protocol.v2.FileUpdateChange
import com.cy.codexui.protocol.protocol.v2.McpToolCallStatus
import com.cy.codexui.protocol.protocol.v2.MessagePhase
import com.cy.codexui.protocol.protocol.v2.PatchApplyStatus
import com.cy.codexui.protocol.protocol.v2.ReasoningEffort
import com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codexui.protocol.protocol.v2.UserInput

/**
 * The v2 `ThreadItem` union — the unit the transcript is made of.
 *
 * Mirrors `codex-rs/app-server-protocol/src/protocol/v2/item.rs`. The server streams one item at a
 * time: `item/started` opens it, zero or more deltas fill it in (`item/agentMessage/delta`,
 * `item/commandExecution/outputDelta`, …) and `item/completed` finalises it. Everything the
 * transcript renders is one of these, so the UI never works from raw text.
 */
sealed interface ThreadItem {
    /** Stable server-assigned id; deltas address the item through it. */
    val id: String
}

data class UserMessageItem(
    override val id: String,
    val clientId: String? = null,
    val content: List<UserInput>,
) : ThreadItem

data class HookPromptItem(
    override val id: String,
    val fragments: List<HookPromptFragment>,
) : ThreadItem

data class HookPromptFragment(val text: String, val hookName: String)

data class AgentMessageItem(
    override val id: String,
    val text: String,
    val phase: MessagePhase? = null,
    val questions: List<AsyncUserInputQuestion>? = null,
) : ThreadItem

data class FunctionCallOutputItem(
    override val id: String,
    val name: String,
    val namespace: String? = null,
    val output: String,
) : ThreadItem

data class PlanItem(
    override val id: String,
    val text: String,
) : ThreadItem

data class ReasoningItem(
    override val id: String,
    val summary: List<String> = emptyList(),
    val content: List<String> = emptyList(),
) : ThreadItem {
    val title: String get() = summary.firstOrNull() ?: content.firstOrNull().orEmpty()
}

data class CommandExecutionItem(
    override val id: String,
    val command: String,
    val cwd: String,
    val processId: String? = null,
    val source: CommandExecutionSource = CommandExecutionSource.Agent,
    val status: CommandExecutionStatus = CommandExecutionStatus.InProgress,
    val commandActions: List<CommandAction> = emptyList(),
    val aggregatedOutput: String? = null,
    val exitCode: Int? = null,
    val durationMs: Long? = null,
) : ThreadItem

data class FileChangeItem(
    override val id: String,
    val changes: List<FileUpdateChange>,
    val status: PatchApplyStatus = PatchApplyStatus.InProgress,
) : ThreadItem

data class McpToolCallItem(
    override val id: String,
    val server: String,
    val tool: String,
    val status: McpToolCallStatus = McpToolCallStatus.InProgress,
    val arguments: String = "{}",
    val result: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
) : ThreadItem

data class DynamicToolCallItem(
    override val id: String,
    val namespace: String? = null,
    val tool: String,
    val arguments: String = "{}",
    val status: DynamicToolCallStatus = DynamicToolCallStatus.InProgress,
    val contentItems: List<String> = emptyList(),
    val success: Boolean? = null,
    val durationMs: Long? = null,
) : ThreadItem

data class CollabAgentToolCallItem(
    override val id: String,
    val tool: CollabAgentTool,
    val status: CollabAgentToolCallStatus = CollabAgentToolCallStatus.InProgress,
    val senderThreadId: String,
    val receiverThreadIds: List<String> = emptyList(),
    val prompt: String? = null,
    val model: String? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val agentsStates: Map<String, CollabAgentState> = emptyMap(),
) : ThreadItem

data class SubAgentActivityItem(
    override val id: String,
    val kind: SubAgentActivityKind,
    val agentThreadId: String,
    val agentPath: String,
) : ThreadItem

data class WebSearchItem(
    override val id: String,
    val query: String,
    val results: List<WebSearchResult> = emptyList(),
    /** What the web tool actually did; `null` on servers that do not model the action yet. */
    val action: WebSearchAction? = null,
) : ThreadItem

data class WebSearchResult(val title: String, val url: String)

/**
 * The `WebSearchAction` union of `app-server-protocol/schema/json/v2`.
 *
 * Mirrors `codex-rs/ext/items/src/web_search.rs`: the search tool can search, open a page or find
 * text in one, and only the action's own fields name what happened.
 */
sealed interface WebSearchAction {
    data class Search(val query: String?, val queries: List<String>?) : WebSearchAction
    data class OpenPage(val url: String?) : WebSearchAction
    data class FindInPage(val url: String?, val pattern: String?) : WebSearchAction
    data object Other : WebSearchAction
}

data class ImageViewItem(
    override val id: String,
    val path: String,
) : ThreadItem

data class SleepItem(
    override val id: String,
    val durationMs: Long,
) : ThreadItem

data class ImageGenerationItem(
    override val id: String,
    val prompt: String,
    val status: DynamicToolCallStatus = DynamicToolCallStatus.Completed,
) : ThreadItem

data class EnteredReviewModeItem(
    override val id: String,
    val review: String,
) : ThreadItem

data class ExitedReviewModeItem(
    override val id: String,
    val review: String,
) : ThreadItem

data class ContextCompactionItem(
    override val id: String,
) : ThreadItem
