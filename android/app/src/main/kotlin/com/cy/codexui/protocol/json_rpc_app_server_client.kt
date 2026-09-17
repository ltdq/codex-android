package com.cy.codexui.protocol

import com.cy.codexui.protocol.protocol.Json
import com.cy.codexui.protocol.protocol.RequestId
import com.cy.codexui.protocol.protocol.array
import com.cy.codexui.protocol.protocol.bool
import com.cy.codexui.protocol.protocol.int
import com.cy.codexui.protocol.protocol.long
import com.cy.codexui.protocol.protocol.objectOrNull
import com.cy.codexui.protocol.protocol.objectValue
import com.cy.codexui.protocol.protocol.required
import com.cy.codexui.protocol.protocol.strings
import com.cy.codexui.protocol.protocol.text
import com.cy.codexui.protocol.protocol.wireText
import com.cy.codexui.protocol.protocol.v2.*
import java.io.EOFException
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put

/**
 * Which JSON-RPC envelope an outgoing message is.
 *
 * The envelope shape is the only thing a transport cannot infer from the text, and the native side
 * uses it to deserialize straight into the typed request/notification/response instead of parsing an
 * envelope and re-encoding its payload.
 */
enum class JsonRpcMessageKind(val code: Int) {
    Request(0),
    Notification(1),
    Response(2),
    Error(3),
}

/** Native start completes the app-server initialize/initialized handshake before returning. */
interface JsonRpcTransport {
    suspend fun start()
    suspend fun send(kind: JsonRpcMessageKind, message: String)
    suspend fun receive(): String?
    suspend fun close()
}

class AppServerRpcException(val code: Int, message: String, val data: JsonElement? = null) : Exception(message)

class JsonRpcAppServerClient(
    private val transport: JsonRpcTransport,
    private val scope: CoroutineScope,
    private val defaultWorkspace: String? = null,
) : AppServerClient {
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connection = state.asStateFlow()
    private val eventStream = MutableSharedFlow<AppServerEvent>(extraBufferCapacity = 128)
    override val events = eventStream.asSharedFlow()
    private val eventQueue = Channel<AppServerEvent>(Channel.UNLIMITED)
    private val requestStream = Channel<ApprovalRequest>(Channel.UNLIMITED)
    override val requests: Flow<ApprovalRequest> = requestStream.receiveAsFlow()
    private val sequence = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val approvals = ConcurrentHashMap<String, Pair<JsonElement, JsonObject>>()
    private val activeTurns = ConcurrentHashMap<String, String>()
    private val sessions = ConcurrentHashMap<String, ThreadSessionState>()
    private val marketplacePaths = ConcurrentHashMap<String, String>()
    private val lifecycle = Mutex()
    private var reader: Job? = null
    private var publisher: Job? = null

    override suspend fun initialize(clientInfo: ClientInfo): Result<Unit> = result {
        lifecycle.withLock {
            if (state.value == ConnectionState.Ready) return@withLock
            state.value = ConnectionState.Connecting
            try {
                transport.start()
                state.value = ConnectionState.Ready
                publisher = scope.launch {
                    for (event in eventQueue) eventStream.emit(event)
                }
                reader = scope.launch {
                    try {
                        while (true) dispatch(transport.receive() ?: throw EOFException("Native app-server disconnected"))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        lifecycle.withLock {
                            runCatching { transport.close() }
                            fail(error)
                            reader = null
                        }
                    }
                }
            } catch (error: Exception) {
                runCatching { transport.close() }
                fail(error)
                throw error
            }
        }
    }

    private fun fail(error: Exception) {
        state.value = ConnectionState.Failed(error.message ?: error.javaClass.simpleName)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        approvals.clear()
        activeTurns.clear()
        sessions.clear()
        publisher?.cancel()
        publisher = null
        while (eventQueue.tryReceive().isSuccess) { /* Discard events from the lost connection. */ }
        while (requestStream.tryReceive().isSuccess) { /* Discard approvals from the lost connection. */ }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun rpc(method: String, params: JsonElement? = obj()): JsonObject {
        check(state.value == ConnectionState.Ready) { "App-server is not connected" }
        val id = "android-${sequence.incrementAndGet()}"
        val response = CompletableDeferred<JsonObject>()
        pending[id] = response
        try {
            transport.send(JsonRpcMessageKind.Request, Json.write(buildJsonObject {
                put("id", id)
                put("method", method)
                if (params != null) put("params", params)
            }))
            return try {
                withTimeout(120_000) { response.await() }
            } catch (error: TimeoutCancellationException) {
                throw IOException("App-server request timed out: $method", error)
            }
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun call(method: String, params: JsonElement? = obj()): Result<Unit> = result { rpc(method, params) }

    private suspend fun dispatch(message: String) {
        val envelope = Json.parse(message).objectValue()
        val method = envelope.text("method")
        val id = envelope["id"]
        if (method == null && id != null) {
            val awaiting = pending.remove(id.wireText()) ?: return
            val error = envelope.objectOrNull("error")
            if (error != null) awaiting.completeExceptionally(AppServerRpcException(error.int("code") ?: -32603, error.text("message") ?: "App-server request failed", error["data"]))
            else if ("result" in envelope) awaiting.complete((envelope["result"] as? JsonObject) ?: obj())
            else awaiting.completeExceptionally(IOException("App-server response has no result or error"))
        } else if (method != null) {
            val params = envelope.objectOrNull("params") ?: obj()
            if (id == null) notification(method, params) else serverRequest(id, method, params)
        }
    }

    override suspend fun close() {
        lifecycle.withLock {
            reader?.cancel()
            reader = null
            publisher?.cancel()
            publisher = null
            while (eventQueue.tryReceive().isSuccess) { /* Discard events from the closed connection. */ }
            while (requestStream.tryReceive().isSuccess) { /* Discard approvals from the closed connection. */ }
            try { transport.close() } finally {
                pending.values.forEach { it.completeExceptionally(EOFException("App-server closed")) }
                pending.clear()
                approvals.clear()
                activeTurns.clear()
                sessions.clear()
                state.value = ConnectionState.Disconnected
            }
        }
    }

    private suspend fun threadPages(archived: Boolean, term: String? = null): List<Thread> {
        val threads = mutableListOf<Thread>()
        var cursor: String? = null
        do {
            val page = rpc("thread/list", obj("archived" to archived, "cursor" to cursor, "limit" to 100, "searchTerm" to term))
            threads += page.array("data").map { WireCodec.thread(it, archived) }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Thread pagination did not advance" }
            cursor = next
        } while (cursor != null)
        return threads
    }

    override suspend fun listThreads(includeArchived: Boolean) = result {
        threadPages(false) + if (includeArchived) threadPages(true) else emptyList()
    }
    override suspend fun searchThreads(term: String, includeArchived: Boolean) = result {
        threadPages(false, term) + if (includeArchived) threadPages(true, term) else emptyList()
    }
    override suspend fun listLoadedThreads() = result { rpc("thread/loaded/list").strings("data") }
    override suspend fun readThread(threadId: String) = result {
        val o = rpc("thread/read", obj("threadId" to threadId, "includeTurns" to true)).get("thread")!!.objectValue()
        val turns = o.array("turns").map(WireCodec::turn)
        turns.lastOrNull { it.status == TurnStatus.InProgress }?.let { activeTurns[threadId] = it.id }
        ThreadReadResponse(WireCodec.thread(o), turns.flatMap { it.items }, turns)
    }
    private suspend fun session(method: String, params: JsonObject) = result {
        WireCodec.session(rpc(method, params)).also { sessions[it.threadId] = it }
    }
    override suspend fun startThread(cwd: String, model: String?) = session("thread/start", obj("cwd" to cwd.ifBlank { defaultWorkspace.orEmpty() }, "model" to model))
    override suspend fun resumeThread(threadId: String) = session("thread/resume", obj("threadId" to threadId))
    override suspend fun forkThread(threadId: String) = session("thread/fork", obj("threadId" to threadId))
    override suspend fun archiveThread(threadId: String) = call("thread/archive", obj("threadId" to threadId))
    override suspend fun unarchiveThread(threadId: String) = call("thread/unarchive", obj("threadId" to threadId))
    override suspend fun deleteThread(threadId: String) = call("thread/delete", obj("threadId" to threadId))
    override suspend fun setThreadName(threadId: String, name: String) = call("thread/name/set", obj("threadId" to threadId, "name" to name))
    override suspend fun compactThread(threadId: String) = call("thread/compact/start", obj("threadId" to threadId))
    override suspend fun runShellCommand(threadId: String, command: String) = call("thread/shellCommand", obj("threadId" to threadId, "command" to command))
    override suspend fun unsubscribeThread(threadId: String) = call("thread/unsubscribe", obj("threadId" to threadId))
    override suspend fun listThreadItems(threadId: String, cursor: String?, limit: Int?) = result {
        val o = rpc("thread/items/list", obj("threadId" to threadId, "cursor" to cursor, "limit" to limit))
        ThreadItemsPage(o.array("data").map { WireCodec.item(it.objectValue()["item"] ?: it) }, o.text("nextCursor"))
    }
    override suspend fun listThreadTurns(threadId: String, cursor: String?, limit: Int?) = result {
        val o = rpc("thread/turns/list", obj("threadId" to threadId, "cursor" to cursor, "limit" to limit))
        ThreadTurnsPage(o.array("data").map(WireCodec::turn), o.text("nextCursor"))
    }
    override suspend fun revertThread(threadId: String, itemId: String?) = result {
        val history = readThread(threadId).getOrThrow()
        val turn = if (itemId == null) history.turns.lastOrNull() else history.turns.find { turn -> turn.items.any { it.id == itemId } }
        requireNotNull(turn) { "The selected message is no longer in this thread" }
        rpc("thread/revert", obj("threadId" to threadId, "beforeTurnId" to turn.id))
        Unit
    }
    override suspend fun updateThreadMetadata(threadId: String, name: String?, projectId: String?) = result {
        if (name != null) setThreadName(threadId, name).getOrThrow()
        WireCodec.thread(rpc("thread/metadata/update", obj("threadId" to threadId, "projectId" to (projectId ?: JsonNull)))["thread"]!!)
    }
    override suspend fun moveThreadToSection(threadId: String, sectionId: String?) = call("thread/section/move", obj("threadId" to threadId, "sectionId" to (sectionId ?: JsonNull)))
    override suspend fun listSections() = result { catalog("threadSection/list").map(WireCatalogCodec::section) }
    override suspend fun createSection(name: String) = result { WireCatalogCodec.section(rpc("threadSection/create", obj("name" to name)).objectOrNull("section")!!) }
    override suspend fun updateSection(sectionId: String, name: String) = result { WireCatalogCodec.section(rpc("threadSection/update", obj("sectionId" to sectionId, "name" to name)).objectOrNull("section")!!) }
    override suspend fun deleteSection(sectionId: String) = call("threadSection/delete", obj("sectionId" to sectionId))

    override suspend fun setGoal(threadId: String, objective: String) = result { WireCatalogCodec.goal(rpc("thread/goal/set", obj("threadId" to threadId, "objective" to objective)).objectOrNull("goal")!!) }
    override suspend fun getGoal(threadId: String) = result { rpc("thread/goal/get", obj("threadId" to threadId)).objectOrNull("goal")?.let(WireCatalogCodec::goal) }
    override suspend fun clearGoal(threadId: String) = call("thread/goal/clear", obj("threadId" to threadId))
    override suspend fun incrementElicitation(threadId: String) = result { rpc("thread/increment_elicitation", obj("threadId" to threadId)).let {
        ElicitationCountResponse(it.int("count") ?: error("Missing elicitation count"), it.bool("paused") == true)
    } }
    override suspend fun decrementElicitation(threadId: String) = result { rpc("thread/decrement_elicitation", obj("threadId" to threadId)).let {
        ElicitationCountResponse(it.int("count") ?: error("Missing elicitation count"), it.bool("paused") == true)
    } }
    override suspend fun listQueue(threadId: String) = result { catalog("thread/queue/list", obj("threadId" to threadId)).map(WireCatalogCodec::queued) }
    override suspend fun addToQueue(threadId: String, inputs: List<UserInput>) = result {
        WireCatalogCodec.queued(rpc("thread/queue/add", obj("threadId" to threadId, "input" to inputs.map(WireCodec::input),
            "clientUserMessageId" to UUID.randomUUID().toString())).objectOrNull("queuedSubmission")!!)
    }
    override suspend fun updateQueued(threadId: String, id: String, inputs: List<UserInput>) = call("thread/queue/update", obj("threadId" to threadId, "queuedSubmissionId" to id, "input" to inputs.map(WireCodec::input)))
    override suspend fun deleteQueued(threadId: String, id: String) = call("thread/queue/delete", obj("threadId" to threadId, "queuedSubmissionId" to id))
    override suspend fun reorderQueue(threadId: String, ids: List<String>) = call("thread/queue/reorder", obj("threadId" to threadId, "queuedSubmissionIds" to ids))
    override suspend fun startQueued(threadId: String, id: String?) = result {
        val turn = rpc("thread/queue/start", obj("threadId" to threadId, "queuedSubmissionId" to id)).objectOrNull("turn")!!
        activeTurns[threadId] = turn.required("id")
        Unit
    }
    override suspend fun updateThreadSettings(threadId: String, model: String?, effort: ReasoningEffort?, approvalPolicy: AskForApproval?, collaborationMode: CollaborationMode?, personality: Personality?) = result {
        require(approvalPolicy != AskForApproval.Granular) { "Granular approval requires an explicit permissions configuration" }
        val current = sessions[threadId]
        val mode = collaborationMode?.let {
            obj("mode" to it.wire, "settings" to obj("model" to (model ?: current?.model ?: error("Load the thread before changing collaboration mode")),
                "reasoning_effort" to (effort ?: current?.reasoningEffort)?.wire, "developer_instructions" to JsonNull))
        }
        rpc("thread/settings/update", obj("threadId" to threadId, "model" to model, "effort" to effort?.wire, "approvalPolicy" to approvalPolicy?.wire,
            "collaborationMode" to mode, "personality" to personality?.wire))
        if (current != null) sessions[threadId] = current.copy(model = model ?: current.model, modelDisplayName = model ?: current.modelDisplayName,
            reasoningEffort = effort ?: current.reasoningEffort, approvalPolicy = approvalPolicy ?: current.approvalPolicy, collaborationMode = collaborationMode ?: current.collaborationMode)
        Unit
    }
    override suspend fun updateThreadSettingsFull(params: ThreadSettingsUpdateParams) = result {
        require(params.approvalPolicy != AskForApproval.Granular) { "Granular approval requires an explicit permissions configuration" }
        val current = sessions[params.threadId]
        val collaboration = params.collaborationMode?.let { obj("mode" to it.wire, "settings" to obj(
            "model" to (params.model ?: current?.model ?: error("Load the thread before changing collaboration mode")),
            "reasoning_effort" to (params.effort ?: current?.reasoningEffort)?.wire, "developer_instructions" to JsonNull)) }
        val sandbox = params.sandboxPolicy?.let { policy -> when (policy.mode) {
            SandboxMode.DangerFullAccess -> obj("type" to "dangerFullAccess")
            SandboxMode.ReadOnly -> obj("type" to "readOnly", "networkAccess" to policy.networkAccess)
            SandboxMode.WorkspaceWrite -> obj("type" to "workspaceWrite", "writableRoots" to policy.writableRoots, "networkAccess" to policy.networkAccess,
                "excludeTmpdirEnvVar" to policy.excludeTmpdirEnvVar, "excludeSlashTmp" to policy.excludeSlashTmp)
        } }
        rpc("thread/settings/update", obj("threadId" to params.threadId, "model" to params.model, "effort" to params.effort?.wire,
            "approvalPolicy" to params.approvalPolicy?.wire, "approvalsReviewer" to params.approvalsReviewer, "summary" to params.summary,
            "sandboxPolicy" to sandbox, "permissions" to params.permissions, "collaborationMode" to collaboration, "personality" to params.personality?.wire,
            "serviceTier" to params.serviceTier, "cwd" to params.cwd, "disabledPluginIds" to params.disabledPluginIds, "multiAgentMode" to params.multiAgentMode))
        if (current != null) sessions[params.threadId] = current.copy(model = params.model ?: current.model, reasoningEffort = params.effort ?: current.reasoningEffort,
            approvalPolicy = params.approvalPolicy ?: current.approvalPolicy, collaborationMode = params.collaborationMode ?: current.collaborationMode)
        Unit
    }
    override suspend fun updateTurnSettings(params: TurnSettingsUpdateParams) = result {
        val response = rpc("turn/settings/update", obj("threadId" to params.threadId, "turnId" to params.turnId, "model" to params.model,
            "effort" to params.effort?.wire, "summary" to params.summary, "approvalsReviewer" to params.approvalsReviewer, "serviceTier" to params.serviceTier))
        check(response.text("status") == "applied") { "The active turn is no longer available" }
        Unit
    }
    override suspend fun setThreadMemoryMode(threadId: String, mode: ThreadMemoryMode) = result {
        require(mode != ThreadMemoryMode.Read) { "The server supports enabled or disabled memory, not read-only mode" }
        rpc("thread/memoryMode/set", obj("threadId" to threadId, "mode" to if (mode == ThreadMemoryMode.Disabled) "disabled" else "enabled"))
        Unit
    }
    override suspend fun startTurn(threadId: String, inputs: List<UserInput>) = result {
        val turn = rpc("turn/start", obj("threadId" to threadId, "input" to inputs.map(WireCodec::input))).objectOrNull("turn") ?: error("Missing turn")
        turn.required("id").also { activeTurns[threadId] = it }
    }
    override suspend fun steerTurn(threadId: String, inputs: List<UserInput>) = result {
        val id = activeTurns[threadId] ?: error("No active turn in this thread")
        rpc("turn/steer", obj("threadId" to threadId, "expectedTurnId" to id, "input" to inputs.map(WireCodec::input))).required("turnId")
    }
    override suspend fun interruptTurn(threadId: String) = result {
        val id = activeTurns[threadId] ?: error("No active turn in this thread")
        rpc("turn/interrupt", obj("threadId" to threadId, "turnId" to id)); Unit
    }
    override suspend fun startReview(threadId: String, target: ReviewTarget) = result {
        val wireTarget = when (target) {
            ReviewTarget.UncommittedChanges -> obj("type" to "uncommittedChanges")
            is ReviewTarget.BaseBranch -> obj("type" to "baseBranch", "branch" to target.branch)
            is ReviewTarget.Commit -> obj("type" to "commit", "sha" to target.sha, "title" to (target.title ?: JsonNull))
            is ReviewTarget.Custom -> obj("type" to "custom", "instructions" to target.instructions)
        }
        val o = rpc("review/start", obj("threadId" to threadId, "target" to wireTarget))
        val reviewThreadId = o.required("reviewThreadId")
        val turn = WireCodec.turn(o["turn"]!!)
        if (turn.status == TurnStatus.InProgress) activeTurns[reviewThreadId] = turn.id
        ReviewStartResponse(reviewThreadId, turn)
    }

    override suspend fun readAccount() = result { WireCodec.account(rpc("account/read")) }
    override suspend fun login(params: LoginAccountParams) = result {
        val body = when (params) {
            is LoginAccountParams.ApiKey -> obj("type" to "apiKey", "apiKey" to params.apiKey)
            is LoginAccountParams.Chatgpt -> obj("type" to "chatgpt", "appBrand" to params.appBrand.wire, "useHostedLoginSuccessPage" to params.useHostedLoginSuccessPage)
            LoginAccountParams.ChatgptDeviceCode -> obj("type" to "chatgptDeviceCode")
            is LoginAccountParams.ChatgptAuthTokens -> obj("type" to "chatgptAuthTokens", "accessToken" to params.accessToken, "chatgptAccountId" to params.chatgptAccountId, "chatgptPlanType" to params.chatgptPlanType)
            is LoginAccountParams.AmazonBedrock -> obj("type" to "amazonBedrock", "apiKey" to params.apiKey, "region" to params.region)
            is LoginAccountParams.AmazonBedrockAccessKeys -> obj("type" to "amazonBedrockAccessKeys", "accessKeyId" to params.accessKeyId, "secretAccessKey" to params.secretAccessKey, "sessionToken" to params.sessionToken, "region" to params.region)
        }
        val o = rpc("account/login/start", body)
        when (o.required("type")) {
            "apiKey" -> LoginAccountResponse.ApiKey
            "chatgpt" -> LoginAccountResponse.Chatgpt(o.required("loginId"), o.required("authUrl"))
            "chatgptDeviceCode" -> LoginAccountResponse.ChatgptDeviceCode(o.required("loginId"), o.required("userCode"), o.required("verificationUrl"))
            "chatgptAuthTokens" -> LoginAccountResponse.ChatgptAuthTokens
            "amazonBedrock", "amazonBedrockAccessKeys" -> LoginAccountResponse.AmazonBedrock
            else -> error("Unsupported login response: ${o.text("type")}")
        }
    }
    override suspend fun cancelLogin(loginId: String) = call("account/login/cancel", obj("loginId" to loginId))
    override suspend fun logout() = call("account/logout", null)
    override suspend fun readRateLimits() = result { WireCodec.rateLimits(rpc("account/rateLimits/read").objectOrNull("rateLimits") ?: error("Missing rate limits")) }
    override suspend fun readUsage() = result {
        val o = rpc("account/usage/read")
        AccountUsage(o.array("dailyUsageBuckets").map { it.objectValue().let { bucket -> UsageBucket(bucket.required("startDate"), bucket.int("tokens") ?: 0) } },
            o.objectOrNull("summary")?.int("lifetimeTokens") ?: 0)
    }
    override suspend fun readWorkspaceMessages() = result { rpc("account/workspaceMessages/read", null).array("messages").map { value -> value.objectValue().let {
        WorkspaceMessage(it.required("messageId"), it.required("messageType"), it.required("messageBody"))
    } } }

    override suspend fun readConfig(cwd: String?, includeLayers: Boolean) = result { WireCodec.config(rpc("config/read", obj("cwd" to cwd, "includeLayers" to includeLayers))) }
    override suspend fun readConfigLayers() = readConfig().map { it.layers.orEmpty() }
    override suspend fun readConfigRequirements() = result { ConfigRequirementsReadResponse(rpc("configRequirements/read", null)["requirements"] ?: JsonNull) }
    private fun configWritten(o: JsonObject) = ConfigWriteResponse(o.required("filePath"), WriteStatus.fromWire(o.text("status")), o.text("version").orEmpty())
    override suspend fun writeConfigValue(params: ConfigValueWriteParams) = result {
        configWritten(rpc("config/value/write", obj("keyPath" to params.keyPath, "value" to params.value, "mergeStrategy" to params.mergeStrategy.wire, "filePath" to params.filePath, "expectedVersion" to params.expectedVersion)))
    }
    override suspend fun writeConfigBatch(params: ConfigBatchWriteParams) = result {
        configWritten(rpc("config/batchWrite", obj("edits" to params.edits.map { obj("keyPath" to it.keyPath, "value" to it.value, "mergeStrategy" to it.mergeStrategy.wire) },
            "filePath" to params.filePath, "expectedVersion" to params.expectedVersion, "reloadUserConfig" to params.reloadUserConfig)))
    }
    override suspend fun reloadMcpServers() = call("config/mcpServer/reload", null)

    override suspend fun listModels() = result {
        val models = mutableListOf<ModelPreset>()
        var cursor: String? = null
        do {
            val page = rpc("model/list", obj("limit" to 100, "cursor" to cursor))
            models += page.array("data").map { value -> value.objectValue().let { o -> ModelPreset(o.required("id"), o.required("model"), o.required("displayName"), o.text("description").orEmpty(),
                ReasoningEffort.fromWire(o.required("defaultReasoningEffort")), o.array("supportedReasoningEfforts").map { ReasoningEffort.fromWire(it.objectValue().required("reasoningEffort")) }, o.bool("isDefault") == true,
                o.int("contextWindow") ?: 0) } }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Model pagination did not advance" }
            cursor = next
        } while (cursor != null)
        models
    }

    private suspend fun catalog(method: String, params: JsonObject = obj()): List<JsonObject> {
        val values = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val page = rpc(method, JsonObject(params + obj("limit" to 100, "cursor" to cursor)))
            values += page.array("data").map { it.objectValue() }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "Catalog pagination did not advance: $method" }
            cursor = next
        } while (cursor != null)
        return values
    }
    override suspend fun readModelProviderCapabilities() = result { rpc("modelProvider/capabilities/read").mapNotNull { (name, value) ->
        (value as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { name to it }
    }.toMap() }
    override suspend fun listPermissionProfiles() = result {
        catalog("permissionProfile/list").map { o -> PermissionProfileEntry(o.required("id"), o.required("id"), o.text("description").orEmpty()) }
    }
    override suspend fun listExperimentalFeatures() = result {
        catalog("experimentalFeature/list").map { o -> ExperimentalFeatureEntry(o.required("name"), o.text("displayName") ?: o.required("name"),
            o.text("description").orEmpty(), o.bool("enabled") == true, o.required("stage")) }
    }
    override suspend fun setExperimentalFeature(id: String, enabled: Boolean) = call("experimentalFeature/enablement/set", obj("enablement" to obj(id to enabled)))
    override suspend fun listCollaborationModes() = result {
        catalog("collaborationMode/list").map { o ->
            val mode = CollaborationMode.entries.find { it.wire == o.text("mode") } ?: CollaborationMode.Default
            CollaborationModeEntry(o.text("mode") ?: o.required("name"), mode, o.required("name"), readonly = mode == CollaborationMode.Plan)
        }
    }
    override suspend fun listSkills() = result {
        rpc("skills/list", obj("cwds" to defaultWorkspace?.let(::listOf))).array("data").flatMap { it.objectValue().array("skills") }
            .map { WireCatalogCodec.skill(it.objectValue()) }
    }
    override suspend fun writeSkillConfig(name: String, enabled: Boolean) = call("skills/config/write", obj(if (name.startsWith('/')) "path" to name else "name" to name, "enabled" to enabled))
    override suspend fun setSkillExtraRoots(roots: List<String>) = call("skills/extraRoots/set", obj("extraRoots" to roots))
    override suspend fun listMcpServers() = result {
        val servers = mutableListOf<McpServerStatusEntry>()
        var cursor: String? = null
        do {
            val page = rpc("mcpServerStatus/list", obj("cursor" to cursor, "limit" to 100))
            servers += page.array("data").map { value -> value.objectValue().let { o -> McpServerStatusEntry(o.required("name"),
                McpServerConnectionStatus.entries.find { it.wire == o.text("runtimeStatus") } ?: McpServerConnectionStatus.Starting,
                o.objectOrNull("tools")?.size ?: 0, o.array("resources").size, o.text("toolsError")) } }
            val next = page.text("nextCursor")
            check(next == null || next != cursor) { "MCP pagination did not advance" }
            cursor = next
        } while (cursor != null)
        servers
    }
    override suspend fun mcpOauthLogin(name: String) = result { rpc("mcpServer/oauth/login", obj("name" to name)).required("authorizationUrl") }
    override suspend fun callMcpTool(server: String, tool: String, arguments: String, threadId: String?) = result {
        val thread = threadId ?: sessions.keys.singleOrNull() ?: error("Select a loaded thread before calling an MCP tool")
        val o = rpc("mcpServer/tool/call", obj("threadId" to thread, "server" to server, "tool" to tool, "arguments" to Json.parse(arguments)))
        McpServerToolCallResponse(Json.write(o), o.bool("isError") == true)
    }
    override suspend fun readMcpResource(server: String, uri: String) = result {
        val contents = rpc("mcpServer/resource/read", obj("server" to server, "uri" to uri)).array("contents").map { it.objectValue() }
        McpResourceReadResponse(uri, contents.firstOrNull()?.text("mimeType"), contents.mapNotNull { it.text("text") }.takeIf { it.isNotEmpty() }?.joinToString("\n"))
    }
    override suspend fun readMemoryStatus() = result { rpc("memory/status").let { MemoryStatusResponse(it.bool("v2Ready") == true, it.int("v2ConsolidatedThreads") ?: 0) } }
    override suspend fun resetMemory() = call("memory/reset", null)

    override suspend fun listProjects() = result { catalog("project/list").map(WireCatalogCodec::project) }
    override suspend fun readProject(projectId: String) = result { WireCatalogCodec.project(rpc("project/read", obj("projectId" to projectId)).objectOrNull("project")!!) }
    override suspend fun createProject(name: String, path: String) = result {
        WireCatalogCodec.project(rpc("project/create", obj("name" to name, "roots" to listOf(obj("path" to path)), "idempotencyKey" to UUID.randomUUID().toString())).objectOrNull("project")!!)
    }
    override suspend fun updateProject(projectId: String, name: String?, path: String?) = result {
        WireCatalogCodec.project(rpc("project/update", obj("projectId" to projectId, "name" to name, "roots" to path?.let { listOf(obj("path" to it)) })).objectOrNull("project")!!)
    }
    override suspend fun deleteProject(projectId: String) = call("project/delete", obj("projectId" to projectId))
    override suspend fun moveProject(projectId: String, position: Int) = result {
        val others = listProjects().getOrThrow().filterNot { it.id == projectId }
        require(position in 0..others.size) { "Project position is out of range" }
        rpc("project/move", obj("projectId" to projectId, "beforeProjectId" to others.getOrNull(position)?.id))
        Unit
    }
    override suspend fun importProject(path: String) = result {
        WireCatalogCodec.project(rpc("project/import", obj("name" to path.trimEnd('/').substringAfterLast('/'), "roots" to listOf(obj("path" to path)),
            "idempotencyKey" to UUID.randomUUID().toString())).objectOrNull("project")!!)
    }

    private fun marketplaces(o: JsonObject): List<MarketplaceEntry> = o.array("marketplaces").map { WireCatalogCodec.marketplace(it.objectValue()) }.also { list ->
        list.forEach { if (it.path.isNotBlank()) marketplacePaths[it.name] = it.path }
    }
    override suspend fun listPlugins(params: PluginListParams) = result {
        val o = rpc("plugin/list", obj("cwds" to (params.cwds ?: defaultWorkspace?.let(::listOf)), "forceRefetch" to params.forceRefetch,
            "marketplaceKinds" to params.marketplaceKinds?.map { it.wire }))
        PluginListResponse(marketplaces(o), o.strings("featuredPluginIds"), WireCatalogCodec.marketplaceErrors(o))
    }
    override suspend fun listInstalledPlugins(params: PluginInstalledParams) = result {
        val o = rpc("plugin/installed", obj("cwds" to (params.cwds ?: defaultWorkspace?.let(::listOf)), "installSuggestionPluginNames" to params.installSuggestionPluginNames))
        PluginInstalledResponse(marketplaces(o), WireCatalogCodec.marketplaceErrors(o))
    }
    private fun pluginSelector(name: String, marketplace: String?): JsonObject {
        val path = marketplace?.takeIf { it.startsWith('/') } ?: marketplacePaths[marketplace.orEmpty()]
        return obj("pluginName" to name, "marketplacePath" to path, "remoteMarketplaceName" to marketplace?.takeIf { path == null })
    }
    override suspend fun readPlugin(name: String, marketplace: String?) = result { WireCatalogCodec.pluginDetail(rpc("plugin/read", pluginSelector(name, marketplace)).objectOrNull("plugin")!!) }
    override suspend fun installPlugin(name: String, marketplace: String?) = result {
        rpc("plugin/install", pluginSelector(name, marketplace))
        readPlugin(name, marketplace).getOrThrow().toEntry()
    }
    override suspend fun uninstallPlugin(pluginId: String) = call("plugin/uninstall", obj("pluginId" to pluginId))
    override suspend fun readPluginSkill(marketplace: String, pluginId: String, skillName: String) = result {
        rpc("plugin/skill/read", obj("remoteMarketplaceName" to marketplace, "remotePluginId" to pluginId, "skillName" to skillName)).text("contents")
    }
    override suspend fun reconcilePlugins() = result {
        val o = rpc("plugin/reconcile")
        check(o.strings("failedRemotePluginIds").isEmpty()) { "Plugin reconciliation failed for: ${o.strings("failedRemotePluginIds").joinToString()}" }
        listInstalledPlugins().getOrThrow().marketplaces.flatMap { it.plugins }
    }
    override suspend fun searchPlugins(term: String) = result {
        listPlugins().getOrThrow().marketplaces.flatMap { it.plugins }.filter { it.name.contains(term, true) || it.description.contains(term, true) }
    }
    override suspend fun addMarketplace(source: String, refName: String?) = result {
        val o = rpc("marketplace/add", obj("source" to source, "refName" to refName))
        MarketplaceEntry(o.required("marketplaceName"), o.required("installedRoot"))
    }
    override suspend fun removeMarketplace(name: String) = call("marketplace/remove", obj("marketplaceName" to name))
    override suspend fun upgradeMarketplace(name: String?) = result {
        val o = rpc("marketplace/upgrade", obj("marketplaceName" to name))
        check(o.array("errors").isEmpty()) { "Marketplace update failed: ${o.array("errors").joinToString { Json.write(it) }}" }
        o.strings("upgradedRoots")
    }
    override suspend fun listApps() = result { catalog("app/list").map(WireCatalogCodec::app) }
    override suspend fun listInstalledApps() = result { rpc("app/installed").array("apps").map { value -> value.objectValue().let { o ->
        AppInfo(o.required("id"), o.text("runtimeName") ?: o.required("id"), installed = true)
    } } }
    override suspend fun readApps(ids: List<String>) = result {
        ids.distinct().chunked(100).flatMap { batch -> rpc("app/read", obj("appIds" to batch)).array("apps").map { WireCatalogCodec.app(it.objectValue()) } }
    }

    override suspend fun readFile(path: String) = result { Base64.getDecoder().decode(rpc("fs/readFile", obj("path" to path)).required("dataBase64")) }
    override suspend fun writeFile(path: String, bytes: ByteArray) = call("fs/writeFile", obj("path" to path, "dataBase64" to Base64.getEncoder().encodeToString(bytes)))
    override suspend fun readDirectory(path: String) = result {
        rpc("fs/readDirectory", obj("path" to path)).array("entries").map { value -> value.objectValue().let { o ->
            FileMetadata(path.trimEnd('/') + "/" + o.required("fileName"), o.bool("isDirectory") == true, isFile = o.bool("isFile") == true, isSymlink = o.bool("isSymlink") == true) } }
    }
    override suspend fun getMetadata(path: String) = result { rpc("fs/getMetadata", obj("path" to path)).let { o ->
        FileMetadata(path, o.bool("isDirectory") == true, modifiedAt = o.long("modifiedAtMs") ?: 0, isFile = o.bool("isFile") == true, isSymlink = o.bool("isSymlink") == true, createdAt = o.long("createdAtMs") ?: 0) } }
    override suspend fun createDirectory(path: String, recursive: Boolean) = call("fs/createDirectory", obj("path" to path, "recursive" to recursive))
    override suspend fun removePath(path: String, recursive: Boolean) = call("fs/remove", obj("path" to path, "recursive" to recursive))
    override suspend fun copyPath(source: String, destination: String, recursive: Boolean) = call("fs/copy", obj("sourcePath" to source, "destinationPath" to destination, "recursive" to recursive))
    override suspend fun watchPath(path: String, watchId: String) = call("fs/watch", obj("path" to path, "watchId" to watchId))
    override suspend fun unwatchPath(watchId: String) = call("fs/unwatch", obj("watchId" to watchId))
    override suspend fun execCommand(command: List<String>, cwd: String?, timeoutMs: Long?, tty: Boolean) = result {
        require(!tty) { "Interactive command sessions are not yet supported" }
        val o = rpc("command/exec", obj("command" to command, "cwd" to (cwd ?: defaultWorkspace), "timeoutMs" to timeoutMs))
        CommandExecResponse(exitCode = o.int("exitCode") ?: error("Missing command exit code"), stdout = o.text("stdout").orEmpty(), stderr = o.text("stderr").orEmpty())
    }
    override suspend fun execWrite(processId: String, data: ByteArray?, closeStdin: Boolean) = call("command/exec/write", obj("processId" to processId, "deltaBase64" to data?.let { Base64.getEncoder().encodeToString(it) }, "closeStdin" to closeStdin))
    override suspend fun execResize(processId: String, rows: Int, cols: Int) = call("command/exec/resize", obj("processId" to processId, "size" to obj("rows" to rows, "cols" to cols)))
    override suspend fun execTerminate(processId: String) = call("command/exec/terminate", obj("processId" to processId))

    private suspend fun notification(method: String, p: JsonObject) {
        val threadId = p.text("threadId").orEmpty()
        val turnId = p.text("turnId").orEmpty()
        val itemId = p.text("itemId").orEmpty()
        fun delta() = ItemTextDelta(threadId, turnId, itemId, p.text("delta").orEmpty(), p.int("summaryIndex") ?: 0)
        val event: AppServerEvent? = when (method) {
            "item/started" -> AppServerEvent.ItemStarted(threadId, turnId, WireCodec.item(p["item"]!!))
            "item/completed" -> AppServerEvent.ItemCompleted(threadId, turnId, WireCodec.item(p["item"]!!))
            "item/agentMessage/delta" -> AppServerEvent.AgentMessageDelta(threadId, delta())
            "item/plan/delta" -> AppServerEvent.PlanDelta(threadId, delta())
            "item/reasoning/textDelta" -> AppServerEvent.ReasoningTextDelta(threadId, delta())
            "item/reasoning/summaryTextDelta" -> AppServerEvent.ReasoningSummaryDelta(threadId, delta())
            "item/reasoning/summaryPartAdded" -> AppServerEvent.ReasoningSummaryPartAdded(threadId, delta())
            "item/commandExecution/outputDelta" -> AppServerEvent.CommandOutputDelta(threadId, CommandExecutionOutputDelta(threadId, turnId, itemId, p.required("delta")))
            "item/commandExecution/terminalInteraction" -> AppServerEvent.CommandTerminalInteraction(threadId, TerminalInteraction(threadId, turnId, itemId, p.required("processId"), p.required("stdin")))
            "item/fileChange/outputDelta" -> AppServerEvent.FileChangeOutputDelta(threadId, FileChangeOutputDelta(threadId, turnId, itemId, p.required("delta")))
            "item/fileChange/patchUpdated" -> AppServerEvent.FileChangePatchUpdated(threadId, FileChangePatchUpdatedNotification(threadId, turnId, itemId, WireCodec.changes(p)))
            "item/mcpToolCall/progress" -> AppServerEvent.McpToolProgress(threadId, McpToolCallProgress(threadId, turnId, itemId, p.required("message")))
            "turn/started" -> {
                val id = p.objectOrNull("turn")!!.required("id")
                activeTurns[threadId] = id
                AppServerEvent.TurnStarted(threadId, id)
            }
            "turn/completed" -> {
                val t = p.objectOrNull("turn")!!
                activeTurns.remove(threadId, t.required("id"))
                AppServerEvent.TurnCompleted(threadId, t.required("id"), TurnStatus.fromWire(t.required("status")), t.objectOrNull("error")?.text("message"))
            }
            "turn/diff/updated" -> AppServerEvent.TurnDiffUpdatedEvent(threadId, TurnDiffUpdated(threadId, turnId, p.required("diff")))
            "turn/plan/updated" -> AppServerEvent.TurnPlanUpdatedEvent(threadId, TurnPlanUpdated(threadId, turnId, p.array("plan").map { it.objectValue().let { step -> PlanStep(step.required("step"), PlanStepStatus.fromWire(step.required("status"))) } }))
            "thread/started" -> WireCodec.thread(p["thread"]!!).let { AppServerEvent.ThreadStartedEvent(it.id, it) }
            "thread/closed" -> AppServerEvent.ThreadClosed(threadId)
            "thread/archived" -> AppServerEvent.ThreadArchived(threadId)
            "thread/unarchived" -> AppServerEvent.ThreadUnarchived(threadId)
            "thread/deleted" -> AppServerEvent.ThreadDeleted(threadId)
            "thread/compacted" -> AppServerEvent.ThreadCompacted(threadId, p.text("summary"))
            "thread/reverted" -> AppServerEvent.ThreadRevertedEvent(threadId, ThreadReverted(threadId, null))
            "thread/queue/changed" -> AppServerEvent.ThreadQueueChangedEvent(threadId, ThreadQueueChanged(threadId))
            "thread/goal/updated" -> AppServerEvent.ThreadGoalUpdatedEvent(threadId, WireCatalogCodec.goal(p.objectOrNull("goal")!!))
            "thread/goal/cleared" -> AppServerEvent.ThreadGoalCleared(threadId)
            "thread/project/updated" -> AppServerEvent.ThreadProjectUpdated(threadId, p.text("projectId"))
            "project/changed" -> AppServerEvent.ProjectChanged(ProjectChangedNotification(p.text("projectId")))
            "thread/name/updated" -> AppServerEvent.ThreadNameUpdatedEvent(threadId, ThreadNameUpdated(threadId, p.text("threadName") ?: p.text("name")))
            "thread/status/changed" -> AppServerEvent.ThreadStatusChangedEvent(threadId, ThreadStatusChanged(threadId, WireCodec.status(p["status"])))
            "thread/settings/updated" -> {
                val settings = p.objectOrNull("threadSettings") ?: p
                AppServerEvent.ThreadSettingsUpdatedEvent(threadId, ThreadSettingsUpdated(threadId, settings.text("model"),
                    settings.text("effort")?.let(ReasoningEffort::fromWire), settings.text("approvalPolicy")?.let(AskForApproval::fromWire)))
            }
            "thread/tokenUsage/updated" -> {
                val usage = p.objectOrNull("tokenUsage")!!
                val total = usage.objectOrNull("total") ?: obj()
                AppServerEvent.ThreadTokenUsageEvent(threadId, ThreadTokenUsageUpdated(threadId, p.text("turnId"), ThreadTokenUsage(total.int("totalTokens") ?: 0, total.int("inputTokens") ?: 0,
                    total.int("cachedInputTokens") ?: 0, total.int("outputTokens") ?: 0, total.int("reasoningOutputTokens") ?: 0, usage.int("modelContextWindow"))))
            }
            "serverRequest/resolved" -> {
                val id = p["requestId"]?.wireText().orEmpty()
                approvals.remove(id)
                AppServerEvent.RequestResolved(threadId, ServerRequestResolved(id, threadId))
            }
            "account/updated" -> { scope.launch { readAccount().onSuccess { eventQueue.send(AppServerEvent.AccountUpdated(it)) } }; null }
            "account/login/completed" -> AppServerEvent.AccountLoginCompleted(AccountLoginCompletedNotification(p.bool("success") == true, p.text("loginId"), p.text("error")))
            "account/rateLimits/updated" -> AppServerEvent.RateLimitsUpdatedEvent(WireCodec.rateLimits(p.objectOrNull("rateLimits")!!))
            "skills/changed" -> AppServerEvent.SkillsChanged(CatalogChanged())
            "app/list/updated" -> AppServerEvent.AppListUpdated(CatalogChanged())
            "model/rerouted" -> AppServerEvent.ModelReroutedEvent(threadId, ModelRerouted(threadId, p.required("fromModel"), p.required("toModel"), p.required("reason")))
            "mcpServer/startupStatus/updated" -> AppServerEvent.McpStartupStatusEvent(McpStartupStatusUpdated(p.required("name"),
                McpServerConnectionStatus.entries.find { it.wire == p.text("status") } ?: McpServerConnectionStatus.Starting, p.text("error")))
            "mcpServer/oauthLogin/completed" -> AppServerEvent.McpOauthLoginCompleted(McpServerOauthLoginCompletedNotification(p.required("name"), p.bool("success") == true, p.text("error")))
            "fs/changed" -> AppServerEvent.FsChangedEvent(FsChangedNotification(p.required("watchId"), p.strings("changedPaths")))
            "command/exec/outputDelta" -> AppServerEvent.CommandExecOutput(CommandExecOutputDeltaNotification(p.required("processId"), p.required("deltaBase64"), CommandExecStream.fromWire(p.text("stream")), p.bool("capReached") == true))
            "android/transportError" -> throw IOException(p.required("message"))
            "android/transportLagged" -> throw IOException("App-server event stream lost ${p.long("skipped") ?: 0} messages; reconnect to reload the thread")
            "error" -> {
                val e = p.objectOrNull("error")!!
                AppServerEvent.ErrorEvent(threadId, ErrorNotification(TurnError(e.required("message"), e.text("additionalDetails"), e["codexErrorInfo"]?.wireText()), threadId, turnId, p.bool("willRetry") == true))
            }
            "warning" -> AppServerEvent.WarningEvent(threadId, WarningNotification(threadId, p.required("message")))
            "configWarning" -> AppServerEvent.ConfigWarningEvent(ConfigWarningNotification(p.required("summary"), p.text("details"), p.text("path")))
            "deprecationNotice" -> AppServerEvent.DeprecationNoticeEvent(DeprecationNoticeNotification(p.required("summary"), p.text("details")))
            else -> null
        }
        if (event != null) eventQueue.send(event)
    }

    private suspend fun serverRequest(id: JsonElement, method: String, p: JsonObject) {
        if (method == "currentTime/read") {
            transport.send(JsonRpcMessageKind.Response, Json.write(obj("id" to id, "result" to obj("currentTimeAt" to System.currentTimeMillis() / 1000))))
            return
        }
        val key = id.wireText()
        val requestId = RequestId(key)
        val thread = p.text("threadId").orEmpty()
        val turn = p.text("turnId").orEmpty()
        val item = p.text("itemId").orEmpty()
        val time = p.long("startedAtMs") ?: System.currentTimeMillis()
        val request = when (method) {
            "item/commandExecution/requestApproval" -> ApprovalRequest.Exec(requestId, thread, turn, item, time,
                CommandExecutionApprovalParams(thread, turn, item, time, p.text("approvalId"), p.text("environmentId"), p.text("reason"), p.text("command"), p.text("cwd")))
            "item/fileChange/requestApproval" -> ApprovalRequest.ApplyPatch(requestId, thread, turn, item, time,
                FileChangeApprovalParams(thread, turn, item, time, p.text("reason"), p.text("grantRoot"), WireCodec.changes(p)))
            "item/permissions/requestApproval" -> {
                val permissions = p.objectOrNull("permissions") ?: obj()
                val fs = permissions.objectOrNull("fileSystem")
                val entries = fs?.array("entries").orEmpty().map { it.objectValue() }
                fun paths(access: String) = entries.filter { it.text("access") == access }.map { entry ->
                    val path = entry.objectOrNull("path") ?: obj()
                    path.text("path") ?: path.text("pattern") ?: Json.write(path)
                }
                ApprovalRequest.Permissions(requestId, thread, turn, item, time, PermissionsApprovalParams(thread, turn, item, p.text("environmentId"), time, p.text("cwd").orEmpty(), p.text("reason"),
                    RequestPermissionProfile(permissions.objectOrNull("network")?.bool("enabled") == true,
                        (fs?.strings("read").orEmpty() + paths("read")).distinct(), (fs?.strings("write").orEmpty() + paths("write")).distinct())))
            }
            "item/tool/requestUserInput" -> {
                val questions = p.array("questions").map { value ->
                    val q = value.objectValue()
                    val options = (q["options"] as? JsonArray)?.map { option ->
                        val o = option.objectValue()
                        ToolRequestUserInputOption(o.required("label"), o.text("description").orEmpty())
                    }
                    ToolRequestUserInputQuestion(q.required("id"), q.required("header"), q.required("question"), q.bool("isOther") == true, q.bool("isSecret") == true, options)
                }
                ApprovalRequest.UserInput(requestId, thread, turn, item, time, ToolRequestUserInputParams(thread, turn, item, questions, p.bool("isBlocking") != false))
            }
            "mcpServer/elicitation/request" -> {
                val schema = p.objectOrNull("requestedSchema") ?: obj()
                val fields = schema.objectOrNull("properties").orEmpty().map { (name, value) ->
                    val field = value.objectValue()
                    val options = field.strings("enum")
                    val kind = when {
                        options.isNotEmpty() -> McpElicitationFieldKind.Enum
                        field.text("type") == "boolean" -> McpElicitationFieldKind.Boolean
                        field.text("type") in listOf("number", "integer") -> McpElicitationFieldKind.Number
                        else -> McpElicitationFieldKind.Text
                    }
                    McpElicitationField(name, field.text("title") ?: name, field.text("description").orEmpty(), kind,
                        name in schema.strings("required"), options, field["default"]?.wireText().orEmpty())
                }
                ApprovalRequest.Elicitation(requestId, thread, p.text("turnId"), item, time, McpElicitationParams(thread, p.text("turnId"), p.required("serverName"),
                    p.text("mode") ?: "form", p.text("message").orEmpty(), McpElicitationSchema(schema.text("title").orEmpty(), fields), p.text("url"), p.text("elicitationId")))
            }
            else -> null
        }
        if (request == null) {
            transport.send(JsonRpcMessageKind.Error, Json.write(obj("id" to id, "error" to obj("code" to -32601, "message" to "Unsupported Android client request: $method"))))
        } else {
            approvals[key] = id to p
            requestStream.send(request)
        }
    }

    override suspend fun respond(requestId: RequestId, response: ApprovalResponse) {
        val (id, params) = approvals[requestId.value] ?: error("Approval is no longer pending")
        val body = when (response) {
            is ApprovalResponse.CommandExecution -> obj("decision" to response.decision.wire)
            is ApprovalResponse.FileChange -> obj("decision" to response.decision.wire)
            is ApprovalResponse.Permissions -> obj("permissions" to if (response.decision == PermissionsApprovalDecision.Decline) obj() else (params["permissions"] ?: obj()),
                "scope" to if (response.decision == PermissionsApprovalDecision.AcceptForSession) "session" else "turn")
            is ApprovalResponse.UserInput -> obj("answers" to JsonObject(response.answers.associate { it.questionId to obj("answers" to it.answers) }))
            is ApprovalResponse.Elicitation -> {
                val properties = params.objectOrNull("requestedSchema")?.objectOrNull("properties")
                val content = response.content.mapValues { (key, value) ->
                    when (properties?.objectOrNull(key)?.text("type")) {
                        "boolean" -> json(value.toBooleanStrict())
                        "integer" -> json(value.toLong())
                        "number" -> json(value.toDouble().also { require(it.isFinite()) })
                        else -> json(value)
                    }
                }
                obj("action" to response.action.wire, "content" to if (response.action == ElicitationAction.Accept) JsonObject(content) else JsonNull)
            }
            is ApprovalResponse.DynamicTool -> obj("success" to response.result.success, "contentItems" to response.result.contentItems.map { obj("type" to "inputText", "text" to it) })
            is ApprovalResponse.Tokens -> obj("accessToken" to response.accessToken, "chatgptAccountId" to response.chatgptAccountId, "chatgptPlanType" to response.chatgptPlanType)
            is ApprovalResponse.Attestation -> obj("token" to response.token)
            is ApprovalResponse.CurrentTime -> obj("currentTimeAt" to response.epochMillis / 1000)
        }
        transport.send(JsonRpcMessageKind.Response, Json.write(obj("id" to id, "result" to body)))
        approvals.remove(requestId.value)
    }
}
