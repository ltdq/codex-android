package com.cy.codexui.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cy.codexui.R
import com.cy.codexui.protocol.ApprovalRequest
import com.cy.codexui.protocol.ElicitationAction
import com.cy.codexui.protocol.protocol.v2.McpElicitationField
import com.cy.codexui.protocol.protocol.v2.McpElicitationFieldKind
import com.cy.codexui.protocol.protocol.v2.McpElicitationRequest
import com.cy.codexui.label
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.codeSurface
import com.cy.codexui.pressableRow
import com.cy.codexui.raisedSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `mcpServer/elicitation/request` in its form mode: an MCP server asks the user to fill in a schema.
 *
 * Mirrors `codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs`: the flattened
 * [McpElicitationField] list is rendered as a real form, required fields gate the submit button,
 * and the submitted map is the *whole* form rather than only the fields the user touched.
 *
 * Every field is a label (with the required marker beside it), an optional description, and the
 * control. The switch and the enum chips both sit on the same [raisedSurface] the option rows of
 * the other dialog use, so the two forms read as one component.
 */

/** Enum options are chips: fully rounded, and the only control here that is not a full-width row. */
private val ChipShape = RoundedCornerShape(percent = UiConsts.PillCorner)

/** The switch row a boolean field renders as. */
private val BooleanRowShape = RoundedCornerShape(UiConsts.CornerControl)

/** One field's own rhythm. */
private val RequiredBadgeShape = RoundedCornerShape(UiConsts.CornerChip)
private val FieldControlGap = UiConsts.Space8

/** The URL a redirect-mode elicitation points at; a code surface, like every other payload. */
private val UrlShape = RoundedCornerShape(UiConsts.CornerControl)

@Composable
internal fun McpElicitationForm(
    request: ApprovalRequest.Elicitation,
    onSubmit: (Map<String, String>) -> Unit,
    onDecline: () -> Unit,
    busy: Boolean = false,
) {
    // The two wire modes are different interactions: a schema form to fill in, or a page to open
    // and accept. Rendering the URL variant as an empty form lost the URL entirely, which is why it
    // gets its own body.
    when (val payload = request.params) {
        is McpElicitationRequest.Url -> McpElicitationUrl(
            payload = payload,
            onAccept = { onSubmit(emptyMap()) },
            onDecline = onDecline,
            busy = busy,
        )

        is McpElicitationRequest.Form -> McpElicitationFields(
            payload = payload,
            onSubmit = onSubmit,
            onDecline = onDecline,
            busy = busy,
        )
    }
}

@Composable
private fun McpElicitationFields(
    payload: McpElicitationRequest.Form,
    onSubmit: (Map<String, String>) -> Unit,
    onDecline: () -> Unit,
    busy: Boolean,
) {
    val params = payload.requestedSchema
    val fields = params.fields
    // fieldName -> current raw value; seeded from the schema's `value`.
    val values = remember(fields) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.name, it.value) }
        }
    }
    var submitted by remember(fields) { mutableStateOf(false) }

    val missing = fields.count { it.required && values[it.name].orEmpty().isBlank() }
    val complete = missing == 0

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            if (payload.message.isNotBlank()) {
                Text(
                    text = payload.message,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
            }
            fields.forEachIndexed { index, field ->
                if (index > 0) Spacer(Modifier.height(UiConsts.DialogFieldGap))
                ElicitationFieldRow(
                    field = field,
                    value = values[field.name].orEmpty(),
                    onValueChange = { values[field.name] = it },
                )
            }
            if (fields.isEmpty()) {
                Text(
                    text = stringResource(R.string.mcp_server_elicitation_empty_form),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        // The scrolling body stops here and the footer starts: without the gap the last question
        // reads as if it ran into the buttons, which are a different thing entirely.
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            // The submit button *is* the protocol's accept action, so it takes that label rather
            // than keeping a second copy of the same word.
            confirmLabel = ElicitationAction.Accept.label(),
            enabled = complete,
            busy = submitted || busy,
            onConfirm = {
                submitted = true
                onSubmit(fields.associate { it.name to values[it.name].orEmpty().trim() })
            },
            onCancel = onDecline,
        )
    }
}

/**
 * URL-mode elicitation: the server wants the user to visit a page, not to fill a schema.
 *
 * Accepting opens the page in the browser and answers `accept`; there is nothing to submit, so the
 * action and the navigation are the same button press.
 */
@Composable
private fun McpElicitationUrl(
    payload: McpElicitationRequest.Url,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    busy: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            Text(
                text = payload.message.ifBlank { stringResource(R.string.mcp_server_elicitation_url_message) },
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceSecondary,
            )
            Spacer(Modifier.height(UiConsts.DialogFieldGap))
            Text(
                text = payload.url,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(codeSurface(), UrlShape)
                    .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
                fontSize = UiType.Code,
                lineHeight = UiType.CodeLine,
                fontFamily = FontFamily.Monospace,
                color = colors.primary,
            )
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            confirmLabel = stringResource(R.string.mcp_server_elicitation_url_open),
            enabled = payload.url.isNotBlank(),
            busy = busy,
            onConfirm = {
                runCatching { uriHandler.openUri(payload.url) }
                onAccept()
            },
            onCancel = onDecline,
        )
    }
}

@Composable
private fun ElicitationFieldRow(
    field: McpElicitationField,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.title.ifBlank { field.name },
                modifier = Modifier.weight(1f),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            if (field.required) {
                Spacer(Modifier.width(UiConsts.Space8))
                Text(
                    text = stringResource(R.string.mcp_server_elicitation_required),
                    modifier = Modifier
                        .background(colors.error.copy(alpha = 0.12f), RequiredBadgeShape)
                        .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2),
                    fontSize = UiType.Badge,
                    lineHeight = UiType.BadgeLine,
                    fontWeight = FontWeight.Medium,
                    color = colors.error,
                )
            }
        }
        if (field.description.isNotBlank()) {
            Spacer(Modifier.height(UiConsts.Space2))
            Text(
                text = field.description,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(FieldControlGap))
        when (field.kind) {
            McpElicitationFieldKind.Text -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = true,
            )

            McpElicitationFieldKind.Multiline -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
            )

            McpElicitationFieldKind.Number -> TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = field.title.ifBlank { field.name },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
            )

            McpElicitationFieldKind.Boolean -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressableRow(
                            shape = BooleanRowShape,
                            container = raisedSurface(),
                            onClick = { onValueChange(if (isTrue(value)) "false" else "true") },
                        )
                        .padding(
                            horizontal = UiConsts.Space16,
                            vertical = UiConsts.Space8,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isTrue(value)) {
                            stringResource(R.string.mcp_server_elicitation_boolean_on)
                        } else {
                            stringResource(R.string.mcp_server_elicitation_boolean_off)
                        },
                        modifier = Modifier.weight(1f),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurface,
                    )
                    Switch(
                        checked = isTrue(value),
                        onCheckedChange = { onValueChange(if (it) "true" else "false") },
                    )
                }
            }

            McpElicitationFieldKind.Enum -> EnumChips(
                options = field.options,
                selected = value,
                onSelect = onValueChange,
            )
        }
    }
}

/** Option chips for an enum field; an unchosen enum reads as unanswered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    if (options.isEmpty()) {
        Text(
            text = stringResource(R.string.mcp_server_elicitation_enum_unavailable),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        options.forEach { option ->
            val chosen = option == selected
            Row(
                modifier = Modifier
                    .background(
                        if (chosen) colors.primary.copy(alpha = 0.14f) else raisedSurface(),
                        ChipShape,
                    )
                    .border(
                        width = UiConsts.OutlineThickness,
                        color = if (chosen) colors.primary else colors.outline.copy(alpha = 0.3f),
                        shape = ChipShape,
                    )
                    .clickable { onSelect(option) }
                    .padding(
                        horizontal = UiConsts.Space16,
                        vertical = UiConsts.Space8,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chosen) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = UiConsts.Space5)
                            .size(UiConsts.IconCheck),
                        tint = colors.primary,
                    )
                }
                Text(
                    text = option,
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                    color = if (chosen) colors.primary else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `Boolean` elicitation fields arrive as strings; anything truthy opens the switch. */
private fun isTrue(value: String): Boolean =
    value.trim().lowercase() in setOf("true", "1", "yes", "on")
