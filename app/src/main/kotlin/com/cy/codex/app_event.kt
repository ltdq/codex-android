package com.cy.codex

import com.cy.codex.protocol.ApprovalResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import com.cy.codex.protocol.protocol.RequestId
import com.cy.codex.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.ConfigBatchWriteParams
import com.cy.codex.protocol.protocol.v2.LoginAccountParams
import com.cy.codex.protocol.protocol.v2.MergeStrategy
import com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability
import com.cy.codex.protocol.protocol.v2.PluginShareTarget
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.ReviewTarget
import com.cy.codex.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupMode

/**
 * Application-level events coordinating UI actions, mirroring `codex-rs/tui/src/app_event.rs`.
 *
 * Two rules decide membership: a read a screen owns stays with that screen's client, and a write
 * belongs here when its effect outlives the page that asked for it.
 */
sealed interface AppEvent {

    data class NewThread(val cwd: String? = null) : AppEvent
    data class ResumeThread(val threadId: String) : AppEvent
    data class ForkThread(val threadId: String) : AppEvent
    data class ArchiveThread(val threadId: String, val archived: Boolean) : AppEvent
    data class DeleteThread(val threadId: String) : AppEvent
    data class RenameThread(val threadId: String, val name: String) : AppEvent
    data class CompactThread(val threadId: String) : AppEvent
    data class RevertThread(val threadId: String, val itemId: String?) : AppEvent
    data object RefreshThreadList : AppEvent

    /** Re-read subagent threads; the dashboard needs metadata the parent transcript does not carry. */
    data class ReloadAgentThreads(val ancestorThreadId: String) : AppEvent

    /** Interrupt the active turn of [threadId], which may not be the open thread. */
    data class StopThreadTurn(val threadId: String) : AppEvent

    /** Archived inclusion is a `thread/list` parameter, so the toggle is a scope, not a client-side filter. */
    data class SetThreadListScope(val includeArchived: Boolean) : AppEvent
    data class MoveThreadToSection(val threadId: String, val sectionId: String?) : AppEvent

    /** Run one shell command in the session's shell, without starting a turn. */
    data class RunShellCommand(val threadId: String, val command: String) : AppEvent

    /** Override a guardian denial for one item. */
    data class ApproveGuardianDeniedAction(val threadId: String, val itemId: String) : AppEvent

    data class CreateSection(val name: String) : AppEvent
    data class RenameSection(val sectionId: String, val name: String) : AppEvent
    data class DeleteSection(val sectionId: String) : AppEvent

    data class SubmitUserMessage(val inputs: List<UserInput>, val queued: Boolean = false) : AppEvent
    data object InterruptTurn : AppEvent

    /** Answer an inline question as an ordinary user message, bypassing the composer so `/` options and the open draft survive (chatwidget/questions.rs). */
    data class AnswerAsyncQuestion(val text: String) : AppEvent
    /**
     * Create or patch the thread goal; null fields are left as they are. `/goal pause|resume`
     * sends only a status, an edit sends both.
     */
    data class SetGoal(
        val objective: String? = null,
        val status: com.cy.codex.protocol.protocol.v2.GoalStatus? = null,
    ) : AppEvent

    data object ClearGoal : AppEvent

    data object GenerateRecap : AppEvent

    data object ContinueMisalignment : AppEvent

    data class ToggleSideConversation(val message: String? = null) : AppEvent

    // Queue events: the queue lives on the server; the `thread/queue/changed` notification pulls the
    // new order back.
    data class StartQueuedMessage(val queuedId: String? = null) : AppEvent
    data class DeleteQueuedMessage(val queuedId: String) : AppEvent

    /** Replace a queued message's body; whole inputs, because a queued entry can hold an image or a file reference. */
    data class UpdateQueuedMessage(val queuedId: String, val inputs: List<UserInput>) : AppEvent
    data class MoveQueuedMessage(val queuedId: String, val delta: Int) : AppEvent
    data object ClearQueue : AppEvent

    data class ResolveApproval(val requestId: RequestId, val response: ApprovalResponse) : AppEvent

    data class DismissApproval(val requestId: RequestId) : AppEvent

    data class DismissAutoReviewDenial(val itemId: String) : AppEvent

    data class SetModel(val model: String) : AppEvent
    data class SetReasoningEffort(val effort: ReasoningEffort) : AppEvent
    data class SetApprovalPolicy(val policy: AskForApproval) : AppEvent
    data class SetApprovalsReviewer(val reviewer: ApprovalsReviewer) : AppEvent

    data class SetCollaborationMode(val mode: CollaborationMode) : AppEvent

    /** Select a model service tier; `null` means the model's default tier. */
    data class SetServiceTier(val tier: String?) : AppEvent
    data class SetExperimentalFeature(val id: String, val enabled: Boolean) : AppEvent

    data class SetMemorySettings(val useMemories: Boolean, val generateMemories: Boolean) : AppEvent

    /** Pin the reviewed hash under `hooks.state.<key>`; `hook_needs_review` compares against it. */
    data class SetHookTrust(val key: String, val currentHash: String) : AppEvent

    data class SetHookEnabled(val key: String, val enabled: Boolean) : AppEvent

    data object ReloadAccount : AppEvent
    data object ReloadRateLimits : AppEvent
    data object ReloadUsage : AppEvent

    /** Start sign-in. The browser/device-code flow completes through `account/login/completed`. */
    data class Login(val params: LoginAccountParams) : AppEvent

    data class CancelLogin(val loginId: String) : AppEvent
    data object Logout : AppEvent

    /** Consume one rate-limit reset credit; `null` means "whichever the server picks". */
    data class ConsumeResetCredit(val creditId: String? = null) : AppEvent

    data class SendAddCreditsNudgeEmail(
        val creditType: com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType,
    ) : AppEvent

    data object BedrockDiscover : AppEvent
    data class BedrockSetup(val params: com.cy.codex.protocol.protocol.v2.BedrockSetupParams) : AppEvent

    data object ReloadSkills : AppEvent
    data object ReloadPlugins : AppEvent
    data object ReloadPluginShares : AppEvent
    data object ReloadApps : AppEvent
    data object ReloadHooks : AppEvent
    data object ReloadMcpServers : AppEvent
    data object ReloadConfig : AppEvent
    data object ReloadProjects : AppEvent
    data object ReloadEnvironments : AppEvent
    data object ReloadMemories : AppEvent
    data object ReloadRealtimeVoices : AppEvent
    data object ReloadUserVerification : AppEvent
    data object ReloadRemoteControl : AppEvent
    data object ReloadDiagnostics : AppEvent
    data object ReloadExternalAgentConfig : AppEvent
    data object ReloadGoal : AppEvent

    data class InstallPlugin(val name: String, val marketplace: String? = null) : AppEvent
    data class UninstallPlugin(val pluginId: String) : AppEvent
    data class SetPluginEnabled(val pluginId: String, val enabled: Boolean) : AppEvent
    data class AddMarketplace(val source: String, val ref: String? = null) : AppEvent
    data class RemoveMarketplace(val name: String) : AppEvent
    data class UpgradeMarketplace(val name: String? = null) : AppEvent

    data object ReconcilePlugins : AppEvent

    data class SavePluginShare(val pluginPath: String, val remotePluginId: String? = null) : AppEvent
    data class DeletePluginShare(val remotePluginId: String) : AppEvent
    data class CheckoutPluginShare(val remotePluginId: String) : AppEvent
    data class UpdatePluginShareTargets(
        val remotePluginId: String,
        val discoverability: PluginShareDiscoverability,
        val targets: List<PluginShareTarget>,
    ) : AppEvent

    data class SetSkillEnabled(val name: String, val enabled: Boolean) : AppEvent

    data class SetSkillExtraRoots(val roots: List<String>) : AppEvent

    data class SetAppInstalled(val appId: String, val installed: Boolean) : AppEvent

    data class McpLogin(val serverName: String) : AppEvent
    data object ReloadMcpConfig : AppEvent

    /** Open or close a server's event stream; [name] and [arguments] identify the MCP tool call the stream belongs to. */
    data class SetMcpEventStream(
        val server: String,
        val threadId: String,
        val subscriptionId: String,
        val name: String,
        val arguments: JsonElement = JsonNull,
        val streaming: Boolean,
    ) : AppEvent

    data class CreateProject(val name: String, val path: String) : AppEvent
    data class UpdateProject(
        val projectId: String,
        val name: String? = null,
        val path: String? = null,
    ) : AppEvent

    data class DeleteProject(val projectId: String) : AppEvent
    data class MoveProject(val projectId: String, val position: Int) : AppEvent
    data class ImportProject(val path: String) : AppEvent
    data class AddEnvironment(val environmentId: String, val execServerUrl: String) : AppEvent

    data class SetRemoteControlEnabled(val enabled: Boolean) : AppEvent
    data object StartRemoteControlPairing : AppEvent

    /** Poll `remoteControl/pairing/status`; nothing pushes the claim, so the page must ask. */
    data object PollRemoteControlPairing : AppEvent
    data class RevokeRemoteControlClient(val clientId: String) : AppEvent

    data object EnrollUserVerification : AppEvent

    /** Sign a challenge; the whole parameter object, since `userVerification/verify` also takes display context. */
    data class VerifyUserVerification(
        val params: com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams,
    ) : AppEvent
    data object CancelUserVerification : AppEvent
    data object DeleteUserVerification : AppEvent

    data class StartRealtime(val threadId: String, val sdpOffer: String? = null) : AppEvent
    data class StopRealtime(val threadId: String) : AppEvent
    data class AppendRealtimeText(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeSpeech(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeAudio(
        val threadId: String,
        val audio: ThreadRealtimeAudioChunk,
    ) : AppEvent

    /** The server pauses the turn while elicitation questions are outstanding; this moves that counter. */
    data class IncrementElicitation(val threadId: String) : AppEvent
    data class DecrementElicitation(val threadId: String) : AppEvent

    data class StartReview(val threadId: String, val target: ReviewTarget) : AppEvent

    data object ResetMemory : AppEvent

    data object DetectExternalAgentConfig : AppEvent

    /** The detected items go back to the server unchanged; there is no id-only import. */
    data class ImportExternalAgentConfig(
        val items: List<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigMigrationItem>,
    ) : AppEvent

    data class UploadFeedback(
        val classification: String,
        val reason: String? = null,
        val threadId: String? = null,
        /** Only ever true from the form's explicit disclosure; the log can carry prompts. */
        val includeLogs: Boolean = false,
    ) : AppEvent

    data class WindowsSandboxSetupStart(
        val mode: WindowsSandboxSetupMode,
        val cwd: String? = null,
    ) : AppEvent

    data class RemoveAttachment(
        val threadId: String,
        val type: AttachmentType,
        val identityKey: String,
    ) : AppEvent
    data class TerminateBackgroundTerminal(val threadId: String, val processId: String) : AppEvent
    data class CleanBackgroundTerminals(val threadId: String) : AppEvent

    data class WriteConfigValue(
        val keyPath: String,
        val value: JsonElement,
        val merge: MergeStrategy = MergeStrategy.Replace,
    ) : AppEvent

    data class WriteConfigBatch(val params: ConfigBatchWriteParams) : AppEvent

    data class SetComposerDraft(val text: String) : AppEvent

    data class RemoveComposerImage(val path: String) : AppEvent

    data class SubmitSlashCommand(val command: String, val args: String) : AppEvent

}
