package com.cy.codexui.chatwidget

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.AppEvent
import com.cy.codexui.CodexApp
import com.cy.codexui.CodexButton
import com.cy.codexui.Motion
import com.cy.codexui.R
import com.cy.codexui.SessionDiagnostic
import com.cy.codexui.SquircleShape
import com.cy.codexui.Surface
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.app.AgentPickerSheet
import com.cy.codexui.app.AgentsOverview
import com.cy.codexui.app.deriveAgentRoster
import com.cy.codexui.bottom_pane.ApprovalDialog
import com.cy.codexui.bottom_pane.Composer
import com.cy.codexui.chatwidget.QueuedMessages
import com.cy.codexui.floatingSurface
import com.cy.codexui.glassTint
import com.cy.codexui.history_cell.DiagnosticCell
import com.cy.codexui.history_cell.ThreadItemCell
import com.cy.codexui.protocol.ApprovalRequest
import com.cy.codexui.protocol.ApprovalResponse
import com.cy.codexui.protocol.protocol.item.AgentMessageItem
import com.cy.codexui.protocol.protocol.item.ThreadItem
import com.cy.codexui.protocol.protocol.v2.AttachmentType
import com.cy.codexui.protocol.protocol.v2.ThreadAttachment
import com.cy.codexui.protocol.protocol.v2.UserInput
import com.cy.codexui.raisedSurface
import com.cy.codexui.status.DiffCard
import com.cy.codexui.status.StatusCard
import com.cy.codexui.status.StatusCardButton
import com.cy.codexui.status.StatusPanelState
import com.cy.codexui.statusDotColor
import com.cy.codexui.statusPillSurface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The chat surface: transcript, drawer, status card, approval cards and composer.
 *
 * Mirrors `codex-rs/tui/src/chatwidget.rs` rendered as one screen. Every widget here is fed from
 * [com.cy.codexui.SessionState], and everything the user does is expressed as an
 * [com.cy.codexui.AppEvent] handed to [CodexApp.onAppEvent] — the screen itself owns only
 * presentation state (which drawer is open, which sheet is up, what is typed).
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
    // Every transition here reads its duration from Motion: the panel, the diff card and the
    // queued banner used to enter at 160 / 170 / 180ms, which is three values nobody can tell
    // apart on purpose.
    panelEnterDurationMs: Int = Motion.EnterMs,
    panelExitDurationMs: Int = Motion.ExitMs,
    diffEnterDurationMs: Int = Motion.EnterMs,
    diffExitDurationMs: Int = Motion.ExitMs,
    queuedEnterDurationMs: Int = Motion.EnterMs,
    queuedExitDurationMs: Int = Motion.ExitMs,
) {
    val session = app.widget.state
    val threads = app.threads
    // `OpenDocument` rather than `GetContent`: the protocol takes an identity key the server can
    // read back later, and a document uri is one the app keeps a grant for across a restart.
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            app.importAttachment(uri)
        }
    }
    var mentionPaths by remember(session.config.cwd) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(app.startupReady, session.config.cwd) {
        if (app.startupReady) {
            app.client.readDirectory(session.config.cwd.ifBlank { app.defaultWorkspace })
                .onSuccess { entries -> mentionPaths = entries.map { it.path } }
        }
    }
    val colors = MiuixTheme.colorScheme
    // The draft is read from the session rather than kept in a `remember`, because two other things
    // write it — a slash command that prefills an argument, and a transcript row that offers to
    // quote itself — and both outlive this composable's own state.
    val prompt = session.composerDraft
    val onPromptChange: (String) -> Unit = { app.onAppEvent(AppEvent.SetComposerDraft(it)) }
    var overviewOpen by remember { mutableStateOf(false) }
    // `show` stays true while the sheet animates out: it calls back when it has actually left, and
    // clearing the flag on the request instead would cut the exit off mid-slide.
    var overviewLeaving by remember { mutableStateOf(false) }
    val panelState = remember { StatusPanelState() }

    val mainAgentLabel = stringResource(R.string.agent_roster_main_label)
    val subAgentNameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    val roster = remember(session.items.toList(), session.threadId, mainAgentLabel, subAgentNameFormat) {
        deriveAgentRoster(session.items.toList(), session.threadId, mainAgentLabel, subAgentNameFormat)
    }
    val approval = app.widget.currentApproval
    val backdrop = rememberLayerBackdrop {
        drawRect(colors.background)
        drawContent()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // The transcript column. On a wide window it is inset by the drawer's width *at all times* —
        // centred while the drawer is shut, pushed across when it opens — so the text is wrapped once
        // and never again. Opening the drawer then costs one composited translation instead of a
        // full re-measure of every cell in the list. On a narrow window there is no room for a second
        // column, so the drawer goes back over the text the way it always did.
        val wide = maxWidth >= UiConsts.WideContentBreakpoint
        val drawerWidth = minOf(UiConsts.SidebarWidth, UiConsts.SidebarWidthCap)
        // Where the column's left edge sits while the drawer is open: clear of the drawer by
        // [UiConsts.ContentGap], not flush against it.
        val contentStart = (UiConsts.ScreenMargin + drawerWidth + UiConsts.ContentGap)
            .coerceAtMost(maxWidth)
        val contentWidth = if (wide) {
            (maxWidth - contentStart - UiConsts.ScreenMargin).coerceAtLeast(UiConsts.MinContentWidth)
        } else {
            maxWidth
        }
        // Rest is centred; the open position is the same column translated, so the text is never
        // re-measured — the width above does not depend on whether the drawer is open.
        val centredStart = (maxWidth - contentWidth) / 2
        val contentShift by animateDpAsState(
            targetValue = if (wide && sidebarExpanded) contentStart - centredStart else 0.dp,
            animationSpec = Motion.PanelDp,
            label = "contentShift",
        )
        // The backdrop layer spans the *window*, not the column. `layerBackdrop` only captures what
        // is drawn inside it, and everything that samples it — the composer across its full width,
        // the two progressive-blur bands — reaches past the column's edges. A layer as wide as the
        // column left those samples reading outside the captured texture, which is what put flat
        // colour blocks in the composer's glass. The background rect keeps the capture opaque under
        // the text, so the blur has no transparent pixels to smear colour into.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(contentWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = contentShift.toPx() },
            ) {
                if (!app.startupReady || (!session.open && !session.loading) ||
                    (session.open && session.items.isEmpty() && session.diagnostics.isEmpty() && !session.running)) {
                    RuntimeTranscript(app, Modifier.fillMaxSize().padding(
                        horizontal = UiConsts.ScreenMargin,
                        vertical = UiConsts.TranscriptTopInset + UiConsts.PromptBarHeight,
                    ))
                } else Transcript(
                    items = session.items.toList(),
                    diagnostics = session.diagnostics.toList(),
                    streamingItemId = session.streamingItemId,
                    plan = session.plan.toList(),
                    loading = session.loading,
                    empty = !session.open && !session.loading,
                    onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
                    onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = UiConsts.TranscriptGutter,
                        end = UiConsts.TranscriptGutter,
                        top = topInset + UiConsts.TranscriptTopInset,
                        bottom = bottomInset + UiConsts.PromptBarHeight + UiConsts.ScreenMargin * 2 +
                            UiConsts.TranscriptBottomInset,
                    ),
                )
            }
        }

        // Top and bottom progressive blur: the transcript fades under the floating chrome instead
        // of being cut off by it.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset + topBlurHeight)
                .progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    blurRadius = topBlurRadius,
                    gradient = ProgressiveBlur.Top.copy(endFraction = 0.92f),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset + bottomBlurHeight)
                .progressiveTextureBlur(
                    backdrop = backdrop,
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    blurRadius = bottomBlurRadius,
                    gradient = ProgressiveBlur.Bottom.copy(endFraction = 0.92f),
                ),
        )

        StatusCardButton(
            open = panelState.open,
            onClick = { panelState.toggle() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        // The right-hand panels. Two cards, not one: the diff card is *added* to the left of the
        // status card, so the card the user was reading keeps its size and its place under the
        // button. Widening one card meant the section column slid sideways as the diff opened and
        // slid back as it closed, and the row that had just been tapped was no longer where it was
        // tapped.
        val paneFile = session.turnDiff.firstOrNull { it.path == panelState.paneFilePath }
        val diffOpen = panelState.openFilePath != null
        val panelMax = maxWidth - UiConsts.ScreenMargin * 2
        val statusWidth = minOf(UiConsts.StatusPanelWidth, panelMax)
        // Both cards fit side by side on anything tablet-shaped; on a narrow window the diff takes
        // the whole strip and the status card steps aside rather than being pushed off-screen.
        val sideBySide = panelMax >= statusWidth + UiConsts.PanelGap + UiConsts.MinDiffPaneWidth
        val diffWidth = if (sideBySide) {
            minOf(UiConsts.DiffPaneWidth, panelMax - statusWidth - UiConsts.PanelGap)
        } else {
            minOf(UiConsts.DiffPaneWidth, panelMax)
        }
        var statusHeight by remember { mutableStateOf(0.dp) }
        val diffHeight = statusHeight.coerceIn(minDiffHeight, maxDiffHeight)

        AnimatedVisibility(
            visible = panelState.open,
            enter = fadeIn(tween(panelEnterDurationMs, easing = Motion.EnterEasing)) + scaleIn(
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = Motion.Panel,
            ),
            exit = fadeOut(tween(panelExitDurationMs, easing = Motion.ExitEasing)) + scaleOut(
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(panelExitDurationMs, easing = Motion.ExitEasing),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    end = UiConsts.ScreenMargin,
                    top = topInset + UiConsts.ScreenMargin + panelTopOffset,
                ),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiConsts.PanelGap),
                verticalAlignment = Alignment.Top,
            ) {
                AnimatedVisibility(
                    visible = diffOpen && paneFile != null,
                    // Grows out of the status card's edge, leftwards: the new card is the one that
                    // moves, and the card beside it is the anchor it moves away from.
                    enter = expandHorizontally(
                        expandFrom = Alignment.End,
                        animationSpec = tween(diffEnterDurationMs, easing = Motion.EnterEasing),
                    ) + fadeIn(tween(diffEnterDurationMs, easing = Motion.EnterEasing)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        animationSpec = tween(diffExitDurationMs, easing = Motion.ExitEasing),
                    ) + fadeOut(tween(diffExitDurationMs, easing = Motion.ExitEasing)),
                ) {
                    // The last opened file survives the close, so the card still has something to
                    // draw while it shrinks away.
                    paneFile?.let { file ->
                        DiffCard(
                            file = file,
                            siblings = session.turnDiff,
                            onClose = panelState::closeFile,
                            width = diffWidth,
                            height = diffHeight,
                        )
                    }
                }
                if (!diffOpen || sideBySide) {
                    StatusCard(
                        state = panelState,
                        session = session.config,
                        status = session.status,
                        usage = session.usage,
                        turnDiff = session.turnDiff,
                        plan = session.plan.toList(),
                        roster = roster,
                        items = session.items.toList(),
                        width = statusWidth,
                        maxHeight = statusCardMaxHeight,
                        models = app.catalog.models,
                        onModel = { app.onAppEvent(AppEvent.SetModel(it)) },
                        onEffort = { app.onAppEvent(AppEvent.SetReasoningEffort(it)) },
                        onPolicy = { app.onAppEvent(AppEvent.SetApprovalPolicy(it)) },
                        onCompact = { app.onAppEvent(AppEvent.CompactThread(session.threadId)) },
                        onOpenAgents = { overviewOpen = true },
                        onOpenAgent = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)) },
                        onOpenAgentInfo = { threadId -> app.openSurface(Surface.SubAgent(threadId)) },
                        modifier = Modifier.onSizeChanged {
                            statusHeight = with(density) { it.height.toDp() }
                        },
                    )
                }
            }
        }

        // The composer only steps aside for the drawer, and only on a wide window: it keeps the full
        // width of the transcript column otherwise. It is a single bar, so re-measuring *it* is cheap
        // — the point of holding the transcript's width fixed is that the list underneath is not
        // re-measured with it.
        val promptBarStartInset by animateDpAsState(
            // The composer lines up with the column, so it starts at the same place the column does.
            targetValue = if (wide && sidebarExpanded) contentStart - UiConsts.ScreenMargin else 0.dp,
            animationSpec = Motion.PanelDp,
            label = "promptBarStartInset",
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = UiConsts.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(composerGap),
        ) {
            AnimatedVisibility(
                visible = session.queued.isNotEmpty(),
                enter = fadeIn(tween(queuedEnterDurationMs, easing = Motion.EnterEasing)) +
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(queuedEnterDurationMs, easing = Motion.EnterEasing),
                    ),
                exit = fadeOut(tween(queuedExitDurationMs, easing = Motion.ExitEasing)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(queuedExitDurationMs, easing = Motion.ExitEasing),
                    ),
            ) {
                AttachmentTray(
                    attachments = session.attachments.toList(),
                    onRemove = { attachment ->
                        app.onAppEvent(
                            AppEvent.RemoveAttachment(
                                threadId = session.threadId,
                                type = AttachmentType.fromWire(attachment.attachmentType),
                                identityKey = attachment.identityKey,
                            ),
                        )
                    },
                    modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
                )
                QueuedMessages(
                    messages = session.queued.toList(),
                    onStart = { entry -> app.onAppEvent(AppEvent.StartQueuedMessage(entry.id)) },
                    onMove = { entry, delta -> app.onAppEvent(AppEvent.MoveQueuedMessage(entry.id, delta)) },
                    onRemove = { entry -> app.onAppEvent(AppEvent.DeleteQueuedMessage(entry.id)) },
                    // The non-text inputs are carried across untouched: the sheet edits the body,
                    // and a queued image is not something a text field can have an opinion about.
                    onEdit = { entry, body ->
                        val kept = entry.input.filterNot { it is UserInput.Text }
                        app.onAppEvent(
                            AppEvent.UpdateQueuedMessage(
                                queuedId = entry.id,
                                inputs = listOf(UserInput.Text(body)) + kept,
                            ),
                        )
                    },
                    onClear = { app.onAppEvent(AppEvent.ClearQueue) },
                    onDismiss = {},
                    modifier = Modifier.padding(horizontal = UiConsts.ScreenMargin),
                )
            }

            Composer(
                value = prompt,
                onValueChange = onPromptChange,
                onSubmit = {
                    if (prompt.isNotBlank()) {
                        app.onAppEvent(
                            AppEvent.SubmitUserMessage(
                                listOf(com.cy.codexui.protocol.protocol.v2.UserInput.Text(prompt)),
                            ),
                        )
                    }
                },
                onInterrupt = { app.onAppEvent(AppEvent.InterruptTurn) },
                onAttach = { attachmentPicker.launch(arrayOf("*/*")) },
                running = session.running,
                enabled = app.startupReady && !session.loading && !app.creatingThread,
                hint = if (session.open) {
                    stringResource(R.string.chat_composer_hint_open)
                } else {
                    stringResource(R.string.chat_composer_hint_empty)
                },
                queuedCount = session.queued.size,
                slashSuggestions = if (prompt.startsWith("/")) {
                    SidebarModel.slashSuggestions().filter {
                        prompt.length <= 1 || it.command.startsWith(prompt, ignoreCase = true)
                    }
                } else {
                    emptyList()
                },
                onSuggestionPicked = { command ->
                    // A command that takes no argument is dispatched on the spot rather than typed
                    // out and submitted: `/clear` with a trailing space is a draft nobody wants,
                    // and the TUI runs it the moment it is picked.
                    if (command.takesArgument) {
                        onPromptChange(command.command + " ")
                    } else {
                        app.onAppEvent(AppEvent.SubmitSlashCommand(command.command, ""))
                    }
                },
                mentionCandidates = mentionPaths,
                // The composer already spliced the picked path into the draft; this hook exists for
                // surfaces that want to react to the mention itself (nothing does yet).
                onMentionPicked = {},
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = (promptBarStartInset + UiConsts.ScreenMargin).coerceAtLeast(0.dp),
                        end = UiConsts.ScreenMargin,
                    ),
            )
        }

        // `AgentPickerSheet` is the roster as a filterable list; it has no trigger yet — the status
        // card's agent section opens the overview instead — so it is not mounted here.
        AgentsOverview(
            show = overviewOpen || overviewLeaving,
            roster = roster,
            activeThreadId = session.threadId,
            onSelect = { threadId -> app.openSurface(Surface.SubAgentThread(threadId)); overviewLeaving = true },
            onDismiss = { overviewLeaving = true },
            onDismissFinished = {
                overviewLeaving = false
                overviewOpen = false
            },
            totalTokens = session.usage.totalTokens,
        )

        // The drawer is painted last and sized to the window: it is a drawer, so the transcript and
        // the composer both pass underneath it and it reaches the bottom edge instead of stopping
        // short of the thing it is covering. The composer still steps aside (promptBarStartInset),
        // so nothing the user has to reach is hidden behind it.
        SidebarPanel(
            expanded = sidebarExpanded,
            onExpandedChange = onSidebarExpandedChange,
            actions = SidebarModel.actions(),
            onAction = { entry -> openSurfaceFor(app, entry.id) },
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
            // Same bottom line as the composer: the drawer and the composer are the two pieces
            // anchored to the bottom of the transcript, and they end together.
            maxPanelHeight = (
                maxHeight - topInset - UiConsts.ScreenMargin * 2
                ).coerceAtLeast(minPanelHeight),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = UiConsts.ScreenMargin, top = topInset + UiConsts.ScreenMargin),
        )

        // Window-level, so it is on screen whatever else is open: the turn is blocked on it, and a
        // request that only showed up inside a collapsed panel would read as a hung session.
        ApprovalDialog(
            request = approval,
            busy = app.widget.answeringApproval,
            error = app.widget.approvalError,
            onDecision = { request, response -> onApprovalDecision(app, request, response) },
            remainingQueue = (app.widget.approvalQueueSize - 1).coerceAtLeast(0),
        )
        if (app.goalMenuOpen) {
            GoalSheet(
                goal = session.goal,
                onSet = { app.onAppEvent(AppEvent.SetGoal(it)) },
                onClear = { app.onAppEvent(AppEvent.ClearGoal) },
                onDismiss = { app.goalMenuOpen = false },
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
            Text("Codex", fontSize = UiType.Display, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            when {
                app.startupLoading || app.creatingThread -> {
                    Text(stringResource(if (app.creatingThread) R.string.runtime_creating_thread else R.string.runtime_starting))
                    LinearProgressIndicator(modifier = Modifier.width(160.dp))
                }
                app.startupError != null -> {
                    Text(stringResource(R.string.runtime_startup_failed), color = colors.error)
                    Text(app.startupError.orEmpty(), fontSize = UiType.Meta, color = colors.onSurfaceVariantSummary)
                    CodexButton(stringResource(R.string.runtime_retry), app::bootstrap)
                }
                !session.open && session.threadId.isNotBlank() -> {
                    Text(session.diagnostics.lastOrNull()?.detail.orEmpty(), fontSize = UiType.Meta, color = colors.error)
                    CodexButton(stringResource(R.string.runtime_retry), { app.openThread(session.threadId) })
                    CodexButton(stringResource(R.string.runtime_new_thread), { app.onAppEvent(AppEvent.NewThread()) })
                }
                !app.catalog.account.loggedIn -> {
                    CodexButton(stringResource(R.string.runtime_sign_in), { app.openSurface(Surface.Account) })
                }
                else -> {
                    Text(stringResource(R.string.runtime_ready), fontSize = UiType.CardTitle, color = colors.onSurfaceVariantSummary)
                    Text(session.config.cwd.ifBlank { app.defaultWorkspace }, fontSize = UiType.Meta, color = colors.onSurfaceVariantSummary)
                }
            }
        }
    }
}

private fun onApprovalDecision(app: CodexApp, request: ApprovalRequest, response: ApprovalResponse) {
    app.onAppEvent(AppEvent.ResolveApproval(request.requestId, response))
}

/**
 * One routing table for every "go to this page" id in the app.
 *
 * The drawer and the settings page both name destinations by id, and both have to land on the same
 * page: the entries moved out of the drawer into settings, and a routing table per caller is how the
 * two drift apart.
 */
internal fun openSurfaceFor(app: CodexApp, id: String) {
    // The three routes that need a subject take it from the open session rather than from the id:
    // there is exactly one open thread, and a caller that had to pass its id would be able to pass
    // one that is no longer open.
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
        "goal" -> app.onAppEvent(AppEvent.SubmitSlashCommand("goal", ""))
        "history" -> app.openSurface(Surface.ThreadHistory)
        else -> Unit
    }
    // `threadId` is read for the same reason the routes above are: a page that needs the open
    // session takes it from the app, and the compiler should see that this table depends on it.
    if (threadId.isEmpty()) return
}

/**
 * The transcript.
 *
 * Mirrors the history viewport of `codex-rs/tui/src/chatwidget.rs`: a scrollable list of items,
 * sticky to the bottom while a turn streams, plus the session's diagnostics as notices. The plan
 * checklist is rendered inline where the `PlanItem` sits, so the transcript reads in order.
 */
@Composable
private fun Transcript(
    items: List<ThreadItem>,
    diagnostics: List<SessionDiagnostic>,
    streamingItemId: String?,
    plan: List<com.cy.codexui.protocol.protocol.v2.PlanStep>,
    loading: Boolean,
    empty: Boolean,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(itemGap),
    ) {
        items(items.size, key = { items[it].id }) { index ->
            val item = items[index]
            Column(modifier = Modifier.fillMaxWidth()) {
                ThreadItemCell(
                    item = item,
                    streaming = item.id == streamingItemId,
                    assistantLabel = stringResource(R.string.chat_transcript_assistant_label),
                    onOpenAgent = onOpenAgent,
                    onOpenAgentInfo = onOpenAgentInfo,
                )
                if (item is AgentMessageItem && plan.isNotEmpty() && index == items.lastIndex) {
                    Spacer(Modifier.height(planGap))
                    com.cy.codexui.chatwidget.PlanTimeline(steps = plan)
                }
            }
        }
        items(diagnostics.size, key = { "diag-${diagnostics[it].hashCode()}" }) { index ->
            DiagnosticCell(diagnostic = diagnostics[index])
        }
        if (loading) {
            item(key = "loading") { LoadingRow() }
        }
    }
}

/**
 * The transcript's auto-pager: keeps the newest row against the bottom of the viewport as a turn
 * writes into it, and leaves the page alone the moment the reader is not at the bottom.
 *
 * Two things make this more than "scroll to the last item when the list grows", which is what the
 * transcript used to do. The newest row is usually the row that is already there, growing: a delta
 * lands *inside* it, so the item count never changes and an effect keyed on the count sits still
 * while the newest line slides under the fold. And content arriving moves the viewport off the
 * bottom by itself, so "not at the bottom" cannot be read as "the reader left" — a pager that read
 * the two the same way would stop following the moment it started to keep up.
 *
 * It is therefore driven by both the layout and the source of the movement. Every remeasure brings
 * the newest row back, but only while the pager is pinned; the pager is pinned as long as the reader
 * has not scrolled away, and a scroll *they* drove — a drag, a fling, a keyboard or an accessibility
 * scroll — unpins it until the bottom is theirs again. Reading back through a running turn is
 * therefore a page that stays put: the newest content keeps arriving below the fold, and the
 * transcript returns to it only when the reader does.
 */
@Composable
private fun AutoPager(listState: LazyListState) {
    // A touch down ends the follow, not the scroll it turns into: the reader's intent is known the
    // moment their finger lands, and waiting for the drag to travel would let the follow fight the
    // gesture for its first frames.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var pinned by remember { mutableStateOf(true) }
    // Set while this pager is the one moving the list, so its own scroll is not read as the
    // reader's — which is the one way a follower could unpin itself.
    var parking by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.atNewestRow() }.collect { atNewest ->
            if (atNewest) {
                pinned = true
            } else if (!parking && (dragging || listState.isScrollInProgress)) {
                // The viewport left the bottom under a scroll the reader drove. Whatever drove it —
                // a drag, a fling, a trackpad or an accessibility action — the page it landed on is
                // the page they asked for, and it stays there.
                pinned = false
            }
        }
    }

    LaunchedEffect(listState) {
        // Keyed on the layout rather than on the item count, because the event being followed is a
        // remeasure: that is what a delta landing in the newest row produces.
        snapshotFlow { listState.layoutInfo }.collect {
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

/** Whether the transcript is showing the end of its content: the bottom edge of the newest row. */
private fun LazyListState.atNewestRow(): Boolean {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    return last.index == layout.totalItemsCount - 1 &&
        last.offset + last.size <= layout.viewportEndOffset - layout.afterContentPadding + 1
}

/**
 * Brings the transcript's newest row to the bottom of the viewport.
 *
 * Three cases, and they are three because the distance is not the same thing as the motion. A row
 * already on screen is the one that is growing, and it is moved without animation, because an
 * animation per delta is cancelled by the next delta and reads as a stutter. A row that arrived as
 * the next row is a card away and is animated — that is the page turn — and the rest of the
 * distance to its bottom edge is animated with it, so a row taller than the viewport does not slide
 * to its top and then jump. A row further down than that is a transcript that just loaded, and that
 * is jumped rather than slid through, because animating a screen of history is a ride nobody asked
 * for.
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

    // Land on the row's bottom edge rather than on its top: the newest row is often taller than the
    // viewport, and the line being written is the last one. The trailing content padding is the gap
    // under the list, so the row ends exactly where the content ends.
    val end = layoutInfo.visibleItemsInfo.lastOrNull() ?: return
    val distance = end.offset + end.size -
        (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding)
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
        modifier = Modifier
            .fillMaxWidth()
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
                modifier = Modifier
                    .size(iconBoxSize)
                    .clip(SquircleShape(iconBoxCorner))
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

/** Small status chip reused by the transcript header rows. */
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
        modifier = modifier
            .clip(CircleShape)
            .background(statusPillSurface(tone))
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(statusDotColor(tone)),
        )
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

/**
 * The files and images attached to the open thread.
 *
 * Between the queue and the composer, because that is the order they are sent in: queued messages
 * go first, then whatever the tray holds when the next turn starts. Nothing renders when it is
 * empty — an empty tray is a row of chrome that says "nothing here" on every thread that has no
 * attachments, which is most of them.
 */
@Composable
private fun AttachmentTray(
    attachments: List<ThreadAttachment>,
    onRemove: (ThreadAttachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    val colors = MiuixTheme.colorScheme
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingSurface(shape = shape, tint = glassTint(0.94f), elevation = UiConsts.PanelElevation)
            .clip(shape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = UiConsts.Space10, vertical = UiConsts.Space7),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(UiConsts.CornerChip))
                    .background(colors.primary.copy(alpha = 0.12f))
                    .padding(start = UiConsts.Space8, end = UiConsts.Space4, top = UiConsts.Space3, bottom = UiConsts.Space3),
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
