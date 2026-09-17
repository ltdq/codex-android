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

    /** Requests the UI can currently answer; only the head is on screen. */
    val currentApproval: ApprovalRequest? get() = pendingApprovals.firstOrNull()
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

    val approvalQueueSize: Int get() = pendingApprovals.size

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
        pendingApprovals.clear()
        patchChanges.clear()
        answeringApproval = false
        approvalError = null
        state.applyStatus(ThreadStatus.NotLoaded)
    }

    /** Open a thread and load its history. */
    fun open(threadId: String, onLoaded: (Result<ThreadReadResponse>) -> Unit = {}) {
        loadJob?.cancel()
        val version = ++loadVersion
        dropMarkdown()
        state.beginLoad(threadId)
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

            is AppEvent.UnsubscribeThread -> request { client.unsubscribeThread(event.threadId) }

            is AppEvent.UpdateThreadMetadata -> request {
                client.updateThreadMetadata(event.threadId, name = event.name, projectId = event.branch)
            }

            is AppEvent.InjectThreadItems -> request {
                client.injectThreadItems(event.threadId, event.items)
            }

            is AppEvent.RunShellCommand -> request {
                client.runShellCommand(event.threadId, event.command).onSuccess {
                    if (state.threadId == event.threadId && state.composerDraft.startsWith("/shell ")) {
                        state.applyDraft("")
                    }
                }
            }

            is AppEvent.ApproveGuardianDeniedAction -> request {
                client.approveGuardianDeniedAction(event.threadId, event.itemId)
            }

            is AppEvent.SetThreadMemoryMode -> request {
                client.setThreadMemoryMode(event.threadId, event.mode)
            }

            // ---- turns ----------------------------------------------------------
            is AppEvent.SubmitUserMessage -> submitInput(event.inputs)
            is AppEvent.SteerTurn -> steer(event.inputs)
            AppEvent.InterruptTurn -> interrupt()
            is AppEvent.ResolveApproval -> resolve(event.requestId, event.response)
            is AppEvent.DismissApproval -> dismiss(event.requestId)

            is AppEvent.SetGoal -> request({ client.setGoal(state.threadId, event.objective) }) { state.applyGoal(it) }

            AppEvent.ClearGoal -> request({ client.clearGoal(state.threadId) }) { state.applyGoal(null) }

            is AppEvent.UpdateTurnSettings -> request { client.updateTurnSettings(event.params) }

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

            // `permissions` and `experimentalFeature/enablement/set` are server-side settings, not
            // view state. They used to fall into the `else` arm and vanish, so the radio group and
            // the switches rendered but changed nothing; both now round-trip.
            is AppEvent.SetPermissionProfile -> request {
                client.updateThreadSettingsFull(
                    ThreadSettingsUpdateParams(
                        threadId = state.threadId,
                        permissions = event.profileId,
                    ),
                )
            }

            is AppEvent.UpdateThreadSettings -> request {
                client.updateThreadSettingsFull(event.params)
            }

            // ---- attachments and background terminals -----------------------------
            is AppEvent.AddAttachment -> request({
                client.addAttachment(
                    threadId = event.threadId,
                    type = event.type,
                    identityKey = event.identityKey,
                    payload = event.payload,
                )
            }) { state.attachments.add(it) }

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
            is AppEvent.ReloadWorkspaceMessages,
            is AppEvent.ReloadConfig,
            is AppEvent.ReloadSkills,
            is AppEvent.ReloadPlugins,
            is AppEvent.ReloadPluginShares,
            is AppEvent.ReloadApps,
            is AppEvent.ReloadHooks,
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
        state.beginLoad(session.threadId)
        turnDiff.reset()
        patchChanges.clear()
        state.bindThread(session.threadId, session)
    }

    fun clear() {
        loadJob?.cancel()
        loadVersion++
        pendingApprovals.clear()
        patchChanges.clear()
        answeringApproval = false
        approvalError = null
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

    private fun submitInput(inputs: List<UserInput>) {
        val text = inputs.filterIsInstance<UserInput.Text>().joinToString("\n") { it.text }.trim()
        if (text.isEmpty() || !state.open || state.loading) return
        val threadId = state.threadId
        if (state.running) {
            request({ client.addToQueue(threadId, inputs) }) {
                if (state.threadId == threadId) state.applyDraft("")
                refreshQueue(threadId)
            }
            return
        }
        state.applyStatus(ThreadStatus.Active())
        scope.launch {
            client.startTurn(threadId, inputs).onSuccess {
                if (state.threadId == threadId) state.applyDraft("")
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

    private fun steer(inputs: List<UserInput>) {
        scope.launch { client.steerTurn(state.threadId, inputs) }
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
        // A request whose turn already finished is stale: the server resolved it while this client
        // was not listening. Showing it would offer a decision with no effect.
        if (request.turnId != null && request.turnId in finishedTurns) return
        pendingApprovals.add(request)
        state.applyStatus(
            ThreadStatus.Active(
                listOf(
                    com.cy.codexui.protocol.protocol.v2.ThreadActiveFlag.WaitingOnApproval,
                ),
            ),
        )
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
            // shared "diagnostic" shape that would have to drop `willRetry` and `path`.
            is AppServerEvent.ErrorEvent -> state.addDiagnostic(
                SessionDiagnostic(
                    severity = DiagnosticSeverity.Error,
                    message = event.delta.error.message,
                    detail = event.delta.error.additionalDetails,
                    // A server-side retry is already in flight; the notice must say so instead of
                    // offering a manual retry against a turn that is still running.
                    willRetry = event.delta.willRetry,
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

            // The review's own item already renders on the transcript; these two only say it began
            // and ended, which the item's status field also says.
            is AppServerEvent.AutoApprovalReviewStarted,
            is AppServerEvent.AutoApprovalReviewCompleted,
            -> Unit

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
    }
}

private fun com.cy.codexui.protocol.protocol.v2.Thread.threadIdOr(fallback: String): String =
    id.ifEmpty { fallback }
