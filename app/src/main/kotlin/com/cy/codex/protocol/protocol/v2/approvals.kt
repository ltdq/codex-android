package com.cy.codex.protocol.protocol.v2

import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.stringOrNull
import com.cy.codex.protocol.protocol.text
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parameters of the four approval families the client has to answer, plus their response bodies.
 *
 * Mirrors `schema/typescript/v2/{CommandExecutionRequestApprovalParams, FileChangeRequestApprovalParams,
 * PermissionsRequestApprovalParams, ToolRequestUserInputParams, McpServerElicitationRequestParams}.ts`.
 * The deprecated v1 `execCommandApproval` / `applyPatchApproval` pair stays registered in
 * [ServerRequestMethod] but is not answered: this client speaks the v2 methods only.
 */

data class CommandExecutionApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val startedAtMs: Long = 0L,
    val approvalId: String? = null,
    val environmentId: String? = null,
    val reason: String? = null,
    val command: String? = null,
    val cwd: String? = null,
    val commandActions: List<CommandAction> = emptyList(),
    /** Rules that accepting with the execpolicy amendment would install, as whole rules. */
    val proposedExecpolicyAmendment: List<String>? = null,
    /** Host rules `ApplyNetworkPolicyAmendment` can enact; an empty list means none proposed. */
    val proposedNetworkPolicyAmendments: List<NetworkPolicyAmendment> = emptyList(),
    /**
     * Which decisions the server will accept. Empty means the server did not say, and the UI falls
     * back to accept / decline.
     */
    val availableDecisions: List<CommandExecutionApprovalDecision> = emptyList(),
)

/**
 * Parameters of `item/fileChange/requestApproval`.
 *
 * The request names the item under review but does not repeat its diff — the wire has no `changes`
 * field (`FileChangeRequestApprovalParams` in codex-rs/app-server-protocol/src/protocol/v2/item.rs);
 * the patch is recovered from the `FileChangeItem` the same ids point at.
 */
data class FileChangeApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val startedAtMs: Long = 0L,
    val reason: String? = null,
    val grantRoot: String? = null,
)

data class PermissionsApprovalParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val environmentId: String? = null,
    val startedAtMs: Long = 0L,
    val cwd: String = "",
    val reason: String? = null,
    val permissions: RequestPermissionProfile = RequestPermissionProfile(),
)

/** Snapshot of what the agent is asking for; rendered as a checklist on the approval card. */
data class RequestPermissionProfile(
    val network: Boolean = false,
    val fileSystemRead: List<String> = emptyList(),
    val fileSystemWrite: List<String> = emptyList(),
    val shell: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !network && fileSystemRead.isEmpty() && fileSystemWrite.isEmpty() && !shell
}

data class ToolRequestUserInputParams(
    val threadId: String,
    val turnId: String,
    val itemId: String,
    val questions: List<ToolRequestUserInputQuestion>,
    val isBlocking: Boolean = true,
)

/** Flattened JSON Schema handed to the elicitation form renderer. */
data class McpElicitationSchema(
    val title: String = "",
    val fields: List<McpElicitationField> = emptyList(),
)

/**
 * Typed `_meta` approval payload of an MCP elicitation (codex-rs/protocol/src/mcp_approval_meta.rs).
 * Non-approval payloads stay raw on [McpElicitationRequest.Form.meta].
 */
data class McpApprovalMeta(
    val kind: McpApprovalKind,
    val persist: Set<McpApprovalPersist> = emptySet(),
    /** Kept untyped because upstream accepts any JSON here. */
    val toolParams: JsonElement? = null,
    /** Parsed entries in wire order. */
    val toolParamsDisplay: List<McpToolParamDisplay> = emptyList(),
    /** Set only for `tool_suggestion` payloads that parsed all-or-nothing. */
    val toolSuggestion: McpToolSuggestion? = null,
) {
    /**
     * Card summary: `tool_params_display` when non-empty, else `tool_params` sorted by name
     * (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs).
     */
    val displayParams: List<McpToolParamDisplay>
        get() =
            toolParamsDisplay.ifEmpty {
                (toolParams as? JsonObject).orEmpty().entries
                    .map { (name, value) -> McpToolParamDisplay(name, value, name) }
                    .sortedBy { it.name }
            }

    companion object {
        // Approval keys of codex-rs/protocol/src/mcp_approval_meta.rs.
        private const val KindKey = "codex_approval_kind"
        private const val PersistKey = "persist"
        private const val ToolParamsKey = "tool_params"
        private const val ToolParamsDisplayKey = "tool_params_display"
        // Tool-suggestion keys of codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs.
        private const val ToolTypeKey = "tool_type"
        private const val SuggestTypeKey = "suggest_type"
        private const val SuggestReasonKey = "suggest_reason"
        private const val ToolIdKey = "tool_id"
        private const val ToolNameKey = "tool_name"
        private const val InstallUrlKey = "install_url"
        // Entry keys of codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs.
        private const val EntryNameKey = "name"
        private const val EntryValueKey = "value"
        private const val EntryDisplayNameKey = "display_name"

        /** Parses a wire `_meta` approval payload; null when absent or unrecognized. */
        fun parse(meta: JsonElement?): McpApprovalMeta? {
            val o = meta as? JsonObject ?: return null
            val kind = McpApprovalKind.fromWire(o.text(KindKey) ?: return null) ?: return null
            return McpApprovalMeta(
                kind = kind,
                persist = parsePersist(o[PersistKey]),
                toolParams = o[ToolParamsKey],
                toolParamsDisplay = o.array(ToolParamsDisplayKey).mapNotNull(::parseDisplayParam),
                toolSuggestion = if (kind == McpApprovalKind.ToolSuggestion) parseToolSuggestion(o) else null,
            )
        }

        /**
         * `persist` is one string or an array on the wire (codex-rs/core/src/mcp_tool_call.rs);
         * anything else reads as no persist choice.
         */
        private fun parsePersist(value: JsonElement?): Set<McpApprovalPersist> {
            val texts = when (value) {
                is JsonPrimitive -> listOfNotNull(value.stringOrNull())
                is JsonArray -> value.mapNotNull { it.stringOrNull() }
                else -> emptyList()
            }
            return McpApprovalPersist.entries.filterTo(linkedSetOf()) { it.wire in texts }
        }

        /**
         * One entry, null when unusable; a missing `display_name` falls back to the name
         * (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs).
         */
        private fun parseDisplayParam(value: JsonElement): McpToolParamDisplay? {
            val o = value as? JsonObject ?: return null
            val name = o.text(EntryNameKey)?.trim().orEmpty()
            if (name.isEmpty()) return null
            val displayName = when (val raw = o.text(EntryDisplayNameKey)) {
                null -> name
                else -> raw.trim().ifEmpty { return null }
            }
            val entryValue = o[EntryValueKey] ?: return null
            return McpToolParamDisplay(name, entryValue, displayName)
        }

        /**
         * All-or-nothing (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs): one unusable
         * field discards the suggestion. Android also rejects empty required strings, unlike upstream.
         */
        private fun parseToolSuggestion(o: JsonObject): McpToolSuggestion? {
            val toolType = when (o.text(ToolTypeKey)) {
                "connector" -> McpToolSuggestionToolType.Connector
                "plugin" -> McpToolSuggestionToolType.Plugin
                else -> return null
            }
            val suggestType = when (o.text(SuggestTypeKey)) {
                "install" -> McpToolSuggestionType.Install
                "enable" -> McpToolSuggestionType.Enable
                else -> return null
            }
            val suggestReason = o.text(SuggestReasonKey)?.takeIf { it.isNotEmpty() } ?: return null
            val toolId = o.text(ToolIdKey)?.takeIf { it.isNotEmpty() } ?: return null
            val toolName = o.text(ToolNameKey)?.takeIf { it.isNotEmpty() } ?: return null
            return McpToolSuggestion(
                toolType = toolType,
                suggestType = suggestType,
                suggestReason = suggestReason,
                toolId = toolId,
                toolName = toolName,
                installUrl = o.text(InstallUrlKey),
            )
        }
    }
}

/** Values of `codex_approval_kind` (codex-rs/protocol/src/mcp_approval_meta.rs). */
enum class McpApprovalKind(val wire: String) {
    McpToolCall("mcp_tool_call"),
    ToolSuggestion("tool_suggestion"),
    BrowserAuth("browser_auth"),
    ;

    companion object {
        fun fromWire(value: String): McpApprovalKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Values of the `persist` key (codex-rs/protocol/src/mcp_approval_meta.rs). */
enum class McpApprovalPersist(val wire: String) {
    Session("session"),
    Always("always"),
}

/** One `tool_params_display` entry (codex-rs/core/src/mcp_tool_approval_templates.rs). */
data class McpToolParamDisplay(
    val name: String,
    val value: JsonElement,
    val displayName: String,
)

/** The install/enable suggestion a `tool_suggestion` payload asks the user to act on. */
data class McpToolSuggestion(
    val toolType: McpToolSuggestionToolType,
    val suggestType: McpToolSuggestionType,
    val suggestReason: String,
    val toolId: String,
    val toolName: String,
    /** Absent for suggestions that are not card-shaped. */
    val installUrl: String? = null,
)

/** Wire values of `tool_type` (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs). */
enum class McpToolSuggestionToolType(val wire: String) {
    Connector("connector"),
    Plugin("plugin"),
}

/** Wire values of `suggest_type` (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs). */
enum class McpToolSuggestionType(val wire: String) {
    Install("install"),
    Enable("enable"),
}

/** `item/tool/call`: the server asks the client to run a dynamically declared tool. */
data class DynamicToolCallParams(
    val threadId: String,
    val turnId: String,
    val callId: String,
    val namespace: String? = null,
    val tool: String,
    val arguments: String = "{}",
)

data class DynamicToolCallResponse(
    val success: Boolean,
    val contentItems: List<String> = emptyList(),
)
