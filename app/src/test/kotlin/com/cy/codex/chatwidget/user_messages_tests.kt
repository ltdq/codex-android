package com.cy.codex.chatwidget

import com.cy.codex.AppEvent
import com.cy.codex.ChatWidget
import com.cy.codex.DiagnosticCode
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.ConnectionState
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.ClientInfo
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.Thread
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserMessagesTest {
    @Test
    fun `running input steers and remains pending after successful RPC`() = runTest {
        val client = InputClient()
        val widget = widget(client)
        widget.submit("steer")
        runCurrent()

        assertEquals(listOf(text("steer")), client.steered.map { it.inputs })
        assertTrue(client.queuedCalls.isEmpty())
        assertTrue(client.started.isEmpty())
        assertEquals("steer", widget.pendingSteers.single().preview)
        assertEquals(client.steered.single().clientId, widget.pendingSteers.single().id)
        assertEquals("", widget.state.composerDraft)
    }

    @Test
    fun `client IDs acknowledge only their steer and duplicate receipts are harmless`() = runTest {
        val client = InputClient()
        val widget = widget(client)
        widget.submit("same")
        widget.submit("same")
        runCurrent()
        val first = UserMessageItem("first-item", client.steered[0].clientId, text("same"))
        val second = UserMessageItem("second-item", client.steered[1].clientId, text("same"))
        client.events.emit(AppServerEvent.ItemStarted("thread", "turn", UserMessageItem("other-item", "another-client", text("same"))))
        runCurrent()
        assertEquals(2, widget.pendingSteers.size)

        client.events.emit(AppServerEvent.ItemStarted("thread", "turn", first))
        runCurrent()
        assertEquals(listOf(second.clientId), widget.pendingSteers.map { it.id })
        client.events.emit(AppServerEvent.ItemCompleted("thread", "turn", first))
        runCurrent()
        assertEquals(listOf(second.clientId), widget.pendingSteers.map { it.id })
        assertEquals(1, widget.state.items.count { it.id == first.id })

        client.events.emit(AppServerEvent.ItemCompleted("thread", "turn", second))
        runCurrent()
        assertTrue(widget.pendingSteers.isEmpty())
    }

    @Test
    fun `an unidentified earlier message cannot acknowledge a failed steer`() = runTest {
        val reply = CompletableDeferred<Result<String>>()
        val client = InputClient().apply { steerReply = reply }
        val widget = widget(client)
        widget.submit("same")

        client.events.emit(AppServerEvent.ItemStarted(
            "thread", "turn", UserMessageItem("earlier", content = text("same")),
        ))
        runCurrent()
        assertEquals("same", widget.pendingSteers.single().preview)

        reply.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertEquals("same", widget.state.composerDraft)
        assertTrue(widget.pendingSteers.isEmpty())
        assertEquals(DiagnosticCode.SendFailed, widget.state.diagnostics.single().code)
    }

    @Test
    fun `explicit follow-up uses the server queue`() = runTest {
        val client = InputClient()
        val widget = widget(client)
        widget.submit("later", queued = true)
        runCurrent()

        assertEquals(listOf("thread" to text("later")), client.queuedCalls)
        assertTrue(client.steered.isEmpty())
        assertTrue(widget.pendingSteers.isEmpty())
        assertEquals("later", widget.state.queued.single().preview)
    }

    @Test
    fun `an asynchronous successful steer keeps the newer draft`() = runTest {
        val reply = CompletableDeferred<Result<String>>()
        val client = InputClient().apply { steerReply = reply }
        val widget = widget(client)
        widget.submit("sent")
        widget.state.applyDraft("new draft")
        reply.complete(Result.success("turn"))
        runCurrent()

        assertEquals("new draft", widget.state.composerDraft)
        assertEquals("sent", widget.pendingSteers.single().preview)
    }

    @Test
    fun `an asynchronous failed steer restores its input before newer edits`() = runTest {
        val reply = CompletableDeferred<Result<String>>()
        val client = InputClient().apply { steerReply = reply }
        val widget = widget(client)
        widget.submit("sent")
        widget.state.applyDraft("new draft")
        reply.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertEquals("sent\nnew draft", widget.state.composerDraft)
        assertTrue(widget.pendingSteers.isEmpty())
        assertEquals(DiagnosticCode.SendFailed, widget.state.diagnostics.single().code)
    }

    @Test
    fun `an asynchronous turn start also keeps newer edits on success and failure`() = runTest {
        for (succeeded in listOf(true, false)) {
            val reply = CompletableDeferred<Result<String>>()
            val client = InputClient().apply { startReply = reply }
            val widget = widget(client, running = false)
            widget.submit("sent")
            runCurrent()
            widget.state.applyDraft("new draft")
            reply.complete(if (succeeded) Result.success("turn") else Result.failure(IllegalStateException("offline")))
            runCurrent()

            assertEquals(if (succeeded) "new draft" else "sent\nnew draft", widget.state.composerDraft)
            assertEquals(succeeded, widget.state.running)
            assertEquals(listOf("thread" to text("sent")), client.started)
        }
    }

    @Test
    fun `interruption restores steers deleted queue entries and current attachments in order`() = runTest {
        val remote = UserInput.Image(fileId = "remote-image", detail = "high")
        val queued = QueuedSubmission("queued", input = listOf(remote, UserInput.Text("queued")))
        val client = InputClient().apply { queue("thread").add(queued) }
        val widget = widget(client)
        widget.submit("steer")
        val image = widget.state.addComposerImage("/tmp/current.png")
        widget.state.applyDraft("current $image")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("steer\nqueued\ncurrent [Image #2]", widget.state.composerDraft)
        assertEquals(listOf("thread" to "queued"), client.deleted)
        assertTrue(widget.pendingSteers.isEmpty())
        assertTrue(widget.state.queued.isEmpty())
        assertFalse(widget.restoringInputs)
        assertTrue(remote in widget.state.pendingTurnInputs())
        assertTrue(UserInput.LocalImage("/tmp/current.png") in widget.state.pendingTurnInputs())

        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()
        assertEquals("steer\nqueued\ncurrent [Image #2]", widget.state.composerDraft)
        assertEquals(1, client.deleted.size)
    }

    @Test
    fun `an already consumed queue entry is not restored when deletion returns false`() = runTest {
        val client = InputClient().apply {
            queue("thread").add(QueuedSubmission("consumed", input = text("already consumed")))
            deleteReplies["consumed"] = Result.success(false)
        }
        val widget = widget(client)
        widget.state.applyDraft("current")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("current", widget.state.composerDraft)
        assertEquals(listOf("thread" to "consumed"), client.deleted)
        assertTrue(widget.state.queued.isEmpty())
    }

    @Test
    fun `a failed queue deletion keeps ownership on the server`() = runTest {
        val queued = QueuedSubmission("retained", input = text("do not duplicate"))
        val client = InputClient().apply {
            queue("thread").add(queued)
            deleteReplies["retained"] = Result.failure(IllegalStateException("offline"))
        }
        val widget = widget(client)
        widget.state.applyDraft("current")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("current", widget.state.composerDraft)
        assertEquals(listOf(queued), widget.state.queued.toList())
        assertEquals(listOf(queued), client.queue("thread"))
        assertTrue(widget.state.diagnostics.any { it.code == DiagnosticCode.SendFailed && it.detail == "offline" })
    }

    @Test
    fun `interruption waits for an in-flight queue submission before recovering it`() = runTest {
        val reply = CompletableDeferred<Result<QueuedSubmission?>>()
        val client = InputClient().apply { queueReply = reply }
        val widget = widget(client)
        widget.submit("later", queued = true)
        widget.state.applyDraft("current")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertTrue(widget.restoringInputs)
        assertEquals("current", widget.state.composerDraft)
        assertTrue(client.listed.isEmpty())
        reply.complete(Result.success(QueuedSubmission("in-flight", input = text("later"))))
        runCurrent()

        assertEquals("later\ncurrent", widget.state.composerDraft)
        assertEquals(listOf("thread" to "in-flight"), client.deleted)
        assertTrue(widget.state.queued.isEmpty())
        assertFalse(widget.restoringInputs)
    }

    @Test
    fun `a failed response for a previous thread restores only that thread`() = runTest {
        val reply = CompletableDeferred<Result<String>>()
        val client = InputClient().apply { steerReply = reply }
        val widget = widget(client)
        widget.submit("first thread input")
        widget.bind(session("other"))
        widget.state.applyDraft("other draft")
        reply.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()

        assertEquals("other", widget.state.threadId)
        assertEquals("other draft", widget.state.composerDraft)
        assertTrue(widget.state.diagnostics.isEmpty())
        widget.bind(session("thread"))
        assertEquals("first thread input", widget.state.composerDraft)
    }

    @Test
    fun `a foreign interrupted turn keeps recovered input away from the visible draft`() = runTest {
        val client = InputClient().apply { queue("thread").add(QueuedSubmission("queued", input = text("first queue"))) }
        val widget = widget(client)
        widget.submit("first steer")
        widget.bind(session("other"))
        widget.state.applyDraft("other draft")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("other draft", widget.state.composerDraft)
        assertEquals(listOf("thread" to "queued"), client.deleted)
        widget.bind(session("thread"))
        assertEquals("first steer\nfirst queue", widget.state.composerDraft)
    }

    @Test
    fun `background queue recovery does not invalidate the visible queue read`() = runTest {
        val reply = CompletableDeferred<Result<List<QueuedSubmission>>>()
        val client = InputClient().apply { queueReadReplies["thread"] = reply }
        val widget = widget(client)
        client.events.emit(AppServerEvent.ThreadQueueChangedEvent(
            "thread", com.cy.codex.protocol.protocol.v2.ThreadQueueChanged("thread"),
        ))
        runCurrent()
        assertEquals(listOf("thread"), client.listed)

        client.events.emit(AppServerEvent.TurnCompleted("other", "other-turn", TurnStatus.Interrupted))
        runCurrent()
        val queued = QueuedSubmission("visible", input = text("visible follow-up"))
        reply.complete(Result.success(listOf(queued)))
        runCurrent()

        assertEquals(listOf(queued), widget.state.queued.toList())
        assertEquals("thread", widget.state.threadId)
    }

    @Test
    fun `older completion preserves input owned by a later active turn`() = runTest {
        val queued = QueuedSubmission("later-queue", input = text("later follow-up"))
        val client = InputClient().apply {
            activeTurnIds["thread"] = "later-turn"
            queue("thread").add(queued)
        }
        val widget = widget(client)
        widget.submit("later steer")
        runCurrent()
        client.events.emit(AppServerEvent.TurnCompleted("thread", "earlier-turn", TurnStatus.Interrupted))
        runCurrent()

        assertEquals("later steer", widget.pendingSteers.single().preview)
        assertTrue(widget.state.running)
        assertEquals("", widget.state.composerDraft)
        assertTrue(client.deleted.isEmpty())
        assertEquals(listOf(queued), client.queue("thread"))
    }

    @Test
    fun `history refresh acknowledges committed client IDs after transport loss`() = runTest {
        val client = InputClient()
        val widget = widget(client)
        widget.submit("steer")
        val receipt = UserMessageItem("receipt", client.steered.single().clientId, text("steer"))
        client.history = ThreadReadResponse(thread("thread"), listOf(receipt))
        client.events.emit(AppServerEvent.TransportLagged(2))
        runCurrent()

        assertEquals(listOf("thread"), client.historyReads)
        assertTrue(widget.pendingSteers.isEmpty())
        assertEquals(receipt, widget.state.items.single { it.id == receipt.id })
        widget.state.applyDraft("current")
        client.events.emit(AppServerEvent.TurnCompleted("thread", "turn", TurnStatus.Interrupted))
        runCurrent()
        assertEquals("current", widget.state.composerDraft)
    }

    private fun TestScope.widget(client: InputClient, running: Boolean = true): ChatWidget =
        ChatWidget(client, backgroundScope).apply {
            bind(session("thread"))
            state.applyStatus(if (running) ThreadStatus.Active() else ThreadStatus.Idle)
            attach()
        }

    private fun ChatWidget.submit(value: String, queued: Boolean = false) {
        state.applyDraft(value)
        action(AppEvent.SubmitUserMessage(text(value), queued = queued))
    }

    private data class SteerCall(val threadId: String, val inputs: List<UserInput>, val clientId: String?)

    private class InputClient : AppServerClient {
        override val events = MutableSharedFlow<AppServerEvent>()
        override val requests = MutableSharedFlow<ApprovalRequest>()
        override val connection = flowOf(ConnectionState.Ready)
        val steered = mutableListOf<SteerCall>()
        val started = mutableListOf<Pair<String, List<UserInput>>>()
        val queuedCalls = mutableListOf<Pair<String, List<UserInput>>>()
        val deleted = mutableListOf<Pair<String, String>>()
        val listed = mutableListOf<String>()
        val historyReads = mutableListOf<String>()
        val deleteReplies = mutableMapOf<String, Result<Boolean>>()
        val queueReadReplies = mutableMapOf<String, CompletableDeferred<Result<List<QueuedSubmission>>>>()
        val activeTurnIds = mutableMapOf("thread" to "turn")
        private val queues = mutableMapOf<String, MutableList<QueuedSubmission>>()
        var steerReply: CompletableDeferred<Result<String>>? = null
        var startReply: CompletableDeferred<Result<String>>? = null
        var queueReply: CompletableDeferred<Result<QueuedSubmission?>>? = null
        var history: ThreadReadResponse? = null

        fun queue(threadId: String): MutableList<QueuedSubmission> = queues.getOrPut(threadId) { mutableListOf() }

        override suspend fun initialize(clientInfo: ClientInfo) = Result.success(Unit)

        override fun activeTurnId(threadId: String): String? = activeTurnIds[threadId]

        override suspend fun steerTurn(
            threadId: String,
            inputs: List<UserInput>,
            clientUserMessageId: String?,
            expectedTurnId: String?,
        ): Result<String> {
            steered.add(SteerCall(threadId, inputs, clientUserMessageId))
            return steerReply?.await() ?: Result.success(expectedTurnId ?: activeTurnIds[threadId] ?: "turn")
        }

        override suspend fun startTurn(
            threadId: String,
            inputs: List<UserInput>,
            outputSchema: JsonElement?,
            effort: ReasoningEffort?,
            clientMetadata: Map<String, String>?,
        ): Result<String> {
            started.add(threadId to inputs)
            return startReply?.await() ?: Result.success("turn")
        }

        override suspend fun addToQueue(threadId: String, inputs: List<UserInput>): Result<QueuedSubmission?> {
            queuedCalls.add(threadId to inputs)
            val result = queueReply?.await() ?: Result.success(QueuedSubmission("queue-${queuedCalls.size}", input = inputs))
            result.getOrNull()?.let { queue(threadId).add(it) }
            return result
        }

        override suspend fun listQueue(threadId: String): Result<List<QueuedSubmission>> {
            listed.add(threadId)
            return queueReadReplies[threadId]?.await() ?: Result.success(queue(threadId).toList())
        }

        override suspend fun deleteQueued(threadId: String, id: String): Result<Boolean> {
            deleted.add(threadId to id)
            val result = deleteReplies[id] ?: Result.success(queue(threadId).any { it.id == id })
            if (result.isSuccess) queue(threadId).removeAll { it.id == id }
            return result
        }

        override suspend fun readThread(params: ThreadReadParams): Result<ThreadReadResponse> {
            historyReads.add(params.threadId)
            return history?.let { Result.success(it) } ?: Result.failure(IllegalStateException("No history configured"))
        }

        override suspend fun respond(requestId: RequestId, response: ApprovalResponse) = Unit
        override suspend fun close() = Unit
    }

    private companion object {
        fun text(value: String): List<UserInput> = listOf(UserInput.Text(value))
        fun session(id: String) = ThreadSessionState(threadId = id, threadName = "Named test")
        fun thread(id: String) = Thread(
            id = id, preview = "", modelProvider = "openai", createdAt = 0, updatedAt = 0, cwd = "",
            status = ThreadStatus.Active(), cliVersion = "1", ephemeral = false, projectId = null, sessionId = id,
        )
    }
}
