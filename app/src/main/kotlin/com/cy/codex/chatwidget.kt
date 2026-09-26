package com.cy.codex

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cy.codex.chatwidget.AgentNotice
import com.cy.codex.chatwidget.ApprovalNoticeKind
import com.cy.codex.diff.TurnDiffAccumulator
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.FileChangeItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.PlanItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.protocol.protocol.v2.FileUpdateChange
import com.cy.codex.protocol.protocol.v2.QueuedSubmission
import com.cy.codex.protocol.protocol.v2.SortDirection
import com.cy.codex.protocol.protocol.v2.ThreadResumeInitialTurnsPageParams
import com.cy.codex.protocol.protocol.v2.ThreadResumeParams
import com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdateParams
import com.cy.codex.protocol.protocol.v2.ThreadReadResponse
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.ThreadTurnsListParams
import com.cy.codex.protocol.protocol.v2.TurnItemsView
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** The wire's "no tier preference" value (`SERVICE_TIER_DEFAULT_REQUEST_VALUE` upstream). */
private const val ServiceTierDefault = "default"

/** Turns in the bounded first screen `thread/resume` returns; older turns stay a click away. */
private const val FirstScreenTurnLimit = 20

/** Turns per "load earlier" page; the server clamps whatever the client asks for. */
private const val EarlierPageSize = 25

/** The reducer that owns one open thread; mirrors `codex-rs/tui/src/chatwidget.rs`. */
class ChatWidget(
    private val client: AppServerClient,
    private val scope: CoroutineScope,
    val state: SessionState = SessionState(),
) {
    /** Server requests waiting for a decision, oldest first; snapshot-backed so composables observe the queue. */
    private val pendingApprovals = mutableStateListOf<ApprovalRequest>()

    /** Notices for [CodexApp] to post; `tryEmit` with a buffer because an alert must never suspend a reducer. */
    private val _notices = MutableSharedFlow<AgentNotice>(extraBufferCapacity = 32)
    val notices: SharedFlow<AgentNotice> = _notices.asSharedFlow()

    /** Incremented on every reset so a stale collection job can recognise itself. */
    private var subscription: Job? = null
    private var loadJob: Job? = null
    private var loadVersion = 0
    private var eventRevision = 0L

    /** Completed turns, bounded; used to drop approvals that arrive after their turn finished. */
    private val finishedTurns = ArrayDeque<String>()

    /** Accumulating `turn/diff/updated` payload; the server re-sends the whole diff, so only the tail is re-parsed. */
    private val turnDiff = TurnDiffAccumulator()

    /**
     * Last streamed patch for a file-change item; `requestApproval` carries no diff and
     * `patchUpdated` can precede `item/started` (tui/src/app/file_change_approvals.rs).
     */
    private val patchChanges = mutableStateMapOf<String, List<FileUpdateChange>>()

    fun fileChangeChanges(itemId: String): List<FileUpdateChange> =
        (state.item(itemId) as? FileChangeItem)?.changes?.takeIf { it.isNotEmpty() }
            ?: patchChanges[itemId].orEmpty()

    /** Deltas accumulate for the render cadence ([Motion.StreamCommitIntervalMs]); a completing item flushes first. */
    private enum class DeltaKind { AgentMessage, Plan, Reasoning, CommandOutput, McpProgress }

    /** One item's pending deltas in arrival order; adjacent same-kind deltas concatenate, a kind change starts a chunk. */
    private class PendingDeltas {
        val chunks = mutableListOf<Pair<DeltaKind, StringBuilder>>()

        fun append(kind: DeltaKind, delta: String) {
            val last = chunks.lastOrNull()
            if (last != null && last.first == kind) last.second.append(delta)
            else chunks += kind to StringBuilder(delta)
        }
    }

    private val pendingDeltas = linkedMapOf<String, PendingDeltas>()
    private var deltaFlushJob: Job? = null

    /** Notifications worth replaying on switch; bounded per thread and by thread count, oldest evicted first. */
    private class ForeignEventBuffer(
        private val perThreadCapacity: Int = 256,
        private val maxThreads: Int = 8,
    ) {
        private val threads = LinkedHashMap<String, ArrayDeque<AppServerEvent>>()

        fun record(threadId: String, event: AppServerEvent) {
            if (!replayable(event)) return
            // Re-insert so the eviction order is least-recently-active, not least-recently-created.
            val queued = threads.remove(threadId) ?: ArrayDeque()
            threads[threadId] = queued
            queued.addLast(event)
            while (queued.size > perThreadCapacity) queued.removeFirst()
            while (threads.size > maxThreads) threads.remove(threads.keys.first())
        }

        fun drain(threadId: String): List<AppServerEvent> = threads.remove(threadId)?.toList().orEmpty()

        private fun replayable(event: AppServerEvent): Boolean = when (event) {
            // A completed item carries its full body; replaying upserts by id, so nothing duplicates.
            is AppServerEvent.ItemCompleted,
            is AppServerEvent.TurnStarted,
            is AppServerEvent.TurnCompleted,
            is AppServerEvent.ErrorEvent,
            is AppServerEvent.WarningEvent,
            is AppServerEvent.GuardianWarningEvent,
            is AppServerEvent.ThreadClosed,
            -> true

            else -> false
        }
    }

    private val safetyBufferedTurns = LinkedHashSet<String>()

    private val titleRequests = mutableSetOf<String>()

    private var recapJob: Job? = null

    private val recap = com.cy.codex.app.RecapScheduler()

    private var gitSummaryJob: Job? = null

    /** Last composer change on a monotonic clock; approvals wait for [ApprovalTypingIdleDelayMs] of idle typing (tui/src/bottom_pane/mod.rs). */
    private var composerActiveAtMs = 0L

    private var promotionJob: Job? = null

    /** Requests for other threads, surfaced as a switch-thread banner (bottom_pane/pending_thread_approvals.rs). */
    private val otherApprovals = mutableStateListOf<ApprovalRequest>()

    /**
     * Replayable notifications from threads not on screen; the snapshot read on switch lacks
     * transient events (app/thread_event_buffer.rs + app/replay_filter.rs).
     */
    private val foreignEvents = ForeignEventBuffer()

    private val reviewsInFlight = mutableStateListOf<PendingReview>()

    private val autoReviewDenials = mutableStateListOf<AutoReviewDenial>()

    var currentApproval by mutableStateOf<ApprovalRequest?>(null)
        private set

    var answeringApproval by mutableStateOf(false)
        private set
    var approvalError by mutableStateOf<String?>(null)
        private set

    /** Set once the live stream overtook the load's snapshot; see [open]. */
    private var streamOvertookLoad = false

    /** Cursor into turns older than the loaded screen, from `thread/resume`'s page; null means nothing older. */
    var nextTurnCursor by mutableStateOf<String?>(null)
        private set

    var loadingEarlier by mutableStateOf(false)
        private set

    val canLoadEarlier: Boolean get() = nextTurnCursor != null && !loadingEarlier

    val approvalQueueSize: Int get() = if (currentApproval == null) 0 else pendingApprovals.size

    /** Side threads inherit the parent's tool specs; `thread/fork` has no `dynamicTools` field. */
    var isSideThread: (String) -> Boolean = { false }

    val otherThreadApprovals: List<ForeignApproval>
        get() = otherApprovals.groupBy { it.threadId }.map { (threadId, requests) ->
            ForeignApproval(threadId, requests.size)
        }

    val pendingReviews: List<PendingReview> get() = reviewsInFlight
    val approvalDenials: List<AutoReviewDenial> get() = autoReviewDenials

    fun noteComposerActivity() {
        composerActiveAtMs = monotonicMs()
        if (currentApproval == null && pendingApprovals.isNotEmpty() && promotionJob != null) {
            schedulePromotion()
        }
    }

    private fun monotonicMs(): Long = System.nanoTime() / 1_000_000

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
            if (currentApproval == null) currentApproval = pendingApprovals.firstOrNull()
        }
    }

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
        // UNDISPATCHED: a socket delivers each notification exactly once, so anything emitted
        // before the collectors register would be lost.
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
        flushDeltas()
        subscription?.cancel()
        subscription = null
    }

    fun connectionLost() {
        flushDeltas()
        resetApprovalState()
        patchChanges.clear()
        state.applyStatus(ThreadStatus.NotLoaded)
    }

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

    fun open(threadId: String, onLoaded: (Result<ThreadReadResponse>) -> Unit = {}) {
        loadJob?.cancel()
        val version = ++loadVersion
        dropDeltas()
        // `otherApprovals` survives the reset by one step so the opening thread adopts its requests back.
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
        nextTurnCursor = null
        loadingEarlier = false
        recap.resetForNewThread()
        loadJob = scope.launch {
            val resumed = client.resumeThread(
                ThreadResumeParams(
                    threadId = threadId,
                    // Metadata-only resume plus one bounded page keeps a long thread from replaying its rollout.
                    excludeTurns = true,
                    initialTurnsPage = ThreadResumeInitialTurnsPageParams(
                        limit = FirstScreenTurnLimit,
                        sortDirection = SortDirection.Desc,
                        itemsView = TurnItemsView.Full,
                    ),
                ),
            )
            if (version != loadVersion) return@launch
            var streamedStatus: ThreadStatus? = null
            resumed.onSuccess { session ->
                streamedStatus = state.status.takeIf { streamOvertookLoad && it != ThreadStatus.NotLoaded }
                state.bindThread(threadId, session)
                if (streamedStatus != null) state.applyStatus(streamedStatus)
                // Read after `bindThread` so a stale resume cannot plant a cursor for the thread that replaced it.
                if (version == loadVersion) nextTurnCursor = session.initialTurnsPage?.nextCursor
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
            val session = resumed.getOrNull()
            val row = session?.thread
            val page = session?.initialTurnsPage
            var loadedTurns = emptyList<com.cy.codex.protocol.protocol.v2.Turn>()
            if (version == loadVersion && row != null && page != null) {
                // Separators are rebuilt; the server does not store this client's divider.
                val transcript = com.cy.codex.history_cell.transcriptWithSeparators(page.turns)
                applyLoadedTranscript(transcript)
                if (streamedStatus == null) state.applyStatus(row.status)
                loadedTurns = page.turns
                onLoaded(Result.success(ThreadReadResponse(row, transcript, page.turns)))
            } else {
                // A server without `initialTurnsPage` still gets the full read it always got.
                val history = client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId))
                if (version != loadVersion) return@launch
                history.onSuccess { response ->
                    if (version != loadVersion) return@onSuccess
                    val transcript = if (response.turns.isNotEmpty()) {
                        com.cy.codex.history_cell.transcriptWithSeparators(response.turns)
                    } else {
                        response.items
                    }
                    applyLoadedTranscript(transcript)
                    response.turns.lastOrNull()?.usage?.let(state::applyUsage)
                    state.applyStatus(response.thread.status)
                    loadedTurns = response.turns
                }.onFailure { error ->
                    if (version != loadVersion) return@onFailure
                    state.addDiagnostic(SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = error.message,
                    ))
                }
                onLoaded(history)
            }
            if (version != loadVersion) return@launch
            // Replay after the snapshot so content arrives once; only snapshot-less facts come from the buffer.
            foreignEvents.drain(threadId).forEach { buffered ->
                if (version != loadVersion) return@launch
                apply(buffered)
            }
            // Seed after the replay so a buffered `TurnCompleted` is not counted twice.
            recap.seedFromTurns(loadedTurns, monotonicMs())
            scheduleRecapCheck()
            refreshQueue(threadId)
            client.getGoal(threadId).onSuccess {
                if (version == loadVersion) state.applyGoal(it)
            }
            refreshGitSummary(threadId)
        }
    }

    /** A snapshot may only fill the gaps when the stream got there first; applied wholesale it would replace streamed bodies with older copies. */
    private fun applyLoadedTranscript(transcript: List<ThreadItem>) {
        if (streamOvertookLoad) {
            transcript.forEach(state::addIfAbsent)
        } else {
            transcript.forEach(state::upsert)
        }
    }

    /** `thread/turns/list` pages newest-first; reverse before prepending. */
    fun loadEarlier() {
        val cursor = nextTurnCursor ?: return
        if (loadingEarlier) return
        val threadId = state.threadId
        if (threadId.isBlank()) return
        loadingEarlier = true
        scope.launch {
            client.listThreadTurns(
                ThreadTurnsListParams(
                    threadId = threadId,
                    cursor = cursor,
                    limit = EarlierPageSize,
                    sortDirection = SortDirection.Desc,
                    itemsView = TurnItemsView.Full,
                ),
            ).onSuccess { page ->
                if (state.threadId != threadId) return@onSuccess
                state.prepend(com.cy.codex.history_cell.transcriptWithSeparators(page.turns.asReversed()))
                nextTurnCursor = page.nextCursor
            }.onFailure { error ->
                if (state.threadId != threadId) return@onFailure
                state.addDiagnostic(
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.ThreadLoadFailed,
                        detail = error.message,
                    ),
                )
            }
            loadingEarlier = false
        }
    }

    fun newThread(cwd: String) {
        resetApprovalState()
        scope.launch {
            client.startThread(
                com.cy.codex.protocol.protocol.v2.ThreadStartParams(
                    cwd = cwd,
                    dynamicTools = DynamicTools.specs(),
                ),
            )
                .onSuccess { session ->
                    dropDeltas()
                    state.beginLoad(session.threadId)
                    turnDiff.reset()
                    patchChanges.clear()
                    recap.resetForNewThread()
                    state.bindThread(session.threadId, session)
                    maybeShowStartupTip()
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

    fun action(event: AppEvent) {
        when (event) {
            is AppEvent.NewThread -> newThread(event.cwd ?: state.config.cwd)
            is AppEvent.ResumeThread -> open(event.threadId)
            is AppEvent.ForkThread -> request({
                client.forkThread(com.cy.codex.protocol.protocol.v2.ThreadForkParams(event.threadId))
            }) { bind(it) }

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

            is AppEvent.SubmitUserMessage -> submitInput(event.inputs)
            is AppEvent.AnswerAsyncQuestion -> submitInput(listOf(UserInput.Text(event.text)), clearDraft = false)
            AppEvent.InterruptTurn -> interrupt()
            is AppEvent.ResolveApproval -> resolve(event.requestId, event.response)
            is AppEvent.DismissApproval -> dismiss(event.requestId)

            AppEvent.GenerateRecap -> generateRecap(com.cy.codex.app.RecapTrigger.Manual)
            AppEvent.ContinueMisalignment -> continueMisalignment()

            is AppEvent.SetGoal -> request({
                client.setGoal(
                    com.cy.codex.protocol.protocol.v2.ThreadGoalSetParams(
                        threadId = state.threadId,
                        objective = event.objective,
                        status = event.status,
                    ),
                )
            }) { state.applyGoal(it) }

            AppEvent.ClearGoal -> request({ client.clearGoal(state.threadId) }) { state.applyGoal(null) }

            is AppEvent.StartQueuedMessage -> request { client.startQueued(state.threadId, event.queuedId) }
            is AppEvent.DeleteQueuedMessage -> request { client.deleteQueued(state.threadId, event.queuedId) }

            is AppEvent.UpdateQueuedMessage -> request {
                client.updateQueued(state.threadId, event.queuedId, event.inputs)
            }

            is AppEvent.ClearQueue -> request({
                // No bulk endpoint; delete per entry, re-read once at the end.
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
                        // The wire spells "no preference" as the literal `default`, not a missing field.
                        serviceTier = event.tier ?: ServiceTierDefault,
                    ),
                )
            }) { state.applyConfig(state.config.copy(serviceTier = event.tier)) }

            is AppEvent.RemoveAttachment -> request({
                client.removeAttachment(event.threadId, event.type, event.identityKey)
            }) { state.attachments.removeAll { it.identityKey == event.identityKey } }

            is AppEvent.TerminateBackgroundTerminal -> request({
                client.terminateBackgroundTerminal(event.threadId, event.processId)
            }) { state.backgroundTerminals.removeAll { it.processId == event.processId } }

            is AppEvent.CleanBackgroundTerminals -> request({
                client.cleanBackgroundTerminals(event.threadId)
            }) { state.backgroundTerminals.clear() }

            // A paste of one local image path stages an attachment instead of text; the path must exist and fit the transport limit.
            is AppEvent.SetComposerDraft -> {
                val pasted = detectPastedImagePath(state.composerDraft, event.text)
                if (pasted == null) {
                    state.applyDraft(event.text)
                } else {
                    val file = java.io.File(pasted.path)
                    when {
                        !file.isFile -> state.applyDraft(event.text)
                        file.length() > MaxComposerImageBytes -> {
                            state.applyDraft(event.text)
                            state.addDiagnostic(
                                SessionDiagnostic(
                                    severity = DiagnosticSeverity.Warning,
                                    code = DiagnosticCode.ImageTooLarge,
                                    args = listOf(file.name),
                                ),
                            )
                        }

                        else -> {
                            val placeholder = state.addComposerImage(pasted.path)
                            state.applyDraft(event.text.replaceRange(pasted.start, pasted.end, "$placeholder "))
                        }
                    }
                }
            }

            is AppEvent.RemoveComposerImage -> state.removeComposerImage(event.path)

            is AppEvent.SubmitSlashCommand,

            // Events `CodexApp` handles before forwarding; listed, not caught by an `else`, so a new event must be classified.
            is AppEvent.ToggleSideConversation,
            is AppEvent.ReloadAccount,
            is AppEvent.ReloadRateLimits,
            is AppEvent.ReloadUsage,
            is AppEvent.ReloadConfig,
            is AppEvent.ReloadAgentThreads,
            is AppEvent.StopThreadTurn,
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
            is AppEvent.SetPluginEnabled,
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

    private fun request(block: suspend () -> Result<*>) {
        request(block, then = {})
    }

    /** `then` is suspend so a continuation can talk to the server itself. */
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

    fun bind(session: com.cy.codex.protocol.protocol.v2.ThreadSessionState) {
        loadJob?.cancel()
        loadVersion++
        dropDeltas()
        resetApprovalState()
        state.beginLoad(session.threadId)
        turnDiff.reset()
        patchChanges.clear()
        recap.resetForNewThread()
        state.bindThread(session.threadId, session)
    }

    /** One random tip per fresh conversation, gated by `show_tooltips` (tui/src/tooltips.rs). */
    private fun maybeShowStartupTip() {
        if (!com.cy.codex.theme.Appearance.showTooltips) return
        if (state.items.isNotEmpty()) return
        val tip = Tooltips.random() ?: return
        state.upsert(
            com.cy.codex.protocol.protocol.item.TipItem("tip-${state.threadId}", tip),
        )
    }

    fun clear() {
        loadJob?.cancel()
        loadVersion++
        resetApprovalState()
        patchChanges.clear()
        dropDeltas()
        turnDiff.reset()
        recap.resetForNewThread()
        state.clear()
    }

    private fun replaceQueue(queued: List<QueuedSubmission>) {
        state.queued.clear()
        state.queued.addAll(queued)
    }

    private fun dropDeltas() {
        deltaFlushJob?.cancel()
        deltaFlushJob = null
        pendingDeltas.clear()
    }

    private fun appendDelta(itemId: String, kind: DeltaKind, delta: String) {
        pendingDeltas.getOrPut(itemId) { PendingDeltas() }.append(kind, delta)
        if (deltaFlushJob == null) {
            deltaFlushJob = scope.launch {
                delay(Motion.StreamCommitIntervalMs)
                flushDeltas()
            }
        }
    }

    internal fun flushDeltas() {
        deltaFlushJob = null
        if (pendingDeltas.isEmpty()) return
        val pending = pendingDeltas.toMap()
        pendingDeltas.clear()
        for ((itemId, batch) in pending) {
            for ((kind, text) in batch.chunks) {
                val delta = text.toString()
                when (kind) {
                    DeltaKind.AgentMessage -> state.appendAgentDelta(itemId, delta)
                    DeltaKind.Plan -> state.appendPlanDelta(itemId, delta)
                    DeltaKind.Reasoning -> appendReasoningText(itemId, delta)
                    DeltaKind.CommandOutput -> appendCommandOutput(itemId, delta)
                    DeltaKind.McpProgress -> appendMcpProgress(itemId, delta)
                }
            }
        }
    }

    private fun submitInput(inputs: List<UserInput>, clearDraft: Boolean = true) {
        val hasText = inputs.filterIsInstance<UserInput.Text>().any { it.text.isNotBlank() }
        val hasMedia = inputs.any { it !is UserInput.Text }
        if ((!hasText && !hasMedia) || !state.open || state.loading) return
        // Viewing a parent-owned sub-agent: the transcript is readable, input is not.
        if (state.config.blocksDirectInput) return
        val threadId = state.threadId
        if (state.running) {
            request({ client.addToQueue(threadId, inputs) }) {
                if (clearDraft && state.threadId == threadId) clearComposer()
                refreshQueue(threadId)
            }
            return
        }
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(threadId, inputs).onSuccess {
                if (clearDraft && state.threadId == threadId) clearComposer()
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

    /** `git`/`gh` run via `command/exec`; failures are silent (tui/src/branch_summary.rs). */
    private fun refreshGitSummary(threadId: String) {
        val cwd = state.config.cwd
        if (threadId.isBlank() || cwd.isBlank()) return
        gitSummaryJob?.cancel()
        gitSummaryJob = scope.launch {
            val summary = loadGitSummary(client, cwd)
            if (state.threadId == threadId) state.applyGitSummary(summary)
        }
    }

    private fun clearComposer() {
        state.applyDraft("")
        state.clearComposerImages()
    }

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

    private fun resolve(requestId: com.cy.codex.protocol.protocol.RequestId, response: ApprovalResponse) {
        if (answeringApproval) return
        answeringApproval = true
        approvalError = null
        scope.launch {
            try {
                client.respond(requestId, response)
                pendingApprovals.removeAll { it.requestId == requestId }
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

    private fun dismiss(requestId: com.cy.codex.protocol.protocol.RequestId) {
        pendingApprovals.removeAll { it.requestId == requestId }
        otherApprovals.removeAll { it.requestId == requestId }
        if (currentApproval?.requestId == requestId) currentApproval = pendingApprovals.firstOrNull()
    }

    private fun onApprovalRequest(request: ApprovalRequest) {
        // Three of the server requests are host-only answers, not user decisions; answering here keeps them out of the queue.
        when (request) {
            is ApprovalRequest.CurrentTimeRead -> {
                scope.launch {
                    client.respond(request.requestId, ApprovalResponse.CurrentTime(System.currentTimeMillis()))
                }
                return
            }

            is ApprovalRequest.DynamicTool -> {
                // Dynamic tools run immediately and never surface as cards (dynamic_tools.rs).
                val calling = request.threadId
                val cwd = state.config.cwd
                val model = state.config.model
                scope.launch {
                    val result = executeDynamicTool(
                        client = client,
                        callingThreadId = calling,
                        cwd = cwd,
                        model = model,
                        params = request.params,
                        isSideThread = isSideThread(calling),
                    )
                    runCatching {
                        client.respond(
                            request.requestId,
                            ApprovalResponse.DynamicTool(result),
                        )
                    }
                }
                return
            }

            is ApprovalRequest.AttestationGenerate -> {
                scope.launch {
                    // No keystore integration: reply well-formed but unverifiable; the server rejects it honestly.
                    client.respond(request.requestId, ApprovalResponse.Attestation(token = ""))
                }
                return
            }

            is ApprovalRequest.ChatgptAuthTokensRefresh -> {
                scope.launch {
                    // Tokens live in the account store; an empty reply makes the server fall back to its own refresh path.
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

        // Alert per request before dedup: another thread's blocking request is what a backgrounded user should hear.
        _notices.tryEmit(
            AgentNotice.Approval(
                threadId = request.threadId,
                kind = approvalNoticeKind(request),
                detail = approvalNoticeDetail(request),
            ),
        )

        if (pendingApprovals.any { it.requestId == request.requestId }) return
        if (otherApprovals.any { it.requestId == request.requestId }) return
        // A request whose turn already finished is stale; showing it would offer a decision with no effect.
        if (request.turnId != null && request.turnId in finishedTurns) return
        // Another thread's request is tracked for the banner and adopted on switch.
        if (request.threadId.isNotBlank() && request.threadId != state.threadId) {
            otherApprovals.add(request)
            return
        }
        pendingApprovals.add(request)
        state.applyStatus(
            ThreadStatus.Active(
                listOf(
                    com.cy.codex.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval,
                ),
            ),
        )
        promoteApprovalIfIdle()
    }

    private fun apply(event: AppServerEvent) {
        val eventThread = event.threadId
        if (eventThread != null && eventThread != state.threadId) {
            foreignEvents.record(eventThread, event)
            return
        }
        eventRevision++
        streamOvertookLoad = true
        when (event) {
            is AppServerEvent.ItemStarted -> state.upsert(event.item)
            is AppServerEvent.ItemCompleted -> onItemCompleted(event.item)
            is AppServerEvent.AgentMessageDelta ->
                appendDelta(event.delta.itemId, DeltaKind.AgentMessage, event.delta.delta)

            is AppServerEvent.PlanDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Plan, event.delta.delta)

            is AppServerEvent.ReasoningTextDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, event.delta.delta)

            is AppServerEvent.ReasoningSummaryDelta ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, event.delta.delta)

            is AppServerEvent.ReasoningSummaryPartAdded ->
                appendDelta(event.delta.itemId, DeltaKind.Reasoning, "\n\n")

            is AppServerEvent.CommandOutputDelta ->
                appendDelta(event.delta.itemId, DeltaKind.CommandOutput, event.delta.delta)

            is AppServerEvent.CommandTerminalInteraction ->
                appendDelta(event.delta.itemId, DeltaKind.CommandOutput, event.delta.stdin)

            is AppServerEvent.FileChangeOutputDelta -> Unit
            is AppServerEvent.McpToolProgress ->
                appendDelta(event.delta.itemId, DeltaKind.McpProgress, event.delta.message)

            // The bridge dropped events; re-read, the same repair a compact or a revert uses.
            is AppServerEvent.TransportLagged -> {
                dropDeltas()
                if (state.threadId.isNotEmpty()) {
                    refreshHistory(state.threadId)
                    refreshQueue(state.threadId)
                }
            }
            is AppServerEvent.TurnStarted -> {
                state.applyStatus(ThreadStatus.Active())
                state.applyStreaming(null)
                // A new turn clears a previous safety stop; the gate is per turn.
                state.applyMisalignment(null)
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
                com.cy.codex.protocol.protocol.RequestId(event.delta.requestId),
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

            // `patchUpdated` may precede `item/started`; keep the update by id so an approval in between still has a diff.
            is AppServerEvent.FileChangePatchUpdated -> {
                patchChanges[event.delta.itemId] = event.delta.changes
                val item = state.item(event.delta.itemId) as? FileChangeItem
                if (item != null) state.upsert(item.copy(changes = event.delta.changes))
            }

            is AppServerEvent.AutoApprovalReviewStarted -> onReviewStarted(event.delta)
            is AppServerEvent.AutoApprovalReviewCompleted -> onReviewCompleted(event.delta)

            is AppServerEvent.StrictReviewRequired -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Warning,
                    message = event.delta.reason,
                    code = DiagnosticCode.StrictReviewRequired,
                ),
            )

            // Only failed hooks get a notice; a session with hooks on would otherwise fill the transcript.
            is AppServerEvent.HookCompleted -> {
                state.applyHookCompleted()
                if (event.delta.run.failed) {
                    state.addDiagnostic(
                        SessionDiagnostic(
                            severity = DiagnosticSeverity.Warning,
                            message = event.delta.run.statusMessage,
                            code = DiagnosticCode.HookFailed,
                            args = listOf(event.delta.run.eventName.ifEmpty { event.delta.run.id }),
                        ),
                    )
                }
            }

            is AppServerEvent.HookStarted -> state.applyHookStarted(
                event.delta.run.statusMessage?.takeIf { it.isNotBlank() }
                    ?: event.delta.run.eventName.ifBlank { event.delta.run.id },
            )

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

            // Events other surfaces own; listed explicitly so a new protocol notification is a compile error here.
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

    private fun onReviewStarted(delta: com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification) {
        if (delta.status != "inProgress") return
        val detail = reviewActionSummary(delta.action) ?: return
        val index = reviewsInFlight.indexOfFirst { it.id == delta.reviewId }
        val entry = PendingReview(delta.reviewId, detail)
        if (index >= 0) reviewsInFlight[index] = entry else reviewsInFlight.add(entry)
    }

    /** A denial is the one review outcome the user can act on; `thread/approveGuardianDeniedAction` needs the cached assessment. */
    private fun onReviewCompleted(delta: com.cy.codex.protocol.protocol.v2.GuardianApprovalReviewNotification) {
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

    private fun onSafetyBuffering(delta: com.cy.codex.protocol.protocol.v2.ModelSafetyBufferingUpdatedNotification) {
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

    private fun onItemCompleted(item: ThreadItem) {
        // Flush first: a late tick would otherwise overwrite the authoritative text with an older prefix.
        flushDeltas()
        state.upsert(withStreamedText(item))
        state.endStream(item.id)
        if (state.streamingItemId == item.id) state.applyStreaming(null)
        if (item is com.cy.codex.protocol.protocol.item.UserMessageItem) maybeGenerateTitle()
        dropResolvedApprovals(item)
    }

    private fun dropResolvedApprovals(item: ThreadItem) {
        val settled = when (item) {
            is CommandExecutionItem -> item.status != com.cy.codex.protocol.protocol.v2.CommandExecutionStatus.InProgress
            is com.cy.codex.protocol.protocol.item.FileChangeItem ->
                item.status != com.cy.codex.protocol.protocol.v2.PatchApplyStatus.InProgress

            is McpToolCallItem -> item.status != com.cy.codex.protocol.protocol.v2.McpToolCallStatus.InProgress
            else -> false
        }
        if (!settled) return
        pendingApprovals.removeAll { it.itemId == item.id }
        syncCurrentApproval()
    }

    private fun syncCurrentApproval() {
        val shown = currentApproval ?: return
        if (pendingApprovals.none { it.requestId == shown.requestId }) {
            currentApproval = pendingApprovals.firstOrNull()
        }
    }

    /** Recap into a client-local cell (app/recap.rs); automatic ones retry once per revision. */
    private fun generateRecap(trigger: com.cy.codex.app.RecapTrigger) {
        if (recapJob?.isActive == true) return
        if (trigger == com.cy.codex.app.RecapTrigger.Automatic && !com.cy.codex.app.RecapSettings.autoRecap) return
        if (!recap.beginInFlight(trigger)) return
        val threadId = state.threadId
        val history = com.cy.codex.app.recapHistory(state.items)
        if (history == null) {
            recap.finishInFlight()
            if (trigger == com.cy.codex.app.RecapTrigger.Manual) {
                state.addDiagnostic(SessionDiagnostic(severity = DiagnosticSeverity.Info, code = DiagnosticCode.RecapNoHistory))
            }
            return
        }
        val capturedCompletedTurns = recap.completedTurns
        val capturedRevision = recap.turnRevision
        val itemId = "recap-$threadId-${System.currentTimeMillis()}"
        if (trigger == com.cy.codex.app.RecapTrigger.Manual) {
            state.upsert(com.cy.codex.protocol.protocol.item.RecapItem(itemId, text = null))
        }
        recapJob = scope.launch {
            try {
                val result = structuredTurn(
                    client = client,
                    cwd = state.config.cwd,
                    model = state.config.model,
                    developerInstructions = null,
                    prompt = com.cy.codex.app.RecapPromptPrefix + history,
                    outputSchema = com.cy.codex.app.recapOutputSchema(),
                )
                val parsed = com.cy.codex.app.parseRecap(result.getOrNull())
                // Freshest wins (handle_generated_recap): drop results computed from a transcript that has moved on.
                val stale = state.threadId != threadId ||
                    state.running ||
                    recap.completedTurns != capturedCompletedTurns ||
                    recap.turnRevision != capturedRevision ||
                    (
                        trigger == com.cy.codex.app.RecapTrigger.Automatic &&
                            !(com.cy.codex.app.RecapSettings.autoRecap && recap.shouldGenerate(monotonicMs()))
                        )
                if (stale) {
                    if (trigger == com.cy.codex.app.RecapTrigger.Manual) state.remove(itemId)
                    return@launch
                }
                when {
                    parsed != null -> {
                        recap.markRecapped(capturedCompletedTurns)
                        state.upsert(
                            com.cy.codex.protocol.protocol.item.RecapItem(
                                id = itemId,
                                text = parsed.summary,
                                nextAction = parsed.nextAction,
                                failed = false,
                            ),
                        )
                    }

                    trigger == com.cy.codex.app.RecapTrigger.Manual ->
                        state.upsert(
                            com.cy.codex.protocol.protocol.item.RecapItem(id = itemId, text = null, failed = true),
                        )

                    else -> recap.scheduleRetry(scope, threadId, capturedRevision) { onRecapCheck(it) }
                }
            } finally {
                recap.finishInFlight()
                recapJob = null
            }
        }
    }

    /** The deferred automatic check; the widget's state may have moved while the timer was armed. */
    private fun onRecapCheck(threadId: String) {
        if (!com.cy.codex.app.RecapSettings.autoRecap) return
        if (!state.open || state.threadId != threadId || state.running) return
        if (!recap.shouldGenerate(monotonicMs())) return
        generateRecap(com.cy.codex.app.RecapTrigger.Automatic)
    }

    private fun scheduleRecapCheck() {
        recap.scheduleCheck(
            scope = scope,
            threadId = state.threadId,
            nowMs = monotonicMs(),
            enabled = com.cy.codex.app.RecapSettings.autoRecap,
        ) { onRecapCheck(it) }
    }

    fun noteForegroundChanged(inForeground: Boolean) {
        if (inForeground) {
            recap.noteFocusGained()
        } else {
            recap.noteFocusLost(monotonicMs())
            scheduleRecapCheck()
        }
    }

    private fun maybeGenerateTitle() {
        val threadId = state.threadId
        if (threadId.isBlank() || !state.open) return
        if (!state.config.threadName.isNullOrBlank()) return
        if (!titleRequests.add(threadId)) return
        state.markTitleGenerationPending(true)
        scope.launch {
            try {
                val prompt = firstUserMessageText(state.items) ?: return@launch
                val result = structuredTurn(
                    client = client,
                    cwd = state.config.cwd,
                    model = state.config.model,
                    developerInstructions = null,
                    prompt = com.cy.codex.app.threadTitlePrompt(prompt),
                    outputSchema = com.cy.codex.app.threadTitleOutputSchema(),
                    effort = com.cy.codex.protocol.protocol.v2.ReasoningEffort.Low,
                )
                val title = com.cy.codex.app.parseThreadTitle(result.getOrNull()) ?: return@launch
                if (state.threadId != threadId || !state.config.threadName.isNullOrBlank()) return@launch
                client.setThreadName(threadId, title).onSuccess {
                    if (state.threadId == threadId) {
                        state.applyConfig(state.config.copy(threadName = title))
                    }
                }
            } finally {
                titleRequests.remove(threadId)
                if (state.threadId == threadId) state.markTitleGenerationPending(false)
            }
        }
    }

    private fun continueMisalignment() {
        val steer = state.misalignment?.steer?.message
            ?.takeIf { it.isNotBlank() && it.length <= 1024 }
            ?: return
        val threadId = state.threadId
        state.applyMisalignment(null)
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(
                threadId = threadId,
                inputs = listOf(UserInput.Text(steer)),
                clientMetadata = mapOf(
                    "misalignment_override" to "{\"timestamp\":${System.currentTimeMillis()}}",
                ),
            ).onFailure { error ->
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

    private fun onTurnCompleted(event: AppServerEvent.TurnCompleted) {
        flushDeltas()
        state.applyStatus(ThreadStatus.Idle)
        recap.noteTurnFinished(event.status, monotonicMs())
        scheduleRecapCheck()
        // A safety stop holds input until the user reviews; the server dropped whatever was queued.
        state.applyMisalignment(event.misalignment)
        if (event.misalignment != null) {
            state.queued.clear()
            refreshQueue(event.threadId)
        }
        // Preview is the last answer's first line, like the TUI's `AgentTurnComplete`.
        _notices.tryEmit(
            AgentNotice.TurnComplete(
                threadId = event.threadId,
                preview = (state.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem)
                    ?.text
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.take(200),
            ),
        )
        // Fold back an interrupted turn's buffered deltas before clearing the stream flag.
        state.settleStreams()
        state.applyStreaming(null)
        // Label from the notification's own timing, so live and history turns read the same.
        if (event.status == TurnStatus.Completed) {
            val label = com.cy.codex.history_cell.finalMessageSeparatorLabel(
                elapsedSeconds = event.durationMs?.let { it / 1000 },
                completedAtMillis = event.completedAt,
            )
            if (label != null) {
                state.appendTurnSeparator(
                    com.cy.codex.protocol.protocol.item.TurnSeparatorItem(
                        id = com.cy.codex.history_cell.TurnSeparatorIdPrefix + event.turnId,
                        label = label,
                    ),
                )
            }
        }
        refreshQueue(event.threadId)
        finishedTurns.addLast(event.turnId)
        while (finishedTurns.size > MaxRememberedTurns) finishedTurns.removeFirst()
        pendingApprovals.removeAll { it.turnId == event.turnId }
        syncCurrentApproval()
        if (event.status != TurnStatus.Completed) {
            state.failInProgressItems()
            // Named by code; the transcript resolves the label.
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
        turnDiff.reset()
        patchChanges.clear()
        refreshGitSummary(event.threadId)
    }

    private fun refreshHistory(threadId: String) {
        val version = loadVersion
        scope.launch {
            repeat(3) {
                val revision = eventRevision
                val response = client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId)).getOrElse {
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
                    // Post-compact/revert: the old cursor may name a turn that no longer exists.
                    nextTurnCursor = null
                    val transcript = if (response.turns.isNotEmpty()) {
                        com.cy.codex.history_cell.transcriptWithSeparators(response.turns)
                    } else {
                        response.items
                    }
                    transcript.forEach(state::upsert)
                    state.applyStatus(response.thread.status)
                    // A snapshot can be older than the live stream; keep only the still-streaming item's buffer.
                    val keep = response.items.mapTo(mutableSetOf()) { it.id }
                    state.streamingItemId?.let(keep::add)
                    state.retainStreams(keep)
                    return@launch
                }
            }
        }
    }

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

private fun com.cy.codex.protocol.protocol.v2.Thread.threadIdOr(fallback: String): String =
    id.ifEmpty { fallback }

/** Which approval-notification wording a server request earns. */
private fun approvalNoticeKind(request: ApprovalRequest): ApprovalNoticeKind = when (request) {
    is ApprovalRequest.Exec -> ApprovalNoticeKind.Command
    is ApprovalRequest.ApplyPatch -> ApprovalNoticeKind.FileChange
    is ApprovalRequest.Elicitation -> ApprovalNoticeKind.Elicitation
    else -> ApprovalNoticeKind.Other
}

/** The command, path or server the notification names, when the request carries one. */
private fun approvalNoticeDetail(request: ApprovalRequest): String? = when (request) {
    is ApprovalRequest.Exec -> request.params.command
    is ApprovalRequest.ApplyPatch -> request.params.grantRoot
    is ApprovalRequest.Elicitation -> request.params.serverName
    else -> null
}

/** One-line summary in the wire's own nouns; null keeps unknown shapes out of the notice (tui/src/auto_review_denials.rs). */
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
