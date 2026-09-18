package com.cy.codexui.status

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.item.ContextCompactionItem
import com.cy.codexui.protocol.protocol.item.ThreadItem
import com.cy.codexui.protocol.protocol.v2.ThreadSessionState
import com.cy.codexui.label
import com.cy.codexui.app.AgentRole
import com.cy.codexui.app.AgentRosterEntry
import com.cy.codexui.chatwidget.PlanTimeline
import com.cy.codexui.protocol.protocol.v2.ThreadStatus
import com.cy.codexui.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codexui.DiffBody
import com.cy.codexui.FileKindBadge
import com.cy.codexui.FileStatText
import com.cy.codexui.FileDiff
import com.cy.codexui.displayDiffPath
import com.cy.codexui.languageFromPath
import com.cy.codexui.runtimeHome
import com.cy.codexui.shortenedParent
import com.cy.codexui.protocol.protocol.v2.ApprovalsReviewer
import com.cy.codexui.protocol.protocol.v2.AskForApproval
import com.cy.codexui.protocol.protocol.v2.ModelPreset
import com.cy.codexui.protocol.protocol.v2.ReasoningEffort
import com.cy.codexui.description
import com.cy.codexui.ButtonRole
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexButtonSize
import com.cy.codexui.ExpandBar
import com.cy.codexui.Motion
import com.cy.codexui.SectionCard
import com.cy.codexui.SquircleShape
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.UiConsts
import com.cy.codexui.floatingSurface
import com.cy.codexui.panelColor
import com.cy.codexui.pressableRow
import com.cy.codexui.statusDotColor
import com.cy.codexui.tone
import com.cy.codexui.usageColor
import com.cy.codexui.UiType
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * The floating status card.
 *
 * Mirrors `codex-rs/tui/src/status/card.rs` and `chatwidget/status_surfaces.rs`: one window that
 * answers "what is this session doing, with which model, how full is the context, and what has it
 * changed".
 *
 * The card is a fixed size and never moves: opening a file's diff opens a *second* card beside it
 * (see [DiffCard]) instead of widening this one. Growing the card meant the section column slid
 * sideways every time a diff opened, so the row the user had just tapped was no longer under their
 * finger, and closing the diff moved it back.
 */
@Composable
fun StatusCard(
    state: StatusPanelState,
    session: ThreadSessionState,
    status: ThreadStatus,
    usage: ThreadTokenUsage,
    turnDiff: List<FileDiff>,
    plan: List<com.cy.codexui.protocol.protocol.v2.PlanStep>,
    roster: List<AgentRosterEntry>,
    items: List<ThreadItem>,
    width: Dp,
    maxHeight: Dp,
    models: List<ModelPreset>,
    onModel: (String) -> Unit,
    onEffort: (ReasoningEffort) -> Unit,
    onPolicy: (AskForApproval) -> Unit,
    onReviewer: (ApprovalsReviewer) -> Unit,

    onCompact: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    panelElevation: Dp = UiConsts.PanelElevation,
) {
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    ExpandBar(
        width = width,
        height = null,
        shape = shape,
        elevation = panelElevation,
        expanded = true,
        modifier = modifier.heightIn(max = maxHeight),
    ) {
        SectionsColumn(
            state = state,
            session = session,
            status = status,
            usage = usage,
            turnDiff = turnDiff,
            plan = plan,
            roster = roster,
            items = items,
            models = models,
            onModel = onModel,
            onEffort = onEffort,
            onPolicy = onPolicy,
            onReviewer = onReviewer,
            onCompact = onCompact,
            onOpenAgents = onOpenAgents,
            onOpenAgent = onOpenAgent,
            onOpenAgentInfo = onOpenAgentInfo,
        )
    }
}

/**
 * The diff card that opens to the left of the status card.
 *
 * Mirrors `codex-rs/tui/src/diff_render.rs`: one file's unified diff with a header, two gutter line
 * numbers, horizontally scrollable code lines and a footer that switches files without closing the
 * card. It is sized to the card beside it, so the two read as one panel split in two rather than as
 * two unrelated windows.
 */
@Composable
fun DiffCard(
    file: FileDiff,
    siblings: List<FileDiff>,
    onClose: () -> Unit,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    cwd: String? = null,
    panelElevation: Dp = UiConsts.PanelElevation,
) {
    val shape = remember { SquircleShape(UiConsts.PanelCorner) }
    ExpandBar(
        width = width,
        height = height,
        shape = shape,
        elevation = panelElevation,
        expanded = true,
        modifier = modifier,
    ) {
        DiffPane(
            file = file,
            siblings = siblings,
            onClose = onClose,
            cwd = cwd,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SectionsColumn(
    state: StatusPanelState,
    session: ThreadSessionState,
    status: ThreadStatus,
    usage: ThreadTokenUsage,
    turnDiff: List<FileDiff>,
    plan: List<com.cy.codexui.protocol.protocol.v2.PlanStep>,
    roster: List<AgentRosterEntry>,
    items: List<ThreadItem>,
    models: List<ModelPreset>,
    onModel: (String) -> Unit,
    onEffort: (ReasoningEffort) -> Unit,
    onPolicy: (AskForApproval) -> Unit,
    onReviewer: (ApprovalsReviewer) -> Unit,

    onCompact: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    sectionGap: Dp = 7.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(sectionGap),
    ) {
        CardHeader(
            session = session,
            status = status,
            fileCount = turnDiff.size,
            agentCount = roster.size,
            onOpenAgents = onOpenAgents,
        )

        UsageSection(
            usage = usage,
            compacted = items.any { it is ContextCompactionItem },
            collapsed = state.isFolded(StatusSection.Usage),
            onToggle = { state.toggleSection(StatusSection.Usage) },
            onCompact = onCompact,
        )

        ModelSection(
            session = session,
            models = models,
            collapsed = state.isFolded(StatusSection.Model),
            onToggle = { state.toggleSection(StatusSection.Model) },
            onModel = onModel,
            onEffort = onEffort,
            onPolicy = onPolicy,
            onReviewer = onReviewer,
        )

        if (plan.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.status_card_plan_title),
                icon = MiuixIcons.Notes,
                trailing = "${plan.count { it.status == com.cy.codexui.protocol.protocol.v2.PlanStepStatus.Completed }}/${plan.size}",
                expandable = true,
                expanded = !state.isFolded(StatusSection.Plan),
                onToggle = { state.toggleSection(StatusSection.Plan) },
            ) {
                PlanTimeline(steps = plan)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            AgentsSection(
                roster = roster,
                collapsed = state.isFolded(StatusSection.Agents),
                onToggle = { state.toggleSection(StatusSection.Agents) },
                onOpenAgents = onOpenAgents,
                onOpenAgent = onOpenAgent,
                onOpenAgentInfo = onOpenAgentInfo,
                modifier = Modifier
                    .weight(1.12f)
                    .fillMaxHeight(),
            )

            FilesSection(
                files = turnDiff,
                cwd = session.cwd,
                openPath = state.openFilePath,
                collapsed = state.isFolded(StatusSection.Files),
                onToggle = { state.toggleSection(StatusSection.Files) },
                onOpen = state::toggleFile,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CardHeader(
    session: ThreadSessionState,
    status: ThreadStatus,
    fileCount: Int,
    agentCount: Int,
    onOpenAgents: () -> Unit,
    headerPadding: PaddingValues = PaddingValues(horizontal = 2.dp),
    iconBoxSize: Dp = UiConsts.IconHeaderSmall,
    iconSize: Dp = 20.dp,
    iconGap: Dp = 7.dp,
    dotSize: Dp = 7.dp,
    dotGap: Dp = 6.dp,
    buttonSize: Dp = UiConsts.IconHeaderSmall,
    buttonIconSize: Dp = 16.dp,
    nameSize: TextUnit = UiType.Message,
    nameLineHeight: TextUnit = UiType.Title,
    summarySize: TextUnit = UiType.Footnote,
    summaryLineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    val tone = status.tone()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(headerPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(iconBoxSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = MiuixIcons.Tasks,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = colors.primary,
            )
        }
        Spacer(Modifier.width(iconGap))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(CircleShape)
                        .background(statusDotColor(tone)),
                )
                Spacer(Modifier.width(dotGap))
                Text(
                    text = session.displayName,
                    modifier = Modifier.weight(1f),
                    fontSize = nameSize,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.status_card_agents_files_summary, agentCount, fileCount),
                fontSize = summarySize,
                lineHeight = summaryLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(dotGap))
        // Not settings: the status card reads one session, and a way into the app's configuration
        // does not belong in a read-out. The slot is worth more as the second way into the agents —
        // the dashboard lists every agent with its usage, which the two rows below cannot fit.
        IconButton(onClick = onOpenAgents, minWidth = buttonSize, minHeight = buttonSize) {
            Icon(
                imageVector = MiuixIcons.Community,
                contentDescription = stringResource(R.string.status_card_agents_overview),
                modifier = Modifier.size(buttonIconSize),
                tint = colors.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun UsageSection(
    usage: ThreadTokenUsage,
    compacted: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onCompact: () -> Unit,
    rowGap: Dp = 7.dp,
    noteGap: Dp = 6.dp,
    windowOffset: Dp = 3.dp,
    progressHeight: Dp = 7.dp,
    totalSize: TextUnit = UiType.Title,
    totalLineHeight: TextUnit = UiType.TitleLine,
    windowSize: TextUnit = UiType.Body,
    windowLineHeight: TextUnit = UiType.Composer,
    detailSize: TextUnit = UiType.Footnote,
    detailLineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    val fraction = usage.usedFraction
    val total = usage.modelContextWindow ?: usage.total.totalTokens.coerceAtLeast(1)
    SectionCard(
        title = stringResource(R.string.status_card_context_title),
        icon = MiuixIcons.Layers,
        trailing = stringResource(R.string.status_card_usage_percent, (fraction * 100).roundToInt()),
        expandable = true,
        expanded = !collapsed,
        onToggle = onToggle,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatTokens(usage.total.totalTokens),
                fontSize = totalSize,
                lineHeight = totalLineHeight,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.width(noteGap))
            Text(
                text = stringResource(R.string.status_card_usage_of_total, formatTokens(total)),
                modifier = Modifier.padding(bottom = windowOffset),
                fontSize = windowSize,
                lineHeight = windowLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
            Spacer(Modifier.weight(1f))
            CompactButton(compacted = compacted, onClick = onCompact)
        }
        Spacer(Modifier.height(rowGap))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = usageColor(fraction),
                backgroundColor = colors.onBackground.copy(alpha = 0.08f),
            ),
            height = progressHeight,
        )
        Spacer(Modifier.height(rowGap))
        Text(
            text = stringResource(
                R.string.status_card_usage_tokens,
                formatTokens(usage.total.inputTokens),
                formatTokens(usage.total.outputTokens),
                formatTokens(usage.total.cachedInputTokens),
            ),
            fontSize = detailSize,
            lineHeight = detailLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        if (compacted) {
            Spacer(Modifier.height(noteGap))
            Text(
                text = stringResource(R.string.status_card_compacted_note),
                fontSize = detailSize,
                lineHeight = detailLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/**
 * The compact-context action of the usage row.
 *
 * Once the thread has been compacted there is nothing left to ask for, so the button keeps its place
 * and goes inert rather than disappearing: the row it would leave behind is the one being read.
 */
@Composable
private fun CompactButton(
    compacted: Boolean,
    onClick: () -> Unit,
) {
    CodexButton(
        text = if (compacted) {
            stringResource(R.string.status_card_compacted_action)
        } else {
            stringResource(R.string.status_card_compact_action)
        },
        onClick = onClick,
        role = ButtonRole.Primary,
        size = CodexButtonSize.Compact,
        enabled = !compacted,
    )
}

@Composable
private fun ModelSection(
    session: ThreadSessionState,
    models: List<ModelPreset>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onModel: (String) -> Unit,
    onEffort: (ReasoningEffort) -> Unit,
    onPolicy: (AskForApproval) -> Unit,
    onReviewer: (ApprovalsReviewer) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.status_card_model_title),
        icon = MiuixIcons.Tune,
        expandable = true,
        expanded = !collapsed,
        onToggle = onToggle,
    ) {
        // Each row *is* the picker. These used to open a modal sheet, which for a three-item choice
        // is a page of ceremony on top of the card the user is already reading; a cascading popup
        // anchored to the row keeps the choice next to the value it changes, and gets the open and
        // close motion from the library instead of having none.
        Column(verticalArrangement = Arrangement.spacedBy(UiConsts.Space1)) {
            val modelIndex = models.indexOfFirst { it.model == session.model }.coerceAtLeast(0)
            PickerRow(
                label = stringResource(R.string.status_card_model_label),
                // The chosen option's own name, not the config's label for it: the two disagree
                // after SetModel writes the id into the label, and the popup's check mark is on the
                // option, so the row has to agree with the option.
                value = models.getOrNull(modelIndex)?.displayName ?: session.modelDisplayName,
                items = models.map { model -> DropdownItem(text = model.displayName, summary = model.model) },
                selectedIndex = modelIndex,
                onSelectedIndexChange = { onModel(models[it].model) },
                enabled = models.isNotEmpty(),
            )

            val efforts = models.getOrNull(modelIndex)?.supportedReasoningEfforts.orEmpty()
            if (efforts.isNotEmpty()) {
                PickerRow(
                    label = stringResource(R.string.status_card_reasoning_label),
                    value = session.reasoningEffort.label(),
                    items = efforts.map { effort -> DropdownItem(text = effort.label()) },
                    selectedIndex = efforts.indexOf(session.reasoningEffort).coerceAtLeast(0),
                    onSelectedIndexChange = { onEffort(efforts[it]) },
                )
            }

            val policies = AskForApproval.entries
            PickerRow(
                label = stringResource(R.string.status_card_approval_label),
                value = session.approvalPolicy.label(),
                items = policies.map { policy ->
                    DropdownItem(text = policy.label(), summary = policy.description())
                },
                selectedIndex = policies.indexOf(session.approvalPolicy).coerceAtLeast(0),
                onSelectedIndexChange = { onPolicy(policies[it]) },
            )

            // The reviewer beside the policy: the policy decides whether a request is raised, the
            // reviewer decides who answers it.
            val reviewers = ApprovalsReviewer.entries
            PickerRow(
                label = stringResource(R.string.status_card_reviewer_label),
                value = session.approvalsReviewer.label(),
                items = reviewers.map { reviewer ->
                    DropdownItem(text = reviewer.label(), summary = reviewer.description())
                },
                selectedIndex = reviewers.indexOf(session.approvalsReviewer).coerceAtLeast(0),
                onSelectedIndexChange = { onReviewer(reviewers[it]) },
            )
        }
    }
}

/**
 * A row that *is* its own picker: the value on the right is the current choice, and tapping the row
 * drops that choice's options next to it.
 *
 * These rows used to be `miuix-preference` spinners, and a preference row is built for a settings
 * page: a 56dp minimum height and a 17/14sp type ramp, a full step above every other row in this
 * card. The popup shell is still the library's — anchoring it to the row, the open/close motion and
 * the haptic are the parts worth not rewriting — but both the row and the options it opens are drawn
 * at the card's own ramp, so a label/value row and its list read like the file and agent rows beside
 * them rather than as a settings page that landed on top of the card.
 */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    items: List<DropdownItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
    labelWidth: Dp = 64.dp,
    labelSize: TextUnit = UiType.RowDetail,
    labelLineHeight: TextUnit = UiType.Message,
    valueSize: TextUnit = UiType.RowTitle,
    valueLineHeight: TextUnit = UiType.SheetTitle,
    arrowGap: Dp = UiConsts.Space4,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.pressableRow(
                        shape = shape,
                        container = Color.Transparent,
                        onClick = {
                            expanded = !expanded
                            if (expanded) {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                            }
                        },
                        onClickLabel = label,
                    )
                } else {
                    Modifier.clip(shape)
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            fontSize = labelSize,
            lineHeight = labelLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = valueSize,
            lineHeight = valueLineHeight,
            fontWeight = FontWeight.Medium,
            color = if (enabled) colors.onSurface else colors.disabledOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(arrowGap))
        // The library's up/down arrow rather than this app's chevron: the row opens a list of
        // alternatives, and the arrow is the affordance that says so everywhere else in the app.
        DropdownArrowEndAction(actionColor = colors.onSurfaceVariantSummary)
        PickerPopup(
            items = items,
            selectedIndex = selectedIndex,
            show = expanded,
            onDismiss = { expanded = false },
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

/**
 * The option list a [PickerRow] drops next to itself.
 *
 * Not the library's `OverlayDropdownPopup`: that popup draws its options with the library's own
 * dropdown row, whose title is `body1` at 16sp over a summary at 14sp and whose padding belongs to a
 * settings page. A list opened from a 13sp row therefore arrived a full step larger than the row
 * itself. The popup's shell, anchoring and scale-in are still the library's; only the rows inside are
 * drawn here.
 */
@Composable
private fun PickerPopup(
    items: List<DropdownItem>,
    selectedIndex: Int,
    show: Boolean,
    onDismiss: () -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 8.dp,
) {
    val haptics = LocalHapticFeedback.current
    // The popup outlives the composition that opened it, so the click reads the callback that is
    // current when the option is tapped rather than the one captured when the list was built.
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    OverlayListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.End,
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        maxHeight = null,
        renderInRootScaffold = true,
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, item ->
                PickerOptionRow(
                    item = item,
                    selected = index == selectedIndex,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        currentOnSelectedIndexChange(index)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * One option of a [PickerPopup], drawn full bleed so the popup's own rounded silhouette is what
 * shapes the top and bottom of the list.
 */
@Composable
private fun PickerOptionRow(
    item: DropdownItem,
    selected: Boolean,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    titleGap: Dp = UiConsts.Space1,
    titleSize: TextUnit = UiType.RowTitle,
    titleLineHeight: TextUnit = UiType.SheetTitle,
    summarySize: TextUnit = UiType.RowDetail,
    summaryLineHeight: TextUnit = UiType.RowDetailLine,
    checkGap: Dp = UiConsts.Space8,
    checkSize: Dp = UiConsts.IconCheck,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = RectangleShape,
                container = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                fontSize = titleSize,
                lineHeight = titleLineHeight,
                fontWeight = FontWeight.Medium,
                color = if (item.enabled) colors.onSurface else colors.disabledOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.summary?.let { summary ->
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = titleGap),
                    fontSize = summarySize,
                    lineHeight = summaryLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(checkGap))
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.status_card_option_selected),
                modifier = Modifier.size(checkSize),
                tint = colors.primary,
            )
        }
    }
}

@Composable
private fun AgentsSection(
    roster: List<AgentRosterEntry>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenAgent: (String) -> Unit,
    onOpenAgentInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowGap: Dp = 1.dp,
) {
    SectionCard(
        title = stringResource(R.string.status_card_agents_title),
        icon = MiuixIcons.Community,
        trailing = stringResource(R.string.status_card_agents_count, roster.size),
        modifier = modifier,
        expandable = true,
        expanded = !collapsed,
        onToggle = onToggle,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            // Same two gestures as a file row: a tap goes into the thing the row names, a long
            // press asks about it. For an agent that is "open its session" and "show me what it is",
            // and the dashboard stays one tap away in the header for the overview.
            roster.forEach { agent ->
                AgentRow(
                    agent = agent,
                    onClick = { onOpenAgent(agent.threadId) },
                    onLongClick = { onOpenAgentInfo(agent.threadId) },
                )
            }
        }
    }
}

@Composable
private fun AgentRow(
    agent: AgentRosterEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
    dotSize: Dp = 7.dp,
    dotGap: Dp = 9.dp,
    nameSize: TextUnit = UiType.RowTitle,
    nameLineHeight: TextUnit = UiType.SheetTitle,
    badgeGap: Dp = 6.dp,
    badgeCorner: Dp = UiConsts.CornerChip,
    badgePadding: PaddingValues = PaddingValues(horizontal = 5.dp, vertical = 1.dp),
    badgeSize: TextUnit = UiType.Badge,
    badgeLineHeight: TextUnit = UiType.Subtitle,
    statusGap: Dp = 8.dp,
    statusSize: TextUnit = UiType.Footnote,
    statusLineHeight: TextUnit = UiType.CardTitle,
    chevronGap: Dp = 3.dp,
    chevronSize: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    // Only a subagent opens. The main agent is the thread this card is describing, so "entering" it
    // would push a page about the page the user is already on.
    val opens = agent.role != AgentRole.Main
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (opens) {
                    Modifier.pressableRow(
                        shape = shape,
                        container = Color.Transparent,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        onClickLabel = stringResource(R.string.status_card_open_agent_session, agent.name),
                        onLongClickLabel = stringResource(R.string.status_card_agent_details, agent.name),
                    )
                } else {
                    Modifier.clip(shape)
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(CircleShape)
                .background(statusDotColor(agent.tone())),
        )
        Spacer(Modifier.width(dotGap))
        Text(
            text = agent.name,
            modifier = Modifier.weight(1f),
            fontSize = nameSize,
            lineHeight = nameLineHeight,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (agent.role == AgentRole.Main) {
            Spacer(Modifier.width(badgeGap))
            Text(
                text = stringResource(R.string.status_card_agent_main_badge),
                modifier = Modifier
                    .clip(RoundedCornerShape(badgeCorner))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .padding(badgePadding),
                fontSize = badgeSize,
                lineHeight = badgeLineHeight,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
            )
        }
        Spacer(Modifier.width(statusGap))
        Text(
            text = agent.statusLabel(),
            fontSize = statusSize,
            lineHeight = statusLineHeight,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
        if (opens) {
            Spacer(Modifier.width(chevronGap))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = stringResource(R.string.status_card_open_agent, agent.name),
                modifier = Modifier.size(chevronSize),
                tint = colors.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun FilesSection(
    files: List<FileDiff>,
    cwd: String?,
    openPath: String?,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    rowGap: Dp = 1.dp,
    emptySize: TextUnit = UiType.RowDetail,
    emptyLineHeight: TextUnit = UiType.Message,
) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.status_card_files_title),
        icon = MiuixIcons.Notes,
        trailing = stringResource(R.string.status_card_files_count, files.size),
        modifier = modifier,
        expandable = true,
        expanded = !collapsed,
        onToggle = onToggle,
    ) {
        if (files.isEmpty()) {
            Text(
                text = stringResource(R.string.status_card_files_empty),
                fontSize = emptySize,
                lineHeight = emptyLineHeight,
                color = colors.onSurfaceVariantSummary,
            )
            return@SectionCard
        }
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            files.forEach { file ->
                FileRow(
                    file = file,
                    cwd = cwd,
                    open = file.path == openPath,
                    onClick = { onOpen(file.path) },
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: FileDiff,
    cwd: String?,
    open: Boolean,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
    badgeGap: Dp = 9.dp,
    nameSize: TextUnit = UiType.Subtitle,
    nameLineHeight: TextUnit = UiType.SheetTitle,
    pathSize: TextUnit = UiType.Badge,
    pathLineHeight: TextUnit = UiType.SheetRowTitle,
    statGap: Dp = 5.dp,
    chevronGap: Dp = 3.dp,
    chevronSize: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    val shownPath = displayDiffPath(file.path, cwd, runtimeHome())
    val title = if (file.oldPath != null) file.displayName else shownPath.substringAfterLast('/')
    val parent = shortenedParent(shownPath)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = shape,
                container = if (open) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileKindBadge(file)
        Spacer(Modifier.width(badgeGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = nameSize,
                lineHeight = nameLineHeight,
                fontWeight = if (open) FontWeight.Medium else FontWeight.Normal,
                color = if (open) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = parent,
                fontSize = pathSize,
                lineHeight = pathLineHeight,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(statGap))
        FileStatText(file.additions, file.removals)
        Spacer(Modifier.width(chevronGap))
        // Same disclosure as every other expanding row: 90 degrees when open, over Motion.Disclosure.
        val chevronRotation by animateFloatAsState(
            targetValue = if (open) 90f else 0f,
            animationSpec = Motion.Disclosure,
            label = "diffRowChevron",
        )
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = if (open) {
                stringResource(R.string.status_card_diff_collapse)
            } else {
                stringResource(R.string.status_card_diff_expand)
            },
            modifier = Modifier
                .size(chevronSize)
                .rotate(chevronRotation),
            tint = colors.onSurfaceVariantSummary,
        )
    }
}

/**
 * The diff pane that opens to the left of the section column.
 *
 * Mirrors `codex-rs/tui/src/diff_render.rs`: one file's unified diff with a header, two gutter line
 * numbers, horizontally scrollable code lines and a footer that switches files without closing the
 * pane.
 */
@Composable
fun DiffPane(
    file: FileDiff,
    siblings: List<FileDiff>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    cwd: String? = null,
    headerPadding: PaddingValues = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
    nameSize: TextUnit = UiType.Message,
    nameLineHeight: TextUnit = UiType.Title,
    pathSize: TextUnit = UiType.Chip,
    pathLineHeight: TextUnit = UiType.SheetRowTitle,
    statGap: Dp = 10.dp,
    closeGap: Dp = 4.dp,
    closeButtonSize: Dp = 32.dp,
    closeIconSize: Dp = 15.dp,
    dividerHeight: Dp = 1.dp,
    footerPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    footerGap: Dp = 6.dp,
    positionSize: TextUnit = UiType.Footnote,
    positionLineHeight: TextUnit = UiType.CardTitle,
    chipPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
    chipTextSize: TextUnit = UiType.Meta,
    chipTextLineHeight: TextUnit = UiType.CardTitle,
) {
    val colors = MiuixTheme.colorScheme
    val index = siblings.indexOfFirst { it.path == file.path }
    val shownPath = displayDiffPath(file.path, cwd, runtimeHome())
    val title = if (file.oldPath != null) file.displayName else shownPath.substringAfterLast('/')
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(headerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = nameSize,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortenedParent(shownPath),
                    fontSize = pathSize,
                    lineHeight = pathLineHeight,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(statGap))
            FileStatText(file.additions, file.removals)
            Spacer(Modifier.width(closeGap))
            IconButton(onClick = onClose, minWidth = closeButtonSize, minHeight = closeButtonSize) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.status_card_diff_collapse),
                    modifier = Modifier.size(closeIconSize),
                    tint = colors.onSurfaceVariantSummary,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dividerHeight)
                .background(colors.outline.copy(alpha = 0.24f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            DiffBody(
                lines = file.lines,
                maxLines = 800,
                language = languageFromPath(file.path),
            )
        }
        if (siblings.size > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dividerHeight)
                    .background(colors.outline.copy(alpha = 0.24f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(footerPadding),
                horizontalArrangement = Arrangement.spacedBy(footerGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.status_card_diff_position, index + 1, siblings.size),
                    fontSize = positionSize,
                    lineHeight = positionLineHeight,
                    color = colors.onSurfaceVariantSummary,
                )
                siblings.forEach { sibling ->
                    // These were drawn as pills with no tap handler behind them, which is a button
                    // that lies about itself. They are names in a list of names, so the fill and the
                    // silhouette are gone and only the open file keeps a colour.
                    Text(
                        text = sibling.fileName,
                        modifier = Modifier.padding(chipPadding),
                        fontSize = chipTextSize,
                        lineHeight = chipTextLineHeight,
                        color = if (sibling.path == file.path) colors.primary else colors.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * The foldable sub-cards of the status card.
 *
 * Every one of them answers a different question — how full the context is, what this turn is doing,
 * which agents and files it has touched — and a reader who is watching one of them pays for the other
 * four with rows they are not reading, so each folds on its own.
 */
enum class StatusSection { Usage, Model, Plan, Agents, Files }

/** Panel-local toggle state, hoisted out of the card so it survives collapse/reopen. */
class StatusPanelState {
    var open by mutableStateOf(false)
        private set

    var openFilePath by mutableStateOf<String?>(null)
        private set

    /**
     * Last opened diff, kept after [openFilePath] clears so the pane still has content to fade out
     * with instead of collapsing to nothing for a frame.
     */
    var paneFilePath by mutableStateOf<String?>(null)
        private set

    /**
     * The sections the reader has folded away; every one starts open.
     *
     * The choice outlives the panel: closing the card and reopening it must not unfold the sections
     * that were folded on purpose.
     */
    private val folded = mutableStateMapOf<StatusSection, Boolean>()

    fun isFolded(section: StatusSection): Boolean = folded[section] == true

    fun toggleSection(section: StatusSection) {
        folded[section] = !isFolded(section)
    }

    fun toggle() {
        open = !open
        if (!open) closeFile()
    }

    fun collapse() {
        open = false
        closeFile()
    }

    fun toggleFile(path: String) {
        if (openFilePath == path) closeFile() else openFile(path)
    }

    private fun openFile(path: String) {
        openFilePath = path
        paneFilePath = path
    }

    fun closeFile() {
        openFilePath = null
    }
}

/**
 * Toggle button for the status card.
 *
 * The press feedback is a tint in the chip's own silhouette rather than the default rectangular
 * ripple: a ripple over a rounded chip spills outside the shape it is answering, which read as the
 * button being a different shape from the one that was drawn. The icon does not move when the panel
 * opens — the panel appearing next to it is the state change, and a rotating glyph on top of that
 * made the button look like it was doing something to the session rather than to the panel.
 *
 * The chip stays silent about the session's state: it opens the card, and the card is where running,
 * waiting and approvals are read. The running outline and the badge dot that used to sit on top of it
 * made the button look like the thing doing the work.
 */
@Composable
fun StatusCardButton(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 12.dp,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { SquircleShape(UiConsts.ChipCorner) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressOverlay by animateColorAsState(
        targetValue = if (pressed) colors.onBackground.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = Motion.Tint,
        label = "statusButtonPress",
    )
    Box(
        modifier = modifier
            .size(UiConsts.ChipSize)
            .floatingSurface(
                shape = shape,
                tint = if (open) colors.primary.copy(alpha = 0.92f) else panelColor(),
                pressOverlay = pressOverlay,
                elevation = elevation,
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Tasks,
            contentDescription = if (open) {
                stringResource(R.string.status_card_collapse_panel)
            } else {
                stringResource(R.string.status_card_expand_panel)
            },
            modifier = Modifier.size(UiConsts.ChipIcon),
            tint = if (open) colors.onPrimary else colors.primary,
        )
    }
}

/** Back chevron used by the drill-down surfaces. */
@Composable
fun BackChevron(
    onClick: () -> Unit,
    description: String = stringResource(R.string.status_card_back),
    buttonSize: Dp = 34.dp,
    iconSize: Dp = 18.dp,
) {
    val colors = MiuixTheme.colorScheme
    IconButton(onClick = onClick, minWidth = buttonSize, minHeight = buttonSize) {
        Icon(
            imageVector = MiuixIcons.ChevronBackward,
            contentDescription = description,
            modifier = Modifier.size(iconSize),
            tint = colors.primary,
        )
    }
}

/** Token counts are unreadable raw; `128400` becomes `128.4K`. */
@Composable
@ReadOnlyComposable
internal fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> stringResource(
        R.string.status_card_tokens_millions,
        String.format(Locale.US, "%.1f", tokens / 1_000_000f),
    )

    tokens >= 1_000 -> {
        val thousands = tokens / 1_000f
        if (thousands >= 100f) {
            stringResource(R.string.status_card_tokens_rounded, Math.round(thousands))
        } else {
            stringResource(R.string.status_card_tokens_thousands, String.format(Locale.US, "%.1f", thousands))
        }
    }

    else -> tokens.toString()
}

internal fun AgentRosterEntry.tone(): ThreadStatusTone = when {
    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Completed -> ThreadStatusTone.Done
    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Interrupted -> ThreadStatusTone.Failed
    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Errored -> ThreadStatusTone.Failed
    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Completed -> ThreadStatusTone.Done
    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Running -> ThreadStatusTone.Running
    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.PendingInit -> ThreadStatusTone.Waiting
    role == AgentRole.Main -> ThreadStatusTone.Idle
    else -> ThreadStatusTone.Idle
}

@Composable
@ReadOnlyComposable
internal fun AgentRosterEntry.statusLabel(): String = when {
    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Completed ->
        stringResource(R.string.status_card_agent_status_completed)

    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Interrupted ->
        stringResource(R.string.status_card_agent_status_interrupted)

    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Interacted ->
        stringResource(R.string.status_card_agent_status_interacting)

    activity == com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind.Started ->
        stringResource(R.string.status_card_agent_status_running)

    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Errored ->
        stringResource(R.string.status_card_agent_status_failed)

    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Completed ->
        stringResource(R.string.status_card_agent_status_completed)

    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.Running ->
        stringResource(R.string.status_card_agent_status_running)

    status == com.cy.codexui.protocol.protocol.v2.AgentRunStatus.PendingInit ->
        stringResource(R.string.status_card_agent_status_starting)

    else -> stringResource(R.string.status_card_agent_status_idle)
}
