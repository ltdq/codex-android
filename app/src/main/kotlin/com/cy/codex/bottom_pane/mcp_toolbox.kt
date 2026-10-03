package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.history_cell.ToolResultBlocks
import com.cy.codex.history_cell.projectMcpResult
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.v2.McpResourceReadResponse
import com.cy.codex.protocol.protocol.v2.McpServerToolCallResponse
import com.cy.codex.successColor
import com.cy.codex.warningColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One MCP server's manual surface: read a resource, call a tool, watch events. Three cards
 * because on the wire each is its own request family; no refresh in the header because nothing
 * here is a snapshot that could go stale.
 *
 * @param server the connected server every request on this page is addressed to.
 * @param onEvent hands the stream switch to the app, which owns the stream's lifecycle.
 */
@Composable
fun McpToolboxScreen(
    server: String,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var resourceUri by remember(server) { mutableStateOf("") }
    var resource by remember(server) { mutableStateOf<McpResourceReadResponse?>(null) }
    var resourceFailure by remember(server) { mutableStateOf<String?>(null) }
    var reading by remember(server) { mutableStateOf(false) }

    var toolName by remember(server) { mutableStateOf("") }
    var toolArguments by remember(server) { mutableStateOf(NoArguments) }
    var toolResult by remember(server) { mutableStateOf<McpServerToolCallResponse?>(null) }
    var toolFailure by remember(server) { mutableStateOf<String?>(null) }
    var calling by remember(server) { mutableStateOf(false) }

    var streaming by remember(server) { mutableStateOf(false) }
    val streamEvents = remember(server) { mutableStateListOf<String>() }

    // The failure branches run inside a coroutine, which cannot read a string resource.
    val readFailureText = stringResource(R.string.mcp_toolbox_resource_failed)
    val callFailureText = stringResource(R.string.mcp_toolbox_tool_failed)

    // The stream is the app's, picked out of the client's single event flow; only each
    // notification's method is showable (the subscription id is the app's). Keep the tail:
    // a chatty server must not grow this page's composition without bound.
    LaunchedEffect(server, client) {
        client.events.collect { event ->
            if (event is AppServerEvent.McpServerEvent) {
                streamEvents += event.delta.notification.method
                if (streamEvents.size > StreamEventLimit) streamEvents.removeAt(0)
            }
        }
    }

    /**
     * Read [resourceUri], replacing whatever the previous read returned. The blank guard lives
     * here so the card's button and the field's IME action cannot disagree.
     */
    fun readResource() {
        val uri = resourceUri.trim()
        if (uri.isEmpty() || reading) return
        reading = true
        scope.launch {
            client
                .readMcpResource(server, uri)
                .onSuccess {
                    resource = it
                    resourceFailure = null
                }
                .onFailure {
                    resource = null
                    resourceFailure = it.message ?: readFailureText
                }
            reading = false
        }
    }

    /**
     * Call [toolName] with [toolArguments] as the JSON text the field holds — the server decodes
     * it, and a blank field means "no arguments", spelled as an empty object.
     */
    fun callTool() {
        val name = toolName.trim()
        if (name.isEmpty() || calling) return
        calling = true
        scope.launch {
            client
                .callMcpTool(
                    server = server,
                    tool = name,
                    arguments = toolArguments.ifBlank { NoArguments },
                )
                .onSuccess {
                    toolResult = it
                    toolFailure = null
                }
                .onFailure {
                    toolResult = null
                    toolFailure = it.message ?: callFailureText
                }
            calling = false
        }
    }

    CodexPage(
        title = stringResource(R.string.mcp_toolbox_title),
        description = server,
        onBack = onBack,
        modifier = modifier,
    ) {
        ResourceSection(
            uri = resourceUri,
            onUriChange = { resourceUri = it },
            reading = reading,
            response = resource,
            failure = resourceFailure,
            onRead = { readResource() },
        )
        ToolSection(
            tool = toolName,
            onToolChange = { toolName = it },
            arguments = toolArguments,
            onArgumentsChange = { toolArguments = it },
            calling = calling,
            response = toolResult,
            failure = toolFailure,
            onCall = { callTool() },
        )
    }
}

/** Resource half: a uri in, one body out. The mime type is shown, not guessed; the first content renders. */
@Composable
private fun ResourceSection(
    uri: String,
    onUriChange: (String) -> Unit,
    reading: Boolean,
    response: McpResourceReadResponse?,
    failure: String?,
    onRead: () -> Unit,
) {
    CodexSection(stringResource(R.string.mcp_toolbox_resource_section)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space8)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset)) {
                Text(
                    text = stringResource(R.string.mcp_toolbox_resource_uri),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = uri,
                    onValueChange = onUriChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mcp_toolbox_resource_uri_placeholder),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardActions =
                        KeyboardActions(
                            onDone = { onRead() },
                            onGo = { onRead() },
                            onSend = { onRead() },
                        ),
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Button(
                onClick = onRead,
                modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset),
                enabled = uri.isNotBlank() && !reading,
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.mcp_toolbox_resource_read),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (failure != null) {
                Spacer(Modifier.height(UiConsts.Space8))
                ServerFailure(text = failure)
            }
            if (response != null) {
                // A read may answer with several contents; render the first.
                val content = response.contents.firstOrNull()
                Spacer(Modifier.height(UiConsts.Space8))
                CodexValueRow(
                    title = stringResource(R.string.mcp_toolbox_resource_uri_label),
                    value = content?.uri.orEmpty().ifEmpty { "—" },
                )
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.mcp_toolbox_resource_mime),
                    value = content?.mimeType.orEmpty().ifEmpty { "—" },
                    monospace = false,
                )
                Spacer(Modifier.height(UiConsts.Space8))
                val body = content?.text
                if (body.isNullOrEmpty()) {
                    // No text can mean bytes or empty; saying so separates "the server had nothing" from "the page lost it".
                    Text(
                        text = stringResource(R.string.mcp_toolbox_resource_no_text),
                        modifier = Modifier.padding(horizontal = UiConsts.RowInset),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = warningColor(),
                    )
                } else {
                    MonospaceOutput(text = body)
                }
            }
        }
    }
}

/** Tool half: a name, JSON arguments, and the answer; `isError` is the tool's own failure and still renders. */
@Composable
private fun ToolSection(
    tool: String,
    onToolChange: (String) -> Unit,
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    calling: Boolean,
    response: McpServerToolCallResponse?,
    failure: String?,
    onCall: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val emptyOutput = stringResource(R.string.mcp_toolbox_result_empty)
    CodexSection(stringResource(R.string.mcp_toolbox_tool_section)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space8)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset)) {
                Text(
                    text = stringResource(R.string.mcp_toolbox_tool_name),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = tool,
                    onValueChange = onToolChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mcp_toolbox_tool_name_placeholder),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardActions =
                        KeyboardActions(
                            onDone = { onCall() },
                            onGo = { onCall() },
                            onSend = { onCall() },
                        ),
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset)) {
                Text(
                    text = stringResource(R.string.mcp_toolbox_tool_arguments),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = arguments,
                    onValueChange = onArgumentsChange,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.mcp_toolbox_tool_arguments_placeholder),
                    useLabelAsPlaceholder = true,
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Button(
                onClick = onCall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset),
                enabled = tool.isNotBlank() && !calling,
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeight / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeight,
                insideMargin =
                    PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.mcp_toolbox_tool_call),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (failure != null) {
                Spacer(Modifier.height(UiConsts.Space8))
                ServerFailure(text = failure)
            }
            if (response != null) {
                Spacer(Modifier.height(UiConsts.Space8))
                Text(
                    text =
                        if (response.isError) {
                            stringResource(R.string.mcp_toolbox_tool_error)
                        } else {
                            stringResource(R.string.mcp_toolbox_tool_ok)
                        },
                    modifier = Modifier.padding(horizontal = UiConsts.RowInset),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = if (response.isError) colors.error else successColor(),
                )
                Spacer(Modifier.height(UiConsts.Space6))
                val blocks = projectMcpResult(response.result)
                if (blocks.isEmpty()) {
                    MonospaceOutput(text = response.result.ifEmpty { emptyOutput })
                } else {
                    ToolResultBlocks(blocks)
                }
            }
        }
    }
}

/** Stream half: one switch plus pushed events; the local boolean is intent because the protocol has no "is open" read. */
@Composable
private fun StreamSection(
    streaming: Boolean,
    events: List<String>,
    onStreamingChange: (Boolean) -> Unit,
) {
    CodexSection(stringResource(R.string.mcp_toolbox_stream_section)) {
        CodexSwitchRow(
            title = stringResource(R.string.mcp_toolbox_stream_switch),
            summary = stringResource(R.string.mcp_toolbox_stream_switch_detail),
            checked = streaming,
            onCheckedChange = onStreamingChange,
        )
        if (events.isEmpty()) {
            CodexRow(title = stringResource(R.string.mcp_toolbox_stream_empty), enabled = false)
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space8)) {
                MonospaceOutput(text = events.joinToString("\n"))
            }
        }
    }
}

/** A failure the *server* reported; shown instead of an empty result pane. */
@Composable
private fun ServerFailure(text: String) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.RowInset)
                .clip(OutputShape)
                .background(colors.error.copy(alpha = 0.12f))
                .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = colors.error,
    )
}

/** Raw server text: monospace, bounded, scrolling both ways so JSON lines are neither clipped nor reflowed. */
@Composable
private fun MonospaceOutput(text: String) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.RowInset)
                .clip(OutputShape)
                .background(codeSurface())
                .heightIn(min = OutputMinHeight, max = OutputMaxHeight)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(UiConsts.Space10)
    ) {
        Text(
            text = text,
            fontSize = UiType.Code,
            lineHeight = UiType.CodeLine,
            fontFamily = FontFamily.Monospace,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

private val OutputShape = RoundedCornerShape(UiConsts.CornerControl)

/** Floor of the output box, so an empty answer still reads as a pane. */
private val OutputMinHeight = 56.dp

/** Ceiling of the output box; the page scrolls as a whole, so a taller box would require a second scroll. */
private val OutputMaxHeight = 260.dp

/** Stream events kept: enough to read a burst, small enough to bound a chatty server. */
private const val StreamEventLimit = 50

/** The protocol's spelling of "this tool takes no arguments". */
private const val NoArguments = "{}"
