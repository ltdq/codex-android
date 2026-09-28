package com.cy.codex.history_cell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.bottom_pane.truncateGraphemes
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision
import com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification
import com.cy.codex.protocol.protocol.v2.NetworkPolicyRuleAction
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision
import com.cy.codex.successColor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Decision receipts from `codex/codex-rs/tui/src/history_cell/approvals.rs`. */
data class ApprovalDecisionReceipt(
    val id: String,
    val turnId: String?,
    val itemId: String,
    val subject: ApprovalDecisionSubject,
    val decision: ReviewDecision,
    val actor: ApprovalDecisionActor = ApprovalDecisionActor.User,
)

enum class ApprovalDecisionActor { User, Guardian }

sealed interface ApprovalDecisionSubject {
    data class Command(val command: String) : ApprovalDecisionSubject
    data class NetworkAccess(val target: String) : ApprovalDecisionSubject
    data class Patch(val files: List<String> = emptyList()) : ApprovalDecisionSubject
    data class Permissions(val reason: String? = null) : ApprovalDecisionSubject
    data class McpTool(val name: String) : ApprovalDecisionSubject
    data class WriteStdin(val processId: String, val input: String) : ApprovalDecisionSubject
}

sealed interface ReviewDecision {
    data object Approved : ReviewDecision
    data object ApprovedForSession : ReviewDecision
    data class ApprovedExecpolicyAmendment(val command: String) : ReviewDecision
    data class NetworkPolicyAmendment(val target: String, val allowed: Boolean) : ReviewDecision
    data object Denied : ReviewDecision
    data object TimedOut : ReviewDecision
    data object Abort : ReviewDecision
}

/** Only an answered approval supplies a user decision; request retirement supplies no outcome. */
fun approvalDecisionReceipt(request: ApprovalRequest, response: ApprovalResponse): ApprovalDecisionReceipt? {
    val subject: ApprovalDecisionSubject
    val decision: ReviewDecision
    when {
        request is ApprovalRequest.Exec && response is ApprovalResponse.CommandExecution -> {
            val command = request.params.command.orEmpty()
            val networkTarget = command.takeIf { it.startsWith("network-access ") }
                ?.removePrefix("network-access ")?.takeIf { it.isNotEmpty() }
            subject = networkTarget?.let(ApprovalDecisionSubject::NetworkAccess)
                ?: ApprovalDecisionSubject.Command(command)
            decision = when (val value = response.decision) {
                CommandExecutionApprovalDecision.Accept -> ReviewDecision.Approved
                CommandExecutionApprovalDecision.AcceptForSession -> ReviewDecision.ApprovedForSession
                CommandExecutionApprovalDecision.Decline -> ReviewDecision.Denied
                CommandExecutionApprovalDecision.Cancel -> ReviewDecision.Abort
                is CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment ->
                    ReviewDecision.ApprovedExecpolicyAmendment(displayCommand(value.execpolicyAmendment))
                is CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment ->
                    ReviewDecision.NetworkPolicyAmendment(
                        networkTarget ?: value.networkPolicyAmendment.host,
                        value.networkPolicyAmendment.action == NetworkPolicyRuleAction.Allow,
                    )
            }
        }
        request is ApprovalRequest.ApplyPatch && response is ApprovalResponse.FileChange -> {
            subject = ApprovalDecisionSubject.Patch()
            decision = when (response.decision) {
                FileChangeApprovalDecision.Accept -> ReviewDecision.Approved
                FileChangeApprovalDecision.AcceptForSession -> ReviewDecision.ApprovedForSession
                FileChangeApprovalDecision.Decline -> ReviewDecision.Denied
                FileChangeApprovalDecision.Cancel -> ReviewDecision.Abort
            }
        }
        request is ApprovalRequest.Permissions && response is ApprovalResponse.Permissions -> {
            subject = ApprovalDecisionSubject.Permissions()
            decision = when (response.decision) {
                PermissionsApprovalDecision.Accept -> ReviewDecision.Approved
                PermissionsApprovalDecision.AcceptForSession -> ReviewDecision.ApprovedForSession
                PermissionsApprovalDecision.Decline -> ReviewDecision.Denied
            }
        }
        else -> return null
    }
    return ApprovalDecisionReceipt("request:${request.requestId.value}", request.turnId, request.itemId, subject, decision)
}

/** Approved and aborted reviews stay quiet (`codex/codex-rs/tui/src/chatwidget/tool_requests.rs`). */
fun guardianApprovalDecisionReceipt(review: GuardianApprovalReviewNotification): ApprovalDecisionReceipt? {
    val decision = when (review.status) {
        "denied" -> ReviewDecision.Denied
        "timedOut" -> ReviewDecision.TimedOut
        else -> return null
    }
    if (review.reviewId.isBlank()) return null
    val action = review.action as? JsonObject ?: return null
    fun text(key: String): String? = (action[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    fun strings(key: String): List<String> = (action[key] as? JsonArray).orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
    val subject = when (text("type")) {
        "command" -> ApprovalDecisionSubject.Command(text("command") ?: return null)
        "execve" -> {
            val program = text("program") ?: return null
            ApprovalDecisionSubject.Command(displayCommand(strings("argv").ifEmpty { listOf(program) }))
        }
        "networkAccess" -> ApprovalDecisionSubject.NetworkAccess(text("target") ?: text("host") ?: return null)
        "applyPatch" -> ApprovalDecisionSubject.Patch(strings("files"))
        "requestPermissions" -> ApprovalDecisionSubject.Permissions(text("reason"))
        "mcpToolCall" -> {
            val server = text("server") ?: return null
            val tool = text("toolName") ?: return null
            ApprovalDecisionSubject.McpTool("$server.$tool")
        }
        "writeStdin" -> ApprovalDecisionSubject.WriteStdin(
            text("processId") ?: return null,
            truncateGraphemes(JsonPrimitive(text("stdin").orEmpty()).toString(), 80),
        )
        else -> return null
    }
    return ApprovalDecisionReceipt(
        "review:${review.reviewId}", review.turnId, review.itemId, subject, decision, ApprovalDecisionActor.Guardian,
    )
}

/** First-line command preview, capped as in upstream `truncate_exec_snippet`. */
internal fun approvalCommandSnippet(command: String): String = truncateGraphemes(
    command.substringBefore('\n') + if ('\n' in command) " ..." else "",
    80,
)

private fun displayCommand(argv: List<String>): String {
    val executable = argv.firstOrNull()?.substringAfterLast('/')
    if (argv.size == 3 && executable in setOf("bash", "sh", "zsh") && argv[1] in setOf("-c", "-lc")) {
        return argv[2]
    }
    return argv.joinToString(" ") { word ->
        if (word.isNotEmpty() && word.all { it.isLetterOrDigit() || it in "_@%+=:,./-" }) word
        else "'" + word.replace("'", "'\"'\"'") + "'"
    }
}

internal fun approvalReceiptIsPositive(receipt: ApprovalDecisionReceipt): Boolean = when (val decision = receipt.decision) {
    ReviewDecision.Approved, ReviewDecision.ApprovedForSession, is ReviewDecision.ApprovedExecpolicyAmendment -> true
    is ReviewDecision.NetworkPolicyAmendment -> decision.allowed
    ReviewDecision.Denied, ReviewDecision.TimedOut, ReviewDecision.Abort -> false
}

internal fun approvalReceiptText(receipt: ApprovalDecisionReceipt, string: (Int, List<Any>) -> String): String {
    fun label(id: Int, vararg args: Any): String = string(id, args.toList())
    val decision = receipt.decision
    if (decision is ReviewDecision.ApprovedExecpolicyAmendment) {
        return label(R.string.approval_receipt_execpolicy, approvalCommandSnippet(decision.command))
    }
    if (decision is ReviewDecision.NetworkPolicyAmendment) {
        return label(
            if (decision.allowed) R.string.approval_receipt_network_allow else R.string.approval_receipt_network_deny,
            decision.target,
        )
    }
    if (receipt.actor == ApprovalDecisionActor.User && receipt.subject is ApprovalDecisionSubject.Permissions) {
        return label(when (decision) {
            ReviewDecision.Approved -> R.string.approval_receipt_permissions_granted
            ReviewDecision.ApprovedForSession -> R.string.approval_receipt_permissions_session
            else -> R.string.approval_receipt_permissions_denied
        })
    }
    val timedOut = decision == ReviewDecision.TimedOut
    val subject = when (val value = receipt.subject) {
        is ApprovalDecisionSubject.Command -> {
            val command = approvalCommandSnippet(value.command)
            if (command.isEmpty()) label(
                if (timedOut) R.string.approval_receipt_request_timeout else R.string.approval_receipt_request,
            ) else label(
                if (timedOut) R.string.approval_receipt_command_timeout else R.string.approval_receipt_command,
                command,
            )
        }
        is ApprovalDecisionSubject.NetworkAccess -> label(
            if (timedOut) R.string.approval_receipt_network_timeout else R.string.approval_receipt_network,
            value.target,
        )
        is ApprovalDecisionSubject.Patch -> {
            val patch = when (value.files.size) {
                0 -> label(R.string.approval_receipt_patch)
                1 -> label(R.string.approval_receipt_patch_file, value.files.single())
                else -> label(R.string.approval_receipt_patch_files, value.files.size)
            }
            label(if (timedOut) R.string.approval_receipt_apply_timeout else R.string.approval_receipt_apply, patch)
        }
        is ApprovalDecisionSubject.Permissions -> {
            val permissions = label(
                if (timedOut) R.string.approval_receipt_permissions_timeout else R.string.approval_receipt_permissions,
            )
            value.reason?.takeIf { it.isNotBlank() }?.let { "$permissions: $it" } ?: permissions
        }
        is ApprovalDecisionSubject.McpTool -> label(
            if (timedOut) R.string.approval_receipt_mcp_timeout else R.string.approval_receipt_mcp,
            value.name,
        )
        is ApprovalDecisionSubject.WriteStdin -> label(
            if (timedOut) R.string.approval_receipt_stdin_timeout else R.string.approval_receipt_stdin,
            value.processId, value.input,
        )
    }
    return label(when (decision) {
        ReviewDecision.Approved -> R.string.approval_receipt_approved
        ReviewDecision.ApprovedForSession -> R.string.approval_receipt_session
        ReviewDecision.Denied -> if (receipt.actor == ApprovalDecisionActor.Guardian) {
            R.string.approval_receipt_guardian_denied
        } else R.string.approval_receipt_denied
        ReviewDecision.TimedOut -> R.string.approval_receipt_timed_out
        ReviewDecision.Abort -> R.string.approval_receipt_canceled
        else -> error("Amendment receipt was already formatted")
    }, subject)
}

@Composable
fun ApprovalDecisionCell(receipt: ApprovalDecisionReceipt, modifier: Modifier = Modifier) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val text = approvalReceiptText(receipt) { id, args -> resources.getString(id, *args.toTypedArray()) }
    val positive = approvalReceiptIsPositive(receipt)
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (positive) "✔" else "✗",
            color = if (positive) successColor() else MiuixTheme.colorScheme.error,
            fontSize = UiType.RowDetail,
            lineHeight = UiType.Message,
        )
        Spacer(Modifier.width(UiConsts.Space8))
        Text(text = text, fontSize = UiType.RowDetail, lineHeight = UiType.Message, color = MiuixTheme.colorScheme.onSurface)
    }
}
