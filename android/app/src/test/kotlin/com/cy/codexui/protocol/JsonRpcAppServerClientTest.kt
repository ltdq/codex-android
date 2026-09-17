package com.cy.codexui.protocol

import com.cy.codexui.protocol.protocol.Json
import com.cy.codexui.protocol.protocol.array
import com.cy.codexui.protocol.protocol.bool
import com.cy.codexui.protocol.protocol.long
import com.cy.codexui.protocol.protocol.objectOrNull
import com.cy.codexui.protocol.protocol.objectValue
import com.cy.codexui.protocol.protocol.required
import com.cy.codexui.protocol.protocol.text
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.v2.ClientInfo
import com.cy.codexui.protocol.protocol.v2.CommandExecutionApprovalDecision
import com.cy.codexui.protocol.protocol.v2.UserInput
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises real wire messages while replacing only the JNI byte transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class JsonRpcAppServerClientTest {
    private class HarnessTransport : JsonRpcTransport {
        val incoming = Channel<Result<String?>>(Channel.UNLIMITED)
        val outgoing = Channel<Pair<JsonRpcMessageKind, String>>(Channel.UNLIMITED)
        var starts = 0
        var closes = 0
        override suspend fun start() { starts++ }
        override suspend fun send(kind: JsonRpcMessageKind, message: String) { outgoing.send(kind to message) }
        override suspend fun receive(): String? = incoming.receive().getOrThrow()
        override suspend fun close() { closes++ }
        suspend fun request(): JsonObject {
            val (kind, message) = outgoing.receive()
            assertEquals(JsonRpcMessageKind.Request, kind)
            return Json.parse(message).objectValue()
        }
        suspend fun response(request: JsonObject, result: JsonObject) {
            incoming.send(Result.success(Json.write(obj("id" to request["id"], "result" to result))))
        }
        suspend fun sentResponse(): JsonObject {
            val (kind, message) = outgoing.receive()
            assertEquals(JsonRpcMessageKind.Response, kind)
            return Json.parse(message).objectValue()
        }
        suspend fun push(message: String) { incoming.send(Result.success(message)) }
    }

    @Test
    fun `native handshake is not repeated and concurrent responses use their ids`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        assertEquals(1, transport.starts)
        assertTrue(transport.outgoing.tryReceive().isFailure)
        val account = async { client.readAccount().getOrThrow() }
        val config = async { client.readConfig().getOrThrow() }
        val accountRequest = transport.request()
        val configRequest = transport.request()
        transport.response(configRequest, obj("config" to obj("model" to "test-model")))
        transport.response(accountRequest, obj("account" to obj("type" to "chatgpt", "email" to "user@example.test", "planType" to "pro")))
        assertEquals("test-model", config.await().snapshot.model)
        assertEquals("user@example.test", account.await().email)
        assertTrue(account.await().loggedIn)
        client.close()
    }

    @Test
    fun `archived listing includes all pages without replacing active threads`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val listing = async { client.listThreads(true).getOrThrow() }
        val first = transport.request()
        assertFalse(first.objectOrNull("params")!!.bool("archived")!!)
        transport.response(first, obj("data" to listOf(obj("id" to "one", "createdAt" to 17, "status" to obj("type" to "idle"))), "nextCursor" to "page-two"))
        val second = transport.request()
        assertEquals("page-two", second.objectOrNull("params")!!.text("cursor"))
        transport.response(second, obj("data" to listOf(obj("id" to "two"))))
        val archive = transport.request()
        assertTrue(archive.objectOrNull("params")!!.bool("archived")!!)
        transport.response(archive, obj("data" to listOf(obj("id" to "old"))))
        val threads = listing.await()
        assertEquals(listOf("one", "two", "old"), threads.map { it.id })
        assertEquals(17_000L, threads.first().createdAt)
        assertTrue(threads.last().archived)
        client.close()
    }

    @Test
    fun `history is decoded from nested turns and stream retains final text`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readThread("t").getOrThrow() }
        transport.response(transport.request(), obj("thread" to obj("id" to "t", "turns" to listOf(
            obj("id" to "turn", "status" to "inProgress", "items" to listOf(obj("type" to "agentMessage", "id" to "item", "text" to "Partial"))),
        ))))
        assertEquals("Partial", (read.await().items.single() as AgentMessageItem).text)
        val event = async(UnconfinedTestDispatcher(testScheduler)) { client.events.first() }
        transport.push("""{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"item","delta":" result"}}""")
        assertEquals(" result", assertIs<AppServerEvent.AgentMessageDelta>(event.await()).delta.delta)
        val stop = async { client.interruptTurn("t").getOrThrow() }
        val request = transport.request()
        assertEquals("turn/interrupt", request.text("method"))
        assertEquals("turn", request.objectOrNull("params")!!.text("turnId"))
        transport.response(request, obj())
        stop.await()
        client.close()
    }

    @Test
    fun `start and steer preserve text escaping and active turn precondition`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val input = "A \"quote\"\nnext line"
        val start = async { client.startTurn("t", listOf(UserInput.Text(input))).getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertEquals(input, params.array("input").single().objectValue().text("text"))
        assertEquals(emptyList(), params.array("input").single().objectValue().array("text_elements"))
        transport.response(request, obj("turn" to obj("id" to "turn", "status" to "inProgress")))
        assertEquals("turn", start.await())
        val steer = async { client.steerTurn("t", listOf(UserInput.Text("More"))).getOrThrow() }
        val steerRequest = transport.request()
        assertEquals("turn", steerRequest.objectOrNull("params")!!.text("expectedTurnId"))
        transport.response(steerRequest, obj("turnId" to "turn"))
        assertEquals("turn", steer.await())
        client.close()
    }

    @Test
    fun `server errors and request timeout are failures while cancellation propagates`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readAccount() }
        val request = transport.request()
        transport.push(Json.write(obj("id" to request["id"], "error" to obj("code" to -32000, "message" to "Cannot read auth"))))
        assertEquals("Cannot read auth", read.await().exceptionOrNull()?.message)
        val timedOut = async { client.readAccount() }
        transport.request()
        advanceTimeBy(120_001)
        assertIs<IOException>(timedOut.await().exceptionOrNull())
        val cancelled = async { client.readAccount() }
        transport.request()
        cancelled.cancel()
        runCurrent()
        assertTrue(cancelled.isCancelled)
        assertTrue(client.listProjects().isFailure)
        client.close()
    }

    @Test
    fun `disconnect fails pending requests and retry starts a fresh transport`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val read = async { client.readAccount() }
        transport.request()
        transport.incoming.send(Result.failure(IOException("Disconnected")))
        assertEquals("Disconnected", read.await().exceptionOrNull()?.message)
        assertIs<ConnectionState.Failed>(client.connection.value)
        assertEquals(1, transport.closes)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        assertEquals(2, transport.starts)
        val retried = async { client.readAccount().getOrThrow() }
        transport.response(transport.request(), obj("account" to JsonNull))
        assertFalse(retried.await().loggedIn)
        client.close()
    }

    @Test
    fun `command approval preserves numeric server request ids`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":42,"method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"cmd","command":"git status","cwd":"/workspace"}}""")
        val received = assertIs<ApprovalRequest.Exec>(approval.await())
        assertEquals("git status", received.params.command)
        client.respond(received.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Decline))
        val response = transport.sentResponse()
        assertEquals(JsonPrimitive(42), response["id"])
        assertEquals("decline", response.objectOrNull("result")!!.text("decision"))
        client.close()
    }

    @Test
    fun `closing discards queued approvals before reconnecting`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        transport.push("""{"id":"old","method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"old-item","command":"git status","cwd":"/workspace"}}""")
        runCurrent()
        client.close()
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"new","method":"item/commandExecution/requestApproval","params":{"threadId":"t","turnId":"next-turn","itemId":"new-item","command":"git diff","cwd":"/workspace"}}""")
        val received = assertIs<ApprovalRequest.Exec>(approval.await())
        assertEquals("new", received.requestId.value)
        client.respond(received.requestId, ApprovalResponse.CommandExecution(CommandExecutionApprovalDecision.Decline))
        assertEquals(JsonPrimitive("new"), transport.sentResponse()["id"])
        client.close()
    }

    @Test
    fun `MCP form response retains boolean and number types and current time uses seconds`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"form","method":"mcpServer/elicitation/request","params":{"threadId":"t","serverName":"test","mode":"form","message":"Settings","requestedSchema":{"properties":{"enabled":{"type":"boolean"},"count":{"type":"integer"}},"required":["count"]}}}""")
        val received = assertIs<ApprovalRequest.Elicitation>(approval.await())
        assertTrue(received.params.requestedSchema.fields.single { it.name == "count" }.required)
        client.respond(received.requestId, ApprovalResponse.Elicitation(ElicitationAction.Accept, mapOf("enabled" to "true", "count" to "3")))
        val content = transport.sentResponse().objectOrNull("result")!!.objectOrNull("content")!!
        assertEquals(JsonPrimitive(true), content["enabled"])
        assertEquals(JsonPrimitive(3), content["count"])
        transport.push("""{"id":"time","method":"currentTime/read","params":{"threadId":"t"}}""")
        val response = transport.sentResponse().objectOrNull("result")!!
        assertTrue(response.long("currentTimeAt")!! in (System.currentTimeMillis() / 1000 - 2)..(System.currentTimeMillis() / 1000 + 2))
        assertNull(response["epochMillis"])
        client.close()
    }

    @Test
    fun `project creation sends roots and idempotency and goals decode nested response`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val created = async { client.createProject("Work", "/workspace").getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertEquals("/workspace", params.array("roots").single().objectValue().text("path"))
        assertTrue(params.required("idempotencyKey").isNotBlank())
        transport.response(request, obj("project" to obj("id" to "p", "name" to "Work", "roots" to listOf(obj("path" to "/workspace")))))
        assertEquals("/workspace", created.await().path)
        val goal = async { client.getGoal("t").getOrThrow() }
        transport.response(transport.request(), obj("goal" to obj("threadId" to "t", "objective" to "Implement", "status" to "active", "tokensUsed" to 123)))
        assertEquals(123, goal.await()!!.tokensUsed)
        client.close()
    }

    @Test
    fun `queue submission has an identity and starts the server supplied turn`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val queue = async { client.addToQueue("t", listOf(UserInput.Text("Next"))).getOrThrow() }
        val request = transport.request()
        val params = request.objectOrNull("params")!!
        assertTrue(params.required("clientUserMessageId").isNotEmpty())
        transport.response(request, obj("queuedSubmission" to obj("id" to "q", "clientUserMessageId" to params.required("clientUserMessageId"), "input" to params["input"])))
        assertEquals("Next", queue.await().preview)
        val start = async { client.startQueued("t", "q").getOrThrow() }
        val startRequest = transport.request()
        assertEquals("q", startRequest.objectOrNull("params")!!.required("queuedSubmissionId"))
        transport.response(startRequest, obj("turn" to obj("id" to "queued-turn")))
        start.await()
        val stop = async { client.interruptTurn("t").getOrThrow() }
        val stopRequest = transport.request()
        assertEquals("queued-turn", stopRequest.objectOrNull("params")!!.required("turnId"))
        transport.response(stopRequest, obj())
        stop.await()
        client.close()
    }

    @Test
    fun `plugin installation reads the real installed state after acknowledgement`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val install = async { client.installPlugin("formatter", "/marketplace.json").getOrThrow() }
        val request = transport.request()
        assertEquals("/marketplace.json", request.objectOrNull("params")!!.required("marketplacePath"))
        assertNull(request.objectOrNull("params")!!.text("remoteMarketplaceName"))
        transport.response(request, obj("authPolicy" to "onInstall", "appsNeedingAuth" to emptyList<JsonElement>()))
        val read = transport.request()
        assertEquals("plugin/read", read.required("method"))
        transport.response(read, obj("plugin" to obj("marketplaceName" to "local", "summary" to obj("id" to "formatter@local", "name" to "formatter", "installed" to true),
            "skills" to emptyList<JsonElement>(), "apps" to emptyList<JsonElement>(), "mcpServers" to emptyList<JsonElement>())))
        assertTrue(install.await().installed)
        client.close()
    }

    @Test
    fun `unit parameter methods omit params instead of serializing an invalid empty object`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val logout = async { client.logout().getOrThrow() }
        val request = transport.request()
        assertEquals("account/logout", request.required("method"))
        assertFalse("params" in request)
        transport.response(request, obj())
        logout.await()
        client.close()
    }

    @Test
    fun `configuration layers retain server reported paths`() {
        val response = WireCodec.config(obj("config" to obj("model" to "actual-model"), "layers" to listOf(
            obj("name" to obj("type" to "user", "file" to "/data/user/0/app/files/home/.codex/config.toml"), "config" to obj()),
            obj("name" to obj("type" to "project", "dotCodexFolder" to "/workspace/.codex"), "config" to obj()),
        )))
        assertEquals("/data/user/0/app/files/home/.codex/config.toml", response.layers!![0].sourcePath)
        assertEquals("/workspace/.codex", response.layers[1].sourcePath)
        assertEquals(com.cy.codexui.protocol.protocol.v2.GoalStatus.Blocked,
            WireCatalogCodec.goal(obj("threadId" to "t", "objective" to "Work", "status" to "blocked")).status)
        assertEquals(com.cy.codexui.protocol.protocol.v2.SkillScope.Project,
            WireCatalogCodec.skill(obj("name" to "local", "path" to "/workspace/SKILL.md", "scope" to "repo")).scope)
    }

    @Test
    fun `permissions approval exposes structured file system entries`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val approval = async { client.requests.first() }
        transport.push("""{"id":"permissions","method":"item/permissions/requestApproval","params":{"threadId":"t","turnId":"turn","itemId":"item","permissions":{"network":{"enabled":true},"fileSystem":{"entries":[{"path":{"type":"path","path":"/workspace"},"access":"write"}]}}}}""")
        val received = assertIs<ApprovalRequest.Permissions>(approval.await())
        assertEquals(listOf("/workspace"), received.params.permissions.fileSystemWrite)
        assertTrue(received.params.permissions.network)
        client.respond(received.requestId, ApprovalResponse.Permissions(com.cy.codexui.protocol.protocol.v2.PermissionsApprovalDecision.Decline))
        val result = transport.sentResponse().objectOrNull("result")!!
        assertTrue(result.objectOrNull("permissions")!!.isEmpty())
        client.close()
    }

    @Test
    fun `a slow event observer cannot block RPC responses behind streaming notifications`() = runTest {
        val transport = HarnessTransport()
        val client = JsonRpcAppServerClient(transport, backgroundScope)
        client.initialize(ClientInfo("android", version = "1")).getOrThrow()
        val observed = async(UnconfinedTestDispatcher(testScheduler)) {
            client.events.first {
                client.readAccount().getOrThrow()
                true
            }
        }
        val delta = """{"method":"item/agentMessage/delta","params":{"threadId":"t","turnId":"turn","itemId":"item","delta":"chunk"}}"""
        transport.push(delta)
        val account = transport.request()
        repeat(300) { transport.push(delta) }
        transport.response(account, obj("account" to JsonNull))
        assertIs<AppServerEvent.AgentMessageDelta>(observed.await())
        assertEquals(0L, testScheduler.currentTime)
        client.close()
    }
}
