package com.cy.codex.app

import com.cy.codex.history_cell.resolveHookLabel
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CollabAgentToolCallItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.item.ImageGenerationFailure
import com.cy.codex.protocol.protocol.item.ImageGenerationItem
import com.cy.codex.protocol.protocol.item.ImageViewItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.RecapItem
import com.cy.codex.protocol.protocol.item.SleepItem
import com.cy.codex.protocol.protocol.item.SubAgentActivityItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.item.WebSearchItem
import com.cy.codex.protocol.protocol.v2.UserInput

/**
 * `/export`: whole conversation as markdown, mirroring
 * `codex-rs/.../app/transcript_export.rs`; activity bodies indent four spaces. A prompt bracketed
 * by the review markers is internal input, as in that file's `visible_export_items`.
 */
internal fun transcriptMarkdown(
    items: List<ThreadItem>,
    hooks: List<com.cy.codex.protocol.protocol.v2.HookMetadata> = emptyList(),
): String? {
    val markdown = StringBuilder("# Codex conversation\n")
    var reviewMode = false
    for (item in items) {
        when (item) {
            is EnteredReviewModeItem -> reviewMode = true
            is ExitedReviewModeItem -> reviewMode = false
            else -> Unit
        }
        if (item is UserMessageItem && reviewMode) continue
        val (heading, indent) = when (item) {
            is UserMessageItem -> "User" to false
            is AgentMessageItem -> "Assistant" to false
            is PlanItem -> "Plan" to false
            is ReasoningItem -> "Reasoning" to false
            is TurnSeparatorItem, is RecapItem -> continue
            else -> "Activity" to true
        }
        val lines = transcriptLines(item, hooks)
        if (lines.isEmpty()) continue
        markdown.append("\n## ").append(heading).append("\n\n")
        for (line in lines) {
            if (indent) markdown.append("    ")
            markdown.append(line).append('\n')
        }
    }
    return markdown.toString().takeIf { it != "# Codex conversation\n" }
}

private fun transcriptLines(
    item: ThreadItem,
    hooks: List<com.cy.codex.protocol.protocol.v2.HookMetadata>,
): List<String> = when (item) {
    is UserMessageItem -> {
        val text = item.content.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }
        val images = item.content.count { it is UserInput.LocalImage || it is UserInput.Image }
        val lines = text.lines().filter { it.isNotEmpty() }.toMutableList()
        if (images > 0 && lines.isNotEmpty()) lines += ""
        for (index in 1..images) {
            val label = com.cy.codex.imagePlaceholder(index)
            if (lines.none { it.contains(label) }) lines += label
        }
        lines
    }

    is AgentMessageItem -> item.text.lines()
    is PlanItem -> item.text.lines()
    is ReasoningItem -> item.summary + item.content
    is FunctionCallOutputItem -> item.output.lines()
    is HookPromptItem -> item.fragments.flatMap { fragment ->
        // Android-only: upstream (codex-rs/tui/src/thread_transcript.rs) prints no identity; name the
        // hook via `hooks/list`, and keep the plain `hook:` prefix when unjoinable rather than leak the id.
        val header = when (val label = resolveHookLabel(fragment.hookRunId, hooks)) {
            null -> "hook:"
            else -> "hook $label:"
        }
        listOf(header) + fragment.text.lines()
    }

    is CommandExecutionItem ->
        listOf("command: ${item.command}", "status: ${item.status.wire}") +
            (item.exitCode?.let { listOf("exit code: $it") } ?: emptyList()) +
            item.aggregatedOutput.orEmpty().lines().filter { it.isNotEmpty() }

    is FileChangeItem -> buildList {
        add("file changes: ${item.status.wire} · ${item.changes.size} changes")
        for (change in item.changes) {
            add("${change.kind.wire}: ${change.path}")
            addAll(change.diff.lines())
        }
    }

    is McpToolCallItem -> buildList {
        add("mcp tool: ${item.server}/${item.tool}(${item.arguments}) · ${item.status.wire}")
        item.result?.lineSequence()?.filter { it.isNotBlank() }?.forEach { add(it) }
        item.error?.let { add("error: $it") }
    }

    is DynamicToolCallItem -> buildList {
        add("dynamic tool: ${item.namespace ?: "codex"}/${item.tool}(${item.arguments}) · ${item.status.wire}")
        item.contentItems.forEach { content ->
            when (content) {
                is com.cy.codex.protocol.protocol.item.DynamicToolOutputContent.InputText ->
                    addAll(content.text.lines())
                is com.cy.codex.protocol.protocol.item.DynamicToolOutputContent.InputImage ->
                    add("Returned image")
                is com.cy.codex.protocol.protocol.item.DynamicToolOutputContent.InputAudio ->
                    add("<audio content>")
            }
        }
    }

    is CollabAgentToolCallItem -> listOf("agent tool: ${item.tool.wire}") +
        (item.prompt?.lines() ?: emptyList())

    is SubAgentActivityItem -> listOf("agent ${item.kind.wire}: ${item.agentPath}")
    is WebSearchItem -> listOf("web search: ${item.query}")
    is ImageViewItem -> listOf("image: ${item.path}")
    is SleepItem -> listOf("sleep: ${item.durationMs}ms")
    is ImageGenerationItem -> buildList {
        add("image generation: ${item.detail}")
        item.failure?.let { failure ->
            when (failure) {
                is ImageGenerationFailure.UsageLimitExceeded ->
                    add("  failure: usage limit ${failure.limitId}")
            }
        }
        item.savedPath?.let { add("  saved to: $it") }
    }
    is EnteredReviewModeItem -> listOf(">> Code review started: ${item.review} <<")
    is ExitedReviewModeItem -> listOf("<< Code review finished: ${item.review} >>")
    is ContextCompactionItem -> listOf("context compacted")
    is TurnSeparatorItem, is RecapItem, is com.cy.codex.protocol.protocol.item.TipItem -> emptyList()
}
