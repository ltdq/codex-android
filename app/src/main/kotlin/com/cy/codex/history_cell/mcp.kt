package com.cy.codex.history_cell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.R
import com.cy.codex.protocol.protocol.item.DynamicToolCallItem
import com.cy.codex.protocol.protocol.item.DynamicToolOutputContent
import com.cy.codex.protocol.protocol.item.FunctionCallOutputItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.v2.DynamicToolCallStatus
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.CodeBlock
import com.cy.codex.markdown_render.LinkTarget
import com.cy.codex.markdown_render.openLink
import com.cy.codex.ToolCard
import com.cy.codex.ThreadStatusTone
import com.cy.codex.statusDotColor
import com.cy.codex.UiType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Tool calls that are not shell commands: MCP servers, dynamic tools, raw function output;
 * mirrors `codex-rs/tui/src/history_cell/mcp.rs`. */
@Composable
fun McpToolCallCell(item: McpToolCallItem, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val tone = mcpTone(item.status)

    ToolCard(
        icon = MiuixIcons.Link,
        title = stringResource(R.string.mcp_cell_namespaced_title, item.server, item.tool),
        subtitle = item.durationMs?.let {
            stringResource(R.string.mcp_cell_duration, formatToolDuration(it))
        },
        modifier = modifier,
        accent = statusDotColor(tone),
        trailing = { StatusChip(label = mcpLabel(item.status), tone = tone) },
    ) {
        // Both render nothing when the call carries no metadata, so a plain MCP card is unchanged.
        val meta = projectMcpMeta(item)
        McpMetaBadges(meta.badges)
        meta.resourceUri?.let { uri -> McpResourceUriRow(uri) }
        val error = item.error
        val blocks = projectMcpResult(item.result)
        Arguments(item.arguments)
        when {
            !error.isNullOrBlank() -> ErrorOutput(error)
            blocks.isNotEmpty() -> ToolResultBlocks(blocks)
            item.status == McpToolCallStatus.InProgress ->
                PendingOutput(stringResource(R.string.mcp_cell_waiting_for_result))
        }
    }
}

@Composable
fun DynamicToolCallCell(item: DynamicToolCallItem, modifier: Modifier = Modifier) {
    val tone = dynamicTone(item.status)
    val blocks = item.contentItems.map { content ->
        when (content) {
            is DynamicToolOutputContent.InputText -> ToolContentBlock.Text(content.text)
            is DynamicToolOutputContent.InputImage -> ToolContentBlock.Summary(
                stringResource(R.string.tool_cell_returned_image),
            )
            is DynamicToolOutputContent.InputAudio -> ToolContentBlock.Summary(
                stringResource(R.string.tool_cell_audio_content),
            )
        }
    }

    ToolCard(
        icon = MiuixIcons.Tune,
        title = item.namespace?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.mcp_cell_namespaced_title, it, item.tool)
        } ?: item.tool,
        subtitle = item.durationMs?.let {
            stringResource(R.string.mcp_cell_duration, formatToolDuration(it))
        },
        modifier = modifier,
        accent = statusDotColor(tone),
        trailing = { StatusChip(label = dynamicLabel(item.status), tone = tone) },
    ) {
        Arguments(item.arguments)
        when {
            item.success == false -> {
                if (blocks.isNotEmpty()) ToolResultBlocks(blocks)
                ErrorOutput(stringResource(R.string.mcp_cell_tool_call_failed))
            }
            blocks.isNotEmpty() -> ToolResultBlocks(blocks)
            item.status == DynamicToolCallStatus.InProgress ->
                PendingOutput(stringResource(R.string.mcp_cell_waiting_for_result))
        }
    }
}

@Composable
internal fun ToolResultBlocks(blocks: List<ToolContentBlock>, spacing: Dp = 8.dp) {
    val colors = MiuixTheme.colorScheme
    blocks.forEachIndexed { index, block ->
        when (block) {
            is ToolContentBlock.Text -> Text(
                text = block.text,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurface,
            )

            is ToolContentBlock.Summary -> Text(
                text = block.summary,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariantSummary,
            )

            is ToolContentBlock.RawJson -> LimitedCodeBlock(text = block.json)
        }
        if (index != blocks.lastIndex) Spacer(Modifier.height(spacing))
    }
}

@Composable
fun FunctionCallOutputCell(item: FunctionCallOutputItem, modifier: Modifier = Modifier) {
    val output = item.output.trimEnd()
    ToolCard(
        icon = MiuixIcons.Notes,
        title = item.namespace?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.mcp_cell_namespaced_title, it, item.name)
        } ?: item.name,
        modifier = modifier,
    ) {
        if (output.isEmpty()) {
            PendingOutput(stringResource(R.string.mcp_cell_no_output))
        } else {
            LimitedCodeBlock(text = output)
        }
    }
}

/** Metadata chips, styled like the command-action chips of exec.kt; order fixed by [projectMcpMeta]. */
@Composable
private fun McpMetaBadges(
    badges: List<McpMetaBadge>,
    chipSpacing: Dp = 6.dp,
    blockSpacing: Dp = 8.dp,
) {
    if (badges.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
    ) {
        badges.forEach { badge -> MetaChip(text = mcpMetaBadgeLabel(badge)) }
    }
    Spacer(Modifier.height(blockSpacing))
}

@Composable
@ReadOnlyComposable
private fun mcpMetaBadgeLabel(badge: McpMetaBadge): String = when (badge) {
    is McpMetaBadge.App -> badge.label
    is McpMetaBadge.Action -> badge.actionName
    is McpMetaBadge.Plugin -> stringResource(R.string.mcp_cell_plugin_badge, badge.pluginId)
    McpMetaBadge.ReadOnly -> stringResource(R.string.mcp_cell_read_only)
}

/**
 * The app resource the call acted on; tapping follows openLink (markdown_render/local_links.kt).
 * Non-web schemes (app://, plugin://) have no Android handler and must never crash the cell.
 */
@Composable
private fun McpResourceUriRow(
    uri: String,
    iconSpacing: Dp = 6.dp,
    blockSpacing: Dp = 8.dp,
    iconSize: Dp = 13.dp,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val target = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    LinkTarget.Web(uri)
                } else {
                    LinkTarget.Local(uri, null, uri)
                }
                openLink(context, uriHandler, target)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Link,
            contentDescription = stringResource(R.string.mcp_cell_resource_uri),
            modifier = Modifier.size(iconSize),
            tint = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(iconSpacing))
        Text(
            text = uri,
            fontFamily = FontFamily.Monospace,
            fontSize = UiType.Caption,
            lineHeight = UiType.CaptionLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(blockSpacing))
}

@Composable
private fun Arguments(arguments: String, blockSpacing: Dp = 8.dp) {
    val trimmed = arguments.trim()
    if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "null") return
    Column(modifier = Modifier.fillMaxWidth()) {
        CodeBlock(code = trimmed, language = "json")
        Spacer(Modifier.height(blockSpacing))
    }
}

@Composable
private fun ErrorOutput(
    message: String,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.BodyLine,
) {
    Text(
        text = message,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.error,
    )
}

@Composable
private fun PendingOutput(
    message: String,
    fontSize: TextUnit = UiType.Body,
    lineHeight: TextUnit = UiType.SheetTitle,
) {
    Text(
        text = message,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

internal fun mcpTone(status: McpToolCallStatus): ThreadStatusTone = when (status) {
    McpToolCallStatus.InProgress -> ThreadStatusTone.Running
    McpToolCallStatus.Completed -> ThreadStatusTone.Done
    McpToolCallStatus.Failed -> ThreadStatusTone.Failed
}

@Composable
@ReadOnlyComposable
internal fun mcpLabel(status: McpToolCallStatus): String = when (status) {
    McpToolCallStatus.InProgress -> stringResource(R.string.mcp_cell_status_calling)
    McpToolCallStatus.Completed -> stringResource(R.string.mcp_cell_status_called)
    McpToolCallStatus.Failed -> stringResource(R.string.mcp_cell_status_failed)
}

internal fun dynamicTone(status: DynamicToolCallStatus): ThreadStatusTone = when (status) {
    DynamicToolCallStatus.InProgress -> ThreadStatusTone.Running
    DynamicToolCallStatus.Completed -> ThreadStatusTone.Done
    DynamicToolCallStatus.Failed -> ThreadStatusTone.Failed
}

@Composable
@ReadOnlyComposable
internal fun dynamicLabel(status: DynamicToolCallStatus): String = when (status) {
    DynamicToolCallStatus.InProgress -> stringResource(R.string.mcp_cell_dynamic_status_calling)
    DynamicToolCallStatus.Completed -> stringResource(R.string.mcp_cell_dynamic_status_completed)
    DynamicToolCallStatus.Failed -> stringResource(R.string.mcp_cell_dynamic_status_failed)
}
