package com.cy.codexui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.item.PlanItem
import com.cy.codexui.protocol.protocol.item.ThreadItem
import com.cy.codexui.protocol.protocol.v2.AccountInfo
import com.cy.codexui.protocol.protocol.v2.AccountUsage
import com.cy.codexui.protocol.protocol.v2.AppInfo
import com.cy.codexui.protocol.protocol.v2.CollaborationModeEntry
import com.cy.codexui.protocol.protocol.v2.ConfigReadResponse
import com.cy.codexui.protocol.protocol.v2.ConfigWriteResponse
import com.cy.codexui.protocol.protocol.v2.ConfigSnapshot
import com.cy.codexui.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codexui.protocol.protocol.v2.ExperimentalFeatureEntry
import com.cy.codexui.protocol.protocol.v2.ExternalAgentConfigImportHistory
import com.cy.codexui.protocol.protocol.v2.ExternalAgentConfigMigrationItem
import com.cy.codexui.protocol.protocol.v2.HookMetadata
import com.cy.codexui.protocol.protocol.v2.LoginAccountResponse
import com.cy.codexui.protocol.protocol.v2.MarketplaceEntry
import com.cy.codexui.protocol.protocol.v2.McpServerStatusEntry
import com.cy.codexui.protocol.protocol.v2.MemoryStatusResponse
import com.cy.codexui.protocol.protocol.v2.ModelPreset
import com.cy.codexui.protocol.protocol.v2.PermissionProfileEntry
import com.cy.codexui.protocol.protocol.v2.PlanStep
import com.cy.codexui.protocol.protocol.v2.PluginEntry
import com.cy.codexui.protocol.protocol.v2.PluginShareEntry
import com.cy.codexui.protocol.protocol.v2.ProjectEntry
import com.cy.codexui.protocol.protocol.v2.QueuedSubmission
import com.cy.codexui.protocol.protocol.v2.RateLimits
import com.cy.codexui.protocol.protocol.v2.RemoteControlClient
import com.cy.codexui.protocol.protocol.v2.RemoteControlStatus
import com.cy.codexui.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codexui.protocol.protocol.v2.SkillEntry
import com.cy.codexui.protocol.protocol.v2.Thread
import com.cy.codexui.protocol.protocol.v2.ThreadGoalUpdated
import com.cy.codexui.protocol.protocol.v2.ThreadSection
import com.cy.codexui.protocol.protocol.v2.ThreadSessionState
import com.cy.codexui.protocol.protocol.v2.ThreadStatus
import com.cy.codexui.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codexui.protocol.protocol.v2.TurnStatus
import com.cy.codexui.protocol.protocol.v2.UserVerificationEnrollResponse
import com.cy.codexui.protocol.protocol.v2.UserVerificationStatusResponse
import com.cy.codexui.protocol.protocol.v2.WindowsSandboxReadiness
import com.cy.codexui.protocol.protocol.v2.WorkspaceMessage

/**
 * Canonical session state, shared by the transcript, the status card and the sidebar.
 *
 * Mirrors `codex-rs/tui/src/session_state.rs` plus the observable half of `chatwidget.rs`: the
 * TUI keeps `ThreadSessionState` as the shape app orchestration reads and the chat widget as the
 * thing that mutates it. On the phone both live here because Compose reads them directly.
 */
class SessionState {

    /** Thread handshake state: which thread is open and whether its history has arrived. */
    var threadId by mutableStateOf("")
        private set

    var open by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var config by mutableStateOf(ThreadSessionState(threadId = ""))
        private set

    var status by mutableStateOf<ThreadStatus>(ThreadStatus.NotLoaded)
        private set

    var running by mutableStateOf(false)
        private set

    /** Items in arrival order; deltas mutate the item they name in place. */
    val items = mutableStateListOf<ThreadItem>()

    /**
     * Monotonic revision of the [items] list, bumped by every mutation of it.
     *
     * Composables that fold the whole transcript into a derived list (the agent roster) key that
     * fold on this counter instead of reading the list themselves: reading the list in a screen's
     * scope subscribes the whole screen to every streaming delta, while this counter lets the fold
     * live in a derived state that only notifies its readers when the folded value changed.
     */
    var itemsRevision by mutableIntStateOf(0)
        private set

    /** The plan the agent is working through, from `turn/plan/updated`. */
    val plan = mutableStateListOf<PlanStep>()

    /**
     * Whole-turn diff, folded from `turn/diff/updated`.
     *
     * Identity comparison is deliberate: the accumulator returns the very same list while the
     * payload is unchanged and a fresh one as soon as an append changed a file, so an identity
     * check is both cheaper than comparing every line and exactly the invalidation the readers
     * need.
     */
    var turnDiff by mutableStateOf<List<FileDiff>>(emptyList(), referentialEqualityPolicy())
        private set

    var usage by mutableStateOf(ThreadTokenUsage())
        private set

    /** Objective of the running goal, when goal mode is on. */
    var goal by mutableStateOf<ThreadGoalUpdated?>(null)
        private set

    /**
     * Messages the user submitted while a turn was already running.
     *
     * The queue belongs to the server — `thread/queue/changed` is only a poke, so this list is
     * whatever the last `thread/queue/list` returned. Keeping the ids (rather than bare text) is
     * what lets the queue rows be reordered and deleted.
     */
    val queued = mutableStateListOf<QueuedSubmission>()

    /** Diagnostics surfaced as transcript notices. */
    val diagnostics = mutableStateListOf<SessionDiagnostic>()

    /** Model label shown on the status card; reroutes update it. */
    var activeModelLabel by mutableStateOf("")

    /**
     * Files and images attached to this thread but not yet sent.
     *
     * The list belongs to the server — `thread/attachment/add` answers with the stored record — so
     * this is whatever the last read or write returned, never a locally invented entry.
     */
    val attachments = mutableStateListOf<com.cy.codexui.protocol.protocol.v2.ThreadAttachment>()

    /** Long-running terminals the session started; the footer lists them. */
    val backgroundTerminals =
        mutableStateListOf<com.cy.codexui.protocol.protocol.v2.ThreadBackgroundTerminal>()

    /**
     * Text the composer is holding.
     *
     * Held here rather than in the composer's own `remember` because two other things write it: a
     * slash command that prefills an argument, and a transcript row that offers "quote this". Both
     * outlive the composer's composition, so the draft has to.
     */
    var composerDraft by mutableStateOf("")
        private set

    fun applyDraft(text: String) {
        composerDraft = text
    }

    /** Transcript text currently streaming into the last agent message, if any. */
    var streamingItemId by mutableStateOf<String?>(null)
        private set

    /**
     * Incremental markdown buffers, keyed by the item id the deltas name.
     *
     * A delta appends here instead of replacing the item: `item.copy(text = item.text + delta)`
     * copied the whole answer on every token, and the renderer had to re-parse the result. The
     * buffer owns the parsed blocks, so a delta costs the tail block and nothing else. Entries are
     * dropped when the item completes or the thread changes; a snapshot write (history refresh)
     * leaves them alone because the snapshot can be older than the stream.
     */
    private val streamBuffers = mutableStateMapOf<String, MarkdownStream>()

    internal fun stream(id: String): MarkdownStream? = streamBuffers[id]

    /** Whole streamed text, materialized only when an item completes without a text body. */
    internal fun streamText(id: String): String? = streamBuffers[id]?.text

    internal fun endStream(id: String) {
        streamBuffers.remove(id)
    }

    /**
     * Freeze every buffer that is still open into its item and drop it.
     *
     * An interrupted turn can end without `item/completed`, and an item whose text never lands in
     * the list would be invisible to everything that reads `ThreadItem.text` — copy, search, the
     * session preview. This materializes the buffer once, at the only moment the streaming ends
     * without the item saying so itself.
     */
    internal fun settleStreams() {
        if (streamBuffers.isEmpty()) return
        for ((id, stream) in streamBuffers) {
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) continue
            val filled = when (val item = items[index]) {
                is AgentMessageItem -> if (item.text.isEmpty()) item.copy(text = stream.text) else null
                is PlanItem -> if (item.text.isEmpty()) item.copy(text = stream.text) else null
                else -> null
            }
            if (filled != null) {
                items[index] = filled
                itemsRevision++
            }
        }
        streamBuffers.clear()
    }

    internal fun endAllStreams() {
        streamBuffers.clear()
    }

    /** Drop buffers for items a history refresh no longer has, keeping the live one. */
    internal fun retainStreams(ids: Set<String>) {
        streamBuffers.keys.retainAll(ids)
    }

    /** Append one agent-message delta and make its item the streaming one. */
    internal fun appendAgentDelta(itemId: String, delta: String) {
        streamBuffers.getOrPut(itemId) { MarkdownStream() }.append(delta)
        if (items.none { it.id == itemId }) {
            items.add(AgentMessageItem(id = itemId, text = ""))
            itemsRevision++
        }
        applyStreaming(itemId)
    }

    /** Append one plan-text delta; the plan body is markdown just like an agent message. */
    internal fun appendPlanDelta(itemId: String, delta: String) {
        streamBuffers.getOrPut(itemId) { MarkdownStream() }.append(delta)
        if (items.none { it.id == itemId }) {
            items.add(PlanItem(id = itemId, text = ""))
            itemsRevision++
        }
    }

    fun bindThread(id: String, state: ThreadSessionState) {
        threadId = id
        config = state
        open = true
        loading = false
        status = ThreadStatus.Idle
        activeModelLabel = state.modelDisplayName
    }

    fun beginLoad(id: String) {
        threadId = id
        open = false
        loading = true
        running = false
        streamingItemId = null
        streamBuffers.clear()
        attachments.clear()
        backgroundTerminals.clear()
        items.clear()
        itemsRevision++
        plan.clear()
        turnDiff = emptyList()
        usage = ThreadTokenUsage()
        goal = null
        queued.clear()
        diagnostics.clear()
        status = ThreadStatus.NotLoaded
    }

    fun failLoad(message: String?) {
        loading = false
        open = false
        status = ThreadStatus.SystemError(message.orEmpty())
    }

    fun clear() {
        beginLoad("")
        loading = false
        config = config.copy(threadId = "")
    }

    fun applyStatus(value: ThreadStatus) {
        status = value
        running = value is ThreadStatus.Active
    }

    fun applyConfig(value: ThreadSessionState) {
        config = value
        activeModelLabel = value.modelDisplayName
    }

    fun applyUsage(value: ThreadTokenUsage) {
        usage = value
    }

    fun applyTurnDiff(value: List<FileDiff>) {
        turnDiff = value
    }

    fun applyPlan(value: List<PlanStep>) {
        plan.clear()
        plan.addAll(value)
    }

    fun applyGoal(value: ThreadGoalUpdated?) {
        goal = value
    }

    fun applyStreaming(id: String?) {
        streamingItemId = id
    }

    /** Append or replace one item, preserving arrival order. */
    fun upsert(item: ThreadItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) items.add(item) else items[index] = item
        itemsRevision++
    }

    /**
     * Add [item] only when the transcript has never seen its id.
     *
     * Used for a `thread/read` snapshot that lands while the thread is still streaming. Every item
     * already in the list arrived through the live stream, so the local copy is at least as new as
     * the snapshot's; the snapshot is only good for the history this client has not seen yet.
     */
    fun addIfAbsent(item: ThreadItem) {
        if (items.none { it.id == item.id }) {
            items.add(item)
            itemsRevision++
        }
    }

    fun item(id: String): ThreadItem? = items.firstOrNull { it.id == id }

    fun addDiagnostic(diagnostic: SessionDiagnostic) {
        diagnostics.add(diagnostic)
        if (diagnostics.size > MaxDiagnostics) diagnostics.removeAt(0)
    }

    private companion object {
        const val MaxDiagnostics = 20
    }
}

/**
 * Which notice a [SessionDiagnostic] is, when the wording is the client's own.
 *
 * The reducer is not composable, so it cannot read a string resource: it records *which* notice
 * happened, and the transcript resolves the code — plus [SessionDiagnostic.args] — through
 * `R.string.chatwidget_diagnostic_*` while it composes the cell. Text that came off the wire stays
 * in [SessionDiagnostic.message] instead, because no resource can name it.
 */
enum class DiagnosticCode {
    /** Opening a thread failed. */
    ThreadLoadFailed,

    /** Creating a thread failed. */
    NewThreadFailed,

    /** Starting a turn failed. */
    SendFailed,

    /** Interrupting a turn failed. */
    InterruptFailed,

    /** The server rerouted the turn to another model. */
    ModelSwitched,

    /** The turn was interrupted. */
    TurnInterrupted,

    /** The turn failed. */
    TurnFailed,

    /** The turn ended with a status that is neither of the two above. */
    TurnFinished,

    /**
     * A directory below the working root is writable by every user on the machine.
     *
     * The server scans for this on Windows and reports the first offending path plus how many more
     * it found, which is why the notice takes two arguments.
     */
    WorldWritable,

    /** The review policy demanded a stricter review before this turn could proceed. */
    StrictReviewRequired,

    /** A hook failed; the notice names it. */
    HookFailed,

    /** An MCP server's OAuth flow failed; the notice names the server. */
    McpLoginFailed,
}

/** One warning or error the transcript shows as a notice cell. */
data class SessionDiagnostic(
    val severity: DiagnosticSeverity,
    /**
     * Text the transport supplied, e.g. `error.message`, or `null` when the notice is the client's
     * own wording and [code] names it.
     */
    val message: String? = null,
    val detail: String? = null,
    /** The notice the client is reporting, for text that has a string resource. */
    val code: DiagnosticCode? = null,
    /**
     * Positional format arguments for the resource [code] resolves to, in placeholder order.
     *
     * `TurnFinished` carries the `TurnStatus` enum constant name, so the UI can name the status
     * through the label it already has instead of the model reaching for one.
     */
    val args: List<String> = emptyList(),
    /**
     * The server is retrying the turn itself.
     *
     * Kept apart from the message because it changes what the notice may *offer*: a retryable error
     * already has a retry in flight, so the cell must not present one.
     */
    val willRetry: Boolean = false,
)

/** Last status a turn finished with, used to colour the notice cell. */
data class TurnOutcome(val turnId: String, val status: TurnStatus, val error: String? = null)

/**
 * How far an `externalAgentConfig/import` has got.
 *
 * A local shape rather than a protocol one: the two import notifications are the only pair on the
 * wire that carry progress without a payload type of their own, so there is nothing to reuse.
 */
data class ImportProgress(val imported: Int, val total: Int, val label: String)

/**
 * Catalog state behind the settings, MCP, skill and plugin pages.
 *
 * Mirrors the pickers the TUI opens from `bottom_pane/{model_popups,permission_popups,
 * settings_popups,plugin_catalog}.rs`; the phone keeps them in one place because the surfaces are
 * separate screens rather than mutually exclusive overlays.
 */
class CatalogState {
    var models by mutableStateOf<List<ModelPreset>>(emptyList())
    var permissionProfiles by mutableStateOf<List<PermissionProfileEntry>>(emptyList())
    var experimentalFeatures by mutableStateOf<List<ExperimentalFeatureEntry>>(emptyList())
    var mcpServers by mutableStateOf<List<McpServerStatusEntry>>(emptyList())
    var skills by mutableStateOf<List<SkillEntry>>(emptyList())
    var plugins by mutableStateOf<List<PluginEntry>>(emptyList())
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
    var hooks by mutableStateOf<List<HookMetadata>>(emptyList())
    var account by mutableStateOf(AccountInfo())
    var rateLimits by mutableStateOf(RateLimits())
    var usage by mutableStateOf(AccountUsage())
    var usageLoaded by mutableStateOf(false)

    /**
     * The config stack behind the settings page.
     *
     * The whole `config/read` response is kept, not just the merged body, because the page's point
     * is showing *which* layer a value came from — a merged-only copy cannot answer that, and
     * [ConfigReadResponse.origins] is the only thing that can.
     */
    var config by mutableStateOf(ConfigReadResponse())

    /** The readable projection of the merged config; see [ConfigSnapshot.from]. */
    val configSnapshot: ConfigSnapshot get() = config.snapshot

    /** `requirements.toml`, whose keys constrain what the page may offer. */
    var configRequirements by mutableStateOf<JsonElement>(JsonObject(emptyMap()))

    /**
     * The last `config/…/write` result.
     *
     * Kept because `WriteStatus.OkOverridden` is the only signal that a saved value is being
     * shadowed by a managed layer; without it the page would report success for an edit that has
     * no effect.
     */
    var lastWrite by mutableStateOf<ConfigWriteResponse?>(null)

    /** Marketplaces the account can install plugins from. */
    var marketplaces by mutableStateOf<List<MarketplaceEntry>>(emptyList())

    /** Account-level notices shown on the account page. */
    var workspaceMessages by mutableStateOf<List<WorkspaceMessage>>(emptyList())

    /** A sign-in waiting for the browser or a device code. */
    var pendingLogin by mutableStateOf<LoginAccountResponse?>(null)
    var loginLoading by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var openedLoginId: String? = null

    /** Collaboration modes the server offers; `null` until `collaborationMode/list` answers. */
    var collaborationModes by mutableStateOf<List<CollaborationModeEntry>>(emptyList())

    /** `modelProvider/capabilities/read`; empty means "not asked yet". */
    var modelProviderCapabilities by mutableStateOf<Map<String, Boolean>>(emptyMap())

    // ---- projects and environments ---------------------------------------------
    //
    // `project/…` and `environment/…` have no TUI counterpart — the terminal works in directories
    // and has nowhere to show a saved list — so these pages are the protocol's own surface rather
    // than a port of anything. The sidebar's groups are still derived from `cwd`; a project row is
    // the *saved* form of one, and the two coexist.
    var projects by mutableStateOf<List<ProjectEntry>>(emptyList())
    /**
     * Environment ids this client has learned about.
     *
     * Ids, not records: the protocol has no "list every environment" call — `environment/info` and
     * `environment/status` each take one id — so a list can only be assembled from ids the client
     * was told about, through `thread/environment/connected` or an `environment/add` of its own.
     */
    var environments by mutableStateOf<List<String>>(emptyList())

    // ---- plugin shares ---------------------------------------------------------
    /** Plugins this account has published, and the checkouts of them that exist locally. */
    var pluginShares by mutableStateOf<List<PluginShareEntry>>(emptyList())

    /** Entries the last `plugin/reconcile` reported as added, removed or rewritten. */
    var reconciledPlugins by mutableStateOf<List<String>>(emptyList())

    /** Entries the last `marketplace/upgrade` reported as changed. */
    var upgradedMarketplaces by mutableStateOf<List<String>>(emptyList())

    /**
     * Where the last `plugin/share/checkout` landed.
     *
     * The call answers with a path and nothing else reads it, so without this the page could not
     * tell the user where the checkout went — the one fact the call exists to produce.
     */
    var pluginCheckoutPath by mutableStateOf<String?>(null)

    // ---- memory ----------------------------------------------------------------
    /** `memory/status`; `null` until asked, which is what lets the page show a loading state. */
    var memories by mutableStateOf<MemoryStatusResponse?>(null)

    // ---- realtime voice --------------------------------------------------------
    /** Voices `thread/realtime/listVoices` offers; empty means the session is off. */
    var realtimeVoices by mutableStateOf<List<String>>(emptyList())

    // ---- user verification -----------------------------------------------------
    /** Local credential readiness; `null` until asked. */
    var userVerification by mutableStateOf<UserVerificationStatusResponse?>(null)

    /** The public half of the credential the last `userVerification/enroll` created. */
    var userVerificationCredential by mutableStateOf<UserVerificationEnrollResponse?>(null)

    // ---- remote control --------------------------------------------------------
    var remoteControl by mutableStateOf<RemoteControlStatus?>(null)
    var remoteControlClients by mutableStateOf<List<RemoteControlClient>>(emptyList())

    /** The code a pairing attempt is waiting on, or `null` when nothing is pending. */
    var remoteControlPairingCode by mutableStateOf<String?>(null)
    var remoteControlPairingClaimed by mutableStateOf<Boolean?>(null)

    // ---- diagnostics -----------------------------------------------------------
    var diagnostics by mutableStateOf<ServerDiagnosticsResponse?>(null)

    // ---- external agent migration ----------------------------------------------
    var externalAgentConfig by mutableStateOf<List<ExternalAgentConfigMigrationItem>>(emptyList())
    var externalAgentConnectors by mutableStateOf<List<com.cy.codexui.protocol.protocol.v2.ExternalAgentDetectedConnectorCandidate>>(emptyList())
    var externalAgentImportHistories by mutableStateOf<List<ExternalAgentConfigImportHistory>>(
        emptyList(),
    )

    /** Progress of a running `externalAgentConfig/import`, or `null` between runs. */
    var externalAgentImport by mutableStateOf<ImportProgress?>(null)

    // ---- windows sandbox -------------------------------------------------------
    /** `windowsSandbox/readiness`; `null` until asked, and only ever non-null on Windows. */
    var windowsSandboxReadiness by mutableStateOf<WindowsSandboxReadiness?>(null)

    /**
     * `thread/increment_elicitation`: how many open-form questions the session has outstanding.
     *
     * The server pauses a turn while this is above zero, so the number is part of the session's
     * state rather than a counter the elicitation dialog keeps to itself.
     */
    var elicitationCount by mutableStateOf(0)

    fun modelPreset(id: String): ModelPreset? = models.firstOrNull { it.id == id || it.model == id }

    companion object {
        /** Dotted paths the config-sources card lists, in display order. */
        val RenderedConfigKeys = listOf(
            "model",
            "model_provider",
            "model_reasoning_effort",
            "approval_policy",
            "sandbox_mode",
            "sandbox_workspace_write.network_access",
            "history",
            "tui_alternate_screen",
            "notifications",
        )
    }
}

/**
 * The sidebar's thread list.
 *
 * Mirrors `codex-rs/tui/src/app/loaded_threads.rs` and `app/session_picker.rs`: threads are grouped
 * by working directory (the phone's stand-in for a project) and the group a thread belongs to is
 * derived from `cwd`, not stored on the thread.
 */
class ThreadListState {
    var threads by mutableStateOf<List<Thread>>(emptyList())
    var sections by mutableStateOf<List<ThreadSection>>(emptyList())
    var includeArchived by mutableStateOf(false)
    var loading by mutableStateOf(false)

    /**
     * Group threads by working directory, newest first, matching the sidebar's project groups.
     *
     * A thread with no working directory is keyed by `null` rather than by a placeholder group name:
     * the name of that group is user-visible text, and user-visible text only exists as a string
     * resource, which a model outside composition cannot read. [ProjectGroup.path] is `null` for that
     * group and the UI names it.
     */
    fun grouped(): List<ProjectGroup> = threads
        .groupBy { it.cwd.ifEmpty { null } }
        .map { (cwd, threadsInGroup) ->
            ProjectGroup(
                id = cwd.orEmpty(),
                name = cwd?.substringAfterLast('/')?.ifEmpty { cwd }.orEmpty(),
                path = cwd,
                threads = threadsInGroup.sortedByDescending { it.updatedAt },
            )
        }
        .sortedByDescending { group -> group.threads.maxOfOrNull { it.updatedAt } ?: 0L }
}

data class ProjectGroup(
    val id: String,
    val name: String,
    /** Working directory the group was keyed by, or `null` when the threads have none recorded. */
    val path: String?,
    val threads: List<Thread>,
)
