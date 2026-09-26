package com.cy.codex

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.ChatWidget
import com.cy.codex.CodexScreen
import com.cy.codex.Surface
import com.cy.codex.ThreadListState
import com.cy.codex.app.AgentsScreen
import com.cy.codex.app.DiagnosticsScreen
import com.cy.codex.app.SUB_AGENT_SOURCE_KINDS
import com.cy.codex.app.EnvironmentDetailScreen
import com.cy.codex.app.deriveAgentRoster
import com.cy.codex.app.ProjectsScreen
import com.cy.codex.app.SessionStatusScreen
import com.cy.codex.app.SubAgentScreen
import com.cy.codex.app.SubAgentThreadScreen
import com.cy.codex.app.ThreadHistoryScreen
import com.cy.codex.app.UserVerificationScreen
import com.cy.codex.app.WorktreesScreen
import com.cy.codex.bottom_pane.AppsScreen
import com.cy.codex.bottom_pane.BackgroundTerminalsScreen
import com.cy.codex.bottom_pane.ExecCommandScreen
import com.cy.codex.bottom_pane.FileBrowserScreen
import com.cy.codex.bottom_pane.GitDiffScreen
import com.cy.codex.bottom_pane.HooksScreen
import com.cy.codex.bottom_pane.McpScreen
import com.cy.codex.bottom_pane.McpToolboxScreen
import com.cy.codex.bottom_pane.MemoriesScreen
import com.cy.codex.bottom_pane.ComposerHistory
import com.cy.codex.bottom_pane.SkillsScreen
import com.cy.codex.bottom_pane.chat_composer.SlashInput
import com.cy.codex.bottom_pane.chat_composer.classifySlashInput
import com.cy.codex.bottom_pane.mentions_v2.MentionKind
import com.cy.codex.bottom_pane.mentions_v2.MentionSuggestion
import com.cy.codex.bottom_pane.mentions_v2.mentionMatches
import com.cy.codex.chatwidget.ChatScreen
import com.cy.codex.chatwidget.AgentNotice
import com.cy.codex.chatwidget.AgentNotification
import com.cy.codex.chatwidget.ApprovalNoticeKind
import com.cy.codex.chatwidget.agentNotificationsAllowed
import com.cy.codex.chatwidget.postAgentNotification
import com.cy.codex.chatwidget.PluginSharesScreen
import com.cy.codex.chatwidget.PluginsScreen
import com.cy.codex.chatwidget.RealtimeScreen
import com.cy.codex.chatwidget.ReviewScreen
import com.cy.codex.chatwidget.SettingsScreen
import com.cy.codex.chatwidget.WindowsSandboxScreen
import com.cy.codex.chatwidget.WorkspacePickerScreen
import com.cy.codex.chatwidget.openSurfaceFor
import com.cy.codex.external_agent_config_migration.ExternalAgentImportScreen
import com.cy.codex.keymap.CodexKeymap
import com.cy.codex.keymap.CodexKeys
import com.cy.codex.keymap.KeyAction
import com.cy.codex.keymap.KeyContext
import com.cy.codex.keymap.LocalChatKeyFocus
import com.cy.codex.keymap.LocalShortcutsHelp
import com.cy.codex.keymap.ShortcutsHelpState
import com.cy.codex.keymap.ShortcutsOverlay
import com.cy.codex.keymap.toKeyChord
import com.cy.codex.runtime.CodexApplication
import com.cy.codex.onboarding.BedrockScreen
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.ConnectionState
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.ConfigBatchWriteParams
import com.cy.codex.protocol.protocol.v2.ConfigEdit
import com.cy.codex.protocol.protocol.v2.DiagnosticSeverity
import com.cy.codex.protocol.protocol.v2.ConfigValueWriteParams
import com.cy.codex.protocol.protocol.v2.FeedbackUploadParams
import com.cy.codex.protocol.protocol.v2.MergeStrategy
import com.cy.codex.protocol.protocol.v2.LoginAccountResponse
import com.cy.codex.protocol.protocol.v2.ThreadSessionState
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codex.status.AccountScreen
import com.cy.codex.status.RemoteControlScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Application root.
 *
 * Mirrors `codex-rs/tui/src/app.rs`: this object owns the connection, thread list and overlay
 * stack, so the widgets stay transport-agnostic.
 */
class CodexApp(
    private val scope: CoroutineScope,
    private val preferences: android.content.SharedPreferences,
    private val context: Context,
    val client: AppServerClient,
    val defaultWorkspace: String,
) {

    val threads = ThreadListState()
    val catalog = CatalogState()

    /** Where a failed request is reported; owned here so the reducer can reach it. */
    val snackbar = SnackbarHostState()

    var widget by mutableStateOf(ChatWidget(client, scope))
        private set

    /**
     * The page stack ([Surface.Chat] at the root), a `miuix-nav` back stack so the shell can hand it
     * to `NavDisplay`. A stack rather than one slot, because pages nest — a single slot made the
     * workspace picker *replace* settings.
     */
    val surfaces: NavBackStack = navBackStackOf(Surface.Chat)

    val surface: Surface get() = surfaces.lastOrNull() as? Surface ?: Surface.Chat

    var startupLoading by mutableStateOf(false)
        private set
    var startupReady by mutableStateOf(false)
        private set
    var startupError by mutableStateOf<String?>(null)
        private set

    /** A connection lost after the first successful start; kept apart from [startupError] so the transcript stays on screen. */
    var connectionLostMessage by mutableStateOf<String?>(null)
        private set
    var creatingThread by mutableStateOf(false)
        private set
    private var observersStarted = false

    private var mentionSessionId: String? = null
    private var mentionSessionStart: Job? = null
    private var mentionUpdateJob: Job? = null

    private var rateLimitNudgeShown = false

    private var recoverySubmission: List<com.cy.codex.protocol.protocol.v2.UserInput>? = null

    /** The in-flight recovery read; new input queues behind it instead of starting a turn. */
    private var rateLimitRecoveryJob: Job? = null

    var goalMenuOpen by mutableStateOf(false)

    /** Set by `/export` without a path; the chat screen launches the system save dialog. */
    var exportTranscriptRequest by mutableStateOf(false)
        private set

    /** Parent of each open side thread, keyed by thread id; the server has no "side conversation" notion. */
    private val sideThreadParents = mutableStateMapOf<String, String>()

    fun sideParentOf(threadId: String): String? = sideThreadParents[threadId]

    var copyMenuOpen by mutableStateOf(false)

    var mentionQuery by mutableStateOf<String?>(null)
        private set

    var mentionSuggestions by mutableStateOf<List<MentionSuggestion>>(emptyList())
        private set

    var rateLimitNudge by mutableStateOf<RateLimitNudge?>(null)
        private set

    /**
     * A folder waiting for a trust decision, with the action to resume when granted (mirrors
     * `tui/src/onboarding/trust_directory.rs`).
     */
    var trustRequest by mutableStateOf<TrustRequest?>(null)
        private set

    /**
     * Highest usage threshold already announced per window; keyed by label + `resetsAt` so a new
     * window starts clean (codex-rs/tui/src/chatwidget/rate_limits.rs).
     */
    private val rateLimitWarnings = mutableMapOf<String, Long>()

    init {
        widget.state.applyConfig(ThreadSessionState(threadId = "", cwd = defaultWorkspace))
        // Forks carry no specs of their own; refuse delegation via the parent map.
        widget.isSideThread = { sideThreadParents.containsKey(it) }
    }

    /**
     * Route one UI event.
     *
     * Events this object does not own go to [ChatWidget.action]; its exhaustive `when` makes an
     * unhandled event a compile error rather than a silent no-op.
     */
    fun onAppEvent(event: AppEvent) {
        when (event) {
            is AppEvent.NewThread -> createThread(event.cwd)
            is AppEvent.ResumeThread -> openThread(event.threadId)
            is AppEvent.RunShellCommand -> {
                closeAllSurfaces()
                widget.action(event)
            }
            is AppEvent.SetModel -> if (widget.state.open) widget.action(event) else {
                onAppEvent(AppEvent.WriteConfigValue("model", JsonPrimitive(event.model)))
            }
            is AppEvent.SetReasoningEffort -> if (widget.state.open) widget.action(event) else {
                onAppEvent(AppEvent.WriteConfigValue("model_reasoning_effort", JsonPrimitive(event.effort.wire)))
            }
            is AppEvent.SetApprovalPolicy -> if (widget.state.open) widget.action(event) else {
                onAppEvent(AppEvent.WriteConfigValue("approval_policy", JsonPrimitive(event.policy.wire)))
            }
            is AppEvent.SetApprovalsReviewer -> if (widget.state.open) widget.action(event) else {
                onAppEvent(AppEvent.WriteConfigValue("approvals_reviewer", JsonPrimitive(event.reviewer.wire)))
            }
            is AppEvent.SubmitUserMessage -> {
                if (!startupReady || creatingThread || widget.state.loading) return
                // Input arriving during the post-limit recovery read is held and submitted once
                // fresh numbers are in (hold_rate_limit_recovery).
                if (rateLimitRecoveryJob?.isActive == true) {
                    recoverySubmission = event.inputs
                    return
                }
                val commandText = event.inputs.singleOrNull()?.let {
                    (it as? com.cy.codex.protocol.protocol.v2.UserInput.Text)?.text?.trim()
                }
                commandText?.let { ComposerHistory.record(context, it) }
                when (val input = commandText?.let { classifySlashInput(it, ComposerCommands) }) {
                    is SlashInput.Command -> {
                        // Reject unavailable commands at submission and keep the draft; the popup
                        // still lists them.
                        val spec = SlashCommands.find(input.name)
                        val name = spec?.name ?: input.name
                        if (spec != null && widget.state.running && !spec.availableDuringTask) {
                            reportUnavailableCommand(name)
                            return
                        }
                        runSlashCommand(AppEvent.SubmitSlashCommand(name, input.args))
                        if (name != "shell") widget.state.applyDraft("")
                        return
                    }

                    is SlashInput.Unknown -> {
                        // A command-shaped token naming nothing must not become a model message;
                        // the draft stays so the typo can be fixed.
                        reportUnknownCommand(input.name)
                        return
                    }

                    else -> Unit
                }
                // `!command` runs in the session shell without starting a turn; a bare `!` is text.
                if (commandText != null && commandText.startsWith("!") && commandText.length > 1) {
                    val command = commandText.substring(1).trim()
                    if (command.isNotEmpty()) {
                        if (widget.state.open) {
                            onAppEvent(AppEvent.RunShellCommand(widget.state.threadId, command))
                        } else {
                            createThread(afterCreated = {
                                onAppEvent(AppEvent.RunShellCommand(widget.state.threadId, command))
                            })
                        }
                        return
                    }
                }
                if (catalog.account.account == null) {
                    openSurface(Surface.Account)
                } else if (!widget.state.open) {
                    createThread(inputs = event.inputs)
                } else {
                    widget.action(event)
                }
            }
            AppEvent.ReloadAccount -> load({ client.readAccount() }) { catalog.account = it }
            AppEvent.ReloadRateLimits -> load({ client.readRateLimits() }) {
                catalog.rateLimits = it
                catalog.rateLimitsUpdatedAtMs = System.currentTimeMillis()
                warnRateLimits()
            }
            AppEvent.ReloadUsage -> load({ client.readUsage() }) { catalog.usage = it; catalog.usageLoaded = true }

            AppEvent.ReloadConfig -> request { reloadConfig() }
            AppEvent.ReloadSkills -> load({ client.listSkills() }) { catalog.skills = it }
            AppEvent.ReloadPlugins -> request { reloadPlugins() }
            AppEvent.ReloadPluginShares -> load({ client.listPluginShares() }) { catalog.pluginShares = it }
            AppEvent.ReloadApps -> load({ client.listApps() }) { catalog.apps = it }
            AppEvent.ReloadHooks -> load({ client.listHooks() }) { entries ->
                catalog.hooks = entries.flatMap { it.hooks }
                catalog.hookWarnings = entries.flatMap { it.warnings }
                catalog.hookErrors = entries.flatMap { it.errors }
            }
            AppEvent.ReloadMcpServers -> load({ client.listMcpServers() }) { catalog.mcpServers = it }
            AppEvent.ReloadProjects -> load({ client.listProjects() }) { catalog.projects = it }
            AppEvent.ReloadMemories -> load({ client.readMemoryStatus() }) { catalog.memories = it }
            AppEvent.ReloadRealtimeVoices ->
                load({ client.listRealtimeVoices() }) { catalog.realtimeVoices = it }

            AppEvent.ReloadUserVerification -> load({ client.readUserVerificationStatus() }) {
                catalog.userVerification = it
            }

            AppEvent.ReloadRemoteControl -> request { reloadRemoteControl() }
            AppEvent.ReloadDiagnostics -> load({ client.readServerDiagnostics() }) { catalog.diagnostics = it }

            AppEvent.ReloadExternalAgentConfig -> {
                // Two independent reads: a detection failure must not stop the history loading.
                request {
                    client.detectExternalAgentConfig().onSuccess {
                        catalog.externalAgentConfig = it.items
                        catalog.externalAgentConnectors = it.connectors
                    }
                }
                request {
                    client.readExternalAgentImportHistories().onSuccess {
                        catalog.externalAgentImportHistories = it
                    }
                }
            }

            // Goals are per-thread; only the widget knows which thread is open.
            AppEvent.ReloadGoal -> request {
                client.getGoal(widget.state.threadId).onSuccess { widget.state.applyGoal(it) }
            }

            AppEvent.ReloadEnvironments -> Unit // nothing lists environments; see CatalogState.

            AppEvent.RefreshThreadList -> refreshThreads()

            is AppEvent.ReloadAgentThreads -> request {
                client.listThreads(
                    com.cy.codex.protocol.protocol.v2.ThreadListParams(
                        ancestorThreadId = event.ancestorThreadId,
                        sourceKinds = SUB_AGENT_SOURCE_KINDS,
                        useStateDbOnly = true,
                    ),
                ).onSuccess { catalog.agentThreads = it.threads }
            }

            is AppEvent.StopThreadTurn -> request { client.interruptTurn(event.threadId) }

            is AppEvent.SetThreadListScope -> {
                threads.includeArchived = event.includeArchived
                refreshThreads()
            }

            is AppEvent.InstallPlugin -> request {
                client.installPlugin(event.name, event.marketplace).onSuccess { response ->
                    reloadPlugins()
                    if (response.appsNeedingAuth.isNotEmpty()) {
                        catalog.pluginInstallAuth = PluginInstallAuthFlow(
                            pluginName = event.name,
                            apps = response.appsNeedingAuth,
                            authPolicy = response.authPolicy,
                        )
                    }
                }
            }

            is AppEvent.UninstallPlugin -> request {
                client.uninstallPlugin(event.pluginId).onSuccess { reloadPlugins() }
            }

            // Upsert the whole `plugins.<id>` object with one key so a concurrent edit to another
            // field is not clobbered (background_requests.rs `write_plugin_enabled`).
            is AppEvent.SetPluginEnabled -> request {
                client.writeConfigValue(
                    ConfigValueWriteParams(
                        keyPath = "plugins.${event.pluginId}",
                        value = buildJsonObject { put("enabled", JsonPrimitive(event.enabled)) },
                        mergeStrategy = MergeStrategy.Upsert,
                    ),
                ).onSuccess { reloadPlugins() }
            }

            is AppEvent.AddMarketplace -> request {
                client.addMarketplace(event.source, event.ref).onSuccess { reloadPlugins() }
            }

            is AppEvent.RemoveMarketplace -> request {
                client.removeMarketplace(event.name).onSuccess { reloadPlugins() }
            }

            is AppEvent.UpgradeMarketplace -> request {
                client.upgradeMarketplace(event.name).onSuccess {
                    catalog.upgradedMarketplaces = it
                    reloadPlugins()
                }
            }

            AppEvent.ReconcilePlugins -> request {
                client.reconcilePlugins().onSuccess {
                    catalog.reconciledPlugins = it.map { plugin -> plugin.name }
                    reloadPlugins()
                }
            }

            is AppEvent.SavePluginShare -> request {
                client.savePluginShare(event.pluginPath, event.remotePluginId)
                    .onSuccess { reloadPluginShares() }
            }

            is AppEvent.DeletePluginShare -> request {
                client.deletePluginShare(event.remotePluginId).onSuccess { reloadPluginShares() }
            }

            is AppEvent.CheckoutPluginShare -> load({
                client.checkoutPluginShare(event.remotePluginId)
            }) { checkout ->
                catalog.pluginCheckoutPath = checkout.pluginPath
                reloadPluginShares()
            }

            is AppEvent.UpdatePluginShareTargets -> request {
                client.updatePluginShareTargets(event.remotePluginId, event.discoverability, event.targets)
                    .onSuccess { reloadPluginShares() }
            }

            is AppEvent.SetSkillEnabled -> request {
                client.writeSkillConfig(event.name, event.enabled)
                    .onSuccess { client.listSkills().onSuccess { fresh -> catalog.skills = fresh } }
            }

            is AppEvent.SetSkillExtraRoots -> request {
                client.setSkillExtraRoots(event.roots)
                    .onSuccess { client.listSkills().onSuccess { fresh -> catalog.skills = fresh } }
            }

            is AppEvent.SetAppInstalled -> request {
                client.writeConfigValue(
                    ConfigValueWriteParams(
                        keyPath = "apps.${event.appId}.installed",
                        value = JsonPrimitive(event.installed),
                    ),
                ).onSuccess { client.listApps().onSuccess { fresh -> catalog.apps = fresh } }
            }

            is AppEvent.McpLogin -> request { client.mcpOauthLogin(event.serverName) }

            AppEvent.ReloadMcpConfig -> request {
                client.reloadMcpServers()
                    .onSuccess { client.listMcpServers().onSuccess { fresh -> catalog.mcpServers = fresh } }
            }

            is AppEvent.SetMcpEventStream -> request {
                if (event.streaming) {
                    client.startMcpEventStream(event.server, event.subscriptionId, event.name, event.arguments, event.threadId)
                } else {
                    client.stopMcpEventStream(event.subscriptionId)
                }
            }

            is AppEvent.Login -> {
                if (catalog.loginLoading) return
                catalog.loginLoading = true
                catalog.loginError = null
                scope.launch {
                    try {
                        client.login(event.params)
                            .onSuccess { response ->
                                catalog.pendingLogin = when (response) {
                                    is LoginAccountResponse.Chatgpt,
                                    is LoginAccountResponse.ChatgptDeviceCode -> response
                                    else -> null
                                }
                                client.readAccount().onSuccess { catalog.account = it }
                                client.listModels().onSuccess { catalog.models = it }
                            }
                            .onFailure { catalog.loginError = it.message }
                    } finally {
                        catalog.loginLoading = false
                    }
                }
            }

            is AppEvent.CancelLogin -> request {
                client.cancelLogin(event.loginId).onSuccess { catalog.pendingLogin = null }
            }

            AppEvent.Logout -> request {
                client.logout().onSuccess {
                    catalog.pendingLogin = null
                    catalog.usageLoaded = false
                    catalog.rateLimits = com.cy.codex.protocol.protocol.v2.AccountRateLimits()
                    client.readAccount().onSuccess { fresh -> catalog.account = fresh }
                }
            }

            is AppEvent.ConsumeResetCredit -> request {
                client.consumeRateLimitResetCredit(event.creditId)
                    .onSuccess { client.readRateLimits().onSuccess { fresh -> catalog.rateLimits = fresh } }
            }

            is AppEvent.SendAddCreditsNudgeEmail ->
                request { client.sendAddCreditsNudgeEmail(event.creditType) }

            AppEvent.BedrockDiscover -> request { client.bedrockDiscover() }
            is AppEvent.BedrockSetup -> request { client.bedrockSetup(event.params) }

            is AppEvent.CreateSection -> request {
                client.createSection(event.name)
                    .onSuccess { client.listSections().onSuccess { threads.sections = it } }
            }

            is AppEvent.RenameSection -> request {
                client.updateSection(event.sectionId, event.name)
                    .onSuccess { client.listSections().onSuccess { threads.sections = it } }
            }

            is AppEvent.DeleteSection -> request {
                client.deleteSection(event.sectionId)
                    .onSuccess { client.listSections().onSuccess { threads.sections = it } }
            }

            is AppEvent.CreateProject -> request {
                client.createProject(event.name, event.path)
                    .onSuccess { client.listProjects().onSuccess { fresh -> catalog.projects = fresh } }
            }

            is AppEvent.UpdateProject -> request {
                client.updateProject(event.projectId, event.name, event.path)
                    .onSuccess { client.listProjects().onSuccess { fresh -> catalog.projects = fresh } }
            }

            is AppEvent.DeleteProject -> request {
                client.deleteProject(event.projectId)
                    .onSuccess { client.listProjects().onSuccess { fresh -> catalog.projects = fresh } }
            }

            is AppEvent.MoveProject -> request {
                client.moveProject(event.projectId, event.position)
                    .onSuccess { client.listProjects().onSuccess { fresh -> catalog.projects = fresh } }
            }

            is AppEvent.ImportProject -> request {
                client.importProject(event.path)
                    .onSuccess { client.listProjects().onSuccess { fresh -> catalog.projects = fresh } }
            }

            is AppEvent.AddEnvironment -> request {
                client.addEnvironment(event.environmentId, event.execServerUrl).onSuccess {
                    catalog.environments = (catalog.environments + event.environmentId).distinct()
                }
            }

            is AppEvent.SetRemoteControlEnabled -> load(
                {
                    if (event.enabled) client.enableRemoteControl() else client.disableRemoteControl()
                },
            ) { catalog.remoteControl = it }

            AppEvent.StartRemoteControlPairing -> load({ client.startRemoteControlPairing() }) {
                catalog.remoteControlPairingCode = it.pairingCode
                catalog.remoteControlPairingClaimed = null
            }

            AppEvent.PollRemoteControlPairing -> {
                val code = catalog.remoteControlPairingCode
                if (code == null) {
                    catalog.remoteControlPairingClaimed = null
                } else {
                    load({ client.readRemoteControlPairing(pairingCode = code) }) {
                        catalog.remoteControlPairingClaimed = it.claimed
                    }
                }
            }

            is AppEvent.RevokeRemoteControlClient -> request {
                val environmentId = catalog.remoteControl?.environmentId.orEmpty()
                client.revokeRemoteControlClient(environmentId, event.clientId)
                    .onSuccess { reloadRemoteControl() }
            }

            AppEvent.EnrollUserVerification -> load({ client.enrollUserVerification() }) { enrolled ->
                catalog.userVerificationCredential = enrolled
                // Readiness is a separate endpoint; the credential exists locally before the server
                // knows about it.
                request {
                    client.readUserVerificationStatus().onSuccess { catalog.userVerification = it }
                }
            }

            is AppEvent.VerifyUserVerification -> load(
                { client.verifyUserVerification(event.params) },
            ) { /* no state to fold the proof into */ }

            // This client cannot name the transport's request id, so it sends an id the server has
            // never seen; the protocol defines that as a no-op rather than an error.
            AppEvent.CancelUserVerification -> request { client.cancelUserVerification("") }
            AppEvent.DeleteUserVerification -> request {
                client.deleteUserVerification().onSuccess {
                    client.readUserVerificationStatus().onSuccess { catalog.userVerification = it }
                }
            }

            is AppEvent.StartRealtime -> request { client.startRealtime(event.threadId, event.sdpOffer) }
            is AppEvent.StopRealtime -> request { client.stopRealtime(event.threadId) }
            is AppEvent.AppendRealtimeText -> request {
                client.appendRealtimeText(event.threadId, event.text)
            }

            is AppEvent.AppendRealtimeSpeech -> request {
                client.appendRealtimeSpeech(event.threadId, event.text)
            }

            is AppEvent.AppendRealtimeAudio -> request {
                client.appendRealtimeAudio(event.threadId, event.audio)
            }

            is AppEvent.IncrementElicitation -> request { client.incrementElicitation(event.threadId) }

            is AppEvent.DecrementElicitation -> request { client.decrementElicitation(event.threadId) }

            is AppEvent.StartReview -> request {
                client.startReview(event.threadId, event.target).onSuccess {
                    if (it.reviewThreadId.isNotBlank() && it.reviewThreadId != widget.state.threadId) {
                        openThread(it.reviewThreadId)
                    } else closeAllSurfaces()
                }
            }

            AppEvent.ResetMemory -> request {
                client.resetMemory()
                    .onSuccess { client.readMemoryStatus().onSuccess { catalog.memories = it } }
            }

            AppEvent.DetectExternalAgentConfig -> request {
                client.detectExternalAgentConfig().onSuccess {
                    catalog.externalAgentConfig = it.items
                    catalog.externalAgentConnectors = it.connectors
                }
            }

            is AppEvent.ImportExternalAgentConfig -> request {
                client.importExternalAgentConfig(event.items).onSuccess {
                    client.readExternalAgentImportHistories().onSuccess { histories ->
                        catalog.externalAgentImportHistories = histories
                    }
                }
            }

            is AppEvent.UploadFeedback -> request {
                client.uploadFeedback(
                    FeedbackUploadParams(
                        classification = event.classification,
                        reason = event.reason,
                        threadId = event.threadId,
                        includeLogs = event.includeLogs,
                    ),
                ).onSuccess { response ->
                    // The response is the report's handle (the TUI labels it "Sentry Feedback ID");
                    // show it so the user can reference what they sent.
                    val reference = response.threadId.ifBlank { response.promptHash.orEmpty() }
                    if (reference.isNotBlank()) {
                        scope.launch {
                            snackbar.showSnackbar(
                                context.getString(R.string.diagnostics_feedback_sent, reference),
                            )
                        }
                    }
                }
            }

            is AppEvent.WindowsSandboxSetupStart ->
                request { client.windowsSandboxSetupStart(event.mode, event.cwd) }

            // config writes: a re-read follows because a higher-precedence layer may shadow the key,
            // and `lastWrite` is the only proof of the merged value.
            is AppEvent.WriteConfigValue -> request {
                client.writeConfigValue(
                    ConfigValueWriteParams(
                        keyPath = event.keyPath,
                        value = event.value,
                        mergeStrategy = event.merge,
                    ),
                ).onSuccess { response ->
                    catalog.lastWrite = response
                    reloadConfig()
                }
            }

            is AppEvent.WriteConfigBatch -> request {
                client.writeConfigBatch(event.params).onSuccess { response ->
                    catalog.lastWrite = response
                    reloadConfig()
                }
            }

            is AppEvent.SetMemorySettings -> request {
                client.writeConfigBatch(
                    ConfigBatchWriteParams(
                        edits = listOf(
                            ConfigEdit("memories.use_memories", JsonPrimitive(event.useMemories), MergeStrategy.Replace),
                            ConfigEdit("memories.generate_memories", JsonPrimitive(event.generateMemories), MergeStrategy.Replace),
                        ),
                    ),
                ).onSuccess { response ->
                    catalog.lastWrite = response
                    reloadConfig()
                    // The open thread reads memory mode from `thread/memoryMode`, fixed at start;
                    // the config write alone would not change it.
                    if (widget.state.open) {
                        client.setThreadMemoryMode(
                            widget.state.threadId,
                            if (event.generateMemories) {
                                com.cy.codex.protocol.protocol.v2.ThreadMemoryMode.Enabled
                            } else {
                                com.cy.codex.protocol.protocol.v2.ThreadMemoryMode.Disabled
                            },
                        )
                    }
                }
            }

            is AppEvent.SetHookTrust -> request {
                client.writeConfigBatch(hookStateWrite(event.key, "trusted_hash", JsonPrimitive(event.currentHash)))
                    .onSuccess { response ->
                        catalog.lastWrite = response
                        reloadConfig()
                        onAppEvent(AppEvent.ReloadHooks)
                    }
            }

            is AppEvent.SetHookEnabled -> request {
                client.writeConfigBatch(hookStateWrite(event.key, "enabled", JsonPrimitive(event.enabled)))
                    .onSuccess { response ->
                        catalog.lastWrite = response
                        reloadConfig()
                        onAppEvent(AppEvent.ReloadHooks)
                    }
            }

            is AppEvent.SetExperimentalFeature -> request {
                client.setExperimentalFeature(event.id, event.enabled).onSuccess {
                    client.listExperimentalFeatures().onSuccess { catalog.experimentalFeatures = it }
                }
            }

            is AppEvent.SubmitSlashCommand -> runSlashCommand(event)

            is AppEvent.ToggleSideConversation -> toggleSideConversation(event.message)
            else -> widget.action(event)
        }
    }

    private fun runSlashCommand(event: AppEvent.SubmitSlashCommand) {
        val argument = event.args.trim()
        val threadId = widget.state.threadId
        val spec = SlashCommands.find(event.command.removePrefix("/"))
        if (spec != null && widget.state.running && !spec.availableDuringTask) {
            reportUnavailableCommand(spec.name)
            return
        }
        when (spec?.name ?: event.command.removePrefix("/")) {
            "new" -> onAppEvent(AppEvent.NewThread(widget.state.config.cwd))
            // No scrollback on a phone: clearing and starting a new conversation are the same act.
            "clear" -> onAppEvent(AppEvent.NewThread(widget.state.config.cwd))
            "resume" -> openSurface(Surface.Sessions)
            "fork" -> onAppEvent(AppEvent.ForkThread(threadId))
            "rename" -> if (argument.isBlank()) {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.slash_rename_needs_name)) }
            } else {
                onAppEvent(AppEvent.RenameThread(threadId, argument))
            }
            "archive" -> onAppEvent(AppEvent.ArchiveThread(threadId, archived = true))
            "stop" -> onAppEvent(AppEvent.InterruptTurn)
            "compact" -> onAppEvent(AppEvent.CompactThread(threadId))
            "revert" -> onAppEvent(AppEvent.RevertThread(threadId, argument.ifEmpty { null }))
            "review" -> if (widget.state.open) openSurface(Surface.Review) else {
                createThread(afterCreated = { openSurface(Surface.Review) })
            }
            "worktree" -> openSurface(Surface.Worktrees)
            "mcp" -> openSurface(Surface.McpServers)
            "skills" -> openSurface(Surface.Skills)
            "plugins" -> openSurface(Surface.Plugins)
            "hooks" -> openSurface(Surface.Hooks)
            "apps" -> openSurface(Surface.Apps)
            "settings" -> openSurface(Surface.Settings)
            "theme" -> openSurface(Surface.Settings)
            "cd" -> openSurface(Surface.WorkspacePicker)
            "import" -> openSurface(Surface.ExternalAgentImport)
            "feedback" -> openSurface(Surface.Diagnostics)
            "voice" -> openSurface(Surface.Realtime)
            "logout" -> onAppEvent(AppEvent.Logout)
            "agents", "subagents" -> openSurface(Surface.Agents)
            "shell" -> if (argument.isNotBlank()) {
                if (widget.state.open) onAppEvent(AppEvent.RunShellCommand(threadId, argument)) else {
                    createThread(afterCreated = {
                        onAppEvent(AppEvent.RunShellCommand(widget.state.threadId, argument))
                    })
                }
            }
            "usage" -> openSurface(Surface.Account)
            "status" -> openSurface(Surface.SessionStatus)
            "copy" -> copyMenuOpen = true
            "model", "approvals", "permissions" -> openSurface(Surface.Settings)
            "memories" -> openSurface(Surface.Memories)

            // `/plan` toggles: the phone has no mode-cycle binding, so one command must do both or
            // plan mode would be a one-way door.
            "plan" -> {
                if (catalog.collaborationModes.none { it.mode == CollaborationMode.Plan }) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.slash_plan_unavailable)) }
                } else {
                    val target = if (widget.state.config.collaborationMode == CollaborationMode.Plan) {
                        CollaborationMode.Default
                    } else {
                        CollaborationMode.Plan
                    }
                    if (widget.state.open) onAppEvent(AppEvent.SetCollaborationMode(target)) else {
                        createThread(afterCreated = { onAppEvent(AppEvent.SetCollaborationMode(target)) })
                    }
                }
            }

            "export" -> requestTranscriptExport(argument.trim().takeIf { it.isNotBlank() })

            // `/side` (alias `/btw`) forks an ephemeral conversation; the optional argument becomes
            // its first turn (slash_dispatch.rs).
            "side" -> onAppEvent(
                AppEvent.ToggleSideConversation(argument.trim().takeIf { it.isNotBlank() }),
            )

            // A recap is a hidden structured turn shown as a client-owned cell, never a server item.
            "recap" -> if (widget.state.open) {
                onAppEvent(AppEvent.GenerateRecap)
            } else createThread(afterCreated = { onAppEvent(AppEvent.GenerateRecap) })

            "init" -> onAppEvent(
                AppEvent.SubmitUserMessage(
                    listOf(com.cy.codex.protocol.protocol.v2.UserInput.Text(InitInstruction)),
                ),
            )

            "diff" -> openSurface(Surface.Diff)

            // `/goal` takes an argument; the picker leaves it in the draft, and anything that is not
            // a subcommand (`clear` / `edit` / `pause` / `resume`, per slash_dispatch.rs) is an objective.
            "goal" -> {
                val arg = argument.trim()
                val openGoal = { onAppEvent(AppEvent.ReloadGoal); goalMenuOpen = true }
                when (arg.lowercase()) {
                    "" -> if (widget.state.open) openGoal() else createThread(afterCreated = openGoal)

                    "edit" -> if (widget.state.open) openGoal() else createThread(afterCreated = openGoal)

                    "clear" -> if (widget.state.open) {
                        onAppEvent(AppEvent.ClearGoal)
                    } else createThread(afterCreated = { onAppEvent(AppEvent.ClearGoal) })

                    "pause" -> if (widget.state.open) {
                        onAppEvent(AppEvent.SetGoal(status = com.cy.codex.protocol.protocol.v2.GoalStatus.Paused))
                    } else createThread(afterCreated = {
                        onAppEvent(AppEvent.SetGoal(status = com.cy.codex.protocol.protocol.v2.GoalStatus.Paused))
                    })

                    "resume" -> if (widget.state.open) {
                        onAppEvent(AppEvent.SetGoal(status = com.cy.codex.protocol.protocol.v2.GoalStatus.Active))
                    } else createThread(afterCreated = {
                        onAppEvent(AppEvent.SetGoal(status = com.cy.codex.protocol.protocol.v2.GoalStatus.Active))
                    })

                    else -> if (widget.state.open) {
                        onAppEvent(AppEvent.SetGoal(objective = arg))
                    } else createThread(afterCreated = { onAppEvent(AppEvent.SetGoal(objective = arg)) })
                }
            }

            else -> reportUnknownCommand(event.command.removePrefix("/"))
        }
    }

    private fun reportUnknownCommand(name: String) {
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.runtime_unknown_slash_command, name))
        }
    }

    /** One `hooks.state.<key>` upsert, matching the table and merge strategy of `hooks_rpc.rs`. */
    private fun hookStateWrite(key: String, field: String, value: kotlinx.serialization.json.JsonElement) =
        ConfigBatchWriteParams(
            edits = listOf(
                ConfigEdit(
                    keyPath = "hooks.state",
                    value = buildJsonObject { put(key, buildJsonObject { put(field, value) }) },
                    mergeStrategy = MergeStrategy.Upsert,
                ),
            ),
            reloadUserConfig = true,
        )

    /**
     * Announce each rate-limit threshold once per window (chatwidget/rate_limits.rs: 50/75/90/95
     * percent, suppressed while workspace credits cover the window).
     */
    private fun warnRateLimits() {
        val snapshot = catalog.rateLimits.rateLimits
        if (snapshot.credits?.unlimited == true || snapshot.credits?.hasCredits == true) return
        val windows = listOfNotNull(
            snapshot.primary?.let { it to context.getString(R.string.status_card_rate_primary) },
            snapshot.secondary?.let { it to context.getString(R.string.status_card_rate_secondary) },
        )
        rateLimitWarnings.keys.retainAll(windows.mapTo(mutableSetOf()) { (window, label) -> "$label:${window.resetsAt ?: 0L}" })
        for ((window, label) in windows) {
            val key = "$label:${window.resetsAt ?: 0L}"
            val threshold = UsageWarningThresholds.lastOrNull { window.usedPercent >= it } ?: continue
            if (threshold <= (rateLimitWarnings[key] ?: Long.MIN_VALUE)) continue
            rateLimitWarnings[key] = threshold
            widget.state.addDiagnostic(
                if (window.usedPercent >= 100) {
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Error,
                        code = DiagnosticCode.RateLimitReached,
                        args = listOf(label),
                    )
                } else {
                    SessionDiagnostic(
                        severity = DiagnosticSeverity.Warning,
                        code = DiagnosticCode.RateLimitWarning,
                        args = listOf((100 - window.usedPercent).coerceAtLeast(0).toString(), label),
                    )
                },
            )
        }
    }

    /** Notify only when the app is not in front, the TUI's `NotificationCondition::Unfocused` gate. */
    private fun postNotice(notice: AgentNotice) {
        if ((context.applicationContext as? CodexApplication)?.inForeground == true) return
        when (notice) {
            is AgentNotice.TurnComplete -> postAgentNotification(
                context,
                AgentNotification.TurnComplete,
                notice.preview ?: context.getString(R.string.notification_turn_complete),
            )

            is AgentNotice.Approval -> postAgentNotification(
                context,
                AgentNotification.ApprovalRequested,
                approvalNoticeBody(context, notice),
            )
        }
    }

    /**
     * Poll rate limits, faster as they fill (chatwidget/rate_limits.rs: 99% → 5 s, 90% → 15 s,
     * 75% → 30 s, else 60 s). Re-reading the interval each round lets a drained window slow down.
     */
    private suspend fun pollRateLimits() {
        while (true) {
            delay(rateLimitRefreshIntervalMs())
            if (!startupReady || catalog.account.account == null) continue
            client.readRateLimits().onSuccess { fresh ->
                catalog.rateLimits = fresh
                catalog.rateLimitsUpdatedAtMs = System.currentTimeMillis()
                warnRateLimits()
            }
        }
    }

    private fun rateLimitRefreshIntervalMs(): Long {
        val snapshot = catalog.rateLimits.rateLimits
        val used = maxOf(
            snapshot.primary?.usedPercent ?: 0L,
            snapshot.secondary?.usedPercent ?: 0L,
        )
        return when {
            used >= 99 -> 5_000L
            used >= 90 -> 15_000L
            used >= 75 -> 30_000L
            else -> 60_000L
        }
    }

    /**
     * Offer the low-cost model once per process, near the limit, without workspace credits, and
     * never when it is already selected (maybe_show_pending_rate_limit_prompt).
     */
    private fun maybeShowRateLimitNudge() {
        if (rateLimitNudge != null || rateLimitNudgeShown) return
        if (catalog.config.snapshot.hideRateLimitModelNudge == true) return
        val snapshot = catalog.rateLimits.rateLimits
        if (snapshot.credits?.hasCredits == true) return
        val used = snapshot.primary?.usedPercent ?: snapshot.secondary?.usedPercent ?: 0L
        if (used < RateLimitNudgeThresholdPercent) return
        val preset = catalog.models.firstOrNull { it.model == RateLimitNudgeModel } ?: return
        if (widget.state.config.model == RateLimitNudgeModel) return
        rateLimitNudgeShown = true
        rateLimitNudge = RateLimitNudge(preset.model, preset.displayName)
    }

    fun switchToRateLimitModel() {
        val nudge = rateLimitNudge ?: return
        rateLimitNudge = null
        onAppEvent(AppEvent.SetModel(nudge.model))
    }

    /** Keep the current model for now; the prompt may return in a later launch. */
    fun dismissRateLimitNudge() {
        rateLimitNudge = null
    }

    /** Keep the current model and persist `notices.hide_rate_limit_model_nudge`. */
    fun hideRateLimitNudgeForever() {
        rateLimitNudge = null
        onAppEvent(
            AppEvent.WriteConfigValue("notices.hide_rate_limit_model_nudge", JsonPrimitive(true)),
        )
    }

    /** After a usage-limit failure, hold input until the limits read lands; a failed read still releases it (hold_rate_limit_recovery). */
    private fun beginRateLimitRecovery() {
        if (rateLimitRecoveryJob?.isActive == true) return
        rateLimitRecoveryJob = scope.launch {
            client.readRateLimits().onSuccess { fresh ->
                catalog.rateLimits = fresh
                catalog.rateLimitsUpdatedAtMs = System.currentTimeMillis()
                warnRateLimits()
            }
            delay(RateLimitRecoveryDelayMs)
            val held = recoverySubmission
            recoverySubmission = null
            if (held != null && widget.state.open) widget.action(AppEvent.SubmitUserMessage(held))
        }
    }

    private fun reportUnavailableCommand(name: String) {
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.slash_unavailable_during_task, name))
        }
    }

    private fun request(block: suspend () -> Result<*>) {
        request(block, then = {})
    }

    private fun <T> request(block: suspend () -> Result<T>, then: suspend (T) -> Unit) {
        scope.launch {
            block()
                .onSuccess { then(it) }
                .onFailure { error ->
                    snackbar.showSnackbar(
                        error.message ?: context.getString(R.string.shell_request_failed),
                    )
                }
        }
    }

    private fun <T> load(block: suspend () -> Result<T>, into: suspend (T) -> Unit) {
        request(block, into)
    }

    private fun refreshThreads() {
        val archived = threads.includeArchived
        request {
            client.listThreads(com.cy.codex.protocol.protocol.v2.ThreadListParams(archived = archived)).onSuccess {
                if (threads.includeArchived == archived) threads.applyListing(it)
            }
        }
    }

    private suspend fun reloadConfig(): Result<*> {
        return client.readConfig().onSuccess {
            catalog.config = it
            if (!widget.state.open) {
                val config = it.snapshot
                val model = config.model ?: catalog.models.firstOrNull { model -> model.isDefault }?.model.orEmpty()
                widget.state.applyConfig(widget.state.config.copy(
                    model = model,
                    modelDisplayName = catalog.modelPreset(model)?.displayName ?: model,
                    reasoningEffort = config.modelReasoningEffort
                        ?: catalog.modelPreset(model)?.defaultReasoningEffort
                        ?: widget.state.config.reasoningEffort,
                    approvalPolicy = config.approvalPolicy ?: widget.state.config.approvalPolicy,
                    approvalsReviewer = config.approvalsReviewer ?: widget.state.config.approvalsReviewer,
                ))
            }
        }
    }

    /** Re-read the plugin catalog; `plugin/list` is the only call, so the response is split into marketplaces and plugins here. */
    private suspend fun reloadPlugins(): Result<*> =
        client.listPlugins().onSuccess { response ->
            catalog.marketplaces = response.marketplaces
            catalog.plugins = response.marketplaces.flatMap { it.plugins }
        }

    private suspend fun reloadPluginShares(): Result<*> =
        client.listPluginShares().onSuccess { catalog.pluginShares = it }

    private suspend fun reloadRemoteControl(): Result<*> {
        val status = client.readRemoteControlStatus().onSuccess { catalog.remoteControl = it }
        // The paired-device list is addressed by environment; a disabled link has none, so asking
        // with a blank id would only be rejected.
        val environmentId = catalog.remoteControl?.environmentId ?: return status
        return client.listRemoteControlClients(environmentId).onSuccess {
            catalog.remoteControlClients = it.data
        }
    }

    /** Catalog-level notifications; a second collector on the same `SharedFlow` the widget reads. */
    private suspend fun observeCatalogs() {
        client.events.collect { event ->
            when (event) {
                is AppServerEvent.ThreadStartedEvent -> {
                    threads.threads = listOf(event.thread) + threads.threads.filterNot { it.id == event.threadId }
                    if (catalog.agentThreads.any { it.id == event.threadId }) {
                        catalog.agentThreads = catalog.agentThreads.map {
                            if (it.id == event.threadId) event.thread else it
                        }
                    }
                }
                is AppServerEvent.ThreadNameUpdatedEvent -> {
                    threads.threads = threads.threads.map {
                        if (it.id == event.threadId) it.copy(name = event.delta.name) else it
                    }
                    catalog.agentThreads = catalog.agentThreads.map {
                        if (it.id == event.threadId) it.copy(name = event.delta.name) else it
                    }
                }
                is AppServerEvent.ThreadStatusChangedEvent -> {
                    threads.threads = threads.threads.map {
                        if (it.id == event.threadId) it.copy(status = event.delta.status) else it
                    }
                    catalog.agentThreads = catalog.agentThreads.map {
                        if (it.id == event.threadId) it.copy(status = event.delta.status) else it
                    }
                }

                is AppServerEvent.ThreadTokenUsageEvent ->
                    catalog.threadUsage = catalog.threadUsage + (event.threadId to event.delta.usage)

                is AppServerEvent.ThreadArchived -> {
                    threads.markArchived(event.threadId, true)
                    catalog.agentThreads = catalog.agentThreads.filterNot { it.id == event.threadId }
                    client.listThreads(com.cy.codex.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.ThreadUnarchived -> {
                    threads.markArchived(event.threadId, false)
                    catalog.agentThreads = catalog.agentThreads.filterNot { it.id == event.threadId }
                    client.listThreads(com.cy.codex.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.ThreadDeleted -> {
                    catalog.agentThreads = catalog.agentThreads.filterNot { it.id == event.threadId }
                    client.listThreads(com.cy.codex.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.FuzzySearchUpdated -> {
                    catalog.mentionFiles = event.delta.files
                    catalog.mentionSearching = false
                    refreshMentionSuggestions()
                }
                is AppServerEvent.FuzzySearchCompleted -> catalog.mentionSearching = false

                // A finished turn is when the rate-limit prompt is checked; a limit-naming failure
                // starts the hold-and-refresh recovery pair.
                is AppServerEvent.TurnCompleted -> {
                    if (event.status == TurnStatus.Failed &&
                        event.error?.contains("limit", ignoreCase = true) == true
                    ) {
                        beginRateLimitRecovery()
                    }
                    maybeShowRateLimitNudge()
                }

                is AppServerEvent.AccountUpdated -> catalog.account = event.account
                is AppServerEvent.RateLimitsUpdatedEvent -> {
                    catalog.rateLimits = catalog.rateLimits.copy(rateLimits = catalog.rateLimits.rateLimits.mergedWith(event.rateLimits))
                    catalog.rateLimitsUpdatedAtMs = System.currentTimeMillis()
                    warnRateLimits()
                }
                is AppServerEvent.AccountLoginCompleted -> {
                    catalog.pendingLogin = null
                    catalog.loginError = event.delta.error
                    client.readAccount().onSuccess { catalog.account = it }
                    client.listModels().onSuccess { catalog.models = it }
                }

                is AppServerEvent.SkillsChanged ->
                    client.listSkills().onSuccess { catalog.skills = it }

                is AppServerEvent.AppListUpdated ->
                    client.listApps().onSuccess { catalog.apps = it }

                is AppServerEvent.McpStartupStatusEvent -> {
                    val delta = event.delta
                    catalog.mcpStartup = when (delta.status) {
                        com.cy.codex.protocol.protocol.v2.McpServerStartupState.Ready,
                        com.cy.codex.protocol.protocol.v2.McpServerStartupState.Cancelled,
                        -> catalog.mcpStartup - delta.serverName

                        com.cy.codex.protocol.protocol.v2.McpServerStartupState.Starting,
                        com.cy.codex.protocol.protocol.v2.McpServerStartupState.Failed,
                        -> catalog.mcpStartup + (delta.serverName to delta)
                    }
                    client.listMcpServers().onSuccess { catalog.mcpServers = it }
                }

                is AppServerEvent.McpOauthLoginCompleted ->
                    client.listMcpServers().onSuccess { catalog.mcpServers = it }

                // The bridge skipped notifications; settle startup rows like
                // `finish_mcp_startup_after_lag`: a dropped Ready/Cancelled would linger, a Failed
                // row is kept.
                is AppServerEvent.TransportLagged -> {
                    catalog.mcpStartup = catalog.mcpStartup.filterValues {
                        it.status == com.cy.codex.protocol.protocol.v2.McpServerStartupState.Failed
                    }
                    refreshThreads()
                }

                is AppServerEvent.ConfigWarningEvent -> reloadConfig()

                is AppServerEvent.ProjectChanged ->
                    client.listProjects().onSuccess { catalog.projects = it }

                is AppServerEvent.RemoteControlStatusChanged -> {
                    catalog.remoteControl = event.delta.status
                    reloadRemoteControl()
                }

                // Attaching is the only way this client learns an environment id; no call lists them.
                is AppServerEvent.EnvironmentConnected -> catalog.environments =
                    (catalog.environments + event.environmentId).distinct()

                is AppServerEvent.EnvironmentDisconnected -> Unit

                is AppServerEvent.WindowsSandboxSetupCompleted ->
                    client.windowsSandboxReadiness().onSuccess { catalog.windowsSandboxReadiness = it.status }

                is AppServerEvent.ExternalAgentImportProgress -> catalog.externalAgentImport =
                    externalImportProgress(event.results)

                is AppServerEvent.ExternalAgentImportCompleted -> {
                    catalog.externalAgentImport = null
                    client.readExternalAgentImportHistories().onSuccess {
                        catalog.externalAgentImportHistories = it
                    }
                }

                else -> Unit
            }
        }
    }

    fun openSurface(next: Surface) {
        // `miuix-nav` rejects duplicate routes, so "open" pops back to the existing entry.
        val at = surfaces.indexOf(next)
        if (at >= 0) {
            while (surfaces.size > at + 1) surfaces.removeAt(surfaces.lastIndex)
            return
        }
        surfaces.add(next)
        when (next) {
            Surface.Account -> {
                onAppEvent(AppEvent.ReloadAccount)
                if (catalog.account.account != null) {
                    onAppEvent(AppEvent.ReloadRateLimits)
                    onAppEvent(AppEvent.ReloadUsage)
                }
            }
            Surface.Hooks -> onAppEvent(AppEvent.ReloadHooks)
            Surface.McpServers -> onAppEvent(AppEvent.ReloadMcpServers)
            Surface.Skills -> onAppEvent(AppEvent.ReloadSkills)
            Surface.Projects -> onAppEvent(AppEvent.ReloadProjects)
            Surface.Plugins -> onAppEvent(AppEvent.ReloadPlugins)
            Surface.Apps -> onAppEvent(AppEvent.ReloadApps)
            Surface.Memories -> onAppEvent(AppEvent.ReloadMemories)
            Surface.Settings -> {
                onAppEvent(AppEvent.ReloadConfig)
                load({ client.listExperimentalFeatures() }) { catalog.experimentalFeatures = it }
            }
            Surface.Sessions -> {
                onAppEvent(AppEvent.RefreshThreadList)
                load({ client.listSections() }) { threads.sections = it }
            }
            else -> Unit
        }
    }

    /** Pop one page; the chat root is never popped. */
    fun closeSurface() {
        if (surfaces.size > 1) surfaces.removeAt(surfaces.lastIndex)
    }

    /**
     * Drive the `@` popup: server-scored files via `fuzzyFileSearch`, plugins and tasks folded in
     * locally (mentions_v2/search_catalog.rs), debounce matching task_mentions.rs.
     */
    fun onMentionQueryChange(query: String?) {
        mentionQuery = query
        if (query == null) {
            stopMentionSearch()
            return
        }
        val roots = listOf(widget.state.config.cwd.ifBlank { defaultWorkspace })
        if (mentionSessionId == null) {
            val id = "mentions-${java.util.UUID.randomUUID()}"
            mentionSessionId = id
            mentionSessionStart = scope.launch { client.startFuzzySearchSession(id, roots) }
        }
        mentionUpdateJob?.cancel()
        mentionUpdateJob = scope.launch {
            // The start is its own request; an update must not overtake it.
            mentionSessionStart?.join()
            delay(MentionSearchDebounceMs)
            val id = mentionSessionId ?: return@launch
            catalog.mentionSearching = true
            client.updateFuzzySearchSession(id, query)
        }
        refreshMentionSuggestions()
    }

    private fun stopMentionSearch() {
        mentionUpdateJob?.cancel()
        mentionUpdateJob = null
        catalog.mentionSearching = false
        catalog.mentionFiles = emptyList()
        mentionSuggestions = emptyList()
        val id = mentionSessionId ?: return
        mentionSessionId = null
        scope.launch { client.stopFuzzySearchSession(id) }
    }

    private fun refreshMentionSuggestions() {
        val query = mentionQuery
        if (query == null) {
            mentionSuggestions = emptyList()
            return
        }
        val plugins = catalog.plugins
            .filter { it.installed && it.enabled }
            .filter { mentionMatches(query, it.name, it.description) }
            .take(MentionPluginLimit)
            .map { plugin ->
                MentionSuggestion(
                    insert = plugin.name,
                    label = plugin.name,
                    detail = plugin.marketplace.ifBlank { null },
                    kind = MentionKind.Plugin,
                )
            }
        val currentThread = widget.state.threadId
        val tasks = threads.threads
            .filter { it.id != currentThread && !it.ephemeral }
            .filter { mentionMatches(query, it.name.orEmpty(), it.preview, it.cwd) }
            .take(MentionTaskLimit)
            .map { thread ->
                val title = thread.name?.takeIf { it.isNotBlank() }
                    ?: thread.preview.lineSequence().firstOrNull().orEmpty()
                MentionSuggestion(
                    insert = title.take(MentionTitleLimit),
                    label = title.take(MentionTitleLimit),
                    detail = thread.cwd,
                    kind = MentionKind.Task,
                )
            }
        val files = catalog.mentionFiles.map { file ->
            MentionSuggestion(
                insert = file.path,
                label = file.path,
                detail = null,
                kind = if (file.matchType == "directory") MentionKind.Directory else MentionKind.File,
            )
        }
        mentionSuggestions = (plugins + tasks + files).take(MentionSuggestionLimit)
    }

    /** Drop every pushed page; one snapshot so the runtime animates a single sweep, not a slide per page. */
    fun closeAllSurfaces() {
        while (surfaces.size > 1) surfaces.removeAt(surfaces.lastIndex)
    }

    fun openThread(threadId: String) {
        openThread(threadId, onFailure = {})
    }

    private fun openThread(threadId: String, onFailure: () -> Unit) {
        // Switching away discards an ephemeral side fork, like `app/side.rs`.
        val currentThread = widget.state.threadId
        if (currentThread != threadId && sideThreadParents.containsKey(currentThread)) {
            closeSideConversation(currentThread)
        }
        // Ask before resuming into an untrusted folder; the read would only fail later.
        val knownCwd = (threads.threads + catalog.agentThreads).firstOrNull { it.id == threadId }?.cwd
        if (!knownCwd.isNullOrBlank() && !isProjectTrusted(knownCwd)) {
            trustRequest = TrustRequest(knownCwd) { openThread(threadId, onFailure) }
            return
        }
        widget.open(threadId) { result ->
            result.onSuccess { response ->
                preferences.edit().putString(KeySelectedSession, threadId).apply()
                threads.threads = listOf(response.thread) + threads.threads.filterNot { it.id == threadId }
            }.onFailure { onFailure() }
        }
        closeAllSurfaces()
    }

    /**
     * Start a side conversation or return to its parent; mirrors `app/side.rs` (ephemeral fork,
     * boundary prompt injected as raw history).
     */
    private fun toggleSideConversation(message: String?) {
        val current = widget.state.threadId
        val parent = sideThreadParents[current]
        if (parent != null) {
            closeSideConversation(current)
            openThread(parent)
            return
        }
        if (!widget.state.open || current.isBlank()) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.side_conversation_unavailable)) }
            return
        }
        scope.launch {
            val config = widget.state.config
            val user = if (message.isNullOrBlank()) emptyList() else {
                listOf(com.cy.codex.protocol.protocol.v2.UserInput.Text(message))
            }
            client.forkThread(
                com.cy.codex.protocol.protocol.v2.ThreadForkParams(
                    threadId = current,
                    model = config.model.takeIf { it.isNotBlank() },
                    modelProvider = config.modelProviderId,
                    cwd = config.cwd,
                    approvalPolicy = config.approvalPolicy,
                    approvalsReviewer = config.approvalsReviewer,
                    sandbox = config.sandboxPolicy,
                    serviceTier = config.serviceTier,
                    developerInstructions = com.cy.codex.chatwidget.SideDeveloperInstructions,
                    ephemeral = true,
                ),
            ).onSuccess { child ->
                sideThreadParents[child.threadId] = current
                widget.bind(child)
                preferences.edit().putString(KeySelectedSession, child.threadId).apply()
                client.injectThreadItems(
                    child.threadId,
                    listOf(com.cy.codex.chatwidget.sideBoundaryPromptItem()),
                )
                if (user.isNotEmpty()) widget.action(AppEvent.SubmitUserMessage(user))
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.side_conversation_start_failed))
            }
        }
    }

    fun requestTranscriptExport(path: String?) {
        if (path.isNullOrBlank()) {
            exportTranscriptRequest = true
        } else {
            exportTranscriptToFile(path)
        }
    }

    fun consumeTranscriptExportRequest() {
        exportTranscriptRequest = false
    }

    fun transcriptExportFileName(): String = "codex-session-${widget.state.threadId}.md"

    private fun exportTranscriptToFile(requested: String) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val markdown = com.cy.codex.app.transcriptMarkdown(widget.state.items)
                        ?: error(context.getString(R.string.transcript_export_empty))
                    val cwd = widget.state.config.cwd.ifBlank { defaultWorkspace }
                    val raw = java.io.File(requested)
                    val target = if (raw.isAbsolute) raw else java.io.File(cwd, requested)
                    target.parentFile?.mkdirs()
                    // `persist_noclobber` upstream: never overwrite an existing file.
                    if (!target.createNewFile()) {
                        error(context.getString(R.string.transcript_export_exists, target.path))
                    }
                    target.writeText(markdown)
                    target.absolutePath
                }
            }
            result.onSuccess { path ->
                snackbar.showSnackbar(context.getString(R.string.transcript_export_saved, path))
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.transcript_export_failed))
            }
        }
    }

    fun exportTranscriptTo(uri: Uri) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val markdown = com.cy.codex.app.transcriptMarkdown(widget.state.items)
                        ?: error(context.getString(R.string.transcript_export_empty))
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error(context.getString(R.string.transcript_export_failed))
                    stream.use { it.write(markdown.toByteArray(Charsets.UTF_8)) }
                    uri.toString()
                }
            }
            result.onSuccess {
                snackbar.showSnackbar(context.getString(R.string.transcript_export_saved, it))
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.transcript_export_failed))
            }
        }
    }

    private fun closeSideConversation(sideThreadId: String) {
        sideThreadParents.remove(sideThreadId)
        scope.launch {
            client.interruptTurn(sideThreadId)
            client.unsubscribeThread(sideThreadId)
        }
    }

    /** A path is trusted when it is a `[projects]` entry or below one, per `resolve_root_git_project_for_trust` minus the git-root lookup. */
    private fun isProjectTrusted(path: String): Boolean {
        val normalized = path.replace('\\', '/').trimEnd('/')
        return catalog.config.snapshot.trustedProjects.any { key ->
            val base = key.replace('\\', '/').trimEnd('/')
            base.isNotEmpty() && (normalized == base || normalized.startsWith("$base/"))
        }
    }

    fun grantTrust() {
        val request = trustRequest ?: return
        trustRequest = null
        // `projects."<path>"` is a quoted TOML key; escape backslashes and quotes like trusted_project_edit.
        val key = request.path.replace("\\", "\\\\").replace("\"", "\\\"")
        scope.launch {
            client.writeConfigBatch(
                ConfigBatchWriteParams(
                    edits = listOf(
                        ConfigEdit(
                            keyPath = "projects.\"$key\".trust_level",
                            value = JsonPrimitive("trusted"),
                            mergeStrategy = MergeStrategy.Replace,
                        ),
                    ),
                    reloadUserConfig = true,
                ),
            ).onSuccess {
                reloadConfig()
                request.onTrust()
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.shell_request_failed))
            }
        }
    }

    fun dismissTrust() {
        trustRequest = null
    }

    fun importAttachment(uri: Uri) {
        val cwd = widget.state.config.cwd.ifBlank { defaultWorkspace }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                        ?: "attachment"
                    val folder = java.io.File(cwd, ".codex-attachments").apply { mkdirs() }
                    val name = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val target = java.io.File(folder, "${System.currentTimeMillis()}-$name")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error(context.getString(R.string.runtime_attachment_failed))
                    input.use { source -> target.outputStream().use { source.copyTo(it) } }
                    Triple(target.absolutePath, displayName, isImageAttachment(context.contentResolver.getType(uri), target.name))
                }
            }.onSuccess { (path, displayName, isImage) ->
                if (isImage) {
                    val file = java.io.File(path)
                    // Over-ceiling files can never be sent at submission; reject at import instead.
                    if (file.length() > MaxComposerImageBytes) {
                        file.delete()
                        snackbar.showSnackbar(
                            context.getString(R.string.chatwidget_diagnostic_image_too_large, displayName),
                        )
                        return@onSuccess
                    }
                    val placeholder = widget.state.addComposerImage(path)
                    val draft = widget.state.composerDraft
                    val gap = if (draft.isBlank() || draft.last().isWhitespace()) "" else " "
                    widget.state.applyDraft(draft + gap + "$placeholder ")
                } else {
                    val draft = widget.state.composerDraft
                    widget.state.applyDraft(draft + (if (draft.isBlank()) "" else "\n") + "@$path ")
                }
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.runtime_attachment_failed))
            }
        }
    }

    private fun createThread(
        cwd: String? = null,
        inputs: List<com.cy.codex.protocol.protocol.v2.UserInput>? = null,
        afterCreated: (() -> Unit)? = null,
    ) {
        if (!startupReady || creatingThread) return
        val targetCwd = cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace
        if (!isProjectTrusted(targetCwd)) {
            trustRequest = TrustRequest(targetCwd) { createThread(cwd, inputs, afterCreated) }
            return
        }
        creatingThread = true
        scope.launch {
            try {
                client.startThread(
                    com.cy.codex.protocol.protocol.v2.ThreadStartParams(
                        cwd = targetCwd,
                        dynamicTools = DynamicTools.specs(),
                    ),
                )
                    .onSuccess { session ->
                        widget.bind(session)
                        preferences.edit().putString(KeySelectedSession, session.threadId).apply()
                        closeAllSurfaces()
                        if (inputs != null) widget.action(AppEvent.SubmitUserMessage(inputs))
                        afterCreated?.invoke()
                        client.listThreads(com.cy.codex.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived)).onSuccess { threads.applyListing(it) }
                    }
                    .onFailure {
                        snackbar.showSnackbar(it.message ?: context.getString(R.string.shell_request_failed))
                    }
            } finally {
                creatingThread = false
            }
        }
    }

    fun reconnect() {
        if (startupLoading || !startupReady) return
        startupReady = false
        connectionLostMessage = null
        bootstrap()
    }

    fun bootstrap() {
        if (startupLoading || startupReady) return
        startupLoading = true
        startupError = null
        if (!observersStarted) {
            observersStarted = true
            widget.attach()
            scope.launch(start = CoroutineStart.UNDISPATCHED) { observeCatalogs() }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                widget.notices.collect { notice -> postNotice(notice) }
            }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                // No terminal focus on Android: backgrounding drives the recap's idle clock.
                (context.applicationContext as? CodexApplication)?.foreground?.collect { inForeground ->
                    widget.noteForegroundChanged(inForeground)
                }
            }
            scope.launch { pollRateLimits() }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                client.connection.collect { connection ->
                    when (connection) {
                        is ConnectionState.Failed -> {
                            if (startupReady) {
                                connectionLostMessage = connection.message
                            } else {
                                startupError = connection.message
                            }
                            widget.connectionLost()
                        }
                        ConnectionState.Disconnected -> if (startupReady) {
                            connectionLostMessage = context.getString(R.string.runtime_disconnected)
                            widget.connectionLost()
                        }
                        else -> Unit
                    }
                }
            }
        }
        scope.launch {
            try {
                client.initialize(
                com.cy.codex.protocol.protocol.v2.ClientInfo(
                    name = "codex-android",
                    title = "Codex",
                    version = BuildConfig.VERSION_NAME,
                ),
                ).getOrThrow()
                threads.applyListing(client.listThreads().getOrThrow())
                catalog.account = client.readAccount().getOrThrow()
                client.listModels().onSuccess { catalog.models = it }
                client.listCollaborationModes().onSuccess { catalog.collaborationModes = it }
                client.readConfigRequirements().onSuccess {
                    catalog.allowedApprovalsReviewers = it.allowedApprovalsReviewers
                }
                reloadConfig()
                check(client.connection.first() == ConnectionState.Ready) {
                    context.getString(R.string.runtime_disconnected)
                }
                startupReady = true
                connectionLostMessage = null
                val stored = preferences.getString(KeySelectedSession, null)?.takeIf { it.isNotBlank() }
                // Threads without a user-message preview can be persisted but omitted by thread/list.
                val fallback = threads.threads.firstOrNull { it.id != stored }?.id
                val candidates = listOfNotNull(stored, fallback)
                fun restore(index: Int) {
                    val threadId = candidates.getOrNull(index)
                    if (threadId == null) {
                        preferences.edit().remove(KeySelectedSession).apply()
                        widget.clear()
                    } else {
                        openThread(threadId) { restore(index + 1) }
                    }
                }
                restore(0)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                startupError = error.message ?: context.getString(R.string.shell_request_failed)
            } finally {
                startupLoading = false
            }
        }
    }

    companion object {
        /** What `/init` submits; fixed, because only the agent can see the project. */
        const val InitInstruction =
            "Create an AGENTS.md file with instructions for future Codex sessions working in this " +
                "repository. Describe the layout, the build and test commands, and the conventions " +
                "the code follows."

        const val KeySelectedSession = "session_selected"
        const val KeyExpandedProjects = "projects_expanded"
        const val KeyProjectsCollapsed = "projects_collapsed"

        /** The recognized commands; derived from [SlashCommands.All] so popup and dispatch cannot drift apart. */
        val ComposerCommands: Set<String> = SlashCommands.Known

        /** Usage percentages that earn a warning, ascending; the TUI's ladder. */
        val UsageWarningThresholds = listOf(50L, 75L, 90L, 95L)
    }
}

/** The low-cost model the rate-limit prompt offers (NUDGE_MODEL_SLUG). */
private const val RateLimitNudgeModel = "gpt-5.6-luna"

/** Used share of the codex window at which the prompt may appear. */
private const val RateLimitNudgeThresholdPercent = 90L

/** How long held input waits after a usage-limit failure before it is submitted anyway. */
private const val RateLimitRecoveryDelayMs = 2_000L

/** Debounce for `fuzzyFileSearch/sessionUpdate`, matching `task_mentions.rs`'s delay. */
private const val MentionSearchDebounceMs = 100L

/** How many popup rows one query may produce, across every source. */
private const val MentionSuggestionLimit = 32
private const val MentionPluginLimit = 8
private const val MentionTaskLimit = 8

/** Task titles are capped the way `MAX_TASK_TITLE_CHARS` caps them upstream. */
private const val MentionTitleLimit = 80

data class RateLimitNudge(val model: String, val displayName: String)

/** A folder awaiting a trust decision plus the action to resume; a callback, only the call site knows what was interrupted. */
class TrustRequest(val path: String, val onTrust: () -> Unit)

/** Approval notification wording (chatwidget/notifications.rs). */
private fun approvalNoticeBody(context: Context, notice: AgentNotice.Approval): String =
    when (notice.kind) {
        ApprovalNoticeKind.Command -> context.getString(
            R.string.notification_approval_command,
            notice.detail.orEmpty().take(80),
        )

        ApprovalNoticeKind.FileChange -> notice.detail?.let {
            context.getString(R.string.notification_approval_file_change, it)
        } ?: context.getString(R.string.notification_approval_file_change_many)

        ApprovalNoticeKind.Elicitation -> context.getString(
            R.string.notification_approval_elicitation,
            notice.detail.orEmpty(),
        )

        ApprovalNoticeKind.Other -> context.getString(R.string.notification_approval_other)
    }

/** Composition entry point: the holder outlives configuration changes; bootstrap starts once. */
@Composable
fun rememberCodexApp(): CodexApp {
    val context = LocalContext.current
    val app = (context.applicationContext as CodexApplication).app
    LaunchedEffect(app) { app.bootstrap() }
    return app
}

@Composable
fun CodexRoot() {
    val app = rememberCodexApp()
    CodexScreen(app = app)
}

/**
 * The shell: the chat mounts always, every other screen pushes over it
 * (codex-rs/tui/src/app.rs, `NavTransitions.Modal`); sidebar view state persists like `local_settings.rs`.
 */
@Composable
fun CodexScreen(
    app: CodexApp,
    snackbarGap: Dp = 8.dp,
) {
    val context = LocalContext.current
    val runtime = context.applicationContext as CodexApplication
    val preferences = remember { context.getSharedPreferences("codex_ui", android.content.Context.MODE_PRIVATE) }
    val colors = MiuixTheme.colorScheme
    val shortcutsHelp = remember { ShortcutsHelpState() }

    var sidebarExpanded by remember { mutableStateOf(false) }
    var projectsCollapsed by remember {
        mutableStateOf(preferences.getBoolean(CodexApp.KeyProjectsCollapsed, false))
    }
    var expandedProjects by remember {
        mutableStateOf(preferences.getStringSet(CodexApp.KeyExpandedProjects, null) ?: emptySet())
    }

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = {
            SnackbarHost(
                state = app.snackbar,
                modifier = Modifier
                    .padding(bottom = UiConsts.ScreenMargin + UiConsts.PromptBarHeight + snackbarGap),
            )
        },
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // The status bar is normally hidden; a transient reveal must not push the chrome, so the
            // inset is latched and may only ever shrink.
            val liveTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            var topInset by remember { mutableStateOf(liveTopInset) }
            LaunchedEffect(liveTopInset) {
                if (liveTopInset < topInset) topInset = liveTopInset
            }
            // Edge-to-edge: the system does not resize for the keyboard; the chat entry consumes the
            // IME inset itself (see `imePadding` below).
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            NavDisplay(
                backStack = app.surfaces,
                modifier = Modifier.fillMaxSize(),
                onBack = app::closeSurface,
                transition = NavTransitions.Modal,
                effects = NavDisplayEffects(
                    cornerClipRadius = UiConsts.DrawerCorner,
                    cornerClipMode = NavCornerClipMode.All,
                    dimAmount = UiConsts.ScrimAlpha,
                ),
            ) {
                entry<Surface.Chat>(swipeDismiss = NavSwipeDirection.None) {
                    val chatKeys = remember { FocusRequester() }
                    var chatHasFocus by remember { mutableStateOf(false) }
                    // One focus target keeps hardware keys routed; request it when the chat
                    // surfaces, unless the composer already holds it.
                    LaunchedEffect(app.surface) {
                        if (app.surface == Surface.Chat && !chatHasFocus) {
                            runCatching { chatKeys.requestFocus() }
                        }
                    }
                    CompositionLocalProvider(
                        LocalChatKeyFocus provides chatKeys,
                        LocalShortcutsHelp provides shortcutsHelp,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .focusRequester(chatKeys)
                                .onFocusChanged { chatHasFocus = it.hasFocus }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    handleHardwareKey(app, shortcutsHelp, chatKeys, event)
                                },
                        ) {
                            BackHandler(enabled = app.surface == Surface.Chat && sidebarExpanded) {
                                sidebarExpanded = false
                            }
                            BackHandler(enabled = shortcutsHelp.visible) {
                                shortcutsHelp.dismiss()
                            }
                            ChatScreen(
                                app = app,
                                topInset = topInset,
                                bottomInset = bottomInset,
                                sidebarExpanded = sidebarExpanded,
                                onSidebarExpandedChange = { sidebarExpanded = it },
                                projectsCollapsed = projectsCollapsed,
                                onToggleProjects = {
                                    projectsCollapsed = !projectsCollapsed
                                    preferences.edit()
                                        .putBoolean(CodexApp.KeyProjectsCollapsed, projectsCollapsed)
                                        .apply()
                                },
                                expandedProjects = expandedProjects,
                                onToggleProject = { id ->
                                    expandedProjects = if (id in expandedProjects) {
                                        expandedProjects - id
                                    } else {
                                        expandedProjects + id
                                    }
                                    preferences.edit()
                                        .putStringSet(CodexApp.KeyExpandedProjects, expandedProjects)
                                        .apply()
                                },
                            )
                        }
                    }
                }

                entry<Surface.Settings>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        SettingsScreen(
                            catalog = app.catalog,
                            session = app.widget.state,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                            onOpenWorkspacePicker = { app.openSurface(Surface.WorkspacePicker) },
                            onOpenEntry = { id -> openSurfaceFor(app, id) },
                            onOpenShortcuts = { shortcutsHelp.toggle() },
                            configPath = runtime.configPath,
                        )
                    }
                }

                entry<Surface.Account>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        AccountScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.McpServers>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        McpScreen(catalog = app.catalog, onBack = app::closeSurface,
                            onOpenServer = { app.openSurface(Surface.McpToolbox(it)) },
                            onEvent = app::onAppEvent)
                    }
                }

                entry<Surface.Skills>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) { SkillsScreen(catalog = app.catalog, onEvent = app::onAppEvent, onBack = app::closeSurface) }
                }

                entry<Surface.Plugins>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        PluginsScreen(
                            catalog = app.catalog,
                            client = app.client,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Apps>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) { AppsScreen(catalog = app.catalog, client = app.client, onEvent = app::onAppEvent, onBack = app::closeSurface) }
                }

                entry<Surface.Hooks>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) { HooksScreen(catalog = app.catalog, onEvent = app::onAppEvent, onBack = app::closeSurface) }
                }

                entry<Surface.Sessions>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) { SessionListScreen(app = app, onBack = app::closeSurface) }
                }

                entry<Surface.WorkspacePicker>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        WorkspacePickerScreen(
                            client = app.client,
                            initialPath = app.widget.state.config.cwd.ifEmpty {
                                app.defaultWorkspace
                            },
                            onPicked = { path ->
                                app.onAppEvent(com.cy.codex.AppEvent.NewThread(path))
                                app.closeAllSurfaces()
                            },
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Projects>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        ProjectsScreen(
                            catalog = app.catalog,
                            client = app.client,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                            onOpenEnvironment = { app.openSurface(Surface.EnvironmentDetail(it)) },
                            onOpenProject = { app.onAppEvent(AppEvent.NewThread(it)) },
                        )
                    }
                }

                entry<Surface.EnvironmentDetail>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        EnvironmentDetailScreen(
                            environmentId = route.environmentId,
                            client = app.client,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.RemoteControl>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadRemoteControl) }
                    SheetPage(onDismiss = app::closeSurface) {
                        RemoteControlScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.UserVerification>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadUserVerification) }
                    SheetPage(onDismiss = app::closeSurface) {
                        UserVerificationScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.PluginShares>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadPluginShares) }
                    SheetPage(onDismiss = app::closeSurface) {
                        PluginSharesScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Memories>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadMemories) }
                    SheetPage(onDismiss = app::closeSurface) {
                        MemoriesScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.SessionStatus>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        SessionStatusScreen(app = app, onBack = app::closeSurface)
                    }
                }

                entry<Surface.Diagnostics>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadDiagnostics) }
                    SheetPage(onDismiss = app::closeSurface) {
                        DiagnosticsScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.ExternalAgentImport>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        ExternalAgentImportScreen(
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Bedrock>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        BedrockScreen(
                            client = app.client,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.WindowsSandbox>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        WindowsSandboxScreen(
                            catalog = app.catalog,
                            client = app.client,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.FileBrowser>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        FileBrowserScreen(
                            path = route.path,
                            picking = route.picking,
                            client = app.client,
                            onBack = app::closeSurface,
                            onPick = { picked ->
                                app.onAppEvent(AppEvent.NewThread(picked))
                                app.closeAllSurfaces()
                            },
                        )
                    }
                }

                entry<Surface.ExecCommand>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        ExecCommandScreen(
                            threadId = app.widget.state.threadId,
                            client = app.client,
                            shellPath = runtime.shellPath,
                            initialCwd = app.widget.state.config.cwd.ifBlank { app.defaultWorkspace },
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.BackgroundTerminals>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        BackgroundTerminalsScreen(
                            threadId = app.widget.state.threadId,
                            client = app.client,
                            session = app.widget.state,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Realtime>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    LaunchedEffect(Unit) { app.onAppEvent(AppEvent.ReloadRealtimeVoices) }
                    SheetPage(onDismiss = app::closeSurface) {
                        RealtimeScreen(
                            threadId = app.widget.state.threadId,
                            catalog = app.catalog,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Worktrees>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        WorktreesScreen(
                            client = app.client,
                            cwd = app.widget.state.config.cwd.ifEmpty { app.defaultWorkspace },
                            onOpen = { path -> app.onAppEvent(AppEvent.NewThread(path)) },
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Review>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        ReviewScreen(
                            threadId = app.widget.state.threadId,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Diff>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        GitDiffScreen(
                            cwd = app.widget.state.config.cwd.ifBlank { app.defaultWorkspace },
                            client = app.client,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.ThreadHistory>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        ThreadHistoryScreen(app = app, onBack = app::closeSurface)
                    }
                }

                entry<Surface.McpToolbox>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        McpToolboxScreen(
                            server = route.server,
                            client = app.client,
                            onEvent = app::onAppEvent,
                            onBack = app::closeSurface,
                        )
                    }
                }

                entry<Surface.Agents>(swipeDismiss = NavSwipeDirection.TopToBottom) {
                    SheetPage(onDismiss = app::closeSurface) {
                        AgentsScreen(app = app, onBack = app::closeSurface)
                    }
                }

                entry<Surface.SubAgentThread>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        // A snapshot, not an observation: the parent transcript keeps streaming and would rebuild this page on every delta.
                        val mainLabel = stringResource(R.string.agent_roster_main_label)
                        val nameFormat = stringResource(R.string.agent_roster_sub_agent_name)
                        val roster = remember(route.threadId, mainLabel, nameFormat) {
                            deriveAgentRoster(
                                items = app.widget.state.items,
                                mainThreadId = app.widget.state.threadId,
                                mainLabel = mainLabel,
                                subAgentNameFormat = nameFormat,
                            )
                        }
                        SubAgentThreadScreen(
                            threadId = route.threadId,
                            client = app.client,
                            onBack = app::closeSurface,
                            roster = roster,
                            onSwitchAgent = { threadId ->
                                app.closeSurface()
                                app.openSurface(Surface.SubAgentThread(threadId))
                            },
                        )
                    }
                }

                entry<Surface.SubAgent>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        SubAgentScreen(
                            threadId = route.threadId,
                            items = app.widget.state.items.toList(),
                            mainThreadId = app.widget.state.threadId,
                            onBack = app::closeSurface,
                        )
                    }
                }
            }
            ShortcutsOverlay(state = shortcutsHelp)
        }
    }
}

private fun handleHardwareKey(
    app: CodexApp,
    shortcutsHelp: ShortcutsHelpState,
    chatKeys: FocusRequester,
    event: KeyEvent,
): Boolean {
    if (app.surface != Surface.Chat) return false
    val chord = event.toKeyChord() ?: return false
    if (shortcutsHelp.visible) {
        val closes = chord.key == CodexKeys.ESCAPE ||
            CodexKeymap.resolve(KeyContext.Global, chord) == KeyAction.ShowShortcuts ||
            CodexKeymap.resolve(KeyContext.Composer, chord) == KeyAction.ShowShortcuts
        if (closes) shortcutsHelp.dismiss()
        return true
    }
    return when (CodexKeymap.resolve(KeyContext.Chat, chord)) {
        KeyAction.InterruptTurn ->
            if (app.widget.state.running) {
                app.onAppEvent(AppEvent.InterruptTurn)
                true
            } else {
                false
            }

        KeyAction.OpenTranscript -> {
            app.openSurface(Surface.ThreadHistory)
            true
        }

        KeyAction.ShowShortcuts -> {
            shortcutsHelp.toggle()
            runCatching { chatKeys.requestFocus() }
            true
        }

        else ->
            if (CodexKeymap.resolve(KeyContext.Composer, chord) == KeyAction.ShowShortcuts &&
                app.widget.state.composerDraft.isEmpty()
            ) {
                shortcutsHelp.toggle()
                runCatching { chatKeys.requestFocus() }
                true
            } else {
                false
            }
    }
}

/** The wire reports successes/failures per item type and no total; the bar counts both and labels the types. */
private fun externalImportProgress(
    results: List<com.cy.codex.protocol.protocol.v2.ExternalAgentConfigImportTypeResult>,
): ImportProgress {
    val done = results.sumOf { it.successes.size }
    val total = results.sumOf { it.successes.size + it.failures.size }
    return ImportProgress(done, total, results.joinToString(", ") { it.itemType })
}

@Composable
private fun SheetPage(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val outsideInteraction = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageWidth = minOf(
            (maxWidth - sheetSideMargin() * 2).coerceAtLeast(0.dp),
            UiConsts.SheetMaxWidth,
        )
        val gutter = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
        Row(modifier = Modifier.fillMaxSize()) {
            OutsideTapTarget(
                interactionSource = outsideInteraction,
                onDismiss = onDismiss,
                modifier = Modifier.width(gutter).fillMaxHeight(),
            )
            Spacer(Modifier.weight(1f))
            OutsideTapTarget(
                interactionSource = outsideInteraction,
                onDismiss = onDismiss,
                modifier = Modifier.width(gutter).fillMaxHeight(),
            )
        }
        OutsideTapTarget(
            interactionSource = outsideInteraction,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(UiConsts.SheetTopGap),
        )
        MiuixSurface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = UiConsts.SheetTopGap)
                .width(pageWidth)
                .fillMaxHeight(),
            shape = RoundedCornerShape(
                topStart = UiConsts.DrawerCorner,
                topEnd = UiConsts.DrawerCorner,
            ),
            color = panelColor(),
            shadowElevation = UiConsts.SheetElevation,
        ) {
            content()
        }
    }
}

@Composable
private fun OutsideTapTarget(
    interactionSource: MutableInteractionSource,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onDismiss,
        ),
    )
}
