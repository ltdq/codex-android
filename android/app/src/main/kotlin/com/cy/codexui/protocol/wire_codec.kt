package com.cy.codexui.protocol

import com.cy.codexui.protocol.protocol.Json
import com.cy.codexui.protocol.protocol.array
import com.cy.codexui.protocol.protocol.bool
import com.cy.codexui.protocol.protocol.int
import com.cy.codexui.protocol.protocol.long
import com.cy.codexui.protocol.protocol.objectOrNull
import com.cy.codexui.protocol.protocol.objectValue
import com.cy.codexui.protocol.protocol.required
import com.cy.codexui.protocol.protocol.stringOrNull
import com.cy.codexui.protocol.protocol.strings
import com.cy.codexui.protocol.protocol.text
import com.cy.codexui.protocol.protocol.wireText
import com.cy.codexui.protocol.protocol.item.*
import com.cy.codexui.protocol.protocol.v2.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

internal fun obj(vararg fields: Pair<String, Any?>): JsonObject =
    JsonObject(fields.filter { it.second != null }.associate { it.first to json(it.second) })

internal fun json(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    // Integral doubles become integer literals so `obj("limit" to 100.0)` does not put `100.0`
    // into an integer field; non-integral values keep their decimal form.
    is Double -> if (value.isFinite() && value == value.toLong().toDouble()) JsonPrimitive(value.toLong()) else JsonPrimitive(value)
    is Float -> json(value.toDouble())
    is Number -> JsonPrimitive(value.toDouble())
    is List<*> -> JsonArray(value.map(::json))
    else -> error("Unsupported JSON value: ${value.javaClass.name}")
}

internal object WireCodec {
    fun input(value: UserInput): JsonElement = when (value) {
        is UserInput.Text -> obj("type" to "text", "text" to value.text, "text_elements" to emptyList<JsonElement>())
        is UserInput.Image -> obj("type" to "image", "url" to value.url)
        is UserInput.LocalImage -> obj("type" to "localImage", "path" to value.path)
        is UserInput.Skill -> obj("type" to "skill", "name" to value.name, "path" to value.path)
        is UserInput.Mention -> obj("type" to "mention", "name" to value.name, "path" to value.path)
    }

    fun input(value: JsonElement): UserInput {
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

    fun status(value: JsonElement?): ThreadStatus {
        val o = value as? JsonObject
        return when (o?.text("type") ?: value?.stringOrNull()) {
            "active" -> ThreadStatus.Active(o?.strings("activeFlags").orEmpty().mapNotNull { flag -> ThreadActiveFlag.entries.find { it.wire == flag } })
            "notLoaded" -> ThreadStatus.NotLoaded
            "systemError" -> ThreadStatus.SystemError(o?.text("message").orEmpty())
            else -> ThreadStatus.Idle
        }
    }

    fun thread(value: JsonElement, archived: Boolean = false): Thread {
        val o = value.objectValue()
        return Thread(
            id = o.required("id"), name = o.text("name"), preview = o.text("preview"),
            modelProvider = o.text("modelProvider").orEmpty(), createdAt = (o.long("createdAt") ?: 0) * 1000,
            updatedAt = (o.long("updatedAt") ?: 0) * 1000, cwd = o.text("cwd").orEmpty(), status = status(o["status"]),
            archived = archived, sectionId = o.objectOrNull("section")?.text("id"), forkedFromId = o.text("forkedFromId"),
            gitBranch = o.objectOrNull("gitInfo")?.text("branch"),
        )
    }

    fun turn(value: JsonElement): Turn {
        val o = value.objectValue()
        return Turn(o.required("id"), o.array("items").map { item(it) }, TurnStatus.fromWire(o.required("status")),
            (o.long("startedAt") ?: 0) * 1000, o.long("completedAt")?.times(1000))
    }

    fun session(value: JsonObject): ThreadSessionState {
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

    fun changes(value: JsonObject): List<FileUpdateChange> = value.array("changes").map {
        val o = it.objectValue()
        FileUpdateChange(o.required("path"), PatchChangeKind.fromWire(o.objectOrNull("kind")?.text("type") ?: o.text("kind").orEmpty()), o.text("diff").orEmpty())
    }

    fun item(value: JsonElement): ThreadItem {
        val o = value.objectValue()
        val id = o.required("id")
        val status = o.text("status")
        return when (val type = o.required("type")) {
            "userMessage" -> UserMessageItem(id, o.text("clientId"), o.array("content").map { input(it) })
            "agentMessage" -> AgentMessageItem(id, o.text("text").orEmpty(), MessagePhase.entries.find { it.wire == o.text("phase") || (it == MessagePhase.FinalAnswer && o.text("phase") == "final_answer") })
            "plan" -> PlanItem(id, o.text("text").orEmpty())
            "reasoning" -> ReasoningItem(id, o.strings("summary"), o.strings("content"))
            "commandExecution" -> CommandExecutionItem(id, o.required("command"), o.required("cwd"), o.text("processId"),
                CommandExecutionSource.entries.find { it.wire == o.text("source") } ?: CommandExecutionSource.Agent,
                CommandExecutionStatus.fromWire(status.orEmpty()), aggregatedOutput = o.text("aggregatedOutput"), exitCode = o.int("exitCode"), durationMs = o.long("durationMs"))
            "fileChange" -> FileChangeItem(id, changes(o), PatchApplyStatus.fromWire(status.orEmpty()))
            "mcpToolCall" -> McpToolCallItem(id, o.required("server"), o.required("tool"), McpToolCallStatus.entries.find { it.wire == status } ?: McpToolCallStatus.InProgress,
                o["arguments"]?.let(Json::write) ?: "{}", o["result"]?.takeUnless { it == JsonNull }?.let(Json::write), o.objectOrNull("error")?.text("message"), o.long("durationMs"))
            "dynamicToolCall" -> DynamicToolCallItem(id, o.text("namespace"), o.required("tool"), o["arguments"]?.let(Json::write) ?: "{}",
                DynamicToolCallStatus.entries.find { it.wire == status } ?: DynamicToolCallStatus.InProgress, o.array("contentItems").map { Json.write(it) }, o.bool("success"), o.long("durationMs"))
            "collabAgentToolCall" -> CollabAgentToolCallItem(id, CollabAgentTool.entries.find { it.wire == o.text("tool") } ?: CollabAgentTool.SendInput,
                CollabAgentToolCallStatus.entries.find { it.wire == status } ?: CollabAgentToolCallStatus.InProgress,
                o.required("senderThreadId"), o.strings("receiverThreadIds"), o.text("prompt"), o.text("model"), o.text("reasoningEffort")?.let(ReasoningEffort::fromWire),
                o.objectOrNull("agentsStates").orEmpty().mapValues { (_, state) -> state.objectValue().let { CollabAgentState(AgentRunStatus.fromWire(it.required("status")), it.text("message")) } })
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

    fun account(o: JsonObject): AccountInfo {
        val a = o.objectOrNull("account") ?: return AccountInfo()
        return AccountInfo(a.text("email"), a.text("planType"), a.text("organization"), true)
    }

    fun rateLimits(o: JsonObject): RateLimits {
        fun window(key: String, label: String): RateLimitWindow? = o.objectOrNull(key)?.let {
            val usedPercent = (it["usedPercent"] as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.doubleOrNull ?: 0.0
            RateLimitWindow(label, usedPercent.toFloat(), it.long("resetsAt")?.times(1000))
        }
        return RateLimits(window("primary", "Primary"), window("secondary", "Secondary"), o.objectOrNull("credits")?.text("balance")?.toDoubleOrNull()?.toInt())
    }

    fun config(o: JsonObject): ConfigReadResponse = ConfigReadResponse(
        config = o["config"] ?: error("Missing config"),
        layers = (o["layers"] as? JsonArray)?.map { value ->
            val layer = value.objectValue()
            val origin = layer.objectOrNull("name")
            ConfigLayer(layer["config"] ?: JsonNull, source(layer["name"]), layer.text("version").orEmpty(), layer.text("disabledReason"),
                origin?.text("file") ?: origin?.text("dotCodexFolder"))
        },
        origins = o.objectOrNull("origins").orEmpty().mapValues { (_, value) -> value.objectValue().let { ConfigLayerOrigin(source(it["name"]), it.text("version").orEmpty()) } },
    )

    private fun source(value: JsonElement?): ConfigLayerSource = ConfigLayerSource.fromWire(value?.stringOrNull() ?: (value as? JsonObject)?.text("type"))
}
