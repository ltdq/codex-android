package com.cy.codex.protocol

import com.cy.codex.protocol.protocol.v2.ClientRequestMethod
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Only this registry connects wire names to client functions; written out because the names differ.
class ClientRequestRegistryTest {

    // Experimental-inclusive like the TUI; `mock/experimentalMethod` is upstream's test scaffold.
    private val wireToClientMethod: Map<String, String> = mapOf(
        "account/bedrock/discover" to "bedrockDiscover",
        "account/bedrock/setup" to "bedrockSetup",
        "account/login/cancel" to "cancelLogin",
        "account/login/start" to "login",
        "account/logout" to "logout",
        "account/rateLimitResetCredit/consume" to "consumeRateLimitResetCredit",
        "account/rateLimits/read" to "readRateLimits",
        "account/read" to "readAccount",
        "account/sendAddCreditsNudgeEmail" to "sendAddCreditsNudgeEmail",
        "account/usage/read" to "readUsage",
        "account/workspaceMessages/read" to "readWorkspaceMessages",
        "app/installed" to "listInstalledApps",
        "app/list" to "listApps",
        "app/read" to "readApps",
        "collaborationMode/list" to "listCollaborationModes",
        "command/exec" to "execCommand",
        "command/exec/resize" to "execResize",
        "command/exec/terminate" to "execTerminate",
        "command/exec/write" to "execWrite",
        "config/batchWrite" to "writeConfigBatch",
        "config/mcpServer/reload" to "reloadMcpServers",
        "config/read" to "readConfig",
        "config/value/write" to "writeConfigValue",
        "configRequirements/read" to "readConfigRequirements",
        "environment/add" to "addEnvironment",
        "environment/info" to "readEnvironmentInfo",
        "environment/status" to "readEnvironmentStatus",
        "experimentalFeature/enablement/set" to "setExperimentalFeature",
        "experimentalFeature/list" to "listExperimentalFeatures",
        "externalAgentConfig/detect" to "detectExternalAgentConfig",
        "externalAgentConfig/import" to "importExternalAgentConfig",
        "externalAgentConfig/import/readHistories" to "readExternalAgentImportHistories",
        "externalAgentConfig/import/recordHistory" to "recordExternalAgentImportHistory",
        "feedback/upload" to "uploadFeedback",
        "fs/copy" to "copyPath",
        "fs/createDirectory" to "createDirectory",
        "fs/getMetadata" to "getMetadata",
        "fs/readDirectory" to "readDirectory",
        "fs/readFile" to "readFile",
        "fs/remove" to "removePath",
        "fs/unwatch" to "unwatchPath",
        "fs/watch" to "watchPath",
        "fs/writeFile" to "writeFile",
        "fuzzyFileSearch" to "fuzzyFileSearch",
        "fuzzyFileSearch/sessionStart" to "startFuzzySearchSession",
        "fuzzyFileSearch/sessionStop" to "stopFuzzySearchSession",
        "fuzzyFileSearch/sessionUpdate" to "updateFuzzySearchSession",
        "hooks/list" to "listHooks",
        "initialize" to "initialize",
        "marketplace/add" to "addMarketplace",
        "marketplace/remove" to "removeMarketplace",
        "marketplace/upgrade" to "upgradeMarketplace",
        "mcpServer/event/stream/start" to "startMcpEventStream",
        "mcpServer/event/stream/stop" to "stopMcpEventStream",
        "mcpServer/oauth/login" to "mcpOauthLogin",
        "mcpServer/resource/read" to "readMcpResource",
        "mcpServer/tool/call" to "callMcpTool",
        "mcpServerStatus/list" to "listMcpServers",
        "memory/reset" to "resetMemory",
        "memory/status" to "readMemoryStatus",
        "model/list" to "listModels",
        "modelProvider/capabilities/read" to "readModelProviderCapabilities",
        "permissionProfile/list" to "listPermissionProfiles",
        "plugin/install" to "installPlugin",
        "plugin/installed" to "listInstalledPlugins",
        "plugin/list" to "listPlugins",
        "plugin/read" to "readPlugin",
        "plugin/reconcile" to "reconcilePlugins",
        "plugin/search" to "searchPlugins",
        "plugin/share/checkout" to "checkoutPluginShare",
        "plugin/share/delete" to "deletePluginShare",
        "plugin/share/list" to "listPluginShares",
        "plugin/share/save" to "savePluginShare",
        "plugin/share/updateTargets" to "updatePluginShareTargets",
        "plugin/skill/read" to "readPluginSkill",
        "plugin/uninstall" to "uninstallPlugin",
        "process/kill" to "killProcess",
        "process/resizePty" to "resizeProcessPty",
        "process/spawn" to "spawnProcess",
        "process/writeStdin" to "writeProcessStdin",
        "project/create" to "createProject",
        "project/delete" to "deleteProject",
        "project/import" to "importProject",
        "project/list" to "listProjects",
        "project/move" to "moveProject",
        "project/read" to "readProject",
        "project/update" to "updateProject",
        "remoteControl/client/list" to "listRemoteControlClients",
        "remoteControl/client/revoke" to "revokeRemoteControlClient",
        "remoteControl/disable" to "disableRemoteControl",
        "remoteControl/enable" to "enableRemoteControl",
        "remoteControl/pairing/start" to "startRemoteControlPairing",
        "remoteControl/pairing/status" to "readRemoteControlPairing",
        "remoteControl/status/read" to "readRemoteControlStatus",
        "review/start" to "startReview",
        "rollout/compress" to "compressRollout",
        "server/diagnostics" to "readServerDiagnostics",
        "skills/config/write" to "writeSkillConfig",
        "skills/extraRoots/set" to "setSkillExtraRoots",
        "skills/list" to "listSkills",
        "thread/approveGuardianDeniedAction" to "approveGuardianDeniedAction",
        "thread/archive" to "archiveThread",
        "thread/attachment/add" to "addAttachment",
        "thread/attachment/list" to "listAttachments",
        "thread/attachment/remove" to "removeAttachment",
        "thread/backgroundTerminals/clean" to "cleanBackgroundTerminals",
        "thread/backgroundTerminals/list" to "listBackgroundTerminals",
        "thread/backgroundTerminals/terminate" to "terminateBackgroundTerminal",
        "thread/compact/start" to "compactThread",
        "thread/decrement_elicitation" to "decrementElicitation",
        "thread/delete" to "deleteThread",
        "thread/fork" to "forkThread",
        "thread/goal/clear" to "clearGoal",
        "thread/goal/get" to "getGoal",
        "thread/goal/set" to "setGoal",
        "thread/increment_elicitation" to "incrementElicitation",
        "thread/inject_items" to "injectThreadItems",
        "thread/items/list" to "listThreadItems",
        "thread/list" to "listThreads",
        "thread/loaded/list" to "listLoadedThreads",
        "thread/memoryMode/set" to "setThreadMemoryMode",
        "thread/metadata/update" to "updateThreadMetadata",
        "thread/name/set" to "setThreadName",
        "thread/queue/add" to "addToQueue",
        "thread/queue/delete" to "deleteQueued",
        "thread/queue/list" to "listQueue",
        "thread/queue/reorder" to "reorderQueue",
        "thread/queue/start" to "startQueued",
        "thread/queue/update" to "updateQueued",
        "thread/read" to "readThread",
        "thread/realtime/appendAudio" to "appendRealtimeAudio",
        "thread/realtime/appendSpeech" to "appendRealtimeSpeech",
        "thread/realtime/appendText" to "appendRealtimeText",
        "thread/realtime/listVoices" to "listRealtimeVoices",
        "thread/realtime/start" to "startRealtime",
        "thread/realtime/stop" to "stopRealtime",
        "thread/resume" to "resumeThread",
        "thread/revert" to "revertThread",
        "thread/search" to "searchThreads",
        "thread/searchOccurrences" to "searchThreadOccurrences",
        "thread/section/move" to "moveThreadToSection",
        "thread/settings/update" to "updateThreadSettings",
        "thread/shellCommand" to "runShellCommand",
        "thread/start" to "startThread",
        "thread/timeline/list" to "listThreadTimeline",
        "thread/turns/list" to "listThreadTurns",
        "thread/unarchive" to "unarchiveThread",
        "thread/unsubscribe" to "unsubscribeThread",
        "threadSection/create" to "createSection",
        "threadSection/delete" to "deleteSection",
        "threadSection/list" to "listSections",
        "threadSection/update" to "updateSection",
        "turn/interrupt" to "interruptTurn",
        "turn/settings/update" to "updateTurnSettings",
        "turn/start" to "startTurn",
        "turn/steer" to "steerTurn",
        "userVerification/cancel" to "cancelUserVerification",
        "userVerification/delete" to "deleteUserVerification",
        "userVerification/enroll" to "enrollUserVerification",
        "userVerification/status" to "readUserVerificationStatus",
        "userVerification/verify" to "verifyUserVerification",
        "windowsSandbox/readiness" to "windowsSandboxReadiness",
        "windowsSandbox/setupStart" to "windowsSandboxSetupStart",
    )

    // Listed deliberately: `respond`/`close` are the transport contract, flows are stream accessors, the rest are projections.
    private val nonRequestMembers = setOf(
        "respond",
        "close",
        "getEvents",
        "getRequests",
        "getConnection",
        "updateThreadSettingsFull",
        "readConfigLayers",
        "readThreadUsage",
    )

    // Demangled: Kotlin appends a signature-hash suffix for value-class signatures (`Result<T>`).
    private fun declaredClientMethods(): Set<String> =
        AppServerClient::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name.substringBefore('-') }
            .toSet()

    @Test
    fun `every registry entry names a client method`() {
        val unmapped = ClientRequestMethod.entries
            .map { it.wire }
            .filterNot { it in wireToClientMethod }

        assertTrue(
            unmapped.isEmpty(),
            "These protocol methods have no AppServerClient function, so nothing can call them: " +
                unmapped.joinToString(", "),
        )
    }

    @Test
    fun `every mapped client method exists on the interface`() {
        val declared = declaredClientMethods()
        val absent = wireToClientMethod
            .filterValues { it !in declared }
            .map { (wire, method) -> "$wire -> $method()" }

        assertTrue(
            absent.isEmpty(),
            "The registry maps these wire methods to functions the interface does not declare: " +
                absent.joinToString(", "),
        )
    }

    @Test
    fun `every interface function is either a request or explicitly local`() {
        val mapped = wireToClientMethod.values.toSet()
        val unregistered = declaredClientMethods()
            .filter { it !in nonRequestMembers }
            .filterNot { it in mapped }
            .distinct()

        assertTrue(
            unregistered.isEmpty(),
            "These AppServerClient functions issue no registered protocol method, so either the " +
                "registry is missing an entry or the function is dead: " + unregistered.joinToString(", "),
        )
    }

    @Test
    fun `the mapping covers the whole registry and nothing else`() {
        val registry = ClientRequestMethod.entries.map { it.wire }.toSet()
        assertEquals(registry, wireToClientMethod.keys)
    }
}
