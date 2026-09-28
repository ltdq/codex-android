package com.cy.codex.history_cell

import com.cy.codex.R
import com.cy.codex.AppEvent
import com.cy.codex.ChatWidget
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ConnectionState
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codex.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalDecision
import com.cy.codex.protocol.protocol.v2.FileChangeApprovalParams
import com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification
import com.cy.codex.protocol.protocol.v2.NetworkPolicyAmendment
import com.cy.codex.protocol.protocol.v2.NetworkPolicyRuleAction
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalDecision
import com.cy.codex.protocol.protocol.v2.PermissionsApprovalParams
import com.cy.codex.protocol.protocol.v2.ToolRequestUserInputParams
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.ServerRequestResolved
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovalReceiptsTest {
    private fun exec(command: String? = "pwd") = ApprovalRequest.Exec(
        RequestId("approval"), "thread", "turn", "item", 0,
        CommandExecutionApprovalParams("thread", "turn", "item", command = command),
    )

    private fun commandReceipt(decision: CommandExecutionApprovalDecision, command: String? = "pwd") =
        assertNotNull(approvalDecisionReceipt(exec(command), ApprovalResponse.CommandExecution(decision)))

    private fun guardian(status: String, action: String) = guardianApprovalDecisionReceipt(
        GuardianApprovalReviewNotification(
            threadId = "thread", turnId = "turn", reviewId = "review", itemId = "item",
            status = status, action = Json.parseToJsonElement(action),
        ),
    )

    @Test
    fun `command decisions preserve scope and cancellation`() {
        val approved = commandReceipt(CommandExecutionApprovalDecision.Accept)
        assertEquals("request:approval", approved.id)
        assertEquals("turn", approved.turnId)
        assertEquals("item", approved.itemId)
        assertEquals("You approved Codex to run pwd this time", text(approved))
        assertTrue(approvalReceiptIsPositive(approved))
        assertEquals(
            "You approved Codex to run pwd every time this session",
            text(commandReceipt(CommandExecutionApprovalDecision.AcceptForSession)),
        )
        assertEquals(
            "You did not approve Codex to run pwd",
            text(commandReceipt(CommandExecutionApprovalDecision.Decline)),
        )
        val canceled = commandReceipt(CommandExecutionApprovalDecision.Cancel)
        assertEquals(ReviewDecision.Abort, canceled.decision)
        assertFalse(approvalReceiptIsPositive(canceled))
        assertEquals("You canceled the request for Codex to run pwd", text(canceled))
    }

    @Test
    fun `empty commands use request wording and snippets keep the first line`() {
        assertEquals("You approved this request this time", text(commandReceipt(CommandExecutionApprovalDecision.Accept, null)))
        assertEquals("echo hello ...", approvalCommandSnippet("echo hello\necho goodbye"))
        assertEquals("x".repeat(77) + "...", approvalCommandSnippet("x".repeat(100)))
        assertEquals("😀".repeat(77) + "...", approvalCommandSnippet("😀".repeat(100)))
    }

    @Test
    fun `amendments retain the approved prefix and the network rule outcome`() {
        val prefix = commandReceipt(
            CommandExecutionApprovalDecision.AcceptWithExecpolicyAmendment(listOf("git", "show")),
            "git show HEAD",
        )
        assertEquals("You approved Codex to always run commands that start with git show", text(prefix))
        val allowed = commandReceipt(
            CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment(NetworkPolicyAmendment(NetworkPolicyRuleAction.Allow, "example.com")),
        )
        assertTrue(approvalReceiptIsPositive(allowed))
        assertEquals("You persisted Codex network access to example.com", text(allowed))
        val denied = commandReceipt(
            CommandExecutionApprovalDecision.ApplyNetworkPolicyAmendment(NetworkPolicyAmendment(NetworkPolicyRuleAction.Deny, "example.com")),
            "network-access https://example.com:443",
        )
        assertFalse(approvalReceiptIsPositive(denied))
        assertEquals("You denied Codex network access to https://example.com:443 and saved that rule", text(denied))
    }

    @Test
    fun `file and permission approvals retain their subjects`() {
        val patch = ApprovalRequest.ApplyPatch(
            RequestId("patch"), "thread", "turn", "item", 0,
            FileChangeApprovalParams("thread", "turn", "item"),
        )
        assertEquals(
            "You did not approve Codex to apply a patch",
            text(assertNotNull(approvalDecisionReceipt(patch, ApprovalResponse.FileChange(FileChangeApprovalDecision.Decline)))),
        )
        val permissions = ApprovalRequest.Permissions(
            RequestId("permissions"), "thread", "turn", "item", 0,
            PermissionsApprovalParams("thread", "turn", "item"),
        )
        assertEquals(
            "You granted additional permissions for this session",
            text(assertNotNull(approvalDecisionReceipt(permissions, ApprovalResponse.Permissions(PermissionsApprovalDecision.AcceptForSession)))),
        )
        assertNull(approvalDecisionReceipt(exec(), ApprovalResponse.FileChange(FileChangeApprovalDecision.Accept)))
        assertNull(approvalDecisionReceipt(
            ApprovalRequest.UserInput(
                RequestId("question"), "thread", "turn", "item", 0,
                ToolRequestUserInputParams("thread", "turn", "item", emptyList()),
            ),
            ApprovalResponse.UserInput(emptyList()),
        ))
    }

    @Test
    fun `only explicit denied and timed out reviews produce guardian receipts`() {
        val action = """{"type":"command","command":"pwd"}"""
        val denied = assertNotNull(guardian("denied", action))
        assertEquals("review:review", denied.id)
        assertEquals(ApprovalDecisionActor.Guardian, denied.actor)
        assertEquals("Request denied for Codex to run pwd", text(denied))
        val timedOut = assertNotNull(guardian("timedOut", action))
        assertEquals(ReviewDecision.TimedOut, timedOut.decision)
        assertEquals("Review timed out before Codex could run pwd", text(timedOut))
        listOf("inProgress", "approved", "aborted", "unknown").forEach { status -> assertNull(guardian(status, action)) }
        assertNull(guardian("denied", """{"type":"futureAction"}"""))
    }

    @Test
    fun `execve argv already contains its executable and shell wrappers are removed`() {
        val command = assertNotNull(guardian("denied", """{"type":"execve","program":"/bin/printf","argv":["printf","hello world"]}"""))
        assertEquals(ApprovalDecisionSubject.Command("printf 'hello world'"), command.subject)
        val shell = assertNotNull(guardian("denied", """{"type":"execve","program":"/bin/bash","argv":["/bin/bash","-lc","pwd"]}"""))
        assertEquals(ApprovalDecisionSubject.Command("pwd"), shell.subject)
        val fallback = assertNotNull(guardian("denied", """{"type":"execve","program":"pwd","argv":[]}"""))
        assertEquals(ApprovalDecisionSubject.Command("pwd"), fallback.subject)
    }

    @Test
    fun `guardian subjects describe each supported action`() {
        val cases = listOf(
            """{"type":"applyPatch","files":["app.kt"]}""" to "Review timed out before Codex could apply a patch touching app.kt",
            """{"type":"applyPatch","files":["a.kt","b.kt"]}""" to "Review timed out before Codex could apply a patch touching 2 files",
            """{"type":"networkAccess","target":"https://example.com"}""" to "Review timed out before Codex could access https://example.com",
            """{"type":"mcpToolCall","server":"docs","toolName":"search"}""" to "Review timed out before Codex could call MCP tool docs.search",
            """{"type":"requestPermissions","reason":"read config"}""" to "Review timed out before Codex could request permissions: read config",
            """{"type":"writeStdin","processId":"7","stdin":"yes\n"}""" to "Review timed out before Codex could send input to terminal 7: \"yes\\n\"",
        )
        cases.forEach { (action, expected) -> assertEquals(expected, text(assertNotNull(guardian("timedOut", action)))) }
    }

    @Test
    fun `a failed answer stays retryable and records one receipt on success`() = runTest {
        val client = ApprovalClient().apply { rejectResponse = true }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = exec()
        client.requests.emit(request)
        runCurrent()
        val event = AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept))
        widget.action(event)
        runCurrent()
        assertTrue(widget.state.approvalReceipts.isEmpty())
        assertEquals(request, widget.currentApproval)
        client.rejectResponse = false
        widget.action(event)
        runCurrent()
        widget.action(event)
        runCurrent()
        assertEquals(ReviewDecision.Approved, widget.state.approvalReceipts.single().decision)
        assertNull(widget.currentApproval)
        assertEquals(1, client.responses)
    }

    @Test
    fun `external resolution retires an approval without inventing a decision`() = runTest {
        val client = ApprovalClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = exec()
        client.requests.emit(request)
        runCurrent()
        client.events.emit(AppServerEvent.RequestResolved("thread", ServerRequestResolved("approval", "thread")))
        runCurrent()
        widget.action(AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept)))
        runCurrent()
        assertNull(widget.currentApproval)
        assertTrue(widget.state.approvalReceipts.isEmpty())
        assertEquals(0, client.responses)
    }

    @Test
    fun `external resolution during a response does not record a decision`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = ApprovalClient().apply { responseGate = gate }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = exec()
        client.requests.emit(request)
        runCurrent()

        widget.action(AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept)))
        runCurrent()
        client.events.emit(AppServerEvent.RequestResolved("thread", ServerRequestResolved("approval", "thread")))
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertNull(widget.currentApproval)
        assertTrue(widget.state.approvalReceipts.isEmpty())
    }

    @Test
    fun `guardian timeout notifications deduplicate and clear on thread switch`() = runTest {
        val client = ApprovalClient()
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val review = GuardianApprovalReviewNotification(
            threadId = "thread", turnId = "turn", reviewId = "timeout", itemId = "item", status = "timedOut",
            action = Json.parseToJsonElement("""{"type":"command","command":"pwd"}"""),
        )
        repeat(2) {
            client.events.emit(AppServerEvent.AutoApprovalReviewCompleted("thread", review))
            runCurrent()
        }
        assertEquals(ReviewDecision.TimedOut, widget.state.approvalReceipts.single().decision)
        assertTrue(widget.approvalDenials.isEmpty())
        widget.bind(ThreadSessionState(threadId = "other"))
        assertTrue(widget.state.approvalReceipts.isEmpty())
    }

    @Test
    fun `a pending response cannot append to another thread`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = ApprovalClient().apply { responseGate = gate }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = exec()
        client.requests.emit(request)
        runCurrent()
        widget.action(AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept)))
        runCurrent()
        assertTrue(widget.answeringApproval)
        widget.bind(ThreadSessionState(threadId = "other"))
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, client.responses)
        assertTrue(widget.state.approvalReceipts.isEmpty())
    }

    private class ApprovalClient : AppServerClient {
        override val events = MutableSharedFlow<AppServerEvent>()
        override val requests = MutableSharedFlow<ApprovalRequest>()
        override val connection = flowOf(ConnectionState.Ready)
        var rejectResponse = false
        var responseGate: CompletableDeferred<Unit>? = null
        var responses = 0
        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)
        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) {
            responseGate?.await()
            check(!rejectResponse) { "connection lost" }
            responses++
        }
        override suspend fun close() = Unit
    }

    private val labels: Map<Int, String> by lazy {
        val file = listOf(File("src/main/res/values/strings_approvals.xml"), File("app/src/main/res/values/strings_approvals.xml"))
            .first { it.isFile }
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        (0 until strings.length).associate { index ->
            val node = strings.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            R.string::class.java.getField(name).getInt(null) to node.textContent
        }
    }

    private fun text(receipt: ApprovalDecisionReceipt): String = approvalReceiptText(receipt) { id, args ->
        String.format(labels.getValue(id), *args.toTypedArray())
    }
}
