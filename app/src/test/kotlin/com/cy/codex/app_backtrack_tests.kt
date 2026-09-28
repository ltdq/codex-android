package com.cy.codex

import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ConnectionState
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.TurnsPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppBacktrackTest {
    @Test
    fun `selection resolves the item identity and retains canonical attachments`() {
        val prompt = UserMessageItem(
            "selected",
            content = listOf(
                UserInput.Text("inspect this"),
                UserInput.Image(fileId = "uploaded-image"),
                UserInput.LocalImage("/tmp/example.png"),
                UserInput.Mention("source", "/project/src"),
            ),
        )
        val turns = listOf(
            Turn("first", listOf(user("older", "inspect this"))),
            Turn("selected-turn", listOf(prompt, AgentMessageItem("answer", "ok"))),
        )
        val selected = backtrackSelection(turns, prompt.copy())
        assertEquals("selected-turn", selected.beforeTurnId)
        assertSame(prompt, selected.prompt)
    }

    @Test
    fun `steer cannot discard its initial prompt`() {
        val first = user("first")
        val steer = user("steer")
        assertFailsWith<IllegalArgumentException> {
            backtrackSelection(listOf(Turn("turn", listOf(first, steer))), steer)
        }
    }

    @Test
    fun `active turns and changed prompts cannot be edited`() {
        val prompt = user("selected")
        assertFailsWith<IllegalArgumentException> {
            backtrackSelection(listOf(Turn("turn", listOf(prompt), status = TurnStatus.InProgress)), prompt)
        }
        assertFailsWith<IllegalArgumentException> {
            backtrackSelection(listOf(Turn("turn", listOf(prompt))), user("selected", "stale"))
        }
        assertFailsWith<IllegalStateException> {
            backtrackSelection(listOf(Turn("turn", listOf(prompt))), user("missing"))
        }
    }

    @Test
    fun `review prompts are unavailable while later ordinary prompts can be selected`() {
        val hidden = user("review")
        val visible = user("visible")
        val turns = listOf(
            Turn("review-turn", listOf(EnteredReviewModeItem("entered", "review"), hidden)),
            Turn("exit-turn", listOf(ExitedReviewModeItem("exited", "done"))),
            Turn("ordinary-turn", listOf(visible)),
        )
        assertFailsWith<IllegalArgumentException> { backtrackSelection(turns, hidden) }
        assertEquals("ordinary-turn", backtrackSelection(turns, visible).beforeTurnId)
    }

    @Test
    fun `nested review duplicate prompts cannot be edited`() {
        val previous = Turn(
            "review",
            listOf(EnteredReviewModeItem("entered", "review"), ExitedReviewModeItem("exited", "done")),
        )
        val hidden = user("hidden", "review input")
        val nested = Turn(
            "child", listOf(hidden, hidden.copy(id = "duplicate")), status = TurnStatus.Interrupted,
        )
        assertFailsWith<IllegalArgumentException> { backtrackSelection(listOf(previous, nested), hidden) }
    }

    @Test
    fun `image only inputs are editable but empty prompts are not`() {
        val image = UserMessageItem("image", content = listOf(UserInput.LocalImage("/tmp/image.png")))
        val empty = user("empty", " \n ")
        assertEquals("turn", backtrackSelection(listOf(Turn("turn", listOf(image))), image).beforeTurnId)
        assertFailsWith<IllegalArgumentException> {
            backtrackSelection(listOf(Turn("turn", listOf(empty))), empty)
        }
    }

    private fun user(id: String, text: String = id) = UserMessageItem(id, content = listOf(UserInput.Text(text)))
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppBacktrackIntegrationTest {
    @Test
    fun `confirmed edit restores attachments ahead of the unsent draft and reloads retained history`() = runTest {
        val prompt = UserMessageItem(
            "selected", content = listOf(UserInput.Text("edit me"), UserInput.Image(fileId = "uploaded")),
        )
        val prior = UserMessageItem("prior", content = listOf(UserInput.Text("keep me")))
        val client = BacktrackClient(listOf(Turn("prior-turn", listOf(prior)), Turn("selected-turn", listOf(prompt))))
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        widget.state.applyDraft("unsent draft")
        widget.state.addApprovalReceipt(receipt("prior-receipt", "prior-turn", "prior"))
        widget.state.addApprovalReceipt(receipt("permissions-receipt", "prior-turn", "not-a-transcript-item"))
        widget.state.addApprovalReceipt(receipt("discarded-receipt", "selected-turn", "selected"))

        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()

        assertEquals(listOf("thread" to "selected-turn"), client.reverted)
        assertTrue("edit me" in widget.state.composerDraft)
        assertTrue("unsent draft" in widget.state.composerDraft)
        assertTrue(widget.state.composerDraft.indexOf("edit me") < widget.state.composerDraft.indexOf("unsent draft"))
        assertTrue(UserInput.Image(fileId = "uploaded") in widget.state.pendingTurnInputs())
        assertEquals(listOf("prior"), widget.state.items.map { it.id })
        assertEquals(listOf("prior-receipt", "permissions-receipt"), widget.state.approvalReceipts.map { it.id })
    }

    @Test
    fun `failed revert retains transcript and unsent draft`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("turn", listOf(prompt)))).apply {
            revertResult = Result.failure(IllegalStateException("offline"))
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        widget.state.applyDraft("unsent draft")
        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        assertEquals("unsent draft", widget.state.composerDraft)
        assertEquals(listOf(prompt), widget.state.items.toList())
        assertTrue(widget.state.diagnostics.isNotEmpty())
    }

    @Test
    fun `switching threads during validation prevents rollback`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("turn", listOf(prompt)))).apply {
            readGate = CompletableDeferred()
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        widget.bind(ThreadSessionState(threadId = "other"))
        widget.state.applyDraft("other draft")
        client.readGate!!.complete(Unit)
        runCurrent()
        assertTrue(client.reverted.isEmpty())
        assertEquals("other draft", widget.state.composerDraft)
    }

    @Test
    fun `repeated edits do not revert twice while the first request is pending`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("turn", listOf(prompt)))).apply {
            revertGate = CompletableDeferred()
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        assertEquals(1, client.reverted.size)
        client.revertGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, client.reverted.size)
    }

    @Test
    fun `turn starting during validation prevents rollback`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("turn", listOf(prompt)))).apply {
            readGate = CompletableDeferred()
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        widget.state.applyDraft("unsent draft")
        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        widget.state.applyStatus(ThreadStatus.Active())
        client.readGate!!.complete(Unit)
        runCurrent()
        assertTrue(client.reverted.isEmpty())
        assertEquals("unsent draft", widget.state.composerDraft)
    }

    @Test
    fun `a newer server turn than the displayed tail prevents rollback`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("selected-turn", listOf(prompt))))
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()
        client.turns += Turn("unseen-turn")
        widget.state.applyDraft("unsent draft")

        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()

        assertTrue(client.reverted.isEmpty())
        assertEquals("unsent draft", widget.state.composerDraft)
        assertTrue(widget.state.diagnostics.any { it.message?.contains("history changed") == true })
        assertFalse(widget.backtracking)
    }

    @Test
    fun `input stays blocked until reverted history has loaded`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("selected-turn", listOf(prompt)))).apply {
            refreshReadGate = CompletableDeferred()
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()

        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        assertTrue(widget.backtracking)
        assertEquals(listOf("selected"), widget.state.items.map { it.id })
        widget.action(AppEvent.SubmitUserMessage(listOf(UserInput.Text("send while stale"))))
        runCurrent()
        assertTrue(client.started.isEmpty())

        client.refreshReadGate!!.complete(Unit)
        runCurrent()
        assertFalse(widget.backtracking)
        assertTrue(widget.state.items.isEmpty())
    }

    @Test
    fun `failed refresh keeps input blocked until the session is reopened`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val client = BacktrackClient(listOf(Turn("selected-turn", listOf(prompt)))).apply {
            refreshReadFailure = IllegalStateException("offline")
        }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()

        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()
        assertTrue(widget.backtracking)
        assertEquals(listOf("selected"), widget.state.items.map { it.id })
        assertTrue(widget.state.diagnostics.any { it.message?.contains("reopen the session") == true })
        widget.action(AppEvent.SubmitUserMessage(listOf(UserInput.Text("send while stale"))))
        runCurrent()
        assertTrue(client.started.isEmpty())

        client.refreshReadFailure = null
        widget.open("thread")
        runCurrent()
        assertFalse(widget.backtracking)
        assertTrue(widget.state.items.isEmpty())
        assertEquals("edit me", widget.state.composerDraft)
    }

    @Test
    fun `stale successful reads do not complete a revert refresh`() = runTest {
        val prompt = UserMessageItem("selected", content = listOf(UserInput.Text("edit me")))
        val original = listOf(Turn("selected-turn", listOf(prompt)))
        val client = BacktrackClient(original).apply { refreshReadTurns = original }
        val widget = ChatWidget(client, backgroundScope)
        widget.open("thread")
        runCurrent()

        widget.action(AppEvent.RevertSessionForPromptEdit("thread", prompt))
        runCurrent()

        assertTrue(widget.backtracking)
        assertEquals(3, client.refreshReads)
        assertEquals(listOf("selected"), widget.state.items.map { it.id })
    }

    private fun receipt(id: String, turnId: String, itemId: String) =
        com.cy.codex.history_cell.ApprovalDecisionReceipt(
            id, turnId, itemId, com.cy.codex.history_cell.ApprovalDecisionSubject.Permissions(),
            com.cy.codex.history_cell.ReviewDecision.Approved,
        )

    private class BacktrackClient(var turns: List<Turn>) : AppServerClient {
        override val events = MutableSharedFlow<AppServerEvent>()
        override val requests = MutableSharedFlow<ApprovalRequest>()
        override val connection = flowOf(ConnectionState.Ready)
        var revertResult = Result.success(Unit)
        var readGate: CompletableDeferred<Unit>? = null
        var revertGate: CompletableDeferred<Unit>? = null
        var refreshReadGate: CompletableDeferred<Unit>? = null
        var refreshReadFailure: Throwable? = null
        var refreshReadTurns: List<Turn>? = null
        var refreshReads = 0
        val reverted = mutableListOf<Pair<String, String>>()
        val started = mutableListOf<List<UserInput>>()
        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)
        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) = Unit
        override suspend fun close() = Unit
        override suspend fun resumeThread(params: ThreadResumeParams): Result<ThreadSessionState> =
            Result.success(ThreadSessionState(
                threadId = params.threadId,
                initialTurnsPage = TurnsPage(turns.asReversed()),
                thread = thread(params.threadId),
            ))
        override suspend fun readThread(params: ThreadReadParams): Result<ThreadReadResponse> {
            val readingAfterRevert = reverted.isNotEmpty()
            if (readingAfterRevert) {
                refreshReads++
                refreshReadGate?.await()
                refreshReadFailure?.let { return Result.failure(it) }
            } else {
                readGate?.await()
            }
            val loadedTurns = if (readingAfterRevert) refreshReadTurns ?: turns else turns
            return Result.success(ThreadReadResponse(
                thread(params.threadId),
                items = loadedTurns.flatMap { it.items },
                turns = loadedTurns,
            ))
        }
        override suspend fun revertThreadBeforeTurn(threadId: String, beforeTurnId: String): Result<Unit> {
            reverted += threadId to beforeTurnId
            revertGate?.await()
            if (revertResult.isSuccess) turns = turns.takeWhile { it.id != beforeTurnId }
            return revertResult
        }
        override suspend fun startTurn(
            threadId: String,
            inputs: List<UserInput>,
            outputSchema: kotlinx.serialization.json.JsonElement?,
            effort: com.cy.codex.protocol.protocol.v2.ReasoningEffort?,
            clientMetadata: Map<String, String>?,
        ): Result<String> {
            started += inputs
            return Result.success("new-turn")
        }

        private fun thread(id: String) = Thread(
            id = id, preview = "", modelProvider = "openai", createdAt = 0L,
            updatedAt = 0L, cwd = "", status = ThreadStatus.Idle, cliVersion = "1.0",
            ephemeral = false, projectId = null, sessionId = id,
        )
    }
}
