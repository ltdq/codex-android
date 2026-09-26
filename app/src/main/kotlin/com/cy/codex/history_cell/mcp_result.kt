package com.cy.codex.history_cell

import com.cy.codex.protocol.protocol.Json
import com.cy.codex.protocol.protocol.array
import com.cy.codex.protocol.protocol.objectOrNull
import com.cy.codex.protocol.protocol.text
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Display projection of an MCP `CallToolResult` (`codex-rs/tui/src/history_cell/mcp_result.rs`):
 * text verbatim, media/resource as summaries, unrecognised blocks keep their exact JSON. */
sealed interface ToolContentBlock {
    data class Text(val text: String) : ToolContentBlock
    data class Summary(val summary: String) : ToolContentBlock
    data class RawJson(val json: String) : ToolContentBlock
}

/** Older history stored the whole payload as a string; keep it as one [RawJson] block. */
fun projectMcpResult(result: String?): List<ToolContentBlock> {
    val body = result?.takeIf { it.isNotBlank() } ?: return emptyList()
    val obj = Json.parseOrNull(body) as? JsonObject ?: return listOf(ToolContentBlock.RawJson(body))
    if (!obj.containsKey("content")) return listOf(ToolContentBlock.RawJson(body))
    return obj.array("content").map(::projectMcpContentBlock)
}

private fun projectMcpContentBlock(block: JsonElement): ToolContentBlock {
    val o = block as? JsonObject ?: return ToolContentBlock.RawJson(Json.write(block))
    return when (o.text("type")) {
        "text" -> ToolContentBlock.Text(o.text("text").orEmpty())
        "image" -> ToolContentBlock.Summary("Returned image")
        "audio" -> ToolContentBlock.Summary("<audio content>")
        "resource" -> {
            val uri = o.objectOrNull("resource")?.let { it.text("uri") ?: it.objectOrNull("resource")?.text("uri") }
            ToolContentBlock.Summary(uri?.let { "embedded resource: $it" } ?: "<unknown embedded resource>")
        }
        "resource_link" -> ToolContentBlock.Summary(o.text("uri")?.let { "link: $it" } ?: "<unknown resource link>")
        else -> ToolContentBlock.RawJson(Json.write(block))
    }
}
