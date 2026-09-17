package com.cy.codexui

import com.cy.codexui.protocol.ApprovalResponse
import kotlinx.serialization.json.JsonElement
import com.cy.codexui.protocol.protocol.RequestId
import com.cy.codexui.protocol.protocol.v2.AskForApproval
import com.cy.codexui.protocol.protocol.v2.AttachmentType
import com.cy.codexui.protocol.protocol.v2.ConfigBatchWriteParams
import com.cy.codexui.protocol.protocol.v2.LoginAccountParams
import com.cy.codexui.protocol.protocol.v2.MergeStrategy
import com.cy.codexui.protocol.protocol.v2.ReasoningEffort
import com.cy.codexui.protocol.protocol.v2.ReviewTarget
import com.cy.codexui.protocol.protocol.v2.ThreadMemoryMode
import com.cy.codexui.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codexui.protocol.protocol.v2.ThreadSettingsUpdateParams
import com.cy.codexui.protocol.protocol.v2.TurnSettingsUpdateParams
import com.cy.codexui.protocol.protocol.v2.UserInput
import com.cy.codexui.protocol.protocol.v2.WindowsSandboxSetupMode

/**
 * Application-level events used to coordinate UI actions.
 *
 * Mirrors `codex-rs/tui/src/app_event.rs`: widgets emit these instead of reaching into the session,
 * and one reducer owns every transition. Keeping the widget→action direction event-based is what
 * lets the composer, the status card and the approval cards stay independent of each other.
 *
 * Two rules decide whether something belongs here rather than inside a screen:
 *
 *  - **A read a screen owns does not.** A page that lists something reads it through the
 *    [com.cy.codexui.protocol.AppServerClient] it was handed — `WorkspacePickerScreen`,
 *    `SubAgentThreadScreen` and the file browser all work that way. Routing a read through an event
 *    would put the answer in a second place and let two copies of it disagree.
 *  - **A write does, when it invalidates something the app holds.** Archiving a thread changes the
 *    sidebar; installing a plugin changes the plugin catalog. The events below are the ones whose
 *    effect outlives the page that asked for it.
 */
sealed interface AppEvent {

    // ---- threads ---------------------------------------------------------------
    //
    // The whole lifecycle, not just the part the sidebar draws. Every one of these used to fall into
    // the reducer's `else` arm: the session list rendered Fork / Rename / Archive / Delete rows and
    // tapping any of them did nothing at all, silently, because no reducer owned the transition.
    data class NewThread(val cwd: String? = null) : AppEvent
    data class ResumeThread(val threadId: String) : AppEvent
    data class ForkThread(val threadId: String) : AppEvent
    data class ArchiveThread(val threadId: String, val archived: Boolean) : AppEvent
    data class DeleteThread(val threadId: String) : AppEvent
    data class RenameThread(val threadId: String, val name: String) : AppEvent
    data class CompactThread(val threadId: String) : AppEvent
    data class RevertThread(val threadId: String, val itemId: String?) : AppEvent
    /** Re-read the thread list with whatever scope is currently set. */
    data object RefreshThreadList : AppEvent

    /**
     * Choose whether the thread list includes archived sessions.
     *
     * A scope, not a filter applied to a copy: `thread/list` takes it as a parameter, so an
     * archived thread is either in the list the server returned or not in it at all.
     */
    data class SetThreadListScope(val includeArchived: Boolean) : AppEvent
    data class MoveThreadToSection(val threadId: String, val sectionId: String?) : AppEvent

    /** Drop the server-side subscription without closing the thread. */
    data class UnsubscribeThread(val threadId: String) : AppEvent

    /** Patch `thread/metadata/update`; `null` leaves a field alone. */
    data class UpdateThreadMetadata(
        val threadId: String,
        val branch: String? = null,
        val name: String? = null,
    ) : AppEvent

    /** Append raw item JSON this client did not author, for replaying a foreign transcript. */
    data class InjectThreadItems(val threadId: String, val items: List<String>) : AppEvent

    /** Run one shell command in the session's shell, without starting a turn. */
    data class RunShellCommand(val threadId: String, val command: String) : AppEvent

    /** Override a guardian denial for one item. */
    data class ApproveGuardianDeniedAction(val threadId: String, val itemId: String) : AppEvent

    data class SetThreadMemoryMode(val threadId: String, val mode: ThreadMemoryMode) : AppEvent

    // ---- sidebar sections ------------------------------------------------------
    data class CreateSection(val name: String) : AppEvent
    data class RenameSection(val sectionId: String, val name: String) : AppEvent
    data class DeleteSection(val sectionId: String) : AppEvent

    // ---- turns -----------------------------------------------------------------
    data class SubmitUserMessage(val inputs: List<UserInput>, val queued: Boolean = false) : AppEvent
    data class SteerTurn(val inputs: List<UserInput>) : AppEvent
    data object InterruptTurn : AppEvent
    data class SetGoal(val objective: String) : AppEvent
    data object ClearGoal : AppEvent

    /** `turn/settings/update`, the per-turn half of the settings split. */
    data class UpdateTurnSettings(val params: TurnSettingsUpdateParams) : AppEvent

    // ---- server-side queue -----------------------------------------------------
    //
    // The queue lives on the server, so every one of these is a request followed by a
    // `thread/queue/changed` notification that pulls the new order back. Nothing here mutates the
    // local list directly: a locally reordered queue and the server's would disagree on the ids.
    data class StartQueuedMessage(val queuedId: String? = null) : AppEvent
    data class DeleteQueuedMessage(val queuedId: String) : AppEvent

    /**
     * Replace a queued message's body.
     *
     * Whole inputs rather than a string: a queued entry can hold an image or a file reference as
     * well as text, and `thread/queue/update` takes the list, so sending a rewritten string would
     * silently drop everything that was not text.
     */
    data class UpdateQueuedMessage(val queuedId: String, val inputs: List<UserInput>) : AppEvent
    data class MoveQueuedMessage(val queuedId: String, val delta: Int) : AppEvent
    data object ClearQueue : AppEvent

    // ---- approvals -------------------------------------------------------------
    /** Answer the request currently on screen. */
    data class ResolveApproval(val requestId: RequestId, val response: ApprovalResponse) : AppEvent

    /** A request the UI is showing was resolved elsewhere. */
    data class DismissApproval(val requestId: RequestId) : AppEvent

    // ---- settings --------------------------------------------------------------
    data class SetModel(val model: String) : AppEvent
    data class SetReasoningEffort(val effort: ReasoningEffort) : AppEvent
    data class SetApprovalPolicy(val policy: AskForApproval) : AppEvent
    data class SetPermissionProfile(val profileId: String?) : AppEvent
    data class SetExperimentalFeature(val id: String, val enabled: Boolean) : AppEvent

    /** Apply a whole settings patch at once; the per-field events above are the common case. */
    data class UpdateThreadSettings(val params: ThreadSettingsUpdateParams) : AppEvent

    // ---- account ---------------------------------------------------------------
    data object ReloadAccount : AppEvent
    data object ReloadRateLimits : AppEvent
    data object ReloadUsage : AppEvent
    data object ReloadWorkspaceMessages : AppEvent

    /** Start sign-in. The browser/device-code flow completes through `account/login/completed`. */
    data class Login(val params: LoginAccountParams) : AppEvent

    data class CancelLogin(val loginId: String) : AppEvent
    data object Logout : AppEvent

    /** Consume one rate-limit reset credit; `null` means "whichever the server picks". */
    data class ConsumeResetCredit(val creditId: String? = null) : AppEvent

    /** Ask the server to e-mail a top-up link; `null` uses the address on the account. */
    data class SendAddCreditsNudgeEmail(val email: String? = null) : AppEvent

    data class BedrockDiscover(val region: String? = null) : AppEvent
    data class BedrockSetup(val region: String) : AppEvent

    // ---- catalogs: reload requests --------------------------------------------
    //
    // One event per catalog rather than one carrying the new value. The `Refresh*` family these
    // replaced took a list the caller had already read, so "refresh" could only ever echo back the
    // copy on screen — and because nothing handled them it did not even do that.
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

    // ---- plugins, marketplaces and shares --------------------------------------
    data class InstallPlugin(val name: String, val marketplace: String? = null) : AppEvent
    data class UninstallPlugin(val pluginId: String) : AppEvent
    data class AddMarketplace(val source: String, val ref: String? = null) : AppEvent
    data class RemoveMarketplace(val name: String) : AppEvent
    data class UpgradeMarketplace(val name: String? = null) : AppEvent

    /** Rewrite the catalog from what is on disk; answers with the entries it changed. */
    data object ReconcilePlugins : AppEvent

    data class SavePluginShare(val pluginPath: String, val remotePluginId: String? = null) : AppEvent
    data class DeletePluginShare(val remotePluginId: String) : AppEvent
    data class CheckoutPluginShare(val remotePluginId: String) : AppEvent
    data class UpdatePluginShareTargets(
        val remotePluginId: String,
        val targets: List<String>,
    ) : AppEvent

    // ---- skills, apps, hooks ---------------------------------------------------
    data class SetSkillEnabled(val name: String, val enabled: Boolean) : AppEvent

    /** Replace the extra roots skills are discovered under. */
    data class SetSkillExtraRoots(val roots: List<String>) : AppEvent

    data class SetAppInstalled(val appId: String, val installed: Boolean) : AppEvent

    // ---- MCP -------------------------------------------------------------------
    data class McpLogin(val serverName: String) : AppEvent
    data object ReloadMcpConfig : AppEvent

    /** Open or close a server's event stream; its notifications arrive on the event flow. */
    data class SetMcpEventStream(val server: String, val streaming: Boolean) : AppEvent

    // ---- projects and environments ---------------------------------------------
    data class CreateProject(val name: String, val path: String) : AppEvent
    data class UpdateProject(
        val projectId: String,
        val name: String? = null,
        val path: String? = null,
    ) : AppEvent

    data class DeleteProject(val projectId: String) : AppEvent
    data class MoveProject(val projectId: String, val position: Int) : AppEvent
    data class ImportProject(val path: String) : AppEvent
    data class AddEnvironment(val name: String, val cwd: String) : AppEvent

    // ---- remote control --------------------------------------------------------
    data class SetRemoteControlEnabled(val enabled: Boolean) : AppEvent
    data object StartRemoteControlPairing : AppEvent

    /**
     * Ask whether the other device has claimed the code yet.
     *
     * `remoteControl/pairing/status` is a poll, not a subscription: nothing pushes the claim, so
     * without this the page could show a code but never learn that it was taken.
     */
    data object PollRemoteControlPairing : AppEvent
    data class RevokeRemoteControlClient(val clientId: String) : AppEvent

    // ---- user verification -----------------------------------------------------
    data object EnrollUserVerification : AppEvent

    /**
     * Sign a challenge with the enrolled credential.
     *
     * The whole parameter object, not just the challenge: `userVerification/verify` also takes a
     * title and a description, and those are *display context the UI has already approved* — a
     * sheet that collects them and drops them would be asking for something it never uses.
     */
    data class VerifyUserVerification(
        val params: com.cy.codexui.protocol.protocol.v2.UserVerificationVerifyParams,
    ) : AppEvent
    data object CancelUserVerification : AppEvent
    data object DeleteUserVerification : AppEvent

    // ---- sessions: realtime voice ----------------------------------------------
    data class StartRealtime(val threadId: String, val sdpOffer: String? = null) : AppEvent
    data class StopRealtime(val threadId: String) : AppEvent
    data class AppendRealtimeText(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeSpeech(val threadId: String, val text: String) : AppEvent
    data class AppendRealtimeAudio(
        val threadId: String,
        val audio: ThreadRealtimeAudioChunk,
    ) : AppEvent

    /**
     * Move the elicitation counter the session tracks.
     *
     * Not a settings toggle: the server counts the questions a session has open on a thread and
     * pauses the turn while any are outstanding, so this is part of the approval protocol.
     */
    data class IncrementElicitation(val threadId: String) : AppEvent
    data class DecrementElicitation(val threadId: String) : AppEvent

    // ---- review ----------------------------------------------------------------
    data class StartReview(val threadId: String, val target: ReviewTarget) : AppEvent

    // ---- memories --------------------------------------------------------------
    data object ResetMemory : AppEvent

    // ---- external agent migration ----------------------------------------------
    data object DetectExternalAgentConfig : AppEvent
    data class ImportExternalAgentConfig(val itemIds: List<String>) : AppEvent
    data class RecordExternalAgentImportHistory(val id: String, val summary: String) : AppEvent

    // ---- feedback --------------------------------------------------------------
    data class UploadFeedback(
        val classification: String,
        val reason: String? = null,
        val threadId: String? = null,
    ) : AppEvent

    // ---- windows sandbox -------------------------------------------------------
    data class WindowsSandboxSetupStart(
        val mode: WindowsSandboxSetupMode,
        val cwd: String? = null,
    ) : AppEvent

    // ---- attachments and background terminals ---------------------------------
    data class AddAttachment(
        val threadId: String,
        val type: AttachmentType,
        val identityKey: String,
        val payload: String? = null,
    ) : AppEvent

    data class RemoveAttachment(val threadId: String, val attachmentId: String) : AppEvent
    data class TerminateBackgroundTerminal(val threadId: String, val processId: String) : AppEvent
    data class CleanBackgroundTerminals(val threadId: String) : AppEvent

    // ---- config ----------------------------------------------------------------
    /** Persist the settings page's toggle through `config/value/write`. */
    data class WriteConfigValue(
        val keyPath: String,
        val value: JsonElement,
        val merge: MergeStrategy = MergeStrategy.Replace,
    ) : AppEvent

    /** Persist several keys in one round trip, which is what a form's submit does. */
    data class WriteConfigBatch(val params: ConfigBatchWriteParams) : AppEvent

    // ---- composer drafts -------------------------------------------------------
    data class SetComposerDraft(val text: String) : AppEvent
    data class SubmitSlashCommand(val command: String, val args: String) : AppEvent

}
