package com.cy.codex.bottom_pane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.FileDiffRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.fileDiffOf
import com.cy.codex.label
import com.cy.codex.parseUnifiedDiff
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.bottom_pane.request_user_input.RequestUserInputForm
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ElicitationAction
import com.cy.codex.protocol.protocol.v2.CommandAction
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.protocol.protocol.v2.DynamicToolCallParams
import com.cy.codex.protocol.protocol.v2.DynamicToolCallResponse
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalParams
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.theme.HideStatusBarInWindow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Non-dismissible sheet until a decision is submitted (codex-rs/tui/src/bottom_pane/approval_overlay.rs). */
@Composable
fun ApprovalDialog(
    request: ApprovalRequest?,
    onDecision: (ApprovalRequest, ApprovalResponse) -> Unit,
    remainingQueue: Int = 0,
    busy: Boolean = false,
    error: String? = null,
    /** Resolves the patch behind a file-change request, which names its item but not its diff. */
    patchChanges: (ApprovalRequest) -> List<FileUpdateChange> = { emptyList() },
) {
    // The dialog outlives the request by one exit animation; keep the last one mounted.
    var lastRequest by remember { mutableStateOf<ApprovalRequest?>(null) }
    if (request != null) lastRequest = request
    val shown = request ?: lastRequest
    // Same rule for the diff: resolved while live, kept through the exit animation.
    var lastChanges by remember { mutableStateOf<List<FileUpdateChange>>(emptyList()) }
    if (request != null) lastChanges = patchChanges(request)

    // One decision per request: a second tap during the exit animation would answer a resolved one.

    WindowBottomSheet(
        show = request != null,
        allowDismiss = false,
        onDismissFinished = {
            lastRequest = null
            lastChanges = emptyList()
        },
        onDismissRequest = {},
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                HideStatusBarInWindow()
                if (shown != null) {
                    ApprovalBody(
                        request = shown,
                        decide = { response ->
                            if (request != null && !busy) {
                                onDecision(shown, response)
                            }
                        },
                        remainingQueue = remainingQueue,
                        busy = busy,
                        patchChanges = lastChanges,
                    )
                    error?.let {
                        Text(it, color = MiuixTheme.colorScheme.error, fontSize = UiType.Meta)
                    }
                }
            }
        }
    }
}

/** Bounds the body against window height: wrap-content measures unbounded, and the footer must stay visible. */
@Composable
internal fun ApprovalScrollBody(
    maxHeightFraction: Float = UiConsts.DialogBodyMaxHeightFraction,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(
                    max =
                        (windowHeight * maxHeightFraction).coerceAtLeast(
                            UiConsts.DialogBodyMinHeight
                        )
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = UiConsts.Space2),
        content = content,
    )
}

@Composable
private fun ApprovalBody(
    request: ApprovalRequest,
    decide: (ApprovalResponse) -> Unit,
    remainingQueue: Int,
    busy: Boolean,
    patchChanges: List<FileUpdateChange>,
) {
    // Decision buttons stay pinned under the scrolling body; the two form families bring their own
    // footer because their primary action has a not-yet-valid state that must stay visible.
    Column(modifier = Modifier.fillMaxWidth()) {
        when (request) {
            is ApprovalRequest.UserInput ->
                RequestUserInputForm(
                    request = request,
                    onSubmit = { decide(ApprovalResponse.UserInput(it)) },
                    onCancel = { decide(ApprovalResponse.UserInput(emptyList())) },
                    busy = busy,
                )

            is ApprovalRequest.Elicitation ->
                McpElicitationForm(
                    request = request,
                    onSubmit = {
                        decide(ApprovalResponse.Elicitation(ElicitationAction.Accept, it))
                    },
                    onDecline = { decide(ApprovalResponse.Elicitation(ElicitationAction.Decline)) },
                    busy = busy,
                )

            else -> {
                ApprovalHeader(request = request, patchChanges = patchChanges)
                Spacer(Modifier.height(UiConsts.DialogHeaderGap))
                ApprovalScrollBody {
                    when (request) {
                        is ApprovalRequest.Exec -> ExecBody(request.params)
                        is ApprovalRequest.ApplyPatch -> PatchBody(request.params, patchChanges)
                        is ApprovalRequest.Permissions -> PermissionsBody(request.params)
                        is ApprovalRequest.DynamicTool -> DynamicToolBody(request.params)
                        else -> Unit
                    }
                }
                Spacer(Modifier.height(UiConsts.DialogFooterGap))
                DecisionRow(decisionsFor(request, decide), busy = busy)
                RemainingQueueLine(remainingQueue)
            }
        }
    }
}

/** One-line identity of the request and why it is asked; the icon is color-coded by kind, not severity. */
@Composable
private fun ApprovalHeader(request: ApprovalRequest, patchChanges: List<FileUpdateChange>) {
    val colors = MiuixTheme.colorScheme
    val accent = approvalAccent(request)
    val title = approvalTitle(request)
    val summary = approvalSummary(request, patchChanges)
    val shape = remember { RoundedCornerShape(UiConsts.CornerControl) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier.size(UiConsts.DialogIconBox).background(accent.copy(alpha = 0.14f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = approvalIcon(request),
                contentDescription = null,
                modifier = Modifier.size(UiConsts.IconRow),
                tint = accent,
            )
        }
        Spacer(Modifier.width(UiConsts.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = UiType.DialogTitle,
                lineHeight = UiType.DialogTitleLine,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = UiConsts.Space3),
                fontSize = UiType.DialogSummary,
                lineHeight = UiType.DialogSummaryLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@ReadOnlyComposable
private fun approvalTitle(request: ApprovalRequest): String =
    when (request) {
        is ApprovalRequest.Exec -> stringResource(R.string.approval_overlay_exec_title)
        is ApprovalRequest.ApplyPatch -> stringResource(R.string.approval_overlay_patch_title)
        is ApprovalRequest.Permissions ->
            stringResource(R.string.approval_overlay_permissions_title)
        is ApprovalRequest.UserInput -> stringResource(R.string.approval_overlay_user_input_title)
        is ApprovalRequest.Elicitation -> {
            val schemaTitle =
                (request.params as? McpElicitationRequest.Form)?.requestedSchema?.title.orEmpty()
            if (schemaTitle.isBlank()) {
                stringResource(R.string.approval_overlay_elicitation_title_fallback)
            } else {
                schemaTitle
            }
        }

        is ApprovalRequest.DynamicTool ->
            stringResource(R.string.approval_overlay_dynamic_tool_title)

        // Host handshakes answered by the reducer before reaching here; rendering keeps the `when` exhaustive so a broken auto-answer path is visible.
        is ApprovalRequest.ChatgptAuthTokensRefresh ->
            stringResource(R.string.approval_overlay_tokens_title)
        is ApprovalRequest.AttestationGenerate ->
            stringResource(R.string.approval_overlay_attestation_title)
        is ApprovalRequest.CurrentTimeRead -> stringResource(R.string.approval_overlay_clock_title)
    }

@Composable
@ReadOnlyComposable
private fun approvalSummary(
    request: ApprovalRequest,
    patchChanges: List<FileUpdateChange>,
): String =
    when (request) {
        is ApprovalRequest.Exec ->
            request.params.reason?.takeIf { it.isNotBlank() }
                ?: request.params.cwd?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.approval_overlay_exec_summary_fallback)

        is ApprovalRequest.ApplyPatch ->
            request.params.reason?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.approval_overlay_patch_summary, patchChanges.size)

        is ApprovalRequest.Permissions ->
            request.params.reason?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.approval_overlay_permissions_summary_fallback)

        is ApprovalRequest.UserInput -> {
            val total = request.params.questions.size
            if (total == 0) {
                stringResource(R.string.approval_overlay_user_input_summary_waiting)
            } else {
                stringResource(R.string.approval_overlay_user_input_summary_questions, total)
            }
        }

        is ApprovalRequest.Elicitation ->
            stringResource(R.string.approval_overlay_elicitation_summary, request.params.serverName)

        is ApprovalRequest.DynamicTool ->
            request.params.namespace
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    stringResource(
                        R.string.approval_overlay_dynamic_tool_qualified,
                        it,
                        request.params.tool,
                    )
                } ?: stringResource(R.string.approval_overlay_dynamic_tool_summary_fallback)

        is ApprovalRequest.ChatgptAuthTokensRefresh ->
            request.reason?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.approval_overlay_tokens_summary)

        is ApprovalRequest.AttestationGenerate ->
            stringResource(R.string.approval_overlay_attestation_summary)
        is ApprovalRequest.CurrentTimeRead ->
            stringResource(R.string.approval_overlay_clock_summary)
    }

private fun approvalIcon(request: ApprovalRequest): ImageVector =
    when (request) {
        is ApprovalRequest.Exec -> MiuixIcons.Play
        is ApprovalRequest.ApplyPatch -> MiuixIcons.Notes
        is ApprovalRequest.Permissions -> MiuixIcons.Lock
        is ApprovalRequest.DynamicTool -> MiuixIcons.Settings
        is ApprovalRequest.UserInput -> MiuixIcons.Notes
        is ApprovalRequest.Elicitation -> MiuixIcons.MindMap
        is ApprovalRequest.ChatgptAuthTokensRefresh,
        is ApprovalRequest.AttestationGenerate,
        is ApprovalRequest.CurrentTimeRead -> MiuixIcons.Lock
    }

/** Error colour for changes that persist (patch, permissions); primary for recoverable actions. */
@Composable
private fun approvalAccent(request: ApprovalRequest): Color =
    when (request) {
        is ApprovalRequest.ApplyPatch,
        is ApprovalRequest.Permissions -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.primary
    }

@Composable
private fun decisionsFor(
    request: ApprovalRequest,
    decide: (ApprovalResponse) -> Unit,
): List<DecisionAction> =
    when (request) {
        is ApprovalRequest.Exec -> {
            val available =
                request.params.availableDecisions.ifEmpty {
                    listOf(
                        CommandExecutionApprovalDecision.Accept,
                        CommandExecutionApprovalDecision.Decline,
                    )
                }
            available.map { decision ->
                DecisionAction(
                    label = decision.label(),
                    role =
                        when (decision) {
                            CommandExecutionApprovalDecision.Accept -> DecisionRole.Primary
                            CommandExecutionApprovalDecision.AcceptForSession,
                            is CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment,
                            is CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment ->
                                DecisionRole.Secondary

                            else -> DecisionRole.Destructive
                        },
                    onClick = { decide(ApprovalResponse.CommandExecution(decision)) },
                )
            }
        }

        is ApprovalRequest.ApplyPatch ->
            listOf(
                DecisionAction(
                    FileChangeApprovalDecision.Accept.label(),
                    role = DecisionRole.Primary,
                ) {
                    decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Accept))
                },
                DecisionAction(FileChangeApprovalDecision.AcceptForSession.label()) {
                    decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.AcceptForSession))
                },
                DecisionAction(
                    FileChangeApprovalDecision.Decline.label(),
                    role = DecisionRole.Destructive,
                ) {
                    decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Decline))
                },
                DecisionAction(
                    FileChangeApprovalDecision.Cancel.label(),
                    role = DecisionRole.Destructive,
                ) {
                    decide(ApprovalResponse.FileChange(FileChangeApprovalDecision.Cancel))
                },
            )

        is ApprovalRequest.Permissions ->
            listOf(
                DecisionAction(
                    PermissionsApprovalDecision.Accept.label(),
                    role = DecisionRole.Primary,
                ) {
                    decide(ApprovalResponse.Permissions(PermissionsApprovalDecision.Accept))
                },
                DecisionAction(PermissionsApprovalDecision.AcceptForSession.label()) {
                    decide(
                        ApprovalResponse.Permissions(PermissionsApprovalDecision.AcceptForSession)
                    )
                },
                DecisionAction(
                    PermissionsApprovalDecision.Decline.label(),
                    role = DecisionRole.Destructive,
                ) {
                    decide(ApprovalResponse.Permissions(PermissionsApprovalDecision.Decline))
                },
            )

        is ApprovalRequest.DynamicTool ->
            listOf(
                // Dynamic tool calls carry no decision enum; borrow command-execution labels — elicitation's accept is the form's submit, not an allow.
                DecisionAction(
                    CommandExecutionApprovalDecision.Accept.label(),
                    role = DecisionRole.Primary,
                ) {
                    decide(ApprovalResponse.DynamicTool(DynamicToolCallResponse(success = true)))
                },
                DecisionAction(
                    CommandExecutionApprovalDecision.Decline.label(),
                    role = DecisionRole.Destructive,
                ) {
                    decide(ApprovalResponse.DynamicTool(DynamicToolCallResponse(success = false)))
                },
            )

        is ApprovalRequest.UserInput,
        is ApprovalRequest.Elicitation -> emptyList()

        is ApprovalRequest.ChatgptAuthTokensRefresh,
        is ApprovalRequest.AttestationGenerate,
        is ApprovalRequest.CurrentTimeRead -> emptyList()
    }

/** `label: value` stacked, not beside: a fixed label column would cost a fifth of a phone-width dialog and force the value to wrap mid-paragraph. */
@Composable
internal fun FieldBlock(
    label: String,
    value: String,
    accent: Boolean = false,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldLabel(label)
        Spacer(Modifier.height(UiConsts.Space3))
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = if (accent) colors.primary else colors.onSurface,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = UiType.Badge,
        lineHeight = UiType.BadgeLine,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CommandBlock(command: String?) {
    FieldLabel(stringResource(R.string.approval_overlay_field_command))
    Spacer(Modifier.height(UiConsts.Space5))
    if (command.isNullOrBlank()) {
        MutedLine(stringResource(R.string.approval_overlay_command_unavailable))
    } else {
        CodeRow(text = command)
    }
}

@Composable
private fun TextBlock(label: String, value: String, accent: Boolean = false) {
    FieldBlock(label = label, value = value, accent = accent)
}

@Composable
private fun ExecBody(params: CommandExecutionApprovalParams) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        CommandBlock(params.command)
        params.cwd
            ?.takeIf { it.isNotBlank() }
            ?.let {
                TextBlock(
                    label = stringResource(R.string.approval_overlay_field_directory),
                    value = it,
                )
            }
        params.reason
            ?.takeIf { it.isNotBlank() }
            ?.let {
                TextBlock(
                    label = stringResource(R.string.approval_overlay_field_reason),
                    value = it,
                )
            }
        if (params.commandActions.isNotEmpty()) {
            // `joinToString` is not inline, so a composable cannot run inside its transform.
            val actions = params.commandActions.map { commandActionLabel(it) }
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_actions),
                value =
                    actions.joinToString(stringResource(R.string.approval_overlay_list_separator)),
            )
        }
        params.proposedExecpolicyAmendment
            ?.takeIf { it.isNotEmpty() }
            ?.let { rules ->
                TextBlock(
                    label = stringResource(R.string.approval_overlay_field_permission_rule),
                    value =
                        rules.joinToString(
                            stringResource(R.string.approval_overlay_list_separator)
                        ),
                    accent = true,
                )
            }
        if (params.proposedNetworkPolicyAmendments.isNotEmpty()) {
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_network_rule),
                value =
                    params.proposedNetworkPolicyAmendments.joinToString(
                        stringResource(R.string.approval_overlay_list_separator)
                    ) { amendment ->
                        "${amendment.action.wire} ${amendment.host}"
                    },
                accent = true,
            )
        }
    }
}

/** Short label for a parsed shell action, mirroring `command_can_run`'s summaries. */
@Composable
@ReadOnlyComposable
private fun commandActionLabel(action: CommandAction): String =
    when (action) {
        is CommandAction.Read -> stringResource(R.string.approval_overlay_action_read, action.path)
        is CommandAction.ListFiles ->
            action.path?.let {
                stringResource(R.string.approval_overlay_action_list, it)
            } ?: stringResource(R.string.approval_overlay_action_list_directory)

        is CommandAction.Search ->
            buildString {
                append(stringResource(R.string.approval_overlay_action_search))
                action.query
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        append(stringResource(R.string.approval_overlay_action_search_query, it))
                    }
                action.path
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        append(stringResource(R.string.approval_overlay_action_search_path, it))
                    }
            }

        is CommandAction.Unknown -> action.command
    }

/** File-change body: a ledger line for the whole patch, then per-file cards. */
@Composable
private fun PatchBody(params: FileChangeApprovalParams, changes: List<FileUpdateChange>) {
    val colors = MiuixTheme.colorScheme
    val files =
        remember(changes) {
            changes.map { change ->
                change.path to fileDiffOf(change.path, parseUnifiedDiff(change.diff))
            }
        }
    // Each file owns its disclosure state; the first opens so the dialog leads with its diff.
    var showAll by remember(changes) { mutableStateOf(false) }
    val expanded =
        remember(changes) {
            mutableStateMapOf<String, Boolean>().apply {
                files.firstOrNull()?.let { put(it.first, true) }
            }
        }
    val shown = if (showAll) files else files.take(UiConsts.PatchPreviewFiles)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        params.grantRoot
            ?.takeIf { it.isNotBlank() }
            ?.let {
                TextBlock(
                    label = stringResource(R.string.approval_overlay_field_grant_root),
                    value = it,
                    accent = true,
                )
            }
        FieldLabel(stringResource(R.string.approval_overlay_field_changes))
        if (files.isEmpty()) {
            Spacer(Modifier.height(UiConsts.Space3))
            MutedLine(stringResource(R.string.approval_overlay_patch_body_unavailable))
        } else {
            Spacer(Modifier.height(UiConsts.Space5))
            Text(
                text =
                    stringResource(
                        R.string.approval_overlay_changes_summary,
                        files.size,
                        files.sumOf { it.second.additions },
                        files.sumOf { it.second.removals },
                    ),
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(UiConsts.Space8))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                shown.forEach { (path, diff) ->
                    FileDiffRow(
                        file = diff,
                        expanded = expanded[path] == true,
                        onToggle = { expanded[path] = expanded[path] != true },
                        cwd = params.grantRoot,
                        bodyMaxLines = UiConsts.PatchBodyMaxLines,
                        corner = UiConsts.CornerControl,
                    )
                }
            }
            if (files.size > shown.size) {
                Spacer(Modifier.height(UiConsts.Space8))
                FlatDisclosure(
                    label =
                        stringResource(
                            R.string.approval_overlay_changes_more,
                            files.size - shown.size,
                        ),
                    onClick = { showAll = true },
                )
            }
        }
    }
}

@Composable
private fun PermissionsBody(params: PermissionsApprovalParams) {
    val permissions = params.permissions
    val checklist = buildList {
        if (permissions.network) add(stringResource(R.string.approval_overlay_permission_network))
        if (permissions.shell) add(stringResource(R.string.approval_overlay_permission_shell))
        if (permissions.fileSystemRead.isNotEmpty()) {
            add(
                stringResource(
                    R.string.approval_overlay_permission_read,
                    permissions.fileSystemRead.joinToString(
                        stringResource(R.string.approval_overlay_list_separator)
                    ),
                )
            )
        }
        if (permissions.fileSystemWrite.isNotEmpty()) {
            add(
                stringResource(
                    R.string.approval_overlay_permission_write,
                    permissions.fileSystemWrite.joinToString(
                        stringResource(R.string.approval_overlay_list_separator)
                    ),
                )
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        if (params.cwd.isNotBlank()) {
            TextBlock(
                label = stringResource(R.string.approval_overlay_field_directory),
                value = params.cwd,
            )
        }
        FieldLabel(stringResource(R.string.approval_overlay_field_permissions))
        if (checklist.isEmpty()) {
            Spacer(Modifier.height(UiConsts.Space3))
            MutedLine(stringResource(R.string.approval_overlay_permissions_empty))
        } else {
            Spacer(Modifier.height(UiConsts.Space5))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space7),
            ) {
                checklist.forEach { CheckRow(it) }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = MiuixIcons.Ok,
            contentDescription = null,
            modifier = Modifier.padding(top = UiConsts.Space1).size(UiConsts.IconInline),
            tint = colors.primary,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun DynamicToolBody(params: DynamicToolCallParams) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiConsts.DialogFieldGap),
    ) {
        FieldBlock(
            label = stringResource(R.string.approval_overlay_field_tool),
            value = params.tool,
        )
        if (params.arguments.isNotBlank()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FieldLabel(stringResource(R.string.approval_overlay_field_input))
                Spacer(Modifier.height(UiConsts.Space5))
                CodeRow(text = params.arguments)
            }
        }
    }
}

@Composable
private fun MutedLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun FlatDisclosure(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space6),
        fontSize = UiType.Action,
        lineHeight = UiType.ActionLine,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
        maxLines = 1,
    )
}

/** Monospace block for a command line or tool argument; `$` is separate so it can take the accent colour. */
@Composable
internal fun CodeRow(
    text: String,
    cornerRadius: Dp = UiConsts.CornerControl,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(codeSurface(), RoundedCornerShape(cornerRadius))
                .padding(
                    horizontal = UiConsts.Space12,
                    vertical = UiConsts.Space10,
                )
    ) {
        Text(
            text = stringResource(R.string.approval_overlay_command_prompt),
            fontSize = UiType.Badge,
            lineHeight = UiType.BadgeLine,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = colors.primary,
            maxLines = 1,
        )
        Spacer(Modifier.height(UiConsts.Space4))
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            fontSize = UiType.Code,
            lineHeight = UiType.CodeLine,
            fontFamily = FontFamily.Monospace,
            color = colors.onSurface,
        )
    }
}
