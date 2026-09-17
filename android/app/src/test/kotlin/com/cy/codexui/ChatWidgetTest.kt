package com.cy.codexui

import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.AppServerEvent
import com.cy.codexui.protocol.ApprovalRequest
import com.cy.codexui.protocol.ApprovalResponse
import com.cy.codexui.protocol.ConnectionState
import com.cy.codexui.protocol.protocol.RequestId
import com.cy.codexui.protocol.protocol.v2.ClientInfo
import com.cy.codexui.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codexui.protocol.protocol.v2.CommandExecutionApprovalParams
import com.cy.codexui.protocol.protocol.v2.ThreadSessionState
import com.cy.codexui.protocol.protocol.v2.ThreadStatus
import com.cy.codexui.protocol.protocol.v2.Thread
import com.cy.codexui.protocol.protocol.v2.ThreadReadResponse
import com.cy.codexui.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.item.CommandExecutionItem
import com.cy.codexui.protocol.protocol.v2.UserInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatWidgetTest {
    @Test
    fun `failed submission retains the draft and leaves running state`() = runTest {
        val client = TestClient().apply { turnResult = Result.failure(IllegalStateException("offline")) }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.state.applyDraft("keep this draft")
        widget.action(AppEvent.SubmitUserMessage(listOf(UserInput.Text("keep this draft"))))
        assertTrue(widget.state.running)
        runCurrent()
        assertFalse(widget.state.running)
        assertEquals("keep this draft", widget.state.composerDraft)
        assertEquals(DiagnosticCode.SendFailed, widget.state.diagnostics.single().code)
    }

    @Test
    fun `failed approval response remains retryable`() = runTest {
        val client = TestClient().apply { rejectResponse = true }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.attach()
        val request = ApprovalRequest.Exec(
            RequestId("approval"), "thread", "turn", "item", 0,
            CommandExecutionApprovalParams("thread", "turn", "item", command = "pwd"),
        )
        client.requests.emit(request)
        runCurrent()
        val event = AppEvent.ResolveApproval(request.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Accept))
        widget.action(event)
        runCurrent()
        assertEquals(request, widget.currentApproval)
        assertFalse(widget.answeringApproval)
        assertNotNull(widget.approvalError)
        client.rejectResponse = false
        widget.action(event)
        runCurrent()
        assertNull(widget.currentApproval)
        assertNull(widget.approvalError)
    }

    @Test
    fun `failed resume exits loading and remains closed`() = runTest {
        val widget = ChatWidget(TestClient(), backgroundScope)
        var loaded: Result<ThreadReadResponse>? = null
        widget.open("unavailable") { loaded = it }
        runCurrent()
        assertFalse(widget.state.loading)
        assertFalse(widget.state.open)
        assertTrue(widget.state.status is ThreadStatus.SystemError)
        assertTrue(loaded?.isFailure == true)
    }

    @Test
    fun `open reports persisted thread metadata and history without a preview`() = runTest {
        val thread = Thread("shell-only", preview = "", cwd = "/workspace")
        val actual = CommandExecutionItem("actual", "pwd", "/workspace", aggregatedOutput = "/workspace\n", exitCode = 0)
        val client = TestClient().apply {
            resumeResult = Result.success(ThreadSessionState(threadId = thread.id))
            historyResult = Result.success(ThreadReadResponse(thread, listOf(actual)))
        }
        val widget = ChatWidget(client, backgroundScope)
        var loaded: Result<ThreadReadResponse>? = null
        widget.open(thread.id) { loaded = it }
        runCurrent()
        assertEquals(thread, loaded?.getOrThrow()?.thread)
        assertEquals(listOf(actual), widget.state.items.toList())
        assertTrue(widget.state.open)
    }

    @Test
    fun `failed history read can clear the stale session for startup fallback`() = runTest {
        val client = TestClient().apply {
            resumeResult = Result.success(ThreadSessionState(threadId = "deleted"))
        }
        val widget = ChatWidget(client, backgroundScope)
        var failed = false
        widget.open("deleted") { result ->
            failed = result.isFailure
            if (failed) widget.clear()
        }
        runCurrent()
        assertTrue(failed)
        assertFalse(widget.state.open)
        assertFalse(widget.state.loading)
        assertEquals("", widget.state.threadId)
        assertEquals("", widget.state.config.threadId)
        assertTrue(widget.state.items.isEmpty())
        assertTrue(widget.state.diagnostics.isEmpty())
    }

    @Test
    fun `switching threads clears old running and streaming state`() {
        val state = SessionState()
        state.bindThread("first", ThreadSessionState(threadId = "first"))
        state.applyStatus(ThreadStatus.Active())
        state.applyStreaming("old-message")
        state.beginLoad("second")
        assertFalse(state.open)
        assertFalse(state.running)
        assertNull(state.streamingItemId)
        assertTrue(state.loading)
        assertEquals("second", state.threadId)
    }

    @Test
    fun `compaction reloads real history without inventing token usage`() = runTest {
        val actual = AgentMessageItem("actual", "Persisted history")
        val client = TestClient().apply {
            historyResult = Result.success(ThreadReadResponse(Thread("thread"), listOf(actual)))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.bind(ThreadSessionState(threadId = "thread"))
        widget.state.applyUsage(ThreadTokenUsage(totalTokens = 8_000, outputTokens = 500))
        widget.state.upsert(AgentMessageItem("old", "Old history"))
        widget.attach()
        client.events.emit(AppServerEvent.ThreadCompacted("thread", null))
        runCurrent()
        assertEquals(listOf(actual), widget.state.items.toList())
        assertEquals(8_000, widget.state.usage.totalTokens)
    }

    private class TestClient : AppServerClient {
        override val events = MutableSharedFlow<AppServerEvent>()
        override val requests = MutableSharedFlow<ApprovalRequest>()
        override val connection = flowOf(ConnectionState.Ready)
        var turnResult: Result<String> = Result.success("turn")
        var resumeResult: Result<ThreadSessionState> = Result.failure(IllegalStateException("unavailable"))
        var historyResult: Result<ThreadReadResponse> = Result.failure(IllegalStateException("unavailable"))
        var rejectResponse = false
        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)
        override suspend fun startTurn(threadId: String, inputs: List<UserInput>) = turnResult
        override suspend fun resumeThread(threadId: String) = resumeResult
        override suspend fun readThread(threadId: String) = historyResult
        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) {
            check(!rejectResponse) { "connection lost" }
        }
        override suspend fun close() = Unit
    }
}
