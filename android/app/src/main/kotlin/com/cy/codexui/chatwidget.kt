package com.cy.codexui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cy.codexui.diff.TurnDiffAccumulator
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.AppServerEvent
import com.cy.codexui.protocol.ApprovalRequest
import com.cy.codexui.protocol.ApprovalResponse
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.item.CommandExecutionItem
import com.cy.codexui.protocol.protocol.item.FileChangeItem
import com.cy.codexui.protocol.protocol.item.McpToolCallItem
import com.cy.codexui.protocol.protocol.item.PlanItem
import com.cy.codexui.protocol.protocol.item.ReasoningItem
import com.cy.codexui.protocol.protocol.item.ThreadItem
import com.cy.codexui.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codexui.protocol.protocol.v2.FileUpdateChange
import com.cy.codexui.protocol.protocol.v2.QueuedSubmission
import com.cy.codexui.protocol.protocol.v2.ReviewTarget
import com.cy.codexui.protocol.protocol.v2.ThreadSettingsUpdateParams
import com.cy.codexui.protocol.protocol.v2.ThreadReadResponse
import com.cy.codexui.protocol.protocol.v2.ThreadStatus
import com.cy.codexui.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codexui.protocol.protocol.v2.TurnStatus
import com.cy.codexui.protocol.protocol.v2.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The wire's "no tier preference" value (`SERVICE_TIER_DEFAULT_REQUEST_VALUE` upstream). */
private const val ServiceTierDefault = "default"

/**
 * The reducer that owns one open thread.
 *
 * Mirrors `codex-rs/tui/src/chatwidget.rs`. Everything the user does to a session goes through
 * [action]; everything the server says arrives through [SessionEvent] and lands in [state]. The
 * widget only sends commands through the application server client.
 */
class ChatWidget(
    private val client: AppServerClient,
    private val scope: CoroutineScope,
    val state: SessionState = SessionState(),
) {
    /**
     * Server requests waiting for a decision, oldest first.
     *
     * Snapshot-backed on purpose. The dialog and the transcript are both composed from this queue,
     * and a plain deque gives a composable that reads it nothing to observe: the request would sit
     * here until some *unrelated* state change (opening the status panel, a streaming delta) forced
     * a recomposition. That is exactly how an approval used to disappear into a blocked turn.
     */
    private val pendingApprovals = mutableStateListOf<ApprovalRequest>()

    /** Incremented on every reset so a stale collection job can recognise itself. */
    private var subscription: Job? = null
    private var loadJob: Job? = null
    private var loadVersion = 0
    private var eventRevision = 0L

    /**
     * Turns that have already completed.
     *
     * Used to drop approvals that arrive late: a request can be emitted just as its turn finishes,
     * and a card for a finished turn can never be answered meaningfully. Bounded because a session
     * only ever needs the recent past.
     */
    private val finishedTurns = ArrayDeque<String>()

    /**
     * The accumulating `turn/diff/updated` payload of the current turn.
     *
     * The server re-sends the whole diff on every notification, so the accumulator re-parses only
     * the appended tail and keeps the parsed `FileDiff` of every unchanged file. Reset wherever the
     * turn or the thread changes, so a later turn never extends the previous turn's payload.
     */
    private val turnDiff = TurnDiffAccumulator()

    /**
     * Patch content for a file-change item, as last announced by the stream.
     *
     * `item/fileChange/requestApproval` identifies the item but carries no diff, and
     * `item/fileChange/patchUpdated` can arrive before `item/started` — so this is where the two
     * are matched up. The item wins when it is present because it is the authoritative copy;
     * upstream recovers the same way in `tui/src/app/file_change_approvals.rs`.
     */
    private val patchChanges = mutableStateMapOf<String, List<FileUpdateChange>>()

    /** The patch under review for [itemId], or empty when nothing has arrived yet. */
    fun fileChangeChanges(itemId: String): List<FileUpdateChange> =
        (state.item(itemId) as? FileChangeItem)?.changes?.takeIf { it.isNotEmpty() }
            ?: patchChanges[itemId].orEmpty()

    /**
     * Markdown deltas waiting for the next commit tick.
     *
     * A server can emit a delta per token, and committing each one recomposes the transcript row
     * and the tail block with it. Deltas are therefore accumulated here and applied together at
     * [Motion.StreamCommitIntervalMs], which is the cadence the transcript renders at; an item that
     * completes flushes first, so nothing is lost at the end of a message.
     */
    private class PendingMarkdown(val itemId: String, val plan: Boolean) {
        val text = StringBuilder()
    }

    private val pendingMarkdown = linkedMapOf<String, PendingMarkdown>()
    private var markdownFlushJob: Job? = null

    /** Turns that already produced their one safety-buffering notice, bounded like [finishedTurns]. */
    private val safetyBufferedTurns = LinkedHashSet<String>()

    /**
     * When the composer last changed, on a monotonic clock.
     *
     * An approval that lands mid-sentence must not take the keyboard away, so a request waits for
     * typing to be idle for [ApprovalTypingIdleDelayMs] before the dialog appears — the same
     * one-second gate upstream keeps in `tui/src/bottom_pane/mod.rs`. Monotonic because a wall-clock
     * adjustment must not turn a live keystroke into an old one; zero means "nothing typed yet".
     */
    private var composerActiveAtMs = 0L

    /** Cancels the pending promotion when a new request or a keystroke restarts the idle window. */
    private var promotionJob: Job? = null

    /**
     * Requests from a thread other than the open one.
     *
     * They cannot be answered from this transcript — the dialog belongs to the thread on screen —
     * so they are kept apart and surfaced as a banner that switches threads. Upstream lists them
     * above the composer the same way (`bottom_pane/pending_thread_approvals.rs`).
     */
    private val otherApprovals = mutableStateListOf<ApprovalRequest>()

    /** In-flight auto reviews, arrival order, for the aggregated review footer. */
    private val reviewsInFlight = mutableStateListOf<PendingReview>()

    /** Recent auto-review denials the user may override once; newest first, bounded. */
    private val autoReviewDenials = mutableStateListOf<AutoReviewDenial>()

    /** The request the dialog is showing; only the head of the queue is ever on screen. */
    var currentApproval by mutableStateOf<ApprovalRequest?>(null)
        private set

    var answeringApproval by mutableStateOf(false)
        private set
    var approvalError by mutableStateOf<String?>(null)
        private set

    /**
     * Set once anything arrives from the live stream for the thread being loaded.
     *
     * `thread/read` is issued while opening, so what it returns is a snapshot taken at request
     * time; this flag says whether the stream has since overtaken it. See [open].
     */
    private var streamOvertookLoad = false

    /** How many requests are actually on screen, i.e. eligible for the "in queue" line. */
    val approvalQueueSize: Int get() = if (currentApproval == null) 0 else pendingApprovals.size

    /** Per-thread counts of approvals waiting in threads that are not open. */
    val otherThreadApprovals: List<ForeignApproval>
        get() = otherApprovals.groupBy { it.threadId }.map { (threadId, requests) ->
            ForeignApproval(threadId, requests.size)
        }

    val pendingReviews: List<PendingReview> get() = reviewsInFlight
    val approvalDenials: List<AutoReviewDenial> get() = autoReviewDenials

    /**
     * Record a composer edit.
     *
     * Called on every text change. When the head of the queue is waiting out the idle window, the
     * deadline moves to one second from this keystroke, so a user who keeps typing is never
     * interrupted.
     */
    fun noteComposerActivity() {
        composerActiveAtMs = monotonicMs()
        if (currentApproval == null && pendingApprovals.isNotEmpty() && promotionJob != null) {
            schedulePromotion()
        }
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000

    /** Milliseconds until the composer has been idle long enough to show an approval. */
    private fun typingIdleRemainingMs(): Long {
        if (composerActiveAtMs == 0L) return 0L
        return composerActiveAtMs + ApprovalTypingIdleDelayMs - monotonicMs()
    }

    private fun schedulePromotion() {
        promotionJob?.cancel()
        val remaining = typingIdleRemainingMs()
        promotionJob = scope.launch {
            if (remaining > 0) delay(remaining)
            promotionJob = null
            // Only the head is promoted; the rest is the modal's own queue.
            if (currentApproval == null) currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /**
     * Show the head unless the composer is still being typed in.
     *
     * A request that arrives while nothing has been typed (or after the idle window) appears at
     * once; otherwise the dialog waits and [noteComposerActivity] moves the deadline with each
     * keystroke.
     */
    private fun promoteApprovalIfIdle() {
        if (currentApproval != null) return
        if (typingIdleRemainingMs() > 0) {
            schedulePromotion()
        } else {
            promotionJob?.cancel()
            promotionJob = null
            currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /** Approvals queued for a thread that is not open, adopted when that thread is opened. */
    private fun adoptOtherThreadApprovals() {
        if (state.threadId.isBlank()) return
        val mine = otherApprovals.filter { it.threadId == state.threadId }
        if (mine.isEmpty()) return
        otherApprovals.removeAll(mine)
        mine.forEach { request ->
            if (pendingApprovals.none { it.requestId == request.requestId }) pendingApprovals.add(request)
        }
        promoteApprovalIfIdle()
    }

    fun attach() {
        subscription?.cancel()
        // Subscribing has to be finished by the time this returns. The transport has no replay
        // buffer on purpose — a socket delivers a notification exactly once — so anything emitted
        // between `attach()` and the collectors' first dispatch would be lost. Starting the
        // collectors undispatched runs their `collect` registration on this very stack frame.
        subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.requests.collect { request -> onApprovalRequest(request) }
            }
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.events.collect { event -> apply(event) }
            }
        }
    }

    fun detach() {
        flushMarkdown()
        subscription?.cancel()
        subscription = null
    }

    fun connectionLost() {
        flushMarkdown()
        resetApprovalState()
        patchChanges.clear()
        state.applyStatus(ThreadStatus.NotLoaded)
    }

    /** Drop every approval-scoped queue and notice; the thread they belonged to is gone. */
    private fun resetApprovalState() {
        promotionJob?.cancel()
        promotionJob = null
        pendingApprovals.clear()
        currentApproval = null
        otherApprovals.clear()
        reviewsInFlight.clear()
        autoReviewDenials.clear()
        answeringApproval = false
        approvalError = null
    }

    /** Open a thread and load its history. */
    fun open(threadId: String, onLoaded: (Result<ThreadReadResponse>) -> Unit = {}) {
        loadJob?.cancel()
        val version = ++loadVersion
        dropMarkdown()
        // Everything approval-scoped belongs to the old thread. `otherApprovals` survives the reset
        // by one step: a request for the thread being opened is adopted back into the queue once
        // `beginLoad` has made that thread current.
        promotionJob?.cancel()
        promotionJob = null
        pendingApprovals.clear()
        currentApproval = null
        reviewsInFlight.clear()
        autoReviewDenials.clear()
        answeringApproval = false
        approvalError = null
        state.beginLoad(threadId)
        adoptOtherThreadApprovals()
        turnDiff.reset()
        patchChanges.clear()
        streamOvertookLoad = false
        loadJob = scope.launch {
            val resumed = client.resumeThread(threadId)
            if (version != loadVersion) return@launch
            resumed.onSuccess { session ->
                val streamedStatus = state.status.takeIf { streamOvertookLoad && it != ThreadStatus.NotLoaded }
                state.bindThread(threadId, session)
                if (streamedStatus != null) state.applyStatus(streamedStatus)
            }
                .onFailure { error ->
                    state.failLoad(error.message)
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Error,
                            code = DiagnosticCode.ThreadLoadFailed,
                            detail = error.message,
                        ),
                    )
                }
            resumed.exceptionOrNull()?.let { error ->
                onLoaded(Result.failure(error))
                return@launch
            }
            val history = client.readThread(com.cy.codexui.protocol.protocol.v2.ThreadReadParams(threadId))
            if (version != loadVersion) return@launch
            history.onSuccess { response ->
                if (version != loadVersion) return@onSuccess
                if (streamOvertookLoad) {
                    // The stream got there first, so this snapshot describes a thread that has
                    // already moved on: it may only fill the gaps it left. Applying it wholesale
                    // replaced freshly streamed bodies with the older copies the snapshot was taken
                    // from — a plan whose deltas had arrived collapsed back to an empty card.
                    response.items.forEach(state::addIfAbsent)
                } else {
                    response.items.forEach(state::upsert)
                    response.turns.lastOrNull()?.usage?.let(state::applyUsage)
                    state.applyStatus(response.thread.status)
                }
            }.onFailure { error ->
                if (version != loadVersion) return@onFailure
                state.addDiagnostic(SessionDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    code = DiagnosticCode.ThreadLoadFailed,
                    detail = error.message,
                ))
            }
            onLoaded(history)
            if (version != loadVersion) return@launch
            refreshQueue(threadId)
            client.getGoal(threadId).onSuccess {
                if (version == loadVersion) state.applyGoal(it)
            }
        }
    }

    /** Start a fresh thread in [cwd]. */
    fun newThread(cwd: String) {
        resetApprovalState()
        scope.launch {
            client.startThread(com.cy.codexui.protocol.protocol.v2.ThreadStartParams(cwd = cwd))
                .onSuccess { session ->
                    dropMarkdown()
                    state.beginLoad(session.threadId)
                    turnDiff.reset()
                    patchChanges.clear()
                    state.bindThread(session.threadId, session)
                }
                .onFailure { error ->
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Error,
                            code = DiagnosticCode.NewThreadFailed,
                            detail = error.message,
                        ),
                    )
                }
        }
    }

    // ---------------------------------------------------------------------------------------
    // User actions
    // ---------------------------------------------------------------------------------------

    /**
     * Reduce one user action.
     *
     * Exhaustive on purpose, with no `else`. The events this widget does not own are listed at the
     * bottom rather than caught by a catch-all, so adding a variant to [AppEvent] without deciding
     * which reducer owns it is a compile error here — which is exactly the check that was missing
     * when Fork / Rename / Archive / Delete rendered in the session list and did nothing at all.
     */
    fun action(event: AppEvent) {
        when (event) {
            // ---- thread lifecycle ---------------------------------------------
            is AppEvent.NewThread -> newThread(event.cwd ?: state.config.cwd)
            is AppEvent.ResumeThread -> open(event.threadId)
            is AppEvent.ForkThread -> request({ client.forkThread(event.threadId) }) { bind(it) }

            is AppEvent.ArchiveThread -> request {
                if (event.archived) client.archiveThread(event.threadId) else
                    client.unarchiveThread(event.threadId)
            }

            is AppEvent.DeleteThread -> request { client.deleteThread(event.threadId) }
            is AppEvent.RenameThread -> request { client.setThreadName(event.threadId, event.name) }
            is AppEvent.CompactThread -> request { client.compactThread(event.threadId) }
            is AppEvent.RevertThread -> request { client.revertThread(event.threadId, event.itemId) }

            is AppEvent.MoveThreadToSection -> request {
                client.moveThreadToSection(event.threadId, event.sectionId)
            }

            is AppEvent.RunShellCommand -> request {
                client.runShellCommand(event.threadId, event.command).onSuccess {
                    if (state.threadId != event.threadId) return@onSuccess
                    // Both spellings are cleared: `/shell cmd`, and the `!cmd` escape the composer
                    // now recognizes.
                    if (state.composerDraft.startsWith("/shell ") || state.composerDraft.startsWith("!")) {
                        state.applyDraft("")
                    }
                }
            }

            is AppEvent.ApproveGuardianDeniedAction -> request({
                client.approveGuardianDeniedAction(event.threadId, event.itemId)
            }) {
                autoReviewDenials.removeAll { it.itemId == event.itemId }
            }

            is AppEvent.DismissAutoReviewDenial ->
                autoReviewDenials.removeAll { it.itemId == event.itemId }

            // ---- turns ----------------------------------------------------------
            is AppEvent.SubmitUserMessage -> submitInput(event.inputs)
            is AppEvent.AnswerAsyncQuestion -> submitInput(listOf(UserInput.Text(event.text)), clearDraft = false)
            AppEvent.InterruptTurn -> interrupt()
            is AppEvent.ResolveApproval -> resolve(event.requestId, event.response)
            is AppEvent.DismissApproval -> dismiss(event.requestId)

            is AppEvent.SetGoal -> request({ client.setGoal(state.threadId, event.objective) }) { state.applyGoal(it) }

            AppEvent.ClearGoal -> request({ client.clearGoal(state.threadId) }) { state.applyGoal(null) }

            // ---- server-side queue ----------------------------------------------
            is AppEvent.StartQueuedMessage -> request { client.startQueued(state.threadId, event.queuedId) }
            is AppEvent.DeleteQueuedMessage -> request { client.deleteQueued(state.threadId, event.queuedId) }

            is AppEvent.UpdateQueuedMessage -> request {
                client.updateQueued(state.threadId, event.queuedId, event.inputs)
            }

            is AppEvent.ClearQueue -> request({
                // No bulk endpoint exists, so dropping the queue means dropping every entry. The
                // list is re-read once at the end rather than after each delete.
                state.queued.map { it.id }.forEach { id -> client.deleteQueued(state.threadId, id) }
                client.listQueue(state.threadId)
            }) { replaceQueue(it) }

            is AppEvent.MoveQueuedMessage -> request {
                val ids = state.queued.map { it.id }.toMutableList()
                val from = ids.indexOf(event.queuedId)
                if (from < 0) return@request Result.success(Unit)
                val to = (from + event.delta).coerceIn(0, ids.lastIndex)
                if (to == from) return@request Result.success(Unit)
                ids.add(to, ids.removeAt(from))
                client.reorderQueue(state.threadId, ids)
            }

            // ---- settings --------------------------------------------------------
            is AppEvent.SetModel -> request({
                client.updateThreadSettings(state.threadId, model = event.model)
            }) {
                state.applyConfig(state.config.copy(model = event.model, modelDisplayName = event.model))
            }

            is AppEvent.SetReasoningEffort -> request({
                client.updateThreadSettings(state.threadId, effort = event.effort)
            }) { state.applyConfig(state.config.copy(reasoningEffort = event.effort)) }

            is AppEvent.SetApprovalPolicy -> request({
                client.updateThreadSettings(state.threadId, approvalPolicy = event.policy)
            }) { state.applyConfig(state.config.copy(approvalPolicy = event.policy)) }

            is AppEvent.SetApprovalsReviewer -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        approvalsReviewer = event.reviewer,
                    ),
                )
            }) { state.applyConfig(state.config.copy(approvalsReviewer = event.reviewer)) }

            is AppEvent.SetCollaborationMode -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        collaborationMode = event.mode,
                    ),
                )
            }) { state.applyConfig(state.config.copy(collaborationMode = event.mode)) }

            is AppEvent.SetServiceTier -> request({
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        // The wire spells "no preference" as the literal `default`, not as a missing
                        // field; `SERVICE_TIER_DEFAULT_REQUEST_VALUE` upstream.
                        serviceTier = event.tier ?: ServiceTierDefault,
                    ),
                )
            }) { state.applyConfig(state.config.copy(serviceTier = event.tier)) }

            // ---- attachments and background terminals -----------------------------
            is AppEvent.RemoveAttachment -> request({
                client.removeAttachment(event.threadId, event.type, event.identityKey)
            }) { state.attachments.removeAll { it.identityKey == event.identityKey } }

            is AppEvent.TerminateBackgroundTerminal -> request({
                client.terminateBackgroundTerminal(event.threadId, event.processId)
            }) { state.backgroundTerminals.removeAll { it.processId == event.processId } }

            is AppEvent.CleanBackgroundTerminals -> request({
                client.cleanBackgroundTerminals(event.threadId)
            }) { state.backgroundTerminals.clear() }

            // ---- composer ---------------------------------------------------------
            is AppEvent.SetComposerDraft -> state.applyDraft(event.text)

            is AppEvent.SubmitSlashCommand,

            // ---- owned by `CodexApp` ----------------------------------------------
            //
            // Listed rather than caught by an `else`, so a new event has to be classified. These
            // never reach here: `CodexApp.onAppEvent` handles them before forwarding.
            is AppEvent.ReloadAccount,
            is AppEvent.ReloadRateLimits,
            is AppEvent.ReloadUsage,
            is AppEvent.ReloadConfig,
            is AppEvent.ReloadSkills,
            is AppEvent.ReloadPlugins,
            is AppEvent.ReloadPluginShares,
            is AppEvent.ReloadApps,
            is AppEvent.ReloadHooks,
            is AppEvent.SetHookTrust,
            is AppEvent.SetHookEnabled,
            is AppEvent.SetMemorySettings,
            is AppEvent.ReloadMcpServers,
            is AppEvent.ReloadProjects,
            is AppEvent.ReloadEnvironments,
            is AppEvent.ReloadMemories,
            is AppEvent.ReloadRealtimeVoices,
            is AppEvent.ReloadUserVerification,
            is AppEvent.ReloadRemoteControl,
            is AppEvent.ReloadDiagnostics,
            is AppEvent.ReloadExternalAgentConfig,
            is AppEvent.ReloadGoal,
            is AppEvent.RefreshThreadList,
            is AppEvent.SetThreadListScope,
            is AppEvent.InstallPlugin,
            is AppEvent.UninstallPlugin,
            is AppEvent.AddMarketplace,
            is AppEvent.RemoveMarketplace,
            is AppEvent.UpgradeMarketplace,
            is AppEvent.ReconcilePlugins,
            is AppEvent.SavePluginShare,
            is AppEvent.DeletePluginShare,
            is AppEvent.CheckoutPluginShare,
            is AppEvent.UpdatePluginShareTargets,
            is AppEvent.SetSkillEnabled,
            is AppEvent.SetSkillExtraRoots,
            is AppEvent.SetAppInstalled,
            is AppEvent.McpLogin,
            is AppEvent.ReloadMcpConfig,
            is AppEvent.SetMcpEventStream,
            is AppEvent.Login,
            is AppEvent.CancelLogin,
            is AppEvent.Logout,
            is AppEvent.ConsumeResetCredit,
            is AppEvent.SendAddCreditsNudgeEmail,
            is AppEvent.BedrockDiscover,
            is AppEvent.BedrockSetup,
            is AppEvent.CreateSection,
            is AppEvent.RenameSection,
            is AppEvent.DeleteSection,
            is AppEvent.CreateProject,
            is AppEvent.UpdateProject,
            is AppEvent.DeleteProject,
            is AppEvent.MoveProject,
            is AppEvent.ImportProject,
            is AppEvent.AddEnvironment,
            is AppEvent.SetRemoteControlEnabled,
            is AppEvent.StartRemoteControlPairing,
            is AppEvent.PollRemoteControlPairing,
            is AppEvent.RevokeRemoteControlClient,
            is AppEvent.EnrollUserVerification,
            is AppEvent.VerifyUserVerification,
            is AppEvent.CancelUserVerification,
            is AppEvent.DeleteUserVerification,
            is AppEvent.StartRealtime,
            is AppEvent.StopRealtime,
            is AppEvent.AppendRealtimeText,
            is AppEvent.AppendRealtimeSpeech,
            is AppEvent.AppendRealtimeAudio,
            is AppEvent.IncrementElicitation,
            is AppEvent.DecrementElicitation,
            is AppEvent.StartReview,
            is AppEvent.ResetMemory,
            is AppEvent.DetectExternalAgentConfig,
            is AppEvent.ImportExternalAgentConfig,
            is AppEvent.UploadFeedback,
            is AppEvent.WindowsSandboxSetupStart,
            is AppEvent.WriteConfigValue,
            is AppEvent.WriteConfigBatch,
            is AppEvent.SetExperimentalFeature,
            -> Unit
        }
    }

    /**
     * Run one request against the open thread, and report a failure where the user can see it.
     *
     * These used to be bare `scope.launch { client.… }` calls whose `Result` was discarded, so a
     * rename the server refused looked exactly like one it accepted.
     */
    private fun request(block: suspend () -> Result<*>) {
        request(block, then = {})
    }

    /**
     * Run one request and fold a successful answer.
     *
     * `then` is a suspend lambda rather than the `onSuccess` receiver so that a continuation may
     * itself talk to the server — following a rename with a re-read, for instance — without the
     * call site having to nest another `launch`.
     */
    private fun <T> request(block: suspend () -> Result<T>, then: suspend (T) -> Unit) {
        scope.launch {
            block()
                .onSuccess { then(it) }
                .onFailure { error ->
                    state.addDiagnostic(
                        SessionDiagnostic(severity = DiagnosticSeverity.Warning, message = error.message),
                    )
                }
        }
    }

    /** Bind a thread a lifecycle call just produced — a fork, which is a new thread id. */
    fun bind(session: com.cy.codexui.protocol.protocol.v2.ThreadSessionState) {
        loadJob?.cancel()
        loadVersion++
        dropMarkdown()
        resetApprovalState()
        state.beginLoad(session.threadId)
        turnDiff.reset()
        patchChanges.clear()
        state.bindThread(session.threadId, session)
    }

    fun clear() {
        loadJob?.cancel()
        loadVersion++
        resetApprovalState()
        patchChanges.clear()
        markdownFlushJob?.cancel()
        markdownFlushJob = null
        pendingMarkdown.clear()
        turnDiff.reset()
        state.clear()
    }

    private fun replaceQueue(queued: List<QueuedSubmission>) {
        state.queued.clear()
        state.queued.addAll(queued)
    }

    private fun dropMarkdown() {
        markdownFlushJob?.cancel()
        markdownFlushJob = null
        pendingMarkdown.clear()
    }

    /** Buffer one markdown delta until the next commit tick. */
    private fun appendMarkdownDelta(itemId: String, delta: String, plan: Boolean) {
        pendingMarkdown.getOrPut(itemId) { PendingMarkdown(itemId, plan) }.text.append(delta)
        if (markdownFlushJob == null) {
            markdownFlushJob = scope.launch {
                delay(Motion.StreamCommitIntervalMs)
                flushMarkdown()
            }
        }
    }

    /**
     * Apply every buffered markdown delta now.
     *
     * The tick calls this after [Motion.StreamCommitIntervalMs]; item completion and turn end call
     * it directly so a message never loses its tail to a pending flush.
     */
    internal fun flushMarkdown() {
        markdownFlushJob = null
        if (pendingMarkdown.isEmpty()) return
        val pending = pendingMarkdown.values.toList()
        pendingMarkdown.clear()
        for (entry in pending) {
            if (entry.plan) {
                state.appendPlanDelta(entry.itemId, entry.text.toString())
            } else {
                state.appendAgentDelta(entry.itemId, entry.text.toString())
            }
        }
    }

    private fun submitInput(inputs: List<UserInput>, clearDraft: Boolean = true) {
        val text = inputs.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }.trim()
        if (text.isEmpty() || !state.open || state.loading) return
        // Viewing a parent-owned sub-agent: the transcript is readable, input is not.
        if (state.config.blocksDirectInput) return
        val threadId = state.threadId
        if (state.running) {
            request({ client.addToQueue(threadId, inputs) }) {
                if (clearDraft && state.threadId == threadId) state.applyDraft("")
                refreshQueue(threadId)
            }
            return
        }
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(threadId, inputs).onSuccess {
                if (clearDraft && state.threadId == threadId) state.applyDraft("")
            }.onFailure { error ->
                if (state.threadId != threadId) return@onFailure
                state.applyStatus(ThreadStatus.Idle)
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.SendFailed,
                        detail = error.message,
                    ),
                )
            }
        }
    }

    /** Re-read the server's queue for [threadId] and replace the local copy. */
    private fun refreshQueue(threadId: String) {
        scope.launch {
            client.listQueue(threadId).onSuccess {
                if (state.threadId == threadId) replaceQueue(it)
            }
        }
    }

    private fun interrupt() {
        scope.launch {
            client.interruptTurn(state.threadId).onFailure { error ->
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        code = DiagnosticCode.InterruptFailed,
                        detail = error.message,
                    ),
                )
            }
        }
    }

    private fun resolve(requestId: com.cy.codexui.protocol.protocol.RequestId, response: ApprovalResponse) {
        if (answeringApproval) return
        answeringApproval = true
        approvalError = null
        scope.launch {
            try {
                client.respond(requestId, response)
                pendingApprovals.removeAll { it.requestId == requestId }
                // The modal already has the user's attention, so the next queued request follows
                // immediately instead of waiting out the typing window again.
                if (currentApproval?.requestId == requestId) currentApproval = pendingApprovals.firstOrNull()
                if (pendingApprovals.isEmpty() && state.running) state.applyStatus(ThreadStatus.Active())
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                approvalError = error.message
            } finally {
                answeringApproval = false
            }
        }
    }

    private fun dismiss(requestId: com.cy.codexui.protocol.protocol.RequestId) {
        pendingApprovals.removeAll { it.requestId == requestId }
        otherApprovals.removeAll { it.requestId == requestId }
        if (currentApproval?.requestId == requestId) currentApproval = pendingApprovals.firstOrNull()
    }

    private fun onApprovalRequest(request: ApprovalRequest) {
        // Three of the eleven server requests are not decisions: the server is asking the *host* for
        // something only the host can produce. Showing them as cards would put a "sign in again"
        // dialog in front of the user for something they cannot answer, and would block the turn
        // behind it. They are answered here and never enter the queue.
        when (request) {
            is ApprovalRequest.CurrentTimeRead -> {
                scope.launch {
                    client.respond(request.requestId, ApprovalResponse.CurrentTime(System.currentTimeMillis()))
                }
                return
            }

            is ApprovalRequest.AttestationGenerate -> {
                scope.launch {
                    // A real host signs the nonce with the platform attestation key. The client has
                    // no keystore integration, so the reply is well-formed but unverifiable — which
                    // is the honest answer, and the server rejects it rather than the UI pretending.
                    client.respond(request.requestId, ApprovalResponse.Attestation(token = ""))
                }
                return
            }

            is ApprovalRequest.ChatgptAuthTokensRefresh -> {
                scope.launch {
                    // The tokens live in the account store, not here; re-reading the account is what
                    // produces them, and an empty reply tells the server to fall back to its own
                    // refresh path instead of waiting on a request nobody will answer.
                    client.respond(
                        request.requestId,
                        ApprovalResponse.Tokens(accessToken = "", chatgptAccountId = ""),
                    )
                }
                return
            }

            is ApprovalRequest.Exec,
            is ApprovalRequest.ApplyPatch,
            is ApprovalRequest.Permissions,
            is ApprovalRequest.UserInput,
            is ApprovalRequest.Elicitation,
            is ApprovalRequest.DynamicTool,
            -> Unit
        }

        if (pendingApprovals.any { it.requestId == request.requestId }) return
        if (otherApprovals.any { it.requestId == request.requestId }) return
        // A request whose turn already finished is stale: the server resolved it while this client
        // was not listening. Showing it would offer a decision with no effect.
        if (request.turnId != null && request.turnId in finishedTurns) return
        // A request for another thread must not take over this transcript. It is tracked for the
        // cross-thread banner, and adopted if the user switches to that thread.
        if (request.threadId.isNotBlank() && request.threadId != state.threadId) {
            otherApprovals.add(request)
            return
        }
        pendingApprovals.add(request)
        state.applyStatus(
            ThreadStatus.Active(
                listOf(
                    com.cy.codexui.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval,
                ),
            ),
        )
        // The dialog waits out the composer's idle window; a request that arrives while nothing has
        // been typed (or after the window) is shown at once.
        promoteApprovalIfIdle()
    }

    // ---------------------------------------------------------------------------------------
    // Server events
    // ---------------------------------------------------------------------------------------

    private fun apply(event: AppServerEvent) {
        if (event.threadId != null && event.threadId != state.threadId) return
        eventRevision++
        streamOvertookLoad = true
        when (event) {
            is AppServerEvent.ItemStarted -> state.upsert(event.item)
            is AppServerEvent.ItemCompleted -> onItemCompleted(event.item)
            is AppServerEvent.AgentMessageDelta ->
                appendMarkdownDelta(event.delta.itemId, event.delta.delta, plan = false)

            is AppServerEvent.PlanDelta ->
                appendMarkdownDelta(event.delta.itemId, event.delta.delta, plan = true)

            is AppServerEvent.ReasoningTextDelta -> appendReasoningText(event.delta.itemId, event.delta.delta)
            is AppServerEvent.ReasoningSummaryDelta -> appendReasoningText(event.delta.itemId, event.delta.delta)
            is AppServerEvent.ReasoningSummaryPartAdded -> appendReasoningText(event.delta.itemId, "\n\n")
            is AppServerEvent.CommandOutputDelta -> appendCommandOutput(event.delta.itemId, event.delta.delta)
            is AppServerEvent.CommandTerminalInteraction -> appendCommandOutput(
                event.delta.itemId,
                event.delta.stdin,
            )

            is AppServerEvent.FileChangeOutputDelta -> Unit
            is AppServerEvent.McpToolProgress -> appendMcpProgress(event.delta.itemId, event.delta.message)
            is AppServerEvent.TurnStarted -> {
                state.applyStatus(ThreadStatus.Active())
                state.applyStreaming(null)
            }

            is AppServerEvent.TurnCompleted -> onTurnCompleted(event)
            is AppServerEvent.TurnDiffUpdatedEvent ->
                state.applyTurnDiff(turnDiff.apply(event.delta.diff))

            is AppServerEvent.TurnPlanUpdatedEvent -> state.applyPlan(event.delta.plan)
            is AppServerEvent.ThreadStartedEvent -> state.bindThread(event.thread.threadIdOr(event.threadId), state.config)
            is AppServerEvent.ThreadStatusChangedEvent -> state.applyStatus(event.delta.status)
            is AppServerEvent.ThreadTokenUsageEvent -> state.applyUsage(event.delta.usage)
            is AppServerEvent.ThreadSettingsUpdatedEvent -> {
                val delta = event.delta
                state.applyConfig(
                    state.config.copy(
                        model = delta.model ?: state.config.model,
                        modelDisplayName = delta.model ?: state.config.modelDisplayName,
                        reasoningEffort = delta.reasoningEffort ?: state.config.reasoningEffort,
                        approvalPolicy = delta.approvalPolicy ?: state.config.approvalPolicy,
                        approvalsReviewer = delta.approvalsReviewer ?: state.config.approvalsReviewer,
                        collaborationMode = delta.collaborationMode ?: state.config.collaborationMode,
                        serviceTier = delta.serviceTier ?: state.config.serviceTier,
                    ),
                )
            }

            is AppServerEvent.ThreadGoalUpdatedEvent -> state.applyGoal(event.delta)
            is AppServerEvent.ThreadGoalCleared -> state.applyGoal(null)
            is AppServerEvent.ThreadNameUpdatedEvent ->
                state.applyConfig(state.config.copy(threadName = event.delta.name))

            is AppServerEvent.ThreadQueueChangedEvent -> refreshQueue(event.threadId)

            is AppServerEvent.ThreadCompacted -> refreshHistory(event.threadId)
            is AppServerEvent.ThreadRevertedEvent -> refreshHistory(event.threadId)

            // The five diagnostic notifications are separate methods on the wire with different
            // payloads, so they are folded into the transcript one by one rather than through a
            // shared "diagnostic" shape that would have to drop `path`.
            is AppServerEvent.ErrorEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    message = event.delta.error.message,
                    detail = event.delta.error.additionalDetails,
                ),
            )

            is AppServerEvent.WarningEvent -> state.addDiagnostic(
                SessionDiagnostic(DiagnosticSeverity.Warning, event.delta.message),
            )

            is AppServerEvent.ConfigWarningEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.summary,
                    detail = listOfNotNull(event.delta.path, event.delta.details).joinToString(" · "),
                ),
            )

            is AppServerEvent.GuardianWarningEvent -> state.addDiagnostic(
                SessionDiagnostic(DiagnosticSeverity.Warning, event.delta.message),
            )

            is AppServerEvent.DeprecationNoticeEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.summary,
                    detail = event.delta.details,
                ),
            )

            is AppServerEvent.WorldWritableWarning -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    code = DiagnosticCode.WorldWritable,
                    args = listOf(
                        event.delta.samplePaths.firstOrNull().orEmpty(),
                        event.delta.extraCount.toString(),
                    ),
                ),
            )

            is AppServerEvent.RequestResolved -> dismiss(
                com.cy.codexui.protocol.protocol.RequestId(event.delta.requestId),
            )

            is AppServerEvent.ModelReroutedEvent -> {
                state.activeModelLabel = event.delta.toModel
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        code = DiagnosticCode.ModelSwitched,
                        args = listOf(event.delta.toModel),
                        detail = event.delta.reason,
                    ),
                )
            }

            // `item/fileChange/patchUpdated` carries the patch as it grows. The item may not have
            // been started yet, in which case the later `item/started` brings the same content; the
            // update is kept by id either way so an approval arriving in between still has a diff.
            is AppServerEvent.FileChangePatchUpdated -> {
                patchChanges[event.delta.itemId] = event.delta.changes
                val item = state.item(event.delta.itemId) as? FileChangeItem
                if (item != null) state.upsert(item.copy(changes = event.delta.changes))
            }

            // The review lifecycle does not render per item; in-flight reviews are aggregated into
            // the composer's approval notice, and a denial is remembered there so it can be
            // overridden once.
            is AppServerEvent.AutoApprovalReviewStarted -> onReviewStarted(event.delta)
            is AppServerEvent.AutoApprovalReviewCompleted -> onReviewCompleted(event.delta)

            is AppServerEvent.StrictReviewRequired -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.reason,
                    code = DiagnosticCode.StrictReviewRequired,
                ),
            )

            // A hook that failed is worth a notice; one that succeeded is not, or a session with
            // hooks on would fill the transcript with a line per tool call.
            is AppServerEvent.HookCompleted -> if (event.delta.run.failed) {
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        message = event.delta.run.statusMessage,
                        code = DiagnosticCode.HookFailed,
                        args = listOf(event.delta.run.eventName.ifEmpty { event.delta.run.id }),
                    ),
                )
            }

            is AppServerEvent.HookStarted -> Unit

            is AppServerEvent.McpOauthLoginCompleted -> if (!event.delta.success) {
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        message = event.delta.error,
                        code = DiagnosticCode.McpLoginFailed,
                        args = listOf(event.delta.name),
                    ),
                )
            }

            // ---------------------------------------------------------------------------------
            // Everything below belongs to a surface this reducer does not own. The transcript is
            // the only thing `ChatWidget` drives; the catalogs, the terminal drawer and the
            // realtime session are `CodexApp`'s. They are listed explicitly rather than caught by
            // an `else` so that adding a notification to the protocol is a compile error here
            // until someone decides which of the two owns it.
            // ---------------------------------------------------------------------------------
            is AppServerEvent.McpStartupStatusEvent,
            is AppServerEvent.SkillsChanged,
            is AppServerEvent.AppListUpdated,
            is AppServerEvent.AccountUpdated,
            is AppServerEvent.AccountLoginCompleted,
            is AppServerEvent.RateLimitsUpdatedEvent,
            is AppServerEvent.ThreadAttachmentUpdated,
            is AppServerEvent.TurnModerationMetadata,
            is AppServerEvent.ThreadProjectUpdated,
            is AppServerEvent.EnvironmentConnected,
            is AppServerEvent.EnvironmentDisconnected,
            is AppServerEvent.ModelVerification,
            is AppServerEvent.ModelProviderAuthRecovery,
            is AppServerEvent.McpServerEvent,
            is AppServerEvent.ProjectChanged,
            is AppServerEvent.RemoteControlStatusChanged,
            is AppServerEvent.FsChangedEvent,
            is AppServerEvent.CommandExecOutput,
            is AppServerEvent.ProcessOutputDelta,
            is AppServerEvent.ProcessExited,
            is AppServerEvent.FuzzySearchUpdated,
            is AppServerEvent.FuzzySearchCompleted,
            is AppServerEvent.ExternalAgentImportProgress,
            is AppServerEvent.ExternalAgentImportCompleted,
            is AppServerEvent.WindowsSandboxSetupCompleted,
            is AppServerEvent.RealtimeStarted,
            is AppServerEvent.RealtimeClosed,
            is AppServerEvent.RealtimeError,
            is AppServerEvent.RealtimeSdp,
            is AppServerEvent.RealtimeItemAdded,
            is AppServerEvent.RealtimeItemStarted,
            is AppServerEvent.RealtimeItemCompleted,
            is AppServerEvent.RealtimeItemTranscriptDelta,
            is AppServerEvent.RealtimeTranscriptDelta,
            is AppServerEvent.RealtimeTranscriptDone,
            is AppServerEvent.RealtimeOutputAudioDelta,
            -> Unit

            is AppServerEvent.ModelSafetyBufferingUpdated -> onSafetyBuffering(event.delta)

            is AppServerEvent.ThreadClosed -> {
                state.endAllStreams()
                state.applyStatus(ThreadStatus.NotLoaded)
            }
            is AppServerEvent.ThreadArchived, is AppServerEvent.ThreadUnarchived,
            is AppServerEvent.ThreadDeleted,
            -> Unit
        }
    }

    /**
     * Fold an in-flight auto review into the aggregated review notice.
     *
     * Parallel reviews are aggregated the way upstream's `PendingGuardianReviewStatus` does it: one
     * entry per review, keyed by the review id so an update replaces rather than duplicates.
     */
    private fun onReviewStarted(delta: com.cy.codexui.protocol.protocol.v2.GuardianApprovalReviewNotification) {
        if (delta.status != "inProgress") return
        val detail = reviewActionSummary(delta.action) ?: return
        val index = reviewsInFlight.indexOfFirst { it.id == delta.reviewId }
        val entry = PendingReview(delta.reviewId, detail)
        if (index >= 0) reviewsInFlight[index] = entry else reviewsInFlight.add(entry)
    }

    /**
     * Drop a finished review and remember a denial so it can be overridden once.
     *
     * A denial is the one review outcome the user can act on: `thread/approveGuardianDeniedAction`
     * needs the serialized assessment, which the client cached under the target item id.
     */
    private fun onReviewCompleted(delta: com.cy.codexui.protocol.protocol.v2.GuardianApprovalReviewNotification) {
        reviewsInFlight.removeAll { it.id == delta.reviewId }
        if (delta.status != "denied" || delta.itemId.isBlank()) return
        autoReviewDenials.removeAll { it.itemId == delta.itemId }
        autoReviewDenials.add(
            0,
            AutoReviewDenial(
                threadId = delta.threadId,
                id = delta.reviewId,
                itemId = delta.itemId,
                summary = reviewActionSummary(delta.action).orEmpty(),
                rationale = delta.rationale,
            ),
        )
        while (autoReviewDenials.size > MaxRememberedDenials) {
            autoReviewDenials.removeAt(autoReviewDenials.lastIndex)
        }
    }

    /**
     * Note the safety buffer once per turn.
     *
     * The notification can repeat while the turn waits, and the TUI only re-shows its transient
     * menu when the retry offer changes. The phone has no retry affordance, so one informational
     * notice per turn is the whole behaviour: `showBufferingUi == false` only dismisses the menu.
     */
    private fun onSafetyBuffering(delta: com.cy.codexui.protocol.protocol.v2.ModelSafetyBufferingUpdatedNotification) {
        if (!delta.showBufferingUi) return
        if (delta.turnId.isEmpty() || delta.turnId in finishedTurns) return
        if (!safetyBufferedTurns.add(delta.turnId)) return
        while (safetyBufferedTurns.size > MaxRememberedTurns) {
            safetyBufferedTurns.remove(safetyBufferedTurns.first())
        }
        state.addDiagnostic(
            SessionDiagnostic(
                severity = DiagnosticSeverity.Info,
                code = DiagnosticCode.SafetyBuffering,
            ),
        )
    }

    private fun onItemCompleted(item: ThreadItem) {        // Any delta still waiting for its tick belongs to this item; the authoritative text that
        // follows would otherwise be overwritten by a late flush with an older prefix.
        flushMarkdown()
        // The completed item carries the authoritative text; the streamed buffer is only a stand-in
        // for the case where it does not (a server that completes an item without a text body).
        state.upsert(withStreamedText(item))
        state.endStream(item.id)
        if (state.streamingItemId == item.id) state.applyStreaming(null)
        // A finished item can never still be waiting for a decision: either this client answered it,
        // another client did (the server says so through `serverRequest/resolved`), or the server
        // resolved it itself. Leaving the card queued would show the user a decision that no longer
        // has any effect, so the queue is pruned on every completion.
        dropResolvedApprovals(item)
    }

    /**
     * Remove pending requests whose item has finished.
     *
     * Only the item id is needed: the request and the item it belongs to share it, which is what
     * makes this check possible without tracking a separate correlation table.
     */
    private fun dropResolvedApprovals(item: ThreadItem) {
        val settled = when (item) {
            is CommandExecutionItem -> item.status != com.cy.codexui.protocol.protocol.v2.CommandExecutionStatus.InProgress
            is com.cy.codexui.protocol.protocol.item.FileChangeItem ->
                item.status != com.cy.codexui.protocol.protocol.v2.PatchApplyStatus.InProgress

            is McpToolCallItem -> item.status != com.cy.codexui.protocol.protocol.v2.McpToolCallStatus.InProgress
            else -> false
        }
        if (!settled) return
        pendingApprovals.removeAll { it.itemId == item.id }
        syncCurrentApproval()
    }

    /**
     * Show the next request after the one on screen was removed from the queue underneath it.
     *
     * Called only when a request is guaranteed gone; a head that is merely waiting out the typing
     * window has `currentApproval == null` already and must not be promoted early.
     */
    private fun syncCurrentApproval() {
        val shown = currentApproval ?: return
        if (pendingApprovals.none { it.requestId == shown.requestId }) {
            currentApproval = pendingApprovals.firstOrNull()
        }
    }

    private fun onTurnCompleted(event: AppServerEvent.TurnCompleted) {
        flushMarkdown()
        state.applyStatus(ThreadStatus.Idle)
        // An interrupted turn may never complete its last item; its buffered deltas are folded back
        // into the item before the stream flag is cleared.
        state.settleStreams()
        state.applyStreaming(null)
        refreshQueue(event.threadId)
        // Every approval belongs to the turn that asked for it. Card families with no item of their
        // own (`request_user_input`, permissions, elicitation) are settled exactly this way: when the
        // turn ends, nothing is waiting any more.
        finishedTurns.addLast(event.turnId)
        while (finishedTurns.size > MaxRememberedTurns) finishedTurns.removeFirst()
        pendingApprovals.removeAll { it.turnId == event.turnId }
        syncCurrentApproval()
        if (event.status != TurnStatus.Completed) {
            // The notice is named by code, not by text: the status label is a string resource and
            // this reducer is not composable. `TurnFinished` carries the status so the transcript
            // can name it through the label it already has.
            val (code, args) = when (event.status) {
                TurnStatus.Interrupted -> DiagnosticCode.TurnInterrupted to emptyList()
                TurnStatus.Failed -> DiagnosticCode.TurnFailed to emptyList()
                else -> DiagnosticCode.TurnFinished to listOf(event.status.name)
            }
            state.addDiagnostic(
                SessionDiagnostic(
                    severity = if (event.status == TurnStatus.Interrupted) {
                        DiagnosticSeverity.Warning
                    } else {
                        DiagnosticSeverity.Error
                    },
                    code = code,
                    args = args,
                    detail = event.error,
                ),
            )
        }
        // The turn is over, so the payload it accumulated is never extended again. [SessionState]
        // keeps the parsed diff for the status card; only the accumulator's memory is released.
        turnDiff.reset()
        patchChanges.clear()
    }

    private fun refreshHistory(threadId: String) {
        val version = loadVersion
        scope.launch {
            repeat(3) {
                val revision = eventRevision
                val response = client.readThread(com.cy.codexui.protocol.protocol.v2.ThreadReadParams(threadId)).getOrElse {
                    if (version == loadVersion) state.addDiagnostic(SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = it.message,
                    ))
                    return@launch
                }
                if (version != loadVersion || state.threadId != threadId) return@launch
                if (revision == eventRevision) {
                    state.items.clear()
                    response.items.forEach(state::upsert)
                    state.applyStatus(response.thread.status)
                    // A snapshot can be older than the live stream, so the buffer for the item
                    // still streaming is kept; only buffers for items this client no longer has
                    // are dropped.
                    val keep = response.items.mapTo(mutableSetOf()) { it.id }
                    state.streamingItemId?.let(keep::add)
                    state.retainStreams(keep)
                    return@launch
                }
            }
        }
    }

    /**
     * Fill in a text body from the item's stream when the completion event left it empty.
     *
     * This materializes the buffer once, at the end of a message, which is the only point where the
     * whole text is needed: until then the transcript renders the parsed blocks instead.
     */
    private fun withStreamedText(item: ThreadItem): ThreadItem = when {
        item is AgentMessageItem && item.text.isEmpty() ->
            state.streamText(item.id)?.let { item.copy(text = it) } ?: item

        item is PlanItem && item.text.isEmpty() ->
            state.streamText(item.id)?.let { item.copy(text = it) } ?: item

        else -> item
    }

    private fun appendReasoningText(itemId: String, delta: String) {
        val item = state.item(itemId) as? ReasoningItem
        if (item == null) {
            state.upsert(ReasoningItem(id = itemId, summary = listOf(delta)))
            return
        }
        val summary = item.summary.toMutableList()
        if (summary.isEmpty()) summary.add(delta) else summary[summary.lastIndex] += delta
        state.upsert(item.copy(summary = summary))
    }

    private fun appendCommandOutput(itemId: String, delta: String) {
        val item = state.item(itemId) as? CommandExecutionItem ?: return
        state.upsert(item.copy(aggregatedOutput = (item.aggregatedOutput ?: "") + delta))
    }

    private fun appendMcpProgress(itemId: String, delta: String) {
        val item = state.item(itemId) as? McpToolCallItem ?: return
        state.upsert(item.copy(result = (item.result ?: "") + delta))
    }

    private companion object {
        /** How many completed turns are remembered for late-approval filtering. */
        const val MaxRememberedTurns = 8

        /** How long the composer must be untouched before an approval dialog may appear. */
        const val ApprovalTypingIdleDelayMs = 1_000L

        /** How many auto-review denials stay overridable, mirroring `auto_review_denials.rs`. */
        const val MaxRememberedDenials = 10
    }
}

private fun com.cy.codexui.protocol.protocol.v2.Thread.threadIdOr(fallback: String): String =
    id.ifEmpty { fallback }

/**
 * One-line summary of the action an auto review is judging.
 *
 * Mirrors `tui/src/auto_review_denials.rs::action_summary`: the strings are the wire's own nouns
 * (commands, paths, hosts), so they stay in the action's language rather than being translated.
 * Returns `null` for a shape this client does not know, which keeps it out of the review notice
 * instead of showing an empty bullet.
 */
private fun reviewActionSummary(action: kotlinx.serialization.json.JsonElement?): String? {
    val o = action as? kotlinx.serialization.json.JsonObject ?: return null
    fun str(key: String): String? =
        (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    fun strs(key: String): List<String> =
        (o[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    val type = str("type") ?: return null
    return when (type) {
        "command" -> str("command")
        "execve" -> {
            val program = str("program") ?: return null
            (listOf(program) + strs("argv")).joinToString(" ")
        }

        "writeStdin" -> {
            val processId = str("processId") ?: return null
            "send input to terminal $processId: ${str("stdin").orEmpty()}"
        }

        "applyPatch" -> {
            val files = strs("files")
            when (files.size) {
                0 -> "apply_patch"
                1 -> "apply_patch touching ${files[0]}"
                else -> "apply_patch touching ${files.size} files"
            }
        }

        "networkAccess" -> "network access to ${str("target") ?: str("host") ?: return null}"
        "mcpToolCall" -> {
            val tool = str("toolName") ?: return null
            val label = str("connectorName") ?: str("server") ?: return null
            "MCP $tool on $label"
        }

        "requestPermissions" -> str("reason")?.let { "permission request: $it" } ?: "permission request"
        else -> null
    }
}
