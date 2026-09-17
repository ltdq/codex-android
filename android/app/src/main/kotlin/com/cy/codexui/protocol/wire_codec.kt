package com.cy.codexui.protocol

import com.cy.codexui.protocol.protocol.Json
import com.cy.codexui.protocol.protocol.JsonValue
import com.cy.codexui.protocol.protocol.item.*
import com.cy.codexui.protocol.protocol.v2.*

internal fun obj(vararg fields: Pair<String, Any?>): JsonValue.Obj = JsonValue.Obj(
    fields.filter { it.second != null }.associate { it.first to json(it.second) },
)

internal fun json(value: Any?): JsonValue = when (value) {
    null -> JsonValue.Null
    is JsonValue -> value
    is String -> JsonValue.Str(value)
    is Boolean -> JsonValue.Bool(value)
    is Number -> JsonValue.Num(value.toDouble())
    is List<*> -> JsonValue.Arr(value.map(::json))
    else -> error("Unsupported JSON value: ${value.javaClass.name}")
}

internal fun JsonValue.objectValue(): JsonValue.Obj = this as? JsonValue.Obj
    ?: error("Expected JSON object")
internal fun JsonValue.Obj.text(key: String): String? = (this[key] as? JsonValue.Str)?.value
internal fun JsonValue.Obj.required(key: String): String = text(key) ?: error("Missing field: $key")
internal fun JsonValue.Obj.long(key: String): Long? = (this[key] as? JsonValue.Num)?.toLong()
internal fun JsonValue.Obj.int(key: String): Int? = (this[key] as? JsonValue.Num)?.toInt()
internal fun JsonValue.Obj.bool(key: String): Boolean? = (this[key] as? JsonValue.Bool)?.value
internal fun JsonValue.Obj.objectOrNull(key: String): JsonValue.Obj? = this[key] as? JsonValue.Obj
internal fun JsonValue.Obj.array(key: String): List<JsonValue> = (this[key] as? JsonValue.Arr)?.values.orEmpty()
internal fun JsonValue.Obj.strings(key: String): List<String> = array(key).mapNotNull { (it as? JsonValue.Str)?.value }
internal fun JsonValue.wireText(): String = (this as? JsonValue.Str)?.value ?: Json.write(this)

internal object WireCodec {
    fun input(value: UserInput): JsonValue = when (value) {
        is UserInput.Text -> obj("type" to "text", "text" to value.text, "text_elements" to emptyList<JsonValue>())
        is UserInput.Image -> obj("type" to "image", "url" to value.url)
        is UserInput.LocalImage -> obj("type" to "localImage", "path" to value.path)
        is UserInput.Skill -> obj("type" to "skill", "name" to value.name, "path" to value.path)
        is UserInput.Mention -> obj("type" to "mention", "name" to value.name, "path" to value.path)
    }

    fun input(value: JsonValue): UserInput {
        val o = value.objectValue()
        return when (o.required("type")) {
            "text" -> UserInput.Text(o.required("text"))
            "image" -> UserInput.Image(o.required("url"))
            "localImage" -> UserInput.LocalImage(o.required("path"))
            "skill" -> UserInput.Skill(o.required("name"), o.required("path"))
            "mention" -> UserInput.Mention(o.required("name"), o.required("path"))
            else -> error("Unsupported user input type: ${o.text("type")}")
        }
    }

    fun status(value: JsonValue?): ThreadStatus {
        val o = value as? JsonValue.Obj
        return when (o?.text("type") ?: (value as? JsonValue.Str)?.value) {
            "active" -> ThreadStatus.Active(o?.strings("activeFlags").orEmpty().mapNotNull { flag -> ThreadActiveFlag.entries.find { it.wire == flag } })
            "notLoaded" -> ThreadStatus.NotLoaded
            "systemError" -> ThreadStatus.SystemError(o?.text("message").orEmpty())
            else -> ThreadStatus.Idle
        }
    }

    fun thread(value: JsonValue, archived: Boolean = false): Thread {
        val o = value.objectValue()
        return Thread(
            id = o.required("id"), name = o.text("name"), preview = o.text("preview"),
            modelProvider = o.text("modelProvider").orEmpty(), createdAt = (o.long("createdAt") ?: 0) * 1000,
            updatedAt = (o.long("updatedAt") ?: 0) * 1000, cwd = o.text("cwd").orEmpty(), status = status(o["status"]),
            archived = archived, sectionId = o.objectOrNull("section")?.text("id"), forkedFromId = o.text("forkedFromId"),
            gitBranch = o.objectOrNull("gitInfo")?.text("branch"),
        )
    }

    fun turn(value: JsonValue): Turn {
        val o = value.objectValue()
        return Turn(o.required("id"), o.array("items").map(::item), TurnStatus.fromWire(o.required("status")),
            (o.long("startedAt") ?: 0) * 1000, o.long("completedAt")?.times(1000))
    }

    fun session(value: JsonValue.Obj): ThreadSessionState {
        val t = value["thread"]!!.objectValue()
        val sandbox = value.objectOrNull("sandbox")
        val mode = when (sandbox?.text("type")) {
            "dangerFullAccess", "externalSandbox" -> SandboxMode.DangerFullAccess
            "readOnly" -> SandboxMode.ReadOnly
            else -> SandboxMode.WorkspaceWrite
        }
        val model = value.required("model")
        val cwd = value.text("cwd") ?: t.required("cwd")
        return ThreadSessionState(
            threadId = t.required("id"), forkedFromId = t.text("forkedFromId"), threadName = t.text("name"),
            model = model, modelDisplayName = model, modelProviderId = value.required("modelProvider"),
            reasoningEffort = value.text("reasoningEffort")?.let(ReasoningEffort::fromWire) ?: ReasoningEffort.Medium,
            approvalPolicy = AskForApproval.fromWire(value.text("approvalPolicy").orEmpty()),
            sandboxPolicy = SandboxPolicy(mode, sandbox?.strings("writableRoots").orEmpty(), sandbox?.bool("networkAccess") ?: (mode == SandboxMode.DangerFullAccess)),
            cwd = cwd, workspaceRoots = listOf(cwd), instructionSourcePaths = value.strings("instructionSources"),
            gitBranch = t.objectOrNull("gitInfo")?.text("branch"), rolloutPath = t.text("path"),
        )
    }

    fun changes(value: JsonValue.Obj): List<FileUpdateChange> = value.array("changes").map {
        val o = it.objectValue()
        FileUpdateChange(o.required("path"), PatchChangeKind.fromWire(o.objectOrNull("kind")?.text("type") ?: o.text("kind").orEmpty()), o.text("diff").orEmpty())
    }

    fun item(value: JsonValue): ThreadItem {
        val o = value.objectValue()
        val id = o.required("id")
        val status = o.text("status")
        return when (val type = o.required("type")) {
            "userMessage" -> UserMessageItem(id, o.text("clientId"), o.array("content").map(::input))
            "agentMessage" -> AgentMessageItem(id, o.text("text").orEmpty(), MessagePhase.entries.find { it.wire == o.text("phase") || (it == MessagePhase.FinalAnswer && o.text("phase") == "final_answer") })
            "plan" -> PlanItem(id, o.text("text").orEmpty())
            "reasoning" -> ReasoningItem(id, o.strings("summary"), o.strings("content"))
            "commandExecution" -> CommandExecutionItem(id, o.required("command"), o.required("cwd"), o.text("processId"),
                CommandExecutionSource.entries.find { it.wire == o.text("source") } ?: CommandExecutionSource.Agent,
                CommandExecutionStatus.fromWire(status.orEmpty()), aggregatedOutput = o.text("aggregatedOutput"), exitCode = o.int("exitCode"), durationMs = o.long("durationMs"))
            "fileChange" -> FileChangeItem(id, changes(o), PatchApplyStatus.fromWire(status.orEmpty()))
            "mcpToolCall" -> McpToolCallItem(id, o.required("server"), o.required("tool"), McpToolCallStatus.entries.find { it.wire == status } ?: McpToolCallStatus.InProgress,
                o["arguments"]?.let(Json::write) ?: "{}", o["result"]?.takeUnless { it == JsonValue.Null }?.let(Json::write), o.objectOrNull("error")?.text("message"), o.long("durationMs"))
            "dynamicToolCall" -> DynamicToolCallItem(id, o.text("namespace"), o.required("tool"), o["arguments"]?.let(Json::write) ?: "{}",
                DynamicToolCallStatus.entries.find { it.wire == status } ?: DynamicToolCallStatus.InProgress, o.array("contentItems").map(Json::write), o.bool("success"), o.long("durationMs"))
            "collabAgentToolCall" -> CollabAgentToolCallItem(id, CollabAgentTool.entries.find { it.wire == o.text("tool") } ?: CollabAgentTool.SendInput,
                CollabAgentToolCallStatus.entries.find { it.wire == status } ?: CollabAgentToolCallStatus.InProgress,
                o.required("senderThreadId"), o.strings("receiverThreadIds"), o.text("prompt"), o.text("model"), o.text("reasoningEffort")?.let(ReasoningEffort::fromWire),
                o.objectOrNull("agentsStates")?.fields.orEmpty().mapValues { (_, state) -> state.objectValue().let { CollabAgentState(AgentRunStatus.fromWire(it.required("status")), it.text("message")) } })
            "subAgentActivity" -> SubAgentActivityItem(id, SubAgentActivityKind.entries.find { it.wire == o.text("kind") } ?: SubAgentActivityKind.Started, o.required("agentThreadId"), o.required("agentPath"))
            "webSearch" -> WebSearchItem(id, o.text("query") ?: o.objectOrNull("action")?.text("query").orEmpty())
            "imageView" -> ImageViewItem(id, o.required("path"))
            "sleep" -> SleepItem(id, o.long("durationMs") ?: 0)
            "imageGeneration" -> ImageGenerationItem(id, o.text("prompt").orEmpty(), DynamicToolCallStatus.entries.find { it.wire == status } ?: DynamicToolCallStatus.InProgress)
            "enteredReviewMode" -> EnteredReviewModeItem(id, o.required("review"))
            "exitedReviewMode" -> ExitedReviewModeItem(id, o.required("review"))
            "contextCompaction" -> ContextCompactionItem(id)
            "hookPrompt" -> HookPromptItem(id, o.array("fragments").map { it.objectValue().let { f -> HookPromptFragment(f.required("text"), f.text("hookName").orEmpty()) } })
            "functionCallOutput" -> FunctionCallOutputItem(id, o.required("name"), o.text("namespace"), o["output"]?.wireText().orEmpty())
            else -> FunctionCallOutputItem(id, type, output = Json.write(o))
        }
    }

    fun account(o: JsonValue.Obj): AccountInfo {
        val a = o.objectOrNull("account") ?: return AccountInfo()
        return AccountInfo(a.text("email"), a.text("planType"), a.text("organization"), true)
    }

    fun rateLimits(o: JsonValue.Obj): RateLimits {
        fun window(key: String, label: String): RateLimitWindow? = o.objectOrNull(key)?.let {
            RateLimitWindow(label, (it["usedPercent"] as? JsonValue.Num)?.value?.toFloat() ?: 0f, it.long("resetsAt")?.times(1000))
        }
        return RateLimits(window("primary", "Primary"), window("secondary", "Secondary"), o.objectOrNull("credits")?.text("balance")?.toDoubleOrNull()?.toInt())
    }

    fun config(o: JsonValue.Obj): ConfigReadResponse = ConfigReadResponse(
        config = o["config"] ?: error("Missing config"),
        layers = (o["layers"] as? JsonValue.Arr)?.values?.map { value ->
            val layer = value.objectValue()
            val origin = layer.objectOrNull("name")
            ConfigLayer(layer["config"] ?: JsonValue.Null, source(layer["name"]), layer.text("version").orEmpty(), layer.text("disabledReason"),
                origin?.text("file") ?: origin?.text("dotCodexFolder"))
        },
        origins = o.objectOrNull("origins")?.fields.orEmpty().mapValues { (_, value) -> value.objectValue().let { ConfigLayerOrigin(source(it["name"]), it.text("version").orEmpty()) } },
    )

    private fun source(value: JsonValue?): ConfigLayerSource = ConfigLayerSource.fromWire((value as? JsonValue.Str)?.value ?: (value as? JsonValue.Obj)?.text("type"))
}
