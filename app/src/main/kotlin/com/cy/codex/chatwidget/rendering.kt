package com.cy.codex.chatwidget

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.CollapsibleSection
import com.cy.codex.bottom_pane.ComposerHistory
import com.cy.codex.MarkdownStream
import com.cy.codex.Motion
import com.cy.codex.R
import com.cy.codex.SessionDiagnostic
import com.cy.codex.SessionState
import com.cy.codex.Surface
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.AgentRosterEntry
import com.cy.codex.app.AgentsOverview
import com.cy.codex.app.rememberAgentRoster
import com.cy.codex.app.sessionStatusReport
import com.cy.codex.app.withThreadMetadata
import com.cy.codex.bottom_pane.ApprovalDialog
import com.cy.codex.bottom_pane.ApprovalNoticeBar
import com.cy.codex.bottom_pane.Composer
import com.cy.codex.bottom_pane.TurnActivityBar
import com.cy.codex.bottom_pane.activeToolDetail
import com.cy.codex.bottom_pane.isConnectorAuth
import com.cy.codex.copyToClipboard
import com.cy.codex.glassTint
import com.cy.codex.history_cell.CommandExecutionCell
import com.cy.codex.history_cell.DiagnosticCell
import com.cy.codex.history_cell.ThreadItemCell
import com.cy.codex.history_cell.commandActionLabel
import com.cy.codex.history_cell.isExploringCall
import com.cy.codex.perf.IdentityKeys
import com.cy.codex.protocol.ApprovalRequest
import com.cy.codex.protocol.ApprovalResponse
import com.cy.codex.protocol.protocol.item.AgentMessageItem
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.AttachmentType
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.ThreadAttachment
import com.cy.codex.protocol.protocol.v2.UserInput
import com.cy.codex.raisedSurface
import com.cy.codex.status.DiffCard
import com.cy.codex.status.StatusCard
import com.cy.codex.status.StatusCardButton
import com.cy.codex.status.StatusPanelState
import com.cy.codex.statusDotColor
import com.cy.codex.statusPillSurface
import java.io.File
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The chat surface: transcript, drawer, status card, approval cards and composer, mirroring
 * `codex-rs/tui/src/chatwidget.rs`. Owns only presentation state; everything else is an
 * [com.cy.codex.AppEvent].
 */
@Composable
fun ChatScreen(
    app: CodexApp,
    modifier: Modifier = Modifier,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    sidebarExpanded: Boolean,
    onSidebarExpandedChange: (Boolean) -> Unit,
    projectsCollapsed: Boolean,
    onToggleProjects: () -> Unit,
    expandedProjects: Set<String>,
    onToggleProject: (String) -> Unit,
    topBlurHeight: Dp = 52.dp,
    bottomBlurHeight: Dp = 78.dp,
    topBlurRadius: Float = 14f,
    bottomBlurRadius: Float = 16f,
    panelTopOffset: Dp = 56.dp,
    composerGap: Dp = 8.dp,
    minPanelHeight: Dp = 240.dp,
    minDiffHeight: Dp = 300.dp,
    maxDiffHeight: Dp = 560.dp,
    statusCardMaxHeight: Dp = 560.dp,
    // All transition durations read from Motion; three hand-picked values nobody could tell apart.
    panelEnterDurationMs: Int = Motion.EnterMs,
    panelExitDurationMs: Int = Motion.ExitMs,
    diffEnterDurationMs: Int = Motion.EnterMs,
    diffExitDurationMs: Int = Motion.ExitMs,
    queuedEnterDurationMs: Int = Motion.EnterMs,
    queuedExitDurationMs: Int = Motion.ExitMs,
) {
    val session = app.widget.state
    val threads = app.threads
    // OpenDocument, not GetContent: the app keeps a document-uri grant the server can read back later.
    val attachmentPicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri
            ->
            if (uri != null) {
                app.importAttachment(uri)
            }
        }
    // Consume the request before launching; a recomposition must not open the dialog twice.
    val exportPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/markdown")
        ) { uri ->
            if (uri != null) {
                app.exportTranscriptTo(uri)
            }
        }
    LaunchedEffect(app.exportTranscriptRequest) {
        if (app.exportTranscriptRequest) {
            app.consumeTranscriptExportRequest()
            exportPicker.launch(app.transcriptExportFileName())
        }
    }
    val colors = MiuixTheme.colorScheme
    var overviewOpen by remember { mutableStateOf(false) }
    // `show` stays true through the exit animation; clearing on the request would cut it mid-slide.
    var overviewLeaving by remember { mutableStateOf(false) }
    val panelState = remember { StatusPanelState() }

    val mainAgentLabel = stringResource(R.string.agent_roster_main_label)
    val subAgentNameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    // Folded via [rememberAgentRoster]: reading the items list here subscribes the whole screen to
    // every streaming delta.
    val roster = rememberAgentRoster(session, mainAgentLabel, subAgentNameFormat)
    val approval = app.widget.currentApproval
    val threadNameOf: (String) -> String = { threadId ->
        threads.threads
            .firstOrNull { it.id == threadId }
            ?.let { thread ->
                thread.name?.takeIf { it.isNotBlank() }
                    ?: thread.preview.take(48).takeIf { it.isNotBlank() }
            } ?: threadId.take(8)
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(colors.background)
        drawContent()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Wide window: the column is inset by the drawer's width at all times — centred while
        // the drawer is shut, pushed across when it opens — so text wraps once and the drawer
        // costs a composited translation, not a re-measure of every cell. Narrow: overlays.
        val wide = maxWidth >= UiConsts.WideContentBreakpoint
        val drawerWidth = minOf(UiConsts.SidebarWidth, UiConsts.SidebarWidthCap)
        val contentStart =
            (UiConsts.ScreenMargin + drawerWidth + UiConsts.ContentGap).coerceAtMost(maxWidth)
        val contentWidth =
            if (wide) {
                (maxWidth - contentStart - UiConsts.ScreenMargin).coerceAtLeast(
                    UiConsts.MinContentWidth
                )
            } else {
                maxWidth
            }
        val centredStart = (maxWidth - contentWidth) / 2
        val contentShift by
            animateDpAsState(
                targetValue = if (wide && sidebarExpanded) contentStart - centredStart else 0.dp,
                animationSpec = Motion.PanelDp,
                label = "contentShift",
            )
        Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .width(contentWidth)
                        .fillMaxHeight()
                        .graphicsLayer { translationX = contentShift.toPx() }
            ) {
                TranscriptPane(
                    app = app,
                    session = session,
                    topInset = topInset,
                    bottomInset = bottomInset,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Box(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topInset + topBlurHeight)
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        blurRadius = topBlurRadius,
                        gradient = ProgressiveBlur.Top.copy(endFraction = 0.92f),
                    )
        )
        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomInset + bottomBlurHeight)
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        blurRadius = bottomBlurRadius,
                        gradient = ProgressiveBlur.Bottom.copy(endFraction = 0.92f),
                    )
        )

        app.connectionLostMessage?.let { message ->
            ConnectionBanner(
                message = message,
                onRetry = app::reconnect,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(
                            start = UiConsts.ScreenMargin,
                            end = UiConsts.ScreenMargin,
                            bottom = bottomInset + UiConsts.PromptBarHeight + UiConsts.ScreenMargin,
                        ),
            )
        }

        StatusCardButton(
            open = panelState.open,
            onClick = { panelState.toggle() },
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(end = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        val panelMax = maxWidth - UiConsts.ScreenMargin * 2
        val statusWidth = minOf(UiConsts.StatusPanelWidth, panelMax)
        val sideBySide = panelMax >= statusWidth + UiConsts.PanelGap + UiConsts.MinDiffPaneWidth
        val diffWidth =
            if (sideBySide) {
                minOf(UiConsts.DiffPaneWidth, panelMax - statusWidth - UiConsts.PanelGap)
            } else {
                minOf(UiConsts.DiffPaneWidth, panelMax)
            }
        val statusHeight = remember { mutableStateOf(0.dp) }

        AnimatedVisibility(
            visible = panelState.open,
            enter =
                fadeIn(tween(panelEnterDurationMs, easing = Motion.EnterEasing)) +
                    scaleIn(
                        initialScale = 0.9f,
                        transformOrigin = TransformOrigin(1f, 0f),
                        animationSpec = Motion.Panel,
                    ),
            exit =
                fadeOut(tween(panelExitDurationMs, easing = Motion.ExitEasing)) +
                    scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = TransformOrigin(1f, 0f),
                        animationSpec = tween(panelExitDurationMs, easing = Motion.ExitEasing),
                    ),
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(
                        end = UiConsts.ScreenMargin,
                        top = topInset + UiConsts.ScreenMargin + panelTopOffset,
                    ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiConsts.PanelGap),
                verticalAlignment = Alignment.Top,
            ) {
                val paneFile = session.turnDiff.firstOrNull { it.path == panelState.paneFilePath }
                val diffOpen = panelState.openFilePath != null
                AnimatedVisibility(
                    visible = diffOpen && paneFile != null,
                    enter =
                        expandHorizontally(
                            expandFrom = Alignment.End,
                            animationSpec = tween(diffEnterDurationMs, easing = Motion.EnterEasing),
                        ) + fadeIn(tween(diffEnterDurationMs, easing = Motion.EnterEasing)),
                    exit =
                        shrinkHorizontally(
                            shrinkTowards = Alignment.End,
                            animationSpec = tween(diffExitDurationMs, easing = Motion.ExitEasing),
                        ) + fadeOut(tween(diffExitDurationMs, easing = Motion.ExitEasing)),
                ) {
                    paneFile?.let { file ->
                        DiffCard(
                            file = file,
                            siblings = session.turnDiff,
                            onClose = panelState::closeFile,
                            cwd = session.config.cwd,
                            width = diffWidth,
                            height = statusHeight.value.coerceIn(minDiffHeight, maxDiffHeight),
                        )
                    }
                }
                if (!diffOpen || sideBySide) {
                    StatusCard(
                        state = panelState,
                        session = session.config,
                        titlePending = session.titleGenerationPending,
                        gitSummary = session.gitSummary,
                        status = session.status,
                        usage = session.usage,
                        turnDiff = session.turnDiff,
                        plan = session.plan,
                        roster = roster,
                        items = session.items,
                        width = statusWidth,
                        maxHeight = statusCardMaxHeight,
                        models = app.catalog.models,
                        rateLimits = app.catalog.rateLimits,
                        rateLimitsUpdatedAt = app.catalog.rateLimitsUpdatedAtMs.takeIf { it > 0L },
                        onModel = { app.onAppEvent(AppEvent.SetModel(it)) },
                        onEffort = { app.onAppEvent(AppEvent.SetReasoningEffort(it)) },
                        onPolicy = { app.onAppEvent(AppEvent.SetApprovalPolicy(it)) },
                        onReviewer = { app.onAppEvent(AppEvent.SetApprovalsReviewer(it)) },
                        autoReviewAvailable = app.catalog.autoReviewAvailable,
                        onServiceTier = { app.onAppEvent(AppEvent.SetServiceTier(it)) },
                        planAvailable =
                            app.catalog.collaborationModes.any {
                                it.mode == CollaborationMode.Plan
                            },
                        onCollaborationMode = { app.onAppEvent(AppEvent.SetCollaborationMode(it)) },
                        onCompact = { app.onAppEvent(AppEvent.CompactThread(session.threadId)) },
                        onOpenAgents = { overviewOpen = true },
                        onOpenAgent = { threadId ->
                            app.openSurface(Surface.SubAgentThread(threadId))
                        },
                        onOpenAgentInfo = { threadId ->
                            app.openSurface(Surface.SubAgent(threadId))
                        },
                        modifier =
                            Modifier.onSizeChanged {
                                statusHeight.value = with(density) { it.height.toDp() }
                            },
                    )
                }
            }
        }

        val promptBarStartInset by
            animateDpAsState(
                targetValue =
                    if (wide && sidebarExpanded) contentStart - UiConsts.ScreenMargin else 0.dp,
                animationSpec = Motion.PanelDp,
                label = "promptBarStartInset",
            )

        ComposerDock(
            app = app,
            session = session,
            threadNameOf = threadNameOf,
            promptBarStartInset = promptBarStartInset,
            backdrop = backdrop,
            onAttach = { attachmentPicker.launch(arrayOf("*/*")) },
            composerGap = composerGap,
            queuedEnterDurationMs = queuedEnterDurationMs,
            queuedExitDurationMs = queuedExitDurationMs,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AgentsOverviewPane(
            app = app,
            session = session,
            roster = roster,
            show = overviewOpen || overviewLeaving,
            onSelect = { threadId ->
                app.openSurface(Surface.SubAgentThread(threadId))
                overviewLeaving = true
            },
            onDismiss = { overviewLeaving = true },
            onDismissFinished = {
                overviewLeaving = false
                overviewOpen = false
            },
        )

        SidebarPanel(
            expanded = sidebarExpanded,
            onExpandedChange = onSidebarExpandedChange,
            actions = SidebarModel.actions(),
            onAction = { entry -> openSurfaceFor(app, entry.id) },
            sessionActions = SidebarModel.sessionEntries(),
            projects = SidebarModel.projects(threads, includeArchived = false),
            projectsCollapsed = projectsCollapsed,
            onToggleProjects = onToggleProjects,
            expandedProjects = expandedProjects,
            onToggleProject = onToggleProject,
            selectedSessionId = session.threadId,
            onSessionSelected = app::openThread,
            onOpenSettings = { app.openSurface(Surface.Settings) },
            panelWidth = minOf(UiConsts.SidebarWidth, UiConsts.SidebarWidthCap),
            collapsedWidth = UiConsts.ChipSize,
            collapsedHeight = UiConsts.ChipSize,
            maxPanelHeight =
                (maxHeight - topInset - UiConsts.ScreenMargin * 2).coerceAtLeast(minPanelHeight),
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(start = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        ApprovalDialog(
            request = approval,
            busy = app.widget.answeringApproval,
            error = app.widget.approvalError,
            onDecision = { request, response -> onApprovalDecision(app, request, response) },
            remainingQueue = (app.widget.approvalQueueSize - 1).coerceAtLeast(0),
            // The patch is not on the request; it is recovered from the item the request names.
            patchChanges = { request -> app.widget.fileChangeChanges(request.itemId) },
        )
        if (app.goalMenuOpen) {
            val goal = session.goal
            GoalSheet(
                goal = goal,
                onSet = { objective ->
                    // Creating leaves status to the server; editing keeps it (upstream edited_goal_status).
                    app.onAppEvent(
                        AppEvent.SetGoal(
                            objective = objective,
                            status = goal?.let { editedGoalStatus(it.status) },
                        )
                    )
                },
                onSetStatus = { status -> app.onAppEvent(AppEvent.SetGoal(status = status)) },
                onClear = { app.onAppEvent(AppEvent.ClearGoal) },
                onDismiss = { app.goalMenuOpen = false },
            )
        }
        if (app.copyMenuOpen) {
            CopySheet(
                response = session.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem,
                status = sessionStatusReport(app),
                onDismiss = { app.copyMenuOpen = false },
            )
        }
        app.trustRequest?.let { request ->
            TrustProjectSheet(
                path = request.path,
                onTrust = app::grantTrust,
                onDismiss = app::dismissTrust,
            )
        }
        app.rateLimitNudge?.let { nudge ->
            RateLimitNudgeSheet(
                nudge = nudge,
                onSwitch = app::switchToRateLimitModel,
                onKeep = app::dismissRateLimitNudge,
                onNever = app::hideRateLimitNudgeForever,
            )
        }
    }
}

/**
 * The transcript surface, split out of [ChatScreen] so a streaming write invalidates only this
 * pane, not the composer, panels and drawer around it.
 */
@Composable
private fun TranscriptPane(
    app: CodexApp,
    session: SessionState,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val items = session.items
    val diagnostics = session.diagnostics
    // Derived boolean: the pane only recomposes when the empty/loading decision flips.
    val runtimeEmpty by
        remember(session) {
            derivedStateOf {
                (!session.open && !session.loading) ||
                    (session.open && items.isEmpty() && diagnostics.isEmpty() && !session.running)
            }
        }
    if (!app.startupReady || runtimeEmpty) {
        RuntimeTranscript(
            app = app,
            modifier =
                modifier.padding(
                    horizontal = UiConsts.ScreenMargin,
                    vertical = UiConsts.TranscriptTopInset + UiConsts.PromptBarHeight,
                ),
        )
        return
    }
    // Stable lambda: a recomposition (a status flip, say) must not force every row to rebuild.
    val isStreaming: (ThreadItem) -> Boolean =
        remember(session) {
            // Deferred to the row's scope: per-row streaming reads must not subscribe this pane.
            { item -> session.running && item.id == session.streamingItemId }
        }
    val streamFor: (String) -> MarkdownStream? = remember(session) { { id -> session.stream(id) } }
    Transcript(
        items = items,
        diagnostics = diagnostics,
        isStreaming = isStreaming,
        streamFor = streamFor,
        plan = session.plan,
        loading = session.loading,
        empty = !session.open && !session.loading,
        cwd = session.config.cwd,
        onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
        onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
        onAnswerQuestion = { text -> app.onAppEvent(AppEvent.AnswerAsyncQuestion(text)) },
        canLoadEarlier = app.widget.canLoadEarlier,
        loadingEarlier = app.widget.loadingEarlier,
        onLoadEarlier = app.widget::loadEarlier,
        contentPadding =
            PaddingValues(
                start = UiConsts.TranscriptGutter,
                end = UiConsts.TranscriptGutter,
                top = topInset + UiConsts.TranscriptTopInset,
                bottom =
                    bottomInset +
                        UiConsts.PromptBarHeight +
                        UiConsts.ScreenMargin * 2 +
                        UiConsts.TranscriptBottomInset,
            ),
    )
}

@Composable
private fun ConnectionBanner(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.PanelCorner) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(glassTint(0.94f), shape)
                .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.connection_banner_title),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = message,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(UiConsts.Space10))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.connection_banner_retry),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RuntimeTranscript(app: CodexApp, modifier: Modifier) {
    val session = app.widget.state
    val colors = MiuixTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space12),
        ) {
            Text(
                "Codex",
                fontSize = UiType.Display,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )
            when {
                app.startupLoading || app.creatingThread -> {
                    Text(
                        stringResource(
                            if (app.creatingThread) R.string.runtime_creating_thread
                            else R.string.runtime_starting
                        )
                    )
                    LinearProgressIndicator(modifier = Modifier.width(160.dp))
                }
                app.startupError != null -> {
                    Text(stringResource(R.string.runtime_startup_failed), color = colors.error)
                    Text(
                        app.startupError.orEmpty(),
                        fontSize = UiType.Meta,
                        color = colors.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = app::bootstrap,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.runtime_retry),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                !session.open && session.threadId.isNotBlank() -> {
                    Text(
                        session.diagnostics.lastOrNull()?.detail.orEmpty(),
                        fontSize = UiType.Meta,
                        color = colors.error,
                    )
                    Button(
                        onClick = { app.openThread(session.threadId) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.runtime_retry),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { app.onAppEvent(AppEvent.NewThread()) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.runtime_new_thread),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                app.catalog.account.account == null -> {
                    Button(
                        onClick = { app.openSurface(Surface.Account) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.runtime_sign_in),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(R.string.runtime_ready),
                        fontSize = UiType.CardTitle,
                        color = colors.onSurfaceVariantSummary,
                    )
                    Text(
                        session.config.cwd.ifBlank { app.defaultWorkspace },
                        fontSize = UiType.Meta,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

private fun onApprovalDecision(
    app: CodexApp,
    request: ApprovalRequest,
    response: ApprovalResponse,
) {
    app.onAppEvent(AppEvent.ResolveApproval(request.requestId, response))
    // A completed connector sign-in invalidates the app catalog; upstream refreshes connectors
    // on the same accept (app_link_view.rs complete_external_flow_and_close).
    if (
        request is ApprovalRequest.Elicitation &&
            request.params.isConnectorAuth() &&
            response is ApprovalResponse.Elicitation &&
            response.action == com.cy.codex.protocol.ElicitationAction.Accept
    ) {
        app.onAppEvent(AppEvent.ReloadApps)
    }
}

/** Shared route table for the drawer and Settings, so the two placements cannot drift. */
internal fun openSurfaceFor(app: CodexApp, id: String) {
    // Routes needing a subject read the open session: a caller-passed id could name one no longer open.
    val threadId = app.widget.state.threadId
    val cwd = app.widget.state.config.cwd.ifBlank { app.defaultWorkspace }
    when (id) {
        "new" -> app.onAppEvent(AppEvent.NewThread())
        "workspace" -> app.openSurface(Surface.WorkspacePicker)
        "sessions" -> {
            app.onAppEvent(AppEvent.SetThreadListScope(false))
            app.openSurface(Surface.Sessions)
        }
        "mcp" -> app.openSurface(Surface.McpServers)
        "skills" -> app.openSurface(Surface.Skills)
        "plugins" -> app.openSurface(Surface.Plugins)
        "hooks" -> app.openSurface(Surface.Hooks)
        "apps" -> app.openSurface(Surface.Apps)
        "settings" -> app.openSurface(Surface.Settings)
        "account" -> app.openSurface(Surface.Account)
        "archived" -> {
            app.onAppEvent(AppEvent.SetThreadListScope(true))
            app.openSurface(Surface.Sessions)
        }
        "projects" -> app.openSurface(Surface.Projects)
        "remote_control" -> app.openSurface(Surface.RemoteControl)
        "verification" -> app.openSurface(Surface.UserVerification)
        "plugin_shares" -> app.openSurface(Surface.PluginShares)
        "memories" -> app.openSurface(Surface.Memories)
        "migration" -> app.openSurface(Surface.ExternalAgentImport)
        "bedrock" -> app.openSurface(Surface.Bedrock)
        "diagnostics" -> app.openSurface(Surface.Diagnostics)
        "sandbox" -> app.openSurface(Surface.WindowsSandbox)
        "files" -> app.openSurface(Surface.FileBrowser(cwd, picking = false))
        "exec" -> app.openSurface(Surface.ExecCommand)
        "terminals" -> app.openSurface(Surface.BackgroundTerminals)
        "realtime" -> app.openSurface(Surface.Realtime)
        "review" -> app.onAppEvent(AppEvent.SubmitSlashCommand("review", ""))
        "worktree" -> app.openSurface(Surface.Worktrees)
        "diff" -> app.openSurface(Surface.Diff)
        "goal" -> app.onAppEvent(AppEvent.SubmitSlashCommand("goal", ""))
        "history" -> app.openSurface(Surface.ThreadHistory)
        "status" -> app.openSurface(Surface.SessionStatus)
        else -> Unit
    }
    // Read so the compiler sees this table depends on the open session.
    if (threadId.isEmpty()) return
}

/** One transcript row after folding; [indices] keep each row reading its own element in its own scope. */
internal data class TranscriptRow(
    val key: String,
    val indices: List<Int>,
    val exposed: Boolean,
)

internal fun foldTranscriptRows(items: List<ThreadItem>): List<TranscriptRow> {
    val rows = ArrayList<TranscriptRow>()
    var index = 0
    while (index < items.size) {
        val item = items[index]
        if (item is CommandExecutionItem && item.isExploringCall()) {
            var end = index + 1
            while (
                end < items.size && (items[end] as? CommandExecutionItem)?.isExploringCall() == true
            ) {
                end++
            }
            rows += TranscriptRow("explored:${item.id}", (index until end).toList(), exposed = true)
            index = end
        } else {
            rows += TranscriptRow(item.id, listOf(index), exposed = false)
            index++
        }
    }
    return rows
}

@Composable
private fun ExploredGroupRow(
    commands: List<CommandExecutionItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = commands.any { it.status == CommandExecutionStatus.InProgress }
    val labels = commands.mapNotNull { command ->
        command.commandActions.firstOrNull()?.let { label -> commandActionLabel(label) }
    }
    val summary =
        labels.take(3).joinToString(" · ") + if (labels.size > 3) " +${labels.size - 3}" else ""
    CollapsibleSection(
        title =
            stringResource(
                if (active) R.string.exec_cell_exploring else R.string.exec_cell_explored
            ),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        subtitle = summary.ifEmpty { null },
        modifier = modifier,
    ) {
        commands.forEach { command -> CommandExecutionCell(command) }
    }
}

/**
 * The transcript: scrollable item list sticky to the bottom while a turn streams, diagnostics as
 * notices. Mirrors the history viewport of `codex-rs/tui/src/chatwidget.rs`.
 */
@Composable
internal fun Transcript(
    items: List<ThreadItem>,
    diagnostics: List<SessionDiagnostic>,
    isStreaming: (ThreadItem) -> Boolean,
    streamFor: (String) -> MarkdownStream?,
    plan: List<com.cy.codex.protocol.protocol.v2.PlanStep>,
    loading: Boolean,
    empty: Boolean,
    cwd: String?,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
    onAnswerQuestion: (String) -> Unit,
    canLoadEarlier: Boolean,
    loadingEarlier: Boolean,
    onLoadEarlier: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    itemGap: Dp = 18.dp,
    planGap: Dp = 10.dp,
) {
    val listState = rememberLazyListState()
    AutoPager(listState = listState)

    if (empty) {
        EmptyTranscript(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding)
        return
    }

    // Content-equal diagnostics would collide as keys; one identity per notice, pruned on eviction.
    val diagnosticKeys = remember { IdentityKeys<SessionDiagnostic>() }
    // Derived fold: a streaming write inside a row must not rewrite it.
    val rowsState = remember(items) { derivedStateOf { foldTranscriptRows(items) } }
    val rows = rowsState.value

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        if (canLoadEarlier || loadingEarlier) {
            item(key = "load-earlier") {
                LoadEarlierRow(loading = loadingEarlier, onLoad = onLoadEarlier)
            }
        }
        // Not copied: rows read their own elements in their own scope, so a one-element delta rebuilds one row.
        items(count = rows.size, key = { rows[it].key }) { index ->
            val row = rows[index]
            val item = row.indices.firstOrNull()?.let { items.getOrNull(it) } ?: return@items
            Column(modifier = Modifier.fillMaxWidth()) {
                if (row.exposed) {
                    ExploredGroupRow(
                        commands =
                            row.indices.mapNotNull { items.getOrNull(it) as? CommandExecutionItem }
                    )
                } else {
                    ThreadItemCell(
                        item = item,
                        stream = streamFor(item.id),
                        streaming = isStreaming(item),
                        assistantLabel = stringResource(R.string.chat_transcript_assistant_label),
                        cwd = cwd,
                        onOpenAgent = onOpenAgent,
                        onOpenAgentInfo = onOpenAgentInfo,
                        onAnswerQuestion = onAnswerQuestion,
                    )
                }
                if (item is AgentMessageItem && plan.isNotEmpty() && index == rows.lastIndex) {
                    Spacer(Modifier.height(planGap))
                    PlanTimeline(steps = plan)
                }
            }
        }
        items(
            count = diagnostics.size,
            key = { index -> diagnosticKeys.keyOf(diagnostics[index], diagnostics) },
        ) { index ->
            DiagnosticCell(diagnostic = diagnostics[index])
        }
        if (loading) {
            item(key = "loading") { LoadingRow() }
        }
    }
}

@Composable
private fun LoadEarlierRow(loading: Boolean, onLoad: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Button(
            onClick = onLoad,
            enabled = !loading,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        if (loading) R.string.transcript_load_earlier_loading
                        else R.string.transcript_load_earlier
                    ),
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Keeps the newest row against the bottom of the viewport while a turn writes, and leaves
 * the page alone the moment the reader is not at the bottom. Driven by layout, not item
 * count — a delta usually lands inside the newest row, and content arriving moves the
 * viewport off the bottom by itself; only a scroll the reader drove unpins the follow.
 */
@Composable
private fun AutoPager(listState: LazyListState) {
    // A touch down unpins before the drag: intent is known the moment the finger lands.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var pinned by remember { mutableStateOf(true) }
    // True while this pager moves the list itself, so its own scroll is not read as the reader's.
    var parking by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.atNewestRow() }
            .collect { atNewest ->
                if (atNewest) {
                    pinned = true
                } else if (!parking && (dragging || listState.isScrollInProgress)) {
                    pinned = false
                }
            }
    }

    LaunchedEffect(listState) {
        // Keyed on layout: the followed event is a remeasure, not an item-count change.
        snapshotFlow { listState.layoutInfo }
            .collect {
                if (pinned && !dragging) {
                    parking = true
                    try {
                        listState.parkOnNewestRow()
                    } finally {
                        parking = false
                    }
                }
            }
    }
}

private fun LazyListState.atNewestRow(): Boolean {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    return last.index == layout.totalItemsCount - 1 &&
        last.offset + last.size <= layout.viewportEndOffset - layout.afterContentPadding + 1
}

/**
 * Bring the newest row to the bottom of the viewport. Row already on screen: no animation
 * (per-delta animations stutter). Next row: animated, including the distance to its bottom edge.
 * Newly loaded transcript: jumped — animating a screen of history is a ride nobody asked for.
 */
private suspend fun LazyListState.parkOnNewestRow() {
    val layout = layoutInfo
    val newest = layout.totalItemsCount - 1
    if (newest < 0) return
    val visible = layout.visibleItemsInfo.lastOrNull() ?: return

    val paging = visible.index == newest - 1
    when {
        paging -> animateScrollToItem(newest)
        visible.index < newest -> scrollToItem(newest)
    }

    // Land on the bottom edge: the newest row is usually taller than the viewport, and the line
    // being written is the last one.
    val end = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val distance =
        end.offset + end.size - (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
    if (distance < 1) return
    if (paging) animateScrollBy(distance.toFloat()) else scrollBy(distance.toFloat())
}

@Composable
private fun LoadingRow(
    corner: Dp = UiConsts.CornerControl,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    iconSize: Dp = 15.dp,
    iconGap: Dp = 10.dp,
    textSize: TextUnit = UiType.Subtitle,
    textLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(corner))
                .background(raisedSurface())
                .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = colors.primary,
        )
        Spacer(Modifier.width(iconGap))
        Text(
            text = stringResource(R.string.chat_loading_history),
            fontSize = textSize,
            lineHeight = textLineHeight,
            color = colors.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun EmptyTranscript(
    modifier: Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    iconBoxSize: Dp = UiConsts.IconBoxLarge,
    iconBoxCorner: Dp = UiConsts.CornerCard,
    iconSize: Dp = UiConsts.IconHeaderSmall,
    iconGap: Dp = 14.dp,
    titleSize: TextUnit = UiType.Display,
    titleLineHeight: TextUnit = UiType.DisplayLine,
    titleGap: Dp = 6.dp,
    hintSize: TextUnit = UiType.CardTitle,
    hintLineHeight: TextUnit = UiType.MessageLine,
    hintGap: Dp = 2.dp,
    slashHintSize: TextUnit = UiType.Subtitle,
    slashHintLineHeight: TextUnit = UiType.SheetTitle,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = modifier.padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier.size(iconBoxSize)
                        .squircleClip(iconBoxCorner)
                        .background(raisedSurface()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Community,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = colors.primary,
                )
            }
            Spacer(Modifier.height(iconGap))
            Text(
                text = stringResource(R.string.chat_empty_title),
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(titleGap))
            Text(
                text = stringResource(R.string.chat_empty_hint),
                fontSize = hintSize,
                lineHeight = hintLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(hintGap))
            Text(
                text = stringResource(R.string.chat_empty_slash_hint),
                fontSize = slashHintSize,
                lineHeight = slashHintLineHeight,
                color = colors.onSurfaceVariantSummary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
internal fun StatusChip(
    label: String,
    tone: ThreadStatusTone,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    dotSize: Dp = 6.dp,
    dotGap: Dp = 6.dp,
    textSize: TextUnit = UiType.RowDetail,
    textLineHeight: TextUnit = UiType.Message,
) {
    Row(
        modifier =
            modifier.clip(CircleShape).background(statusPillSurface(tone)).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(dotSize).clip(CircleShape).background(statusDotColor(tone)))
        Spacer(Modifier.width(dotGap))
        Text(
            text = label,
            fontSize = textSize,
            lineHeight = textLineHeight,
            color = statusDotColor(tone),
            maxLines = 1,
        )
    }
}

/** Cache-relative name of the file `ACTION_EDIT` edits; matches `res/xml/file_paths.xml`. */
private const val ComposerDraftFile = "drafts/composer.txt"

/** Queue tray and composer; reads live here so a keystroke or queue change does not recompose the content behind it. */
@Composable
private fun ComposerDock(
    app: CodexApp,
    session: SessionState,
    threadNameOf: (String) -> String,
    promptBarStartInset: Dp,
    backdrop: Backdrop,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
    composerGap: Dp = 8.dp,
    queuedEnterDurationMs: Int = Motion.EnterMs,
    queuedExitDurationMs: Int = Motion.ExitMs,
) {
    // The draft lives in the session: a slash prefill and a quote offer write it too.
    val prompt = session.composerDraft
    val onPromptChange: (String) -> Unit = { app.onAppEvent(AppEvent.SetComposerDraft(it)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var misalignmentReview by remember {
        mutableStateOf<com.cy.codex.protocol.protocol.v2.MisalignmentErrorDetails?>(null)
    }
    // Snapshot for the editor: it cannot wipe the draft, and the result code is ignored — several
    // editors return CANCELED after writing the file.
    var editorOriginal by remember { mutableStateOf<String?>(null) }
    val externalEditor =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val original = editorOriginal
            editorOriginal = null
            val edited = runCatching {
                File(context.cacheDir, ComposerDraftFile).readText()
            }.getOrNull()
            if (edited != null && edited != original) onPromptChange(edited)
        }

    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = UiConsts.ScreenMargin),
        verticalArrangement = Arrangement.spacedBy(composerGap),
    ) {
        ApprovalNoticeBar(
            foreign = app.widget.otherThreadApprovals,
            threadName = threadNameOf,
            reviews = app.widget.pendingReviews,
            denials = app.widget.approvalDenials,
            onOpenThread = app::openThread,
            onApproveDenial = { denial ->
                app.onAppEvent(AppEvent.ApproveGuardianDeniedAction(denial.threadId, denial.itemId))
            },
            onDismissDenial = { denial ->
                app.onAppEvent(AppEvent.DismissAutoReviewDenial(denial.itemId))
            },
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )
        AnimatedVisibility(
            visible = session.queued.isNotEmpty(),
            enter =
                fadeIn(tween(queuedEnterDurationMs, easing = Motion.EnterEasing)) +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(queuedEnterDurationMs, easing = Motion.EnterEasing),
                    ),
            exit =
                fadeOut(tween(queuedExitDurationMs, easing = Motion.ExitEasing)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(queuedExitDurationMs, easing = Motion.ExitEasing),
                    ),
        ) {
            AttachmentTray(
                attachments = session.attachments,
                onRemove = { attachment ->
                    app.onAppEvent(
                        AppEvent.RemoveAttachment(
                            threadId = session.threadId,
                            type = AttachmentType.fromWire(attachment.attachmentType),
                            identityKey = attachment.identityKey,
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
            QueuedMessages(
                messages = session.queued,
                onStart = { entry -> app.onAppEvent(AppEvent.StartQueuedMessage(entry.id)) },
                onMove = { entry, delta ->
                    app.onAppEvent(AppEvent.MoveQueuedMessage(entry.id, delta))
                },
                onRemove = { entry -> app.onAppEvent(AppEvent.DeleteQueuedMessage(entry.id)) },
                // Non-text inputs ride along: the sheet edits only the body.
                onEdit = { entry, body ->
                    val kept = entry.input.filterNot { it is UserInput.Text }
                    app.onAppEvent(
                        AppEvent.UpdateQueuedMessage(
                            queuedId = entry.id,
                            inputs = listOf(UserInput.Text(body)) + kept,
                        )
                    )
                },
                onClear = { app.onAppEvent(AppEvent.ClearQueue) },
                onDismiss = {},
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }

        session.misalignment?.let { details ->
            MisalignmentBar(
                details = details,
                onReview = { misalignmentReview = details },
                onContinue = { app.onAppEvent(AppEvent.ContinueMisalignment) },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }
        misalignmentReview?.let { details ->
            MisalignmentReviewSheet(
                details = details,
                onDismiss = { misalignmentReview = null },
            )
        }

        app.sideParentOf(session.threadId)?.let { parent ->
            SideConversationBanner(
                parentLabel = threadNameOf(parent),
                onBack = { app.onAppEvent(AppEvent.ToggleSideConversation()) },
                modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
            )
        }

        TurnActivityBar(
            running = session.running,
            startedAtMs = session.turnStartedAtMs,
            detail = remember(session.itemsRevision) { activeToolDetail(session.items) },
            hookStatus = session.hookStatus,
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )

        ComposerImageTray(
            images = session.composerImages,
            onRemove = { image -> app.onAppEvent(AppEvent.RemoveComposerImage(image.path)) },
            modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
        )

        Composer(
            value = prompt,
            onValueChange = onPromptChange,
            // Held one second after the last edit, so an approval dialog cannot steal the keyboard mid-sentence.
            onActivity = app.widget::noteComposerActivity,
            onSubmit = {
                // Staged images come from the draft, so a deleted placeholder cannot resurrect its file.
                val inputs = session.pendingTurnInputs()
                if (inputs.isNotEmpty()) {
                    app.onAppEvent(AppEvent.SubmitUserMessage(inputs))
                }
            },
            onInterrupt = { app.onAppEvent(AppEvent.InterruptTurn) },
            onAttach = onAttach,
            history = ComposerHistory.entries,
            onCopyLastResponse = {
                val last = session.items.lastOrNull { it is AgentMessageItem } as? AgentMessageItem
                if (last == null || last.text.isBlank()) {
                    scope.launch {
                        app.snackbar.showSnackbar(
                            context.getString(R.string.composer_copy_last_empty)
                        )
                    }
                } else {
                    copyToClipboard(
                        context,
                        last.text,
                        context.getString(R.string.copy_sheet_whole_response),
                    )
                }
            },
            onOpenExternalEditor = {
                val file = File(context.cacheDir, ComposerDraftFile)
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(prompt)
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    val intent =
                        Intent(Intent.ACTION_EDIT)
                            .setDataAndType(uri, "text/plain")
                            .addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                    editorOriginal = prompt
                    externalEditor.launch(intent)
                }
                    .onFailure {
                        scope.launch {
                            app.snackbar.showSnackbar(
                                context.getString(R.string.composer_external_editor_failed)
                            )
                        }
                    }
            },
            running = session.running,
            enabled =
                app.startupReady &&
                    !session.loading &&
                    !app.creatingThread &&
                    !session.config.blocksDirectInput &&
                    session.misalignment == null,
            hint =
                when {
                    session.config.blocksDirectInput ->
                        stringResource(R.string.chat_composer_hint_parent_owned)
                    // The same shell-mode signal upstream shows in its footer.
                    prompt.startsWith("!") -> stringResource(R.string.chat_composer_hint_shell)
                    session.open -> stringResource(R.string.chat_composer_hint_open)
                    else -> stringResource(R.string.chat_composer_hint_empty)
                },
            queuedCount = session.queued.size,
            slashSuggestions =
                if (prompt.startsWith("/")) {
                    SidebarModel.slashSuggestions(
                        query = prompt,
                        planAvailable =
                            app.catalog.collaborationModes.any {
                                it.mode == com.cy.codex.protocol.protocol.v2.CollaborationMode.Plan
                            },
                    )
                } else {
                    emptyList()
                },
            onSuggestionPicked = { command ->
                // Argument-less commands dispatch on the spot: `/clear` plus a trailing space is a draft nobody wants.
                if (command.takesArgument) {
                    onPromptChange(command.command + " ")
                } else {
                    app.onAppEvent(AppEvent.SubmitSlashCommand(command.command, ""))
                }
            },
            mentionSuggestions = app.mentionSuggestions,
            onMentionPicked = {},
            onMentionQueryChange = app::onMentionQueryChange,
            // Only enabled skills are offered: one mentioned by name would resolve to nothing.
            skillCandidates = app.catalog.skills.filter { it.enabled }.map { it.name },
            onSkillPicked = {},
            backdrop = backdrop,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = (promptBarStartInset + UiConsts.ScreenMargin).coerceAtLeast(0.dp),
                        end = UiConsts.ScreenMargin,
                    ),
        )
    }
}

/**
 * The agent overview sheet, reads scoped away from [ChatScreen] so usage updates invalidate the
 * sheet, not the chat screen behind it.
 */
@Composable
private fun AgentsOverviewPane(
    app: CodexApp,
    session: SessionState,
    roster: List<AgentRosterEntry>,
    show: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val usage = app.catalog.threadUsage
    val listedThreads = app.catalog.agentThreads + app.threads.threads
    val entries =
        remember(roster, usage, listedThreads) {
            val byId = listedThreads.associateBy { it.id }
            roster.map { agent ->
                agent.withThreadMetadata(byId[agent.threadId], usage[agent.threadId])
            }
        }
    AgentsOverview(
        show = show,
        roster = entries,
        activeThreadId = session.threadId,
        onSelect = onSelect,
        onDismiss = onDismiss,
        onDismissFinished = onDismissFinished,
        totalTokens = entries.sumOf { it.tokens.toLong() },
    )
}

@Composable
private fun SideConversationBanner(
    parentLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UiConsts.CornerChip))
                .background(colors.primary.copy(alpha = 0.10f))
                .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.side_conversation_banner, parentLabel),
            modifier = Modifier.weight(1f),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.side_conversation_back),
            modifier =
                Modifier.clip(RoundedCornerShape(UiConsts.CornerChip))
                    .clickable(onClick = onBack)
                    .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space4),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.primary,
            maxLines = 1,
        )
    }
}

/**
 * Removable `[Image #N]` chips; the placeholder also sits in the draft, so closing deletes
 * both handles to the same attachment.
 */
@Composable
private fun ComposerImageTray(
    images: List<com.cy.codex.ComposerImageAttachment>,
    onRemove: (com.cy.codex.ComposerImageAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (images.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        images.forEach { image ->
            Row(
                modifier =
                    Modifier.clip(RoundedCornerShape(UiConsts.CornerChip))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(
                            start = UiConsts.Space10,
                            end = UiConsts.Space4,
                            top = UiConsts.Space3,
                            bottom = UiConsts.Space3,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = image.placeholder,
                    fontSize = UiType.Chip,
                    lineHeight = UiType.ChipLine,
                    color = colors.onSurface,
                    maxLines = 1,
                )
                IconButton(
                    onClick = { onRemove(image) },
                    minWidth = UiConsts.IconButtonCompact,
                    minHeight = UiConsts.IconButtonCompact,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.composer_remove_attachment),
                        modifier = Modifier.size(UiConsts.IconInline),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/** Attachments between queue and composer — the order they are sent in. */
@Composable
private fun AttachmentTray(
    attachments: List<ThreadAttachment>,
    onRemove: (ThreadAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.PanelCorner) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(glassTint(0.94f), shape)
                .clip(shape)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space7),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier =
                    Modifier.clip(RoundedCornerShape(UiConsts.CornerChip))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .padding(
                            start = UiConsts.Space8,
                            end = UiConsts.Space4,
                            top = UiConsts.Space3,
                            bottom = UiConsts.Space3,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = attachment.identityKey.ifEmpty { attachment.id },
                    fontSize = UiType.Chip,
                    lineHeight = UiType.ChipLine,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { onRemove(attachment) },
                    minWidth = UiConsts.IconButtonCompact,
                    minHeight = UiConsts.IconButtonCompact,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = stringResource(R.string.composer_remove_attachment),
                        modifier = Modifier.size(UiConsts.IconInline),
                        tint = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
