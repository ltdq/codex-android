package com.cy.codex.history_cell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.CollapsibleSection
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** cua_repl is the whole of computer activity; the tool name is not consulted.
 * Mirrors `McpInvocation::is_computer_activity` in `codex-rs/tui/src/history_cell/mcp.rs`. */
internal fun McpToolCallItem.isComputerActivity(): Boolean = server == "cua_repl"

/** Mirrors `ComputerActivityCell::is_active` in `codex-rs/tui/src/history_cell/computer_activity.rs`. */
internal fun computerActivityActive(calls: List<McpToolCallItem>): Boolean =
    calls.any { it.status == McpToolCallStatus.InProgress }

/**
 * The name the model gave this action; absent when its arguments carry none. Upstream also marks a
 * row whose result carried an image, which its own structured result reports and this side's text
 * does not.
 */
internal fun computerActionTitle(call: McpToolCallItem): String? =
    runCatching {
        (Json.parseToJsonElement(call.arguments) as? JsonObject)
            ?.get("title")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

/**
 * The calls a collapsed group names: the running one alone, otherwise failures first, capped at
 * three — two once the group is longer (`ComputerActivityCell::display_lines`).
 */
internal fun computerActivityVisibleCalls(calls: List<McpToolCallItem>): List<McpToolCallItem> {
    val active = calls.indexOfLast { it.status == McpToolCallStatus.InProgress }
    if (active >= 0) return listOf(calls[active])
    return calls.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<McpToolCallItem>> {
                it.value.status == McpToolCallStatus.Failed
            }.thenByDescending { it.index },
        )
        .take(if (calls.size > 3) 2 else 3)
        .sortedBy { it.index }
        .map { it.value }
}

/**
 * Adjacent cua_repl calls collapsed under one "Using/Used computer · N actions" header; mirrors
 * `ComputerActivityCell` in `codex-rs/tui/src/history_cell/computer_activity.rs`.
 */
@Composable
internal fun ComputerActivityRow(items: List<ThreadItem>, modifier: Modifier = Modifier) {
    val calls = items.filterIsInstance<McpToolCallItem>()
    val active = computerActivityActive(calls)
    val failures = calls.count { it.status == McpToolCallStatus.Failed }
    var expanded by remember { mutableStateOf(false) }
    val actions =
        stringResource(
            if (calls.size == 1) R.string.computer_activity_action
            else R.string.computer_activity_actions,
            calls.size,
        )
    val label =
        stringResource(
            if (active) R.string.computer_activity_using else R.string.computer_activity_used,
        )
    val failed =
        if (failures > 0) stringResource(R.string.computer_activity_failed, failures) else null
    val unnamed = stringResource(R.string.computer_activity_action_title)
    val visible = computerActivityVisibleCalls(calls)
    val rows = buildList {
        visible.forEach { call ->
            val name = computerActionTitle(call) ?: unnamed
            add(
                if (call.status == McpToolCallStatus.Failed) {
                    stringResource(R.string.computer_activity_failed_action, name)
                } else {
                    name
                }
            )
        }
        // Upstream only points at the remainder once the group has stopped growing.
        if (!active && calls.size > visible.size) {
            add(stringResource(R.string.computer_activity_more, calls.size - visible.size))
        }
    }
    CollapsibleSection(
        title =
            listOfNotNull(label, actions, failed).joinToString(separator = " · "),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        collapsedRows = rows,
        modifier = modifier,
    ) {
        items.forEach { item ->
            when (item) {
                is McpToolCallItem -> McpToolCallCell(item)
                is ReasoningItem -> ReasoningCell(item)
                else -> Unit
            }
        }
    }
}
