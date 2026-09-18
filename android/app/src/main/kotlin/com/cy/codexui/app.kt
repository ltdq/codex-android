package com.cy.codexui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.cy.codexui.AppEvent
import com.cy.codexui.CatalogState
import com.cy.codexui.ChatWidget
import com.cy.codexui.CodexScreen
import com.cy.codexui.Surface
import com.cy.codexui.ThreadListState
import com.cy.codexui.app.DiagnosticsScreen
import com.cy.codexui.app.EnvironmentDetailScreen
import com.cy.codexui.app.ProjectsScreen
import com.cy.codexui.app.SubAgentScreen
import com.cy.codexui.app.SubAgentThreadScreen
import com.cy.codexui.app.ThreadHistoryScreen
import com.cy.codexui.app.UserVerificationScreen
import com.cy.codexui.bottom_pane.AppsScreen
import com.cy.codexui.bottom_pane.BackgroundTerminalsScreen
import com.cy.codexui.bottom_pane.ExecCommandScreen
import com.cy.codexui.bottom_pane.FileBrowserScreen
import com.cy.codexui.bottom_pane.GitDiffScreen
import com.cy.codexui.bottom_pane.HooksScreen
import com.cy.codexui.bottom_pane.McpScreen
import com.cy.codexui.bottom_pane.McpToolboxScreen
import com.cy.codexui.bottom_pane.MemoriesScreen
import com.cy.codexui.bottom_pane.SkillsScreen
import com.cy.codexui.chatwidget.ChatScreen
import com.cy.codexui.chatwidget.PluginSharesScreen
import com.cy.codexui.chatwidget.PluginsScreen
import com.cy.codexui.chatwidget.RealtimeScreen
import com.cy.codexui.chatwidget.ReviewScreen
import com.cy.codexui.chatwidget.SettingsScreen
import com.cy.codexui.chatwidget.WindowsSandboxScreen
import com.cy.codexui.chatwidget.WorkspacePickerScreen
import com.cy.codexui.chatwidget.openSurfaceFor
import com.cy.codexui.external_agent_config_migration.ExternalAgentImportScreen
import com.cy.codexui.keymap.CodexKeymap
import com.cy.codexui.keymap.CodexKeys
import com.cy.codexui.keymap.KeyAction
import com.cy.codexui.keymap.KeyContext
import com.cy.codexui.keymap.LocalChatKeyFocus
import com.cy.codexui.keymap.LocalShortcutsHelp
import com.cy.codexui.keymap.ShortcutsHelpState
import com.cy.codexui.keymap.ShortcutsOverlay
import com.cy.codexui.keymap.toKeyChord
import com.cy.codexui.runtime.CodexApplication
import com.cy.codexui.onboarding.BedrockScreen
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.AppServerEvent
import com.cy.codexui.protocol.ConnectionState
import kotlinx.serialization.json.JsonPrimitive
import com.cy.codexui.protocol.protocol.v2.ConfigValueWriteParams
import com.cy.codexui.protocol.protocol.v2.FeedbackUploadParams
import com.cy.codexui.protocol.protocol.v2.LoginAccountResponse
import com.cy.codexui.protocol.protocol.v2.ThreadSessionState
import com.cy.codexui.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codexui.status.AccountScreen
import com.cy.codexui.status.RemoteControlScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
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
 * Mirrors `codex-rs/tui/src/app.rs`: one object owns the backend connection, the thread list, the
 * open session and the overlay stack, and hands them to the screen. The TUI's `App` is a struct
 * with an event loop; here it is a small holder plus Compose state, but the ownership boundaries are
 * the same so the widgets below stay transport-agnostic.
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

    /**
     * Where a failed request is reported.
     *
     * Owned here rather than by the shell so that the reducer can reach it: a write that the server
     * rejects has to say so, and the reducer is the only place that sees the rejection.
     */
    val snackbar = SnackbarHostState()

    var widget by mutableStateOf(ChatWidget(client, scope))
        private set

    /**
     * The page stack: [Surface.Chat] at the root, every pushed page above it, outermost last.
     *
     * A stack rather than one slot, because the pages nest: the settings page opens the workspace
     * picker, and a single slot made that picker *replace* settings, so backing out of it landed on
     * the transcript and the page the user came from was gone.
     *
     * It is a `miuix-nav` back stack, so the shell can hand it straight to `NavDisplay`: the
     * navigation runtime owns the animated depth, the transition and the back gesture, and this
     * object stays a plain list of destinations. It is an in-memory stack (not
     * `rememberNavBackStack`), which is why [Surface] only has to be a `NavKey` and not
     * `@Serializable` — the app object outlives recomposition but not the process, exactly like the
     * rest of the session state it holds.
     */
    val surfaces: NavBackStack = navBackStackOf(Surface.Chat)

    /** The page on top, or [Surface.Chat] when the chat is unobstructed. */
    val surface: Surface get() = surfaces.lastOrNull() as? Surface ?: Surface.Chat

    var startupLoading by mutableStateOf(false)
        private set
    var startupReady by mutableStateOf(false)
        private set
    var startupError by mutableStateOf<String?>(null)
        private set
    var creatingThread by mutableStateOf(false)
        private set
    private var observersStarted = false
    var goalMenuOpen by mutableStateOf(false)

    init {
        widget.state.applyConfig(ThreadSessionState(threadId = "", cwd = defaultWorkspace))
    }

    /**
     * Route one UI event.
     *
     * Two reducers, not one. `ChatWidget` owns the open thread — its transcript, turns and
     * approvals — while the catalogs (account, config, plugins, skills, MCP, projects, remote
     * control) are this object's. They used to share a single forwarding call into the widget,
     * which meant every catalog event fell into that reducer's `else` arm and vanished: the
     * permission radio group, the account refresh button and the experimental switches all
     * rendered but changed nothing.
     *
     * Events this object does not own are forwarded to [ChatWidget.action], whose `when` is
     * exhaustive over [AppEvent] — so an event added without a home is a compile error in one of
     * the two reducers rather than a silent no-op at runtime.
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
                val commandText = event.inputs.singleOrNull()?.let {
                    (it as? com.cy.codexui.protocol.protocol.v2.UserInput.Text)?.text?.trim()
                }
                when (val input = commandText?.let { classifySlashInput(it, ComposerCommands) }) {
                    is SlashInput.Command -> {
                        runSlashCommand(AppEvent.SubmitSlashCommand(input.name, input.args))
                        if (input.name != "shell") widget.state.applyDraft("")
                        return
                    }

                    is SlashInput.Unknown -> {
                        // A command-shaped token that names nothing must not become a model message:
                        // the agent would answer a stray line of prose and the user would never learn
                        // the command does not exist. The draft stays so the typo can be fixed.
                        reportUnknownCommand(input.name)
                        return
                    }

                    // Plain text, an upload, or a path that happens to begin with a slash.
                    else -> Unit
                }
                if (catalog.account.account == null) {
                    openSurface(Surface.Account)
                } else if (!widget.state.open) {
                    createThread(inputs = event.inputs)
                } else {
                    widget.action(event)
                }
            }
            // ---- reads that fill a catalog ------------------------------------
            AppEvent.ReloadAccount -> load({ client.readAccount() }) { catalog.account = it }
            AppEvent.ReloadRateLimits -> load({ client.readRateLimits() }) { catalog.rateLimits = it }
            AppEvent.ReloadUsage -> load({ client.readUsage() }) { catalog.usage = it; catalog.usageLoaded = true }

            AppEvent.ReloadConfig -> request { reloadConfig() }
            AppEvent.ReloadSkills -> load({ client.listSkills() }) { catalog.skills = it }
            AppEvent.ReloadPlugins -> request { reloadPlugins() }
            AppEvent.ReloadPluginShares -> load({ client.listPluginShares() }) { catalog.pluginShares = it }
            AppEvent.ReloadApps -> load({ client.listApps() }) { catalog.apps = it }
            AppEvent.ReloadHooks -> load({ client.listHooks() }) { catalog.hooks = it }
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
                // Two independent reads, so two requests: a detection failure must not stop the
                // history from loading, which is what one chained call would have done.
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

            // The goal is per-thread, so the answered objective goes back to the widget: it is the
            // only reducer that knows which thread is open.
            AppEvent.ReloadGoal -> request {
                client.getGoal(widget.state.threadId).onSuccess { widget.state.applyGoal(it) }
            }

            AppEvent.ReloadEnvironments -> Unit // nothing lists environments; see CatalogState.

            AppEvent.RefreshThreadList -> refreshThreads()

            is AppEvent.SetThreadListScope -> {
                threads.includeArchived = event.includeArchived
                refreshThreads()
            }

            // ---- writes -------------------------------------------------------
            is AppEvent.InstallPlugin -> request {
                client.installPlugin(event.name, event.marketplace).onSuccess { reloadPlugins() }
            }

            is AppEvent.UninstallPlugin -> request {
                client.uninstallPlugin(event.pluginId).onSuccess { reloadPlugins() }
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

            // `app/installed` has no write of its own: installing an app is a config change, so the
            // page writes the key and the list is re-read from the server's own view of it.
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
                    catalog.rateLimits = com.cy.codexui.protocol.protocol.v2.AccountRateLimits()
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

            // ---- sections -----------------------------------------------------
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

            // ---- projects and environments ------------------------------------
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

            // ---- remote control -----------------------------------------------
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

            // ---- user verification ---------------------------------------------
            AppEvent.EnrollUserVerification -> load({ client.enrollUserVerification() }) { enrolled ->
                catalog.userVerificationCredential = enrolled
                // Readiness is a separate endpoint, so it is re-read rather than assumed from the
                // enrollment: the credential exists locally before the server knows about it.
                request {
                    client.readUserVerificationStatus().onSuccess { catalog.userVerification = it }
                }
            }

            is AppEvent.VerifyUserVerification -> load(
                { client.verifyUserVerification(event.params) },
            ) { /* the proof is the answer; there is no state to fold it into */ }

            // A deliberate no-op when nothing is in flight. `userVerification/cancel` names the
            // request id the *transport* assigned to a verification RPC, and this client does not
            // surface those ids — so it sends an id the server has never seen, which the protocol
            // defines as a no-op rather than an error. A verification that already finished is not
            // rolled back either way.
            AppEvent.CancelUserVerification -> request { client.cancelUserVerification("") }
            AppEvent.DeleteUserVerification -> request {
                client.deleteUserVerification().onSuccess {
                    client.readUserVerificationStatus().onSuccess { catalog.userVerification = it }
                }
            }

            // ---- sessions: realtime voice --------------------------------------
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

            // ---- review ---------------------------------------------------------
            is AppEvent.StartReview -> request {
                client.startReview(event.threadId, event.target).onSuccess {
                    if (it.reviewThreadId.isNotBlank() && it.reviewThreadId != widget.state.threadId) {
                        openThread(it.reviewThreadId)
                    } else closeAllSurfaces()
                }
            }

            // ---- memories, migration, feedback ----------------------------------
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
                    ),
                )
            }

            // ---- windows sandbox -------------------------------------------------
            is AppEvent.WindowsSandboxSetupStart ->
                request { client.windowsSandboxSetupStart(event.mode, event.cwd) }

            // ---- config ----------------------------------------------------------
            //
            // `config/value/write` is the only way a settings toggle survives a restart, so the
            // write is followed by a re-read: the merged value can differ from what was written
            // when a higher-precedence layer shadows the key, and `lastWrite` is what says so.
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

            is AppEvent.SetExperimentalFeature -> request {
                client.setExperimentalFeature(event.id, event.enabled).onSuccess {
                    client.listExperimentalFeatures().onSuccess { catalog.experimentalFeatures = it }
                }
            }

            // ---- slash commands --------------------------------------------------
            is AppEvent.SubmitSlashCommand -> runSlashCommand(event)

            // ---- everything the open thread owns --------------------------------
            else -> widget.action(event)
        }
    }

    /**
     * Run a slash command the composer picked.
     *
     * Here rather than in the widget because most of these either navigate or change the thread
     * list, and the widget owns neither. The commands whose whole effect is a transcript-local sheet
     * — the model, approval and permission pickers — are routed to the page that carries the same
     * controls, because a command line cannot open a sheet that belongs to another composable.
     *
     * Exhaustive over the command list: a command that is offered in the popup but handled nowhere
     * is a row that does nothing, which is the failure this dispatch exists to prevent.
     */
    private fun runSlashCommand(event: AppEvent.SubmitSlashCommand) {
        val argument = event.args.trim()
        val threadId = widget.state.threadId
        when (event.command.removePrefix("/")) {
            "new" -> onAppEvent(AppEvent.NewThread(widget.state.config.cwd))
            "resume" -> openSurface(Surface.Sessions)
            "fork" -> onAppEvent(AppEvent.ForkThread(threadId))
            "archive" -> onAppEvent(AppEvent.ArchiveThread(threadId, archived = true))
            "compact" -> onAppEvent(AppEvent.CompactThread(threadId))
            "revert" -> onAppEvent(AppEvent.RevertThread(threadId, argument.ifEmpty { null }))
            "review" -> if (widget.state.open) openSurface(Surface.Review) else {
                createThread(afterCreated = { openSurface(Surface.Review) })
            }
            "mcp" -> openSurface(Surface.McpServers)
            "skills" -> openSurface(Surface.Skills)
            "plugins" -> openSurface(Surface.Plugins)
            "hooks" -> openSurface(Surface.Hooks)
            "apps" -> openSurface(Surface.Apps)
            "settings" -> openSurface(Surface.Settings)
            "shell" -> if (argument.isNotBlank()) {
                if (widget.state.open) onAppEvent(AppEvent.RunShellCommand(threadId, argument)) else {
                    createThread(afterCreated = {
                        onAppEvent(AppEvent.RunShellCommand(widget.state.threadId, argument))
                    })
                }
            }
            "usage" -> openSurface(Surface.Account)
            "status" -> openSurface(Surface.Diagnostics)
            "model", "approvals", "permissions" -> openSurface(Surface.Settings)

            // `/init` is a *turn*: the TUI submits a fixed instruction and lets the agent write the
            // file, because only the agent knows what the project's conventions are.
            "init" -> onAppEvent(
                AppEvent.SubmitUserMessage(
                    listOf(com.cy.codexui.protocol.protocol.v2.UserInput.Text(InitInstruction)),
                ),
            )

            // The status card's pane only shows the *turn's* diff; `/diff` is the working tree, which
            // includes changes no turn made and files git has never seen.
            "diff" -> openSurface(Surface.Diff)

            // `/goal` never reaches here — it takes an argument, so the picker leaves it in the
            // draft — but listing it keeps this dispatch total.
            "goal" -> if (argument.isBlank()) {
                if (widget.state.open) {
                    onAppEvent(AppEvent.ReloadGoal)
                    goalMenuOpen = true
                } else createThread(afterCreated = { goalMenuOpen = true })
            } else if (widget.state.open) onAppEvent(AppEvent.SetGoal(argument)) else {
                createThread(afterCreated = { onAppEvent(AppEvent.SetGoal(argument)) })
            }

            // Unreachable while [ComposerCommands] is exactly this dispatch's command list, but a
            // command that is offered and handled nowhere must not fail silently.
            else -> reportUnknownCommand(event.command.removePrefix("/"))
        }
    }

    /** Say that [name] names no command; the caller keeps the draft so it can be corrected. */
    private fun reportUnknownCommand(name: String) {
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.runtime_unknown_slash_command, name))
        }
    }

    /**
     * Run one request, and report a failure where the user can see it.
     *
     * Every catalog write used to be a `scope.launch { client.… }` whose `Result` was dropped on the
     * floor. A rejected write — a marketplace url the server will not take, a plugin that is not
     * installed — therefore looked exactly like a successful one until the page was reopened.
     */
    private fun request(block: suspend () -> Result<*>) {
        request(block, then = {})
    }

    /**
     * Run one request and fold a successful answer.
     *
     * `then` is a suspend lambda rather than the `onSuccess` receiver so a continuation may itself
     * talk to the server — installing a plugin and then re-reading the catalog is one action, not
     * two — without the call site nesting another `launch`.
     */
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

    /** Run one read whose answer belongs in a catalog field, reporting a failure like [request]. */
    private fun <T> load(block: suspend () -> Result<T>, into: suspend (T) -> Unit) {
        request(block, into)
    }

    private fun refreshThreads() {
        val archived = threads.includeArchived
        request {
            client.listThreads(com.cy.codexui.protocol.protocol.v2.ThreadListParams(archived = archived)).onSuccess {
                if (threads.includeArchived == archived) threads.applyListing(it)
            }
        }
    }

    /** Re-read the config stack, which the settings page renders. */
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

    /**
     * Refresh the plugin catalog, and the marketplace list with it.
     *
     * One call, because the protocol has only one: `plugin/list` answers with marketplaces and the
     * plugins hanging off them, and there is no `marketplace/list` to ask separately. Splitting the
     * response here is what lets the rest of the app keep thinking in two flat lists.
     */
    private suspend fun reloadPlugins(): Result<*> =
        client.listPlugins().onSuccess { response ->
            catalog.marketplaces = response.marketplaces
            catalog.plugins = response.marketplaces.flatMap { it.plugins }
        }

    private suspend fun reloadPluginShares(): Result<*> =
        client.listPluginShares().onSuccess { catalog.pluginShares = it }

    /** The status read and the paired-device list, which the remote-control page shows together. */
    private suspend fun reloadRemoteControl(): Result<*> {
        val status = client.readRemoteControlStatus().onSuccess { catalog.remoteControl = it }
        // The paired-device list is addressed by environment, and a disabled link has none — asking
        // with a blank id would be a request the server can only reject.
        val environmentId = catalog.remoteControl?.environmentId ?: return status
        return client.listRemoteControlClients(environmentId).onSuccess {
            catalog.remoteControlClients = it.data
        }
    }

    /**
     * Fold the catalog-level notifications into [catalog].
     *
     * A second collector on the same stream the widget reads, rather than routing through it:
     * `SharedFlow` fans out, so both reducers see every event, and neither has to know what the
     * other is interested in. These are the notifications that say "what you are showing is stale"
     * — without them a plugin installed on another device, or a skill toggled by another client,
     * would never appear.
     */
    private suspend fun observeCatalogs() {
        client.events.collect { event ->
            when (event) {
                is AppServerEvent.ThreadStartedEvent -> {
                    threads.threads = listOf(event.thread) + threads.threads.filterNot { it.id == event.threadId }
                }
                is AppServerEvent.ThreadNameUpdatedEvent -> threads.threads = threads.threads.map {
                    if (it.id == event.threadId) it.copy(name = event.delta.name) else it
                }
                is AppServerEvent.ThreadStatusChangedEvent -> threads.threads = threads.threads.map {
                    if (it.id == event.threadId) it.copy(status = event.delta.status) else it
                }
                is AppServerEvent.ThreadArchived -> {
                    threads.markArchived(event.threadId, true)
                    client.listThreads(com.cy.codexui.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.ThreadUnarchived -> {
                    threads.markArchived(event.threadId, false)
                    client.listThreads(com.cy.codexui.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.ThreadDeleted -> {
                    client.listThreads(com.cy.codexui.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived))
                        .onSuccess { threads.applyListing(it) }
                }
                is AppServerEvent.AccountUpdated -> catalog.account = event.account
                is AppServerEvent.RateLimitsUpdatedEvent ->
                    catalog.rateLimits = catalog.rateLimits.copy(rateLimits = catalog.rateLimits.rateLimits.mergedWith(event.rateLimits))
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

                is AppServerEvent.McpStartupStatusEvent ->
                    client.listMcpServers().onSuccess { catalog.mcpServers = it }

                is AppServerEvent.McpOauthLoginCompleted ->
                    client.listMcpServers().onSuccess { catalog.mcpServers = it }

                // A config write from anywhere else invalidates the stack this page is showing.
                is AppServerEvent.ConfigWarningEvent -> reloadConfig()

                is AppServerEvent.ProjectChanged ->
                    client.listProjects().onSuccess { catalog.projects = it }

                is AppServerEvent.RemoteControlStatusChanged -> {
                    catalog.remoteControl = event.delta.status
                    reloadRemoteControl()
                }

                // An environment the session just attached to is the only way this client learns an
                // environment id exists; there is no call that lists them.
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

                // Every other notification belongs to the open thread, and `ChatWidget` folds it.
                else -> Unit
            }
        }
    }

    fun openSurface(next: Surface) {
        // A route value may appear at most once on a `miuix-nav` stack: the reconciler rejects two
        // entries with the same content key outright. So "open" is really "bring to the front" — if
        // the page is already on the stack, everything above it pops and the user lands on it.
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

    /** Pop one page. The chat is the root and is never popped. */
    fun closeSurface() {
        if (surfaces.size > 1) surfaces.removeAt(surfaces.lastIndex)
    }

    /**
     * Drop every pushed page and land back on the chat.
     *
     * The removals land in one snapshot, so the runtime sees a single multi-pop and animates the
     * whole stack away as one continuous sweep rather than one slide per page.
     */
    fun closeAllSurfaces() {
        while (surfaces.size > 1) surfaces.removeAt(surfaces.lastIndex)
    }

    /** Open a thread, remembering it for the next launch the way the TUI persists its last session. */
    fun openThread(threadId: String) {
        openThread(threadId, onFailure = {})
    }

    private fun openThread(threadId: String, onFailure: () -> Unit) {
        widget.open(threadId) { result ->
            result.onSuccess { response ->
                preferences.edit().putString(KeySelectedSession, threadId).apply()
                threads.threads = listOf(response.thread) + threads.threads.filterNot { it.id == threadId }
            }.onFailure { onFailure() }
        }
        closeAllSurfaces()
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
                    target.absolutePath
                }
            }.onSuccess { path ->
                val draft = widget.state.composerDraft
                widget.state.applyDraft(draft + (if (draft.isBlank()) "" else "\n") + "@$path ")
            }.onFailure {
                snackbar.showSnackbar(it.message ?: context.getString(R.string.runtime_attachment_failed))
            }
        }
    }

    private fun createThread(
        cwd: String? = null,
        inputs: List<com.cy.codexui.protocol.protocol.v2.UserInput>? = null,
        afterCreated: (() -> Unit)? = null,
    ) {
        if (!startupReady || creatingThread) return
        creatingThread = true
        scope.launch {
            try {
                client.startThread(com.cy.codexui.protocol.protocol.v2.ThreadStartParams(cwd = cwd?.takeIf { it.isNotBlank() } ?: defaultWorkspace))
                    .onSuccess { session ->
                        widget.bind(session)
                        preferences.edit().putString(KeySelectedSession, session.threadId).apply()
                        closeAllSurfaces()
                        if (inputs != null) widget.action(AppEvent.SubmitUserMessage(inputs))
                        afterCreated?.invoke()
                        client.listThreads(com.cy.codexui.protocol.protocol.v2.ThreadListParams(archived = threads.includeArchived)).onSuccess { threads.applyListing(it) }
                    }
                    .onFailure {
                        snackbar.showSnackbar(it.message ?: context.getString(R.string.shell_request_failed))
                    }
            } finally {
                creatingThread = false
            }
        }
    }

    /** Start the embedded server once; a failed startup can be retried. */
    fun bootstrap() {
        if (startupLoading || startupReady) return
        startupLoading = true
        startupError = null
        if (!observersStarted) {
            observersStarted = true
            widget.attach()
            scope.launch(start = CoroutineStart.UNDISPATCHED) { observeCatalogs() }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                client.connection.collect { connection ->
                    when (connection) {
                        is ConnectionState.Failed -> {
                            startupReady = false
                            startupError = connection.message
                            widget.connectionLost()
                        }
                        ConnectionState.Disconnected -> if (startupReady) {
                            startupReady = false
                            startupError = context.getString(R.string.runtime_disconnected)
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
                com.cy.codexui.protocol.protocol.v2.ClientInfo(
                    name = "codex-android",
                    title = "Codex",
                    version = "1.0",
                ),
                ).getOrThrow()
                threads.applyListing(client.listThreads().getOrThrow())
                catalog.account = client.readAccount().getOrThrow()
                client.listModels().onSuccess { catalog.models = it }
                reloadConfig()
                check(client.connection.first() == ConnectionState.Ready) {
                    context.getString(R.string.runtime_disconnected)
                }
                startupReady = true
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
        /**
         * What `/init` submits.
         *
         * A fixed instruction rather than a generated one: the agent is the only thing that can see
         * the project, so the client asks for the file and lets the turn do the reading.
         */
        const val InitInstruction =
            "Create an AGENTS.md file with instructions for future Codex sessions working in this " +
                "repository. Describe the layout, the build and test commands, and the conventions " +
                "the code follows."

        const val KeySelectedSession = "session_selected"
        const val KeyExpandedProjects = "projects_expanded"
        const val KeyProjectsCollapsed = "projects_collapsed"

        /**
         * Every command [CodexApp.runSlashCommand] answers.
         *
         * This list and that dispatch are one set: a name that is handled but missing here is
         * unreachable, and a name that is listed but handled nowhere is the same failure seen from
         * the other side. `/diff`, `/hooks`, `/status`, `/permissions` and `/revert` were handled
         * before they were listed, so typing them sent the literal line to the model.
         */
        val ComposerCommands = setOf(
            "new", "resume", "fork", "archive", "compact", "revert",
            "review", "diff", "init", "goal", "shell",
            "mcp", "skills", "plugins", "apps", "hooks", "status", "permissions",
            "model", "approvals", "settings", "usage",
        )
    }
}

/**
 * Composition entry point: builds the app holder, keeps it alive across configuration changes and
 * starts the bootstrap once.
 */
@Composable
fun rememberCodexApp(): CodexApp {
    val context = LocalContext.current
    val app = (context.applicationContext as CodexApplication).app
    LaunchedEffect(app) { app.bootstrap() }
    return app
}

/** Top-level composable rendered by [MainActivity]. */
@Composable
fun CodexRoot() {
    val app = rememberCodexApp()
    CodexScreen(app = app)
}

/**
 * The shell.
 *
 * Mirrors `codex-rs/tui/src/app.rs`: the chat surface is always mounted and every other screen is
 * pushed on top of it, so dismissing a page always lands back on the live transcript.
 *
 * The page stack is a `miuix-nav` back stack, and the transition is `NavTransitions.Modal` — the
 * entering page slides up from the bottom edge over the chat, which is exactly the bottom-sheet
 * motion this shell used to hand-roll out of one `WindowBottomSheet` per stack level. Handing the
 * stack to the navigation runtime instead buys three things the hand-rolled version could not have:
 * a real transition (a window that is created already-shown never animates in), one continuous
 * sweep when several pages pop at once, and a predictive-back gesture that drives the same
 * transition rather than a second animation written to look like it.
 *
 * The shell also owns the sidebar's persisted view state, which is the same job
 * `local_settings.rs` does for the TUI.
 */
@Composable
fun CodexScreen(
    app: CodexApp,
    /** Space kept between the snackbar and the composer it floats above. */
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
            // The status bar is hidden by the activity, so this is normally 0 and the chrome sits on
            // the screen edge. A swipe down from the top edge can reveal it as a *transient* overlay
            // for a moment, and a bar that is overlaying the app must not push it: following the live
            // inset made the drawer, both chips and the transcript jump down and back. So the inset
            // is latched — it may only ever shrink — and a reveal costs nothing but the bar itself.
            val liveTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            var topInset by remember { mutableStateOf(liveTopInset) }
            LaunchedEffect(liveTopInset) {
                if (liveTopInset < topInset) topInset = liveTopInset
            }
            // The window is edge-to-edge, so the system does not resize it for the keyboard; the
            // chat entry consumes the IME inset itself (see the `imePadding` on its box below).
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            NavDisplay(
                backStack = app.surfaces,
                modifier = Modifier.fillMaxSize(),
                onBack = app::closeSurface,
                // Bottom-up modal: the layer underneath stays visible and untouched, so the chat
                // keeps its place while a page rides over it.
                transition = NavTransitions.Modal,
                effects = NavDisplayEffects(
                    // The runtime rounds the moving page for the duration of the animation; the page
                    // draws the same silhouette itself once it settles (see [SheetPage]).
                    cornerClipRadius = UiConsts.DrawerCorner,
                    cornerClipMode = NavCornerClipMode.All,
                    // The same dim a modal sheet draws, so opening a picker over a page does not
                    // darken the app in two steps.
                    dimAmount = UiConsts.ScrimAlpha,
                ),
            ) {
                entry<Surface.Chat>(swipeDismiss = NavSwipeDirection.None) {
                    val chatKeys = remember { FocusRequester() }
                    var chatHasFocus by remember { mutableStateOf(false) }
                    // The window has to keep one focus target or hardware key events have nowhere
                    // to go: the composer only holds focus while the user is typing, and Esc hands
                    // it back to this node. Requested when the chat becomes the visible surface and
                    // only while nothing inside it is focused yet, so a tap on the composer is
                    // never undone. A sheet takes focus while it is up and may hand back none when
                    // it closes, which is the other half of the same rule.
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
                                // The window is edge-to-edge, so the keyboard overlays the app
                                // instead of resizing it: consuming the IME inset here lifts the
                                // composer above the keyboard and shrinks the transcript viewport
                                // with it. Keys still route through the focus node below.
                                .imePadding()
                                .focusRequester(chatKeys)
                                .onFocusChanged { chatHasFocus = it.hasFocus }
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    handleHardwareKey(app, shortcutsHelp, chatKeys, event)
                                },
                        ) {
                            // Back closes the drawer before it leaves the chat. Registered here,
                            // inside the entry, so a covered chat never sees the event at all.
                            BackHandler(enabled = app.surface == Surface.Chat && sidebarExpanded) {
                                sidebarExpanded = false
                            }
                            // The overlay is modal, so the back gesture dismisses it instead of
                            // leaving the app; registered after the drawer handler, which only
                            // matters when both are somehow up.
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
                            onOpenServer = { app.openSurface(Surface.McpToolbox(it)) })
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
                                app.onAppEvent(com.cy.codexui.AppEvent.NewThread(path))
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
                    // The page is a report on state another client can change, so it re-reads on
                    // entry rather than trusting whatever the last notification left behind.
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
                            // A picked directory becomes the open thread's working directory, which
                            // is the only thing a picker in this app is ever for.
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
                        ThreadHistoryScreen(client = app.client, onBack = app::closeSurface)
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

                entry<Surface.SubAgentThread>(swipeDismiss = NavSwipeDirection.TopToBottom) { route ->
                    SheetPage(onDismiss = app::closeSurface) {
                        SubAgentThreadScreen(
                            threadId = route.threadId,
                            client = app.client,
                            onBack = app::closeSurface,
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
            // Drawn last, so it covers the nav stack; the composer's `?` reaches the same state
            // object through [LocalShortcutsHelp].
            ShortcutsOverlay(state = shortcutsHelp)
        }
    }
}

/**
 * Global hardware-key dispatch for the chat surface.
 *
 * Consumed chords stop here; an unconsumed one falls through to the focused node — the composer's
 * own preview handler, or the text field's editing shortcuts — which is how Ctrl+C stays "copy"
 * while no turn is running and how Esc reaches an open popup before it interrupts anything.
 */
private fun handleHardwareKey(
    app: CodexApp,
    shortcutsHelp: ShortcutsHelpState,
    chatKeys: FocusRequester,
    event: KeyEvent,
): Boolean {
    // A page over the chat owns the keyboard while it is up; the chat entry stays composed behind
    // it, so without this gate Ctrl+T inside a sheet would push another page.
    if (app.surface != Surface.Chat) return false
    val chord = event.toKeyChord() ?: return false
    // The help overlay is modal for keys: nothing behind it may react while it is up, and Esc or
    // either toggle closes it.
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
                // Esc still belongs to an open popup or the focused field, and Ctrl+C to the
                // clipboard.
                false
            }

        KeyAction.OpenTranscript -> {
            app.openSurface(Surface.ThreadHistory)
            true
        }

        KeyAction.ShowShortcuts -> {
            // Focus moves to the root so a soft keyboard cannot keep typing into the field behind
            // the overlay.
            shortcutsHelp.toggle()
            runCatching { chatKeys.requestFocus() }
            true
        }

        else ->
            // `?` opens the same overlay, but only while there is no draft to type it into.
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

/**
 * The frame every pushed page is drawn in.
 *
 * A page is a card, not a screen swap: it stops [UiConsts.SheetTopGap] short of the top edge, keeps
 * [sheetSideMargin] at either side, and reaches the bottom one. That geometry comes from
 * [SheetFrame], the same composable the modal sheets are laid out in, because a page and a sheet are
 * one object seen at two sizes — they had already drifted to 20dp and 14dp off the edge with two
 * different shadows while each owned its own copy of the numbers.
 *
 * The band the page does not cover is live: tapping it closes the page, which is exactly what the
 * same tap does to a sheet. A swipe down and the system back gesture still work — this is a third
 * way out, not a replacement for the other two, and it is the one a thumb reaches for first on a
 * device whose only navigation control is a gesture.
 */
/**
 * Fold the import notifications' per-type results into the progress bar's shape.
 *
 * The wire reports successes and failures per item type and nothing else — there is no total — so
 * the bar counts both, and the label names the types involved rather than an invented sentence.
 */
private fun externalImportProgress(
    results: List<com.cy.codexui.protocol.protocol.v2.ExternalAgentConfigImportTypeResult>,
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
    val shape = remember { SheetShape(UiConsts.DrawerCorner) }
    val outsideInteraction = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageWidth = minOf(maxWidth - sheetSideMargin(), UiConsts.SheetMaxWidth)
        // Only the band the page does not cover takes the tap. A sheet can put a scrim over the whole
        // window because it is modal; a page is not — the transcript behind it stays mounted — so
        // this is a hit target around the page rather than a layer over everything.
        val gutter = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
        // Drawn first, so the page painted over it wins every hit test that lands on the page.
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
        SheetFrame(
            shape = shape,
            tint = panelColor(),
            fillHeight = true,
        ) {
            content()
        }
    }
}

/** The dead band around a pushed page: tap it and the page closes, exactly as a sheet's scrim does. */
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
