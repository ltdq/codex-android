package com.cy.codex.history_cell

import com.cy.codex.protocol.protocol.item.McpToolCallItem

/**
 * One metadata badge above an MCP tool call's payload: the kind fixes the row order, the string
 * carries the raw value (the projection stays Compose-free).
 */
sealed interface McpMetaBadge {
    /** The connector/app the call went through. */
    data class App(val label: String) : McpMetaBadge

    /** The server-derived action slug (codex-rs/core/src/mcp_tool_call.rs); shown verbatim, never humanized. */
    data class Action(val actionName: String) : McpMetaBadge

    data class Plugin(val pluginId: String) : McpMetaBadge

    /** The descriptor declared `readOnlyHint: true`. */
    data object ReadOnly : McpMetaBadge
}

/** Display projection of the app/plugin/read-only metadata of one [McpToolCallItem]. */
data class McpMeta(
    /** Always App, then Action, then Plugin, then ReadOnly — entries only for present fields. */
    val badges: List<McpMetaBadge>,
    /** The app resource uri to link, or null when none was captured. */
    val resourceUri: String?,
)

/**
 * Projects the app/plugin/read-only metadata of one MCP tool call. Android-only: upstream
 * `codex-rs/tui/src/history_cell/mcp.rs` renders none of these fields, so a plain call projects empty.
 */
fun projectMcpMeta(item: McpToolCallItem): McpMeta {
    val context = item.appContext
    val badges = buildList {
        val appLabel = context?.appName?.takeIf { it.isNotBlank() }
            ?: context?.connectorId?.takeIf { it.isNotBlank() }
        if (appLabel != null) add(McpMetaBadge.App(appLabel))
        context?.actionName?.takeIf { it.isNotBlank() }?.let { add(McpMetaBadge.Action(it)) }
        item.pluginId?.takeIf { it.isNotBlank() }?.let { add(McpMetaBadge.Plugin(it)) }
        // readOnlyHint is tri-state on the wire: false and null both mean "declared nothing".
        if (item.readOnlyHint == true) add(McpMetaBadge.ReadOnly)
    }
    // `appResourceUri` already prefers the descriptor's own uri over the legacy field (ThreadItem.kt).
    return McpMeta(badges = badges, resourceUri = item.appResourceUri?.takeIf { it.isNotBlank() })
}
