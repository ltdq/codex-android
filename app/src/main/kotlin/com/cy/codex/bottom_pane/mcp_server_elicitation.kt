package com.cy.codex.bottom_pane

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
import androidx.compose.runtime.key
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
import com.cy.codex.AppEvent
import com.cy.codex.LocalAppEvent
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.label
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.McpApprovalKind
import com.cy.codex.protocol.protocol.v2.McpApprovalMeta
import com.cy.codex.protocol.protocol.v2.McpApprovalPersist
import com.cy.codex.protocol.protocol.v2.McpElicitationField
import com.cy.codex.protocol.protocol.v2.McpElicitationFieldKind
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.v2.McpToolParamDisplay
import com.cy.codex.protocol.protocol.v2.UserVerificationProof
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codex.raisedSurface
import java.text.BreakIterator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `mcpServer/elicitation/request` (codex-rs/tui/src/bottom_pane/mcp_server_elicitation.rs).
 * The submitted map is the whole form; Cancel (upstream's Esc) is not Decline; approval accepts
 * carry no content so the binding sends JSON null rather than `{}`.
 */

/** Enum options are chips: fully rounded, and the only control here that is not a full-width row. */
private val ChipShape = RoundedCornerShape(percent = UiConsts.PillCorner)

private val RequiredBadgeShape = RoundedCornerShape(UiConsts.CornerChip)
private val FieldControlGap = UiConsts.Space8

/** The URL a redirect-mode elicitation points at; a code surface, like every other payload. */
private val UrlShape = RoundedCornerShape(UiConsts.CornerControl)

private val Whitespace = Regex("\\s+")

/** Values of the synthetic `__approval` single-select. */
internal enum class ApprovalChoice(val value: String) {
    Accept("accept"),
    AcceptSession("accept_session"),
    AcceptAlways("accept_always"),
    Decline("decline"),
    Cancel("cancel"),
    ;

    companion object {
        /** An unrecognised value answers Cancel, upstream's catch-all. */
        fun fromValue(value: String): ApprovalChoice =
            entries.firstOrNull { it.value == value } ?: Cancel
    }
}

/** Options a message-only schema derives into; a tool call never offers deny. */
internal fun approvalCardChoices(
    approval: McpApprovalMeta?,
    isTool: Boolean,
): List<ApprovalChoice> {
    val persist = approval?.persist.orEmpty()
    return buildList {
        add(ApprovalChoice.Accept)
        if (McpApprovalPersist.Session in persist) add(ApprovalChoice.AcceptSession)
        if (McpApprovalPersist.Always in persist) add(ApprovalChoice.AcceptAlways)
        if (!isTool) add(ApprovalChoice.Decline)
        add(ApprovalChoice.Cancel)
    }
}

/**
 * Accept-with-persist echoes `{"persist":...}` as `_meta`; no approval answer carries content —
 * the empty map reads as JSON null at the binding layer.
 */
internal fun approvalAnswer(choice: ApprovalChoice): ApprovalResponse.Elicitation =
    when (choice) {
        ApprovalChoice.Accept -> ApprovalResponse.Elicitation(ElicitationAction.Accept)
        ApprovalChoice.AcceptSession ->
            ApprovalResponse.Elicitation(
                ElicitationAction.Accept,
                meta = persistMeta(McpApprovalPersist.Session),
            )
        ApprovalChoice.AcceptAlways ->
            ApprovalResponse.Elicitation(
                ElicitationAction.Accept,
                meta = persistMeta(McpApprovalPersist.Always),
            )
        ApprovalChoice.Decline -> ApprovalResponse.Elicitation(ElicitationAction.Decline)
        ApprovalChoice.Cancel -> ApprovalResponse.Elicitation(ElicitationAction.Cancel)
    }

/** `{"persist":"session"}` / `{"persist":"always"}` — upstream literals (protocol/…/mcp_approval_meta.rs). */
private fun persistMeta(persist: McpApprovalPersist): JsonElement =
    buildJsonObject { put("persist", persist.wire) }

private const val ToolParamDisplayLimit = 3
private const val ToolParamValueMaxGraphemes = 60

/** "display_name: value" lines under the prompt; three are all a card can afford before the decision row scrolls off. */
internal fun toolParamDisplayLines(params: List<McpToolParamDisplay>): List<String> =
    params.take(ToolParamDisplayLimit).map { param ->
        "${param.displayName}: ${formatToolParamValue(param.value)}"
    }

internal fun formatToolParamValue(value: JsonElement): String {
    val formatted =
        when {
            value is JsonPrimitive && value.isString ->
                value.content.trim().split(Whitespace).joinToString(" ")
            else -> value.toString()
        }
    return truncateGraphemes(formatted, ToolParamValueMaxGraphemes)
}

/**
 * Truncate to [max] graphemes, appending "..." within the budget (codex-rs/tui/src/text_formatting.rs).
 * Graphemes, not chars, so an emoji sequence is never cut in half.
 */
internal fun truncateGraphemes(text: String, max: Int): String {
    if (max <= 0) return ""
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val ends = ArrayList<Int>(max + 1)
    while (iterator.next() != BreakIterator.DONE) {
        ends.add(iterator.current())
        if (ends.size == max + 1) break
    }
    if (ends.size <= max) return text
    if (max < 3) return text.substring(0, ends[max - 1])
    val keep = max - 3
    return text.substring(0, if (keep == 0) 0 else ends[keep - 1]) + "..."
}

/** Which card an elicitation form renders (codex-rs/tui/src/bottom_pane/mod.rs). */
internal enum class ElicitationFormKind {
    Approval,

    Suggestion,

    /** A tool suggestion without an install URL: upstream keeps a plain, directly acceptable empty form. */
    EmptyForm,

    Fields,
}

internal fun elicitationFormKind(payload: McpElicitationRequest.Form): ElicitationFormKind {
    val suggestion = payload.approval?.toolSuggestion
    if (suggestion?.installUrl != null) return ElicitationFormKind.Suggestion
    return when {
        payload.fields.isNotEmpty() -> ElicitationFormKind.Fields
        suggestion != null -> ElicitationFormKind.EmptyForm
        else -> ElicitationFormKind.Approval
    }
}

internal fun userVerificationVerifyParams(
    payload: McpElicitationRequest.UserVerification,
): UserVerificationVerifyParams =
    UserVerificationVerifyParams(
        challenge = payload.challenge,
        title = payload.title,
        description = payload.description,
    )

/**
 * Only a produced proof may be accepted (`credentialId`+`signature` are the whole content);
 * anything incomplete cancels the original request (tui/…/user_verification.rs).
 */
internal fun userVerificationAnswer(proof: UserVerificationProof?): ApprovalResponse.Elicitation =
    if (proof == null) {
        ApprovalResponse.Elicitation(ElicitationAction.Cancel)
    } else {
        ApprovalResponse.Elicitation(
            ElicitationAction.Accept,
            mapOf("credentialId" to proof.credentialId, "signature" to proof.signature),
        )
    }

/**
 * Identity, never payload: two queued requests can carry equal `Form`s, and a payload key would
 * carry `submitted` over to the next one, latching both buttons on the non-dismissible sheet.
 */
internal fun elicitationFormStateKey(request: ApprovalRequest.Elicitation): RequestId =
    request.requestId

@Composable
internal fun McpElicitationForm(
    request: ApprovalRequest.Elicitation,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean = false,
) {
    key(elicitationFormStateKey(request)) {
        when (val payload = request.params) {
            is McpElicitationRequest.Url -> McpElicitationUrl(payload, onDecide, busy)

            is McpElicitationRequest.Form ->
                when (elicitationFormKind(payload)) {
                    ElicitationFormKind.Approval -> McpElicitationApproval(payload, onDecide, busy)
                    ElicitationFormKind.Suggestion ->
                        McpElicitationSuggestion(payload, onDecide, busy)
                    ElicitationFormKind.EmptyForm, ElicitationFormKind.Fields ->
                        McpElicitationFields(payload, onDecide, busy)
                }

            is McpElicitationRequest.UserVerification ->
                McpElicitationUserVerification(payload, request.requestId, onDecide, busy)
        }
    }
}

/**
 * The `__approval` card: one single-select plus confirm. Upstream pre-selects the first option
 * (default_idx 0), so one confirm accepts.
 */
@Composable
private fun McpElicitationApproval(
    payload: McpElicitationRequest.Form,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean,
) {
    val isTool = payload.approval?.kind == McpApprovalKind.McpToolCall
    val choices = remember(payload) { approvalCardChoices(payload.approval, isTool) }
    var selected by remember(payload) {
        mutableStateOf(choices.firstOrNull() ?: ApprovalChoice.Cancel)
    }
    var submitted by remember(payload) { mutableStateOf(false) }

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
            val summary =
                toolParamDisplayLines(
                    // Upstream only summarizes tool approvals (is_tool_approval_action).
                    if (isTool) payload.approval?.displayParams.orEmpty() else emptyList()
                )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary.joinToString("\n"),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
            }
            choices.forEachIndexed { index, choice ->
                if (index > 0) Spacer(Modifier.height(UiConsts.Space6))
                RadioButtonPreference(
                    title = approvalOptionLabel(choice),
                    summary = approvalOptionDescription(choice, isTool),
                    radioButtonLocation = RadioButtonLocation.End,
                    selected = selected == choice,
                    onClick = { selected = choice },
                )
            }
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            // The escape hatch is always Cancel, never the selected refusal, so a mis-tap cannot decline.
            confirmLabel = ElicitationAction.Accept.label(),
            enabled = true,
            busy = submitted || busy,
            onConfirm = {
                submitted = true
                onDecide(approvalAnswer(selected))
            },
            onCancel = { onDecide(approvalAnswer(ApprovalChoice.Cancel)) },
        )
    }
}

@Composable
private fun approvalOptionLabel(choice: ApprovalChoice): String =
    stringResource(
        when (choice) {
            ApprovalChoice.Accept -> R.string.mcp_server_elicitation_allow
            ApprovalChoice.AcceptSession -> R.string.mcp_server_elicitation_allow_session
            ApprovalChoice.AcceptAlways -> R.string.mcp_server_elicitation_allow_always
            ApprovalChoice.Decline -> R.string.mcp_server_elicitation_deny
            ApprovalChoice.Cancel -> R.string.mcp_server_elicitation_cancel
        }
    )

@Composable
private fun approvalOptionDescription(choice: ApprovalChoice, isTool: Boolean): String =
    stringResource(
        when {
            choice == ApprovalChoice.Accept && isTool -> R.string.mcp_server_elicitation_allow_tool
            choice == ApprovalChoice.Accept -> R.string.mcp_server_elicitation_allow_request
            choice == ApprovalChoice.AcceptSession && isTool ->
                R.string.mcp_server_elicitation_allow_session_tool
            choice == ApprovalChoice.AcceptSession ->
                R.string.mcp_server_elicitation_allow_session_request
            choice == ApprovalChoice.AcceptAlways && isTool ->
                R.string.mcp_server_elicitation_allow_always_tool
            choice == ApprovalChoice.AcceptAlways ->
                R.string.mcp_server_elicitation_allow_always_request
            choice == ApprovalChoice.Decline -> R.string.mcp_server_elicitation_deny_description
            isTool -> R.string.mcp_server_elicitation_cancel_tool
            else -> R.string.mcp_server_elicitation_cancel_request
        }
    )

/** The open-browser step needs a URL `validateAppLinkUrl` accepted; every other step just accepts. */
internal fun suggestionPrimaryEnabled(
    model: AppLinkSuggestion,
    screen: AppLinkScreen,
): Boolean =
    model.instructions != AppLinkSuggestionInstructions.Install ||
        screen != AppLinkScreen.Link ||
        model.url != null

/**
 * The install/enable card of a `tool_suggestion` with an install URL (codex-rs/tui/src/bottom_pane/mod.rs).
 * Install walks the open-browser-and-return flow of app_link.kt; enable accepts directly.
 */
@Composable
private fun McpElicitationSuggestion(
    payload: McpElicitationRequest.Form,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean,
) {
    val suggestion = payload.approval?.toolSuggestion ?: return
    val model = remember(suggestion) { appLinkSuggestion(suggestion) }
    val colors = MiuixTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var screen by remember(payload) { mutableStateOf(AppLinkScreen.Link) }
    val install = model.instructions == AppLinkSuggestionInstructions.Install

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            Text(
                text = model.title,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.DialogTitle,
                lineHeight = UiType.DialogTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            // The suggestion reason replaces the form message (codex-rs/tui/src/bottom_pane/mod.rs).
            if (suggestion.suggestReason.isNotBlank()) {
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = suggestion.suggestReason,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.DialogFieldGap))
            Text(
                text =
                    if (screen == AppLinkScreen.Confirmation) {
                        stringResource(R.string.app_link_finish_browser_title)
                    } else if (install) {
                        stringResource(R.string.mcp_server_elicitation_suggestion_install_instructions)
                    } else {
                        stringResource(R.string.mcp_server_elicitation_suggestion_enable_instructions)
                    },
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceSecondary,
            )
            if (screen == AppLinkScreen.Confirmation) {
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text = stringResource(R.string.mcp_server_elicitation_suggestion_return_body),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
            }
            model.url?.let { url ->
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                UrlSurface(url)
            }
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        DecisionRow(
            decisions =
                listOf(
                    DecisionAction(
                        label =
                            when {
                                install && screen == AppLinkScreen.Link ->
                                    stringResource(R.string.mcp_server_elicitation_suggestion_install_button)
                                install ->
                                    stringResource(R.string.mcp_server_elicitation_suggestion_installed_button)
                                else ->
                                    stringResource(R.string.mcp_server_elicitation_suggestion_enable_button)
                            },
                        role = DecisionRole.Primary,
                        enabled = suggestionPrimaryEnabled(model, screen),
                    ) {
                        if (install && screen == AppLinkScreen.Link) {
                            model.url?.let { url ->
                                runCatching { uriHandler.openUri(url) }
                                screen = AppLinkScreen.Confirmation
                            }
                        } else {
                            onDecide(ApprovalResponse.Elicitation(ElicitationAction.Accept))
                        }
                    },
                    DecisionAction(
                        label = ElicitationAction.Decline.label(),
                        role = DecisionRole.Secondary,
                    ) {
                        onDecide(ApprovalResponse.Elicitation(ElicitationAction.Decline))
                    },
                    DecisionAction(
                        label = ElicitationAction.Cancel.label(),
                        role = DecisionRole.Destructive,
                    ) {
                        onDecide(ApprovalResponse.Elicitation(ElicitationAction.Cancel))
                    },
                ),
            busy = busy,
        )
    }
}

/**
 * Verify/cancel prompt of an `openai/userVerification` elicitation (codex-rs/tui/src/bottom_pane/user_verification.rs).
 * Verify never answers here: it raises [AppEvent.VerifyUserVerification] and the reducer folds the proof in.
 */
@Composable
private fun McpElicitationUserVerification(
    payload: McpElicitationRequest.UserVerification,
    requestId: RequestId,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean,
) {
    val onAppEvent = LocalAppEvent.current
    var waiting by remember(payload) { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            Text(
                text = payload.title,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.DialogTitle,
                lineHeight = UiType.DialogTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            Text(
                text = stringResource(R.string.user_verification_prompt_server, payload.serverName),
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            if (payload.description.isNotBlank()) {
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text = payload.description,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
            }
            if (waiting) {
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text = stringResource(R.string.user_verification_prompt_waiting),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
            }
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        DecisionRow(
            decisions =
                listOf(
                    DecisionAction(
                        label = stringResource(R.string.user_verification_prompt_verify),
                        role = DecisionRole.Primary,
                    ) {
                        // A missing sink must not look like a verified request.
                        if (!waiting && onAppEvent != null) {
                            waiting = true
                            onAppEvent(
                                AppEvent.VerifyUserVerification(
                                    params = userVerificationVerifyParams(payload),
                                    elicitationRequestId = requestId,
                                ),
                            )
                        }
                    },
                    DecisionAction(
                        label = stringResource(R.string.user_verification_prompt_cancel),
                        role = DecisionRole.Destructive,
                    ) {
                        onDecide(userVerificationAnswer(proof = null))
                    },
                ),
            busy = busy || waiting,
        )
    }
}

@Composable
private fun McpElicitationFields(
    payload: McpElicitationRequest.Form,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean,
) {
    val params = payload.requestedSchema
    val fields = params.fields
    // fieldName -> current raw value; seeded from the schema's `value`.
    val values =
        remember(fields) {
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
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            // The submit button is the protocol's accept action, so it takes that label.
            confirmLabel = ElicitationAction.Accept.label(),
            enabled = complete,
            busy = submitted || busy,
            onConfirm = {
                submitted = true
                onDecide(
                    ApprovalResponse.Elicitation(
                        ElicitationAction.Accept,
                        fields.associate { it.name to values[it.name].orEmpty().trim() },
                    )
                )
            },
            // Upstream Esc semantics: the labelled Cancel answers Cancel, not Decline.
            onCancel = { onDecide(ApprovalResponse.Elicitation(ElicitationAction.Cancel)) },
        )
    }
}

/** URL-mode elicitation in two screens, mirroring bottom_pane/app_link_view.rs; an invalid URL shows no open button. */
@Composable
private fun McpElicitationUrl(
    payload: McpElicitationRequest.Url,
    onDecide: (ApprovalResponse.Elicitation) -> Unit,
    busy: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val prompt = remember(payload) { appLinkPrompt(payload) }
    var screen by remember(payload) { mutableStateOf(AppLinkScreen.Link) }
    val auth = prompt?.kind == AppLinkKind.Auth
    val link = prompt?.url ?: payload.url

    Column(modifier = Modifier.fillMaxWidth()) {
        ApprovalScrollBody {
            if (screen == AppLinkScreen.Confirmation && prompt != null) {
                Text(
                    text =
                        stringResource(
                            if (auth) R.string.app_link_finish_auth_title
                            else R.string.app_link_finish_browser_title
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.DialogTitle,
                    lineHeight = UiType.DialogTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text =
                        stringResource(
                            if (auth) R.string.app_link_finish_auth_body
                            else R.string.app_link_finish_browser_body
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                UrlSurface(link)
            } else {
                Text(
                    text =
                        when {
                            prompt == null ->
                                stringResource(R.string.mcp_server_elicitation_url_message)
                            auth -> prompt.connectorName ?: prompt.connectorId.orEmpty()
                            else -> stringResource(R.string.app_link_external_title)
                        },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.DialogTitle,
                    lineHeight = UiType.DialogTitleLine,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                if (prompt != null && !auth) {
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text =
                            stringResource(
                                R.string.app_link_external_description,
                                prompt.serverName,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
                if (prompt?.message?.isNotBlank() == true) {
                    Spacer(Modifier.height(UiConsts.DialogFieldGap))
                    Text(
                        text = prompt.message,
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = UiType.Body,
                        lineHeight = UiType.BodyLine,
                        color = colors.onSurfaceSecondary,
                    )
                }
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                Text(
                    text =
                        stringResource(
                            if (auth) R.string.app_link_auth_instructions
                            else R.string.app_link_external_instructions
                        ),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceSecondary,
                )
                Spacer(Modifier.height(UiConsts.DialogFieldGap))
                UrlSurface(link)
            }
        }
        Spacer(Modifier.height(UiConsts.DialogFooterGap))
        FormButtons(
            confirmLabel =
                when {
                    prompt == null -> stringResource(R.string.mcp_server_elicitation_url_open)
                    screen == AppLinkScreen.Link && auth ->
                        stringResource(R.string.app_link_open_sign_in)
                    screen == AppLinkScreen.Link -> stringResource(R.string.app_link_open_link)
                    auth -> stringResource(R.string.app_link_signed_in)
                    else -> stringResource(R.string.app_link_finished)
                },
            // A URL that failed validation has no open button; declining is the only way out, as the TUI's rejection of the conversion.
            enabled = prompt != null,
            busy = busy,
            onConfirm = {
                val target = prompt
                if (target != null) {
                    when (screen) {
                        AppLinkScreen.Link -> {
                            runCatching { uriHandler.openUri(target.url) }
                            screen = AppLinkScreen.Confirmation
                        }

                        AppLinkScreen.Confirmation ->
                            onDecide(ApprovalResponse.Elicitation(ElicitationAction.Accept))
                    }
                }
            },
            onCancel = { onDecide(ApprovalResponse.Elicitation(ElicitationAction.Cancel)) },
        )
    }
}

@Composable
private fun UrlSurface(url: String) {
    Text(
        text = url,
        modifier =
            Modifier.fillMaxWidth()
                .background(codeSurface(), UrlShape)
                .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        fontSize = UiType.Code,
        lineHeight = UiType.CodeLine,
        fontFamily = FontFamily.Monospace,
        color = MiuixTheme.colorScheme.primary,
    )
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
                    modifier =
                        Modifier.background(colors.error.copy(alpha = 0.12f), RequiredBadgeShape)
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
            McpElicitationFieldKind.Text ->
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = field.title.ifBlank { field.name },
                    singleLine = true,
                )

            McpElicitationFieldKind.Multiline ->
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = field.title.ifBlank { field.name },
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                )

            McpElicitationFieldKind.Number ->
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = field.title.ifBlank { field.name },
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                )

            McpElicitationFieldKind.Boolean ->
                SwitchPreference(
                    title =
                        if (isTrue(value)) {
                            stringResource(R.string.mcp_server_elicitation_boolean_on)
                        } else {
                            stringResource(R.string.mcp_server_elicitation_boolean_off)
                        },
                    checked = isTrue(value),
                    onCheckedChange = { onValueChange(if (it) "true" else "false") },
                    modifier = Modifier.fillMaxWidth(),
                )

            McpElicitationFieldKind.Enum ->
                EnumChips(
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
                modifier =
                    Modifier.background(
                            if (chosen) colors.primary.copy(alpha = 0.14f) else raisedSurface(),
                            ChipShape,
                        )
                        .border(
                            width = UiConsts.OutlineThickness,
                            color =
                                if (chosen) colors.primary else colors.outline.copy(alpha = 0.3f),
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
                        modifier = Modifier.padding(end = UiConsts.Space5).size(UiConsts.IconCheck),
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
