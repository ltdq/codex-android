package com.cy.codex.protocol.protocol.v2

import kotlinx.serialization.json.JsonElement

/**
 * `ClientRequest` — the 163 methods the client may call, experimental-inclusive (the TUI's
 * [InitializeCapabilities.experimentalApi] defaults to `true`). `UpstreamSchemaTest` pins the
 * registry to the upstream export minus `mock/experimentalMethod`, and the enum doubles as the
 * request registry so a call site cannot invent a method name.
 */
enum class ClientRequestMethod(val wire: String) {
    Initialize("initialize"),

    ThreadStart("thread/start"),
    ThreadResume("thread/resume"),
    ThreadFork("thread/fork"),
    ThreadList("thread/list"),
    ThreadLoadedList("thread/loaded/list"),
    ThreadRead("thread/read"),
    ThreadItemsList("thread/items/list"),
    ThreadTurnsList("thread/turns/list"),
    ThreadTimelineList("thread/timeline/list"),
    ThreadArchive("thread/archive"),
    ThreadUnarchive("thread/unarchive"),
    ThreadDelete("thread/delete"),
    ThreadNameSet("thread/name/set"),
    ThreadMetadataUpdate("thread/metadata/update"),
    ThreadCompactStart("thread/compact/start"),
    ThreadRevert("thread/revert"),
    ThreadUnsubscribe("thread/unsubscribe"),
    ThreadInjectItems("thread/inject_items"),
    ThreadShellCommand("thread/shellCommand"),
    ThreadApproveGuardianDeniedAction("thread/approveGuardianDeniedAction"),
    ThreadSectionMove("thread/section/move"),
    ThreadSearch("thread/search"),
    ThreadSearchOccurrences("thread/searchOccurrences"),

    ThreadSettingsUpdate("thread/settings/update"),
    ThreadMemoryModeSet("thread/memoryMode/set"),
    ThreadIncrementElicitation("thread/increment_elicitation"),
    ThreadDecrementElicitation("thread/decrement_elicitation"),

    ThreadGoalSet("thread/goal/set"),
    ThreadGoalGet("thread/goal/get"),
    ThreadGoalClear("thread/goal/clear"),

    ThreadQueueAdd("thread/queue/add"),
    ThreadQueueList("thread/queue/list"),
    ThreadQueueUpdate("thread/queue/update"),
    ThreadQueueDelete("thread/queue/delete"),
    ThreadQueueReorder("thread/queue/reorder"),
    ThreadQueueStart("thread/queue/start"),

    ThreadAttachmentAdd("thread/attachment/add"),
    ThreadAttachmentList("thread/attachment/list"),
    ThreadAttachmentRemove("thread/attachment/remove"),

    ThreadBackgroundTerminalsList("thread/backgroundTerminals/list"),
    ThreadBackgroundTerminalsTerminate("thread/backgroundTerminals/terminate"),
    ThreadBackgroundTerminalsClean("thread/backgroundTerminals/clean"),

    ThreadRealtimeStart("thread/realtime/start"),
    ThreadRealtimeStop("thread/realtime/stop"),
    ThreadRealtimeListVoices("thread/realtime/listVoices"),
    ThreadRealtimeAppendAudio("thread/realtime/appendAudio"),
    ThreadRealtimeAppendSpeech("thread/realtime/appendSpeech"),
    ThreadRealtimeAppendText("thread/realtime/appendText"),

    TurnStart("turn/start"),
    TurnSteer("turn/steer"),
    TurnInterrupt("turn/interrupt"),
    TurnSettingsUpdate("turn/settings/update"),

    ThreadSectionList("threadSection/list"),
    ThreadSectionCreate("threadSection/create"),
    ThreadSectionUpdate("threadSection/update"),
    ThreadSectionDelete("threadSection/delete"),

    AccountRead("account/read"),
    AccountLoginStart("account/login/start"),
    AccountLoginCancel("account/login/cancel"),
    AccountLogout("account/logout"),
    AccountRateLimitsRead("account/rateLimits/read"),
    AccountUsageRead("account/usage/read"),
    AccountWorkspaceMessagesRead("account/workspaceMessages/read"),
    AccountRateLimitResetCreditConsume("account/rateLimitResetCredit/consume"),
    AccountSendAddCreditsNudgeEmail("account/sendAddCreditsNudgeEmail"),
    AccountBedrockDiscover("account/bedrock/discover"),
    AccountBedrockSetup("account/bedrock/setup"),

    FsReadFile("fs/readFile"),
    FsWriteFile("fs/writeFile"),
    FsReadDirectory("fs/readDirectory"),
    FsCreateDirectory("fs/createDirectory"),
    FsGetMetadata("fs/getMetadata"),
    FsRemove("fs/remove"),
    FsCopy("fs/copy"),
    FsWatch("fs/watch"),
    FsUnwatch("fs/unwatch"),

    CommandExec("command/exec"),
    CommandExecWrite("command/exec/write"),
    CommandExecResize("command/exec/resize"),
    CommandExecTerminate("command/exec/terminate"),

    ProcessSpawn("process/spawn"),
    ProcessWriteStdin("process/writeStdin"),
    ProcessResizePty("process/resizePty"),
    ProcessKill("process/kill"),

    ConfigRead("config/read"),
    ConfigValueWrite("config/value/write"),
    ConfigBatchWrite("config/batchWrite"),
    ConfigMcpServerReload("config/mcpServer/reload"),
    ConfigRequirementsRead("configRequirements/read"),

    ModelList("model/list"),
    ModelProviderCapabilitiesRead("modelProvider/capabilities/read"),
    PermissionProfileList("permissionProfile/list"),

    ExperimentalFeatureList("experimentalFeature/list"),
    ExperimentalFeatureEnablementSet("experimentalFeature/enablement/set"),

    CollaborationModeList("collaborationMode/list"),

    McpServerStatusList("mcpServerStatus/list"),
    McpServerOauthLogin("mcpServer/oauth/login"),
    McpServerResourceRead("mcpServer/resource/read"),
    McpServerToolCall("mcpServer/tool/call"),
    McpServerEventStreamStart("mcpServer/event/stream/start"),
    McpServerEventStreamStop("mcpServer/event/stream/stop"),

    MemoryStatus("memory/status"),
    MemoryReset("memory/reset"),

    SkillsList("skills/list"),
    SkillsConfigWrite("skills/config/write"),
    SkillsExtraRootsSet("skills/extraRoots/set"),
    PluginList("plugin/list"),
    PluginInstalled("plugin/installed"),
    PluginRead("plugin/read"),
    PluginInstall("plugin/install"),
    PluginUninstall("plugin/uninstall"),
    PluginSkillRead("plugin/skill/read"),
    PluginReconcile("plugin/reconcile"),
    PluginSearch("plugin/search"),
    PluginShareList("plugin/share/list"),
    PluginShareSave("plugin/share/save"),
    PluginShareDelete("plugin/share/delete"),
    PluginShareCheckout("plugin/share/checkout"),
    PluginShareUpdateTargets("plugin/share/updateTargets"),
    MarketplaceAdd("marketplace/add"),
    MarketplaceRemove("marketplace/remove"),
    MarketplaceUpgrade("marketplace/upgrade"),

    AppList("app/list"),
    AppInstalled("app/installed"),
    AppRead("app/read"),

    ProjectList("project/list"),
    ProjectRead("project/read"),
    ProjectCreate("project/create"),
    ProjectUpdate("project/update"),
    ProjectDelete("project/delete"),
    ProjectMove("project/move"),
    ProjectImport("project/import"),

    EnvironmentInfo("environment/info"),
    EnvironmentStatus("environment/status"),
    EnvironmentAdd("environment/add"),

    RemoteControlStatusRead("remoteControl/status/read"),
    RemoteControlEnable("remoteControl/enable"),
    RemoteControlDisable("remoteControl/disable"),
    RemoteControlPairingStart("remoteControl/pairing/start"),
    RemoteControlPairingStatus("remoteControl/pairing/status"),
    RemoteControlClientList("remoteControl/client/list"),
    RemoteControlClientRevoke("remoteControl/client/revoke"),

    UserVerificationStatus("userVerification/status"),
    UserVerificationEnroll("userVerification/enroll"),
    UserVerificationVerify("userVerification/verify"),
    UserVerificationCancel("userVerification/cancel"),
    UserVerificationDelete("userVerification/delete"),

    ExternalAgentConfigDetect("externalAgentConfig/detect"),
    ExternalAgentConfigImport("externalAgentConfig/import"),
    ExternalAgentConfigImportReadHistories("externalAgentConfig/import/readHistories"),
    ExternalAgentConfigImportRecordHistory("externalAgentConfig/import/recordHistory"),

    ReviewStart("review/start"),
    RolloutCompress("rollout/compress"),
    FuzzyFileSearch("fuzzyFileSearch"),
    FuzzyFileSearchSessionStart("fuzzyFileSearch/sessionStart"),
    FuzzyFileSearchSessionUpdate("fuzzyFileSearch/sessionUpdate"),
    FuzzyFileSearchSessionStop("fuzzyFileSearch/sessionStop"),
    HooksList("hooks/list"),
    FeedbackUpload("feedback/upload"),
    ServerDiagnostics("server/diagnostics"),

    // windowsSandbox/… (only meaningful on Windows hosts; carried so the registry is complete)
    WindowsSandboxReadiness("windowsSandbox/readiness"),
    WindowsSandboxSetupStart("windowsSandbox/setupStart"),

    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(wire: String): ClientRequestMethod? = byWire[wire]
    }
}

/**
 * `ClientNotification` — the only notification in the protocol, and it must be sent before any
 * other traffic is accepted.
 */
enum class ClientNotificationMethod(val wire: String) {
    Initialized("initialized"),
}

data class InitializeCapabilities(
    /** Receives experimental methods and fields; the TUI itself declares `true`. */
    val experimentalApi: Boolean = true,
    val requestAttestation: Boolean = false,
    val mcpServerOpenaiFormElicitation: Boolean = false,
    val optOutNotificationMethods: List<String>? = null,
    val extensions: Map<String, JsonElement>? = null,
)

data class InitializeParams(
    val clientInfo: ClientInfo,
    val capabilities: InitializeCapabilities? = null,
)

data class ClientInfo(
    val name: String,
    val title: String? = null,
    val version: String,
)

/**
 * `initialize` response. Mirrors v1 `InitializeResponse`: the native transport completes the
 * handshake before returning, so nothing decodes this today; the type covers the wire schema.
 */
data class InitializeResponse(
    val userAgent: String,
    val codexHome: String,
    val platformFamily: String,
    val platformOs: String,
)
