package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexSearchField
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.canReadThreadUsage
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.AgentRunStatus
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import com.cy.codex.protocol.protocol.v2.ThreadUsage
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.status.formatCreditMicros
import com.cy.codex.status.formatEstimatedUsdMicros
import com.cy.codex.status.formatTokens
import com.cy.codex.statusDotColor
import com.cy.codex.usageColor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Source kinds a `thread/list` scoped to an agent's descendants should include. */
internal val SUB_AGENT_SOURCE_KINDS =
    listOf(
        "subAgent",
        "subAgentReview",
        "subAgentCompact",
        "subAgentThreadSpawn",
        "subAgentOther",
    )

/**
 * `/agents` as a page, mirroring `codex-rs/.../app/agents_overview.rs`; subagent liveness
 * and meters come from `thread/list` and `thread/tokenUsage/updated`.
 */
@Composable
fun AgentsScreen(
    app: CodexApp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val session = app.widget.state
    val mainLabel = stringResource(R.string.agent_roster_main_label)
    val nameFormat = stringResource(R.string.agent_roster_sub_agent_name)
    val agentThreads = app.catalog.agentThreadsFor(session.threadId)
    val roster = rememberAgentRoster(session, mainLabel, nameFormat, agentThreads)
    val usage = app.catalog.threadUsage
    val listedThreads = app.threads.threads
    val entries =
        remember(roster, usage, agentThreads, listedThreads) {
            val threadsById = (agentThreads + listedThreads).associateBy { it.id }
            roster.map { agent ->
                agent.withThreadMetadata(threadsById[agent.threadId], usage[agent.threadId])
            }
        }
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(entries, query) {
            val needle = query.trim()
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
    val busiest = filtered.maxOfOrNull { it.tokens }?.coerceAtLeast(1) ?: 1
    val totalTokens = entries.sumOf { it.tokens.toLong() }
    val canReadUsage = app.catalog.account.canReadThreadUsage
    var usageTarget by remember(session.threadId, app.accountGeneration) { mutableStateOf<String?>(null) }
    var estimate by remember(usageTarget, app.accountGeneration) { mutableStateOf<ThreadUsage?>(null) }
    var estimateFailed by remember(usageTarget, app.accountGeneration) { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<AgentRosterEntry?>(null) }
    val renameAgent = {
        val target = renameTarget
        if (target != null && renameText.isNotBlank()) {
            app.onAppEvent(AppEvent.RenameThread(target.threadId, renameText.trim()))
            renameTarget = null
        }
    }

    // Refreshed once per open: `thread/list` is the only source of subagent liveness and cli metadata.
    LaunchedEffect(session.threadId) {
        app.onAppEvent(AppEvent.ReloadAgentThreads(session.threadId))
    }

    LaunchedEffect(usageTarget, app.accountGeneration) {
        val threadId = usageTarget ?: return@LaunchedEffect
        if (!canReadUsage) return@LaunchedEffect
        while (true) {
            app.client.readThreadUsage(threadId)
                .onSuccess {
                    currentCoroutineContext().ensureActive()
                    if (it.threadId == threadId) {
                        estimate = it
                        estimateFailed = false
                    } else {
                        estimateFailed = true
                    }
                }
                .onFailure {
                    currentCoroutineContext().ensureActive()
                    estimateFailed = true
                }
            delay(60_000)
        }
    }

    CodexPage(
        title = stringResource(R.string.agents_overview_title),
        description =
            stringResource(
                R.string.agents_screen_subtitle,
                entries.size,
                formatTokens(totalTokens),
            ),
        onBack = onBack,
        modifier = modifier,
    ) {
        CodexSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.agents_screen_filter),
        )
        if (filtered.isEmpty()) {
            CodexEmptyRow(
                stringResource(
                    if (entries.isEmpty()) R.string.agents_overview_empty
                    else R.string.agent_picker_empty
                )
            )
        } else {
            CodexCardGrid(count = filtered.size) { index ->
                val agent = filtered[index]
                AgentCard(
                    agent = agent,
                    selected = agent.threadId == (app.selectedAgent ?: session.threadId),
                    tokens = agent.tokens,
                    busiestTokens = busiest,
                    usage =
                        if (usageTarget == agent.threadId && canReadUsage) {
                            estimate?.let {
                                listOfNotNull(
                                    stringResource(
                                        R.string.account_screen_credits_balance,
                                        formatCreditMicros(it.estimatedUsageCreditsMicros),
                                    ),
                                    formatEstimatedUsdMicros(it.estimatedUsageUsdMicros),
                                ).joinToString(" · ")
                            } ?: if (estimateFailed) {
                                stringResource(R.string.session_status_load_failed)
                            } else {
                                "…"
                            }
                        } else {
                            null
                        },
                    onClick = { app.openAgentSummary(agent.threadId) },
                    onStop = { app.onAppEvent(AppEvent.StopThreadTurn(agent.threadId)) },
                    onUsage =
                        if (canReadUsage) {
                            {
                                usageTarget =
                                    if (usageTarget == agent.threadId) null else agent.threadId
                            }
                        } else {
                            null
                        },
                    onRename = {
                        renameTarget = agent
                        renameText = agent.name
                    },
                    onArchive = { archiveTarget = agent },
                )
            }
        }
    }

    WindowBottomSheet(
        show = renameTarget != null,
        onDismissRequest = { renameTarget = null },
        onDismissFinished = {},
        title = stringResource(R.string.agents_rename_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = renameTarget?.name.orEmpty(),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.agents_rename_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = { renameAgent() },
                            onGo = { renameAgent() },
                            onSend = { renameAgent() },
                        ),
                )
                Spacer(Modifier.height(UiConsts.Space12))
                SheetActions(
                    confirm = stringResource(R.string.agents_rename_confirm),
                    confirmEnabled = renameText.isNotBlank(),
                    onConfirm = renameAgent,
                    onCancel = { renameTarget = null },
                )
            }
        }
    }

    WindowBottomSheet(
        show = archiveTarget != null,
        onDismissRequest = { archiveTarget = null },
        onDismissFinished = {},
        title = stringResource(R.string.agents_archive_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text = archiveTarget?.name.orEmpty(),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Text(
                    text = stringResource(R.string.agents_archive_message),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(UiConsts.Space12))
                SheetActions(
                    confirm = stringResource(R.string.agents_archive_confirm),
                    destructive = true,
                    onConfirm = {
                        archiveTarget?.let {
                            app.onAppEvent(AppEvent.ArchiveThread(it.threadId, archived = true))
                        }
                        archiveTarget = null
                    },
                    onCancel = { archiveTarget = null },
                )
            }
        }
    }
}

private fun AgentRosterEntry.canStop(): Boolean =
    when {
        threadStatus != null -> threadStatus is ThreadStatus.Active
        else -> status == AgentRunStatus.Running || status == AgentRunStatus.PendingInit
    }

/** One roster entry as a catalogue card: its liveness on the header, what it can be told under it. */
@Composable
private fun AgentCard(
    agent: AgentRosterEntry,
    selected: Boolean,
    tokens: Int,
    busiestTokens: Int,
    usage: String?,
    onClick: () -> Unit,
    onStop: () -> Unit,
    onUsage: (() -> Unit)?,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    CodexCatalogCard(
        title = agent.name,
        description =
            agent.task?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.agents_overview_no_task),
        icon = MiuixIcons.Community,
        onClick = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(tone = agent.tone())
                Spacer(Modifier.width(UiConsts.Space6))
                RoleTag(role = agent.role)
                Spacer(Modifier.width(UiConsts.Space6))
                Text(
                    text = agent.statusLabel(),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = statusDotColor(agent.tone()),
                    maxLines = 1,
                )
                if (selected) {
                    Spacer(Modifier.width(UiConsts.Space6))
                    Text(
                        text = stringResource(R.string.agents_overview_current),
                        modifier =
                            Modifier.clip(RoundedCornerShape(UiConsts.BadgeCorner))
                                .background(colors.primary.copy(alpha = 0.14f))
                                .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
                        fontSize = UiType.Badge,
                        lineHeight = UiType.BadgeLine,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary,
                        maxLines = 1,
                    )
                }
            }
        },
        footer = {
            val meta = listOfNotNull(agent.model, agent.effort?.label(), agent.itemId)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    fontSize = UiType.Footnote,
                    lineHeight = UiType.FootnoteLine,
                    color = colors.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (busiestTokens > 0) {
                Spacer(Modifier.height(UiConsts.Space8))
                AgentTokenMeter(tokens = tokens, busiestTokens = busiestTokens)
            }
            AgentActions(
                agent = agent,
                onStop = onStop,
                onUsage = onUsage,
                onRename = onRename,
                onArchive = onArchive,
            )
            if (usage != null) {
                CodexRow(
                    title = stringResource(R.string.session_status_estimated_usage),
                    summary = usage,
                )
            }
        },
    )
}

/** The agent's share of the page's tokens, against the busiest one on screen. */
@Composable
private fun AgentTokenMeter(tokens: Int, busiestTokens: Int) {
    val colors = MiuixTheme.colorScheme
    val fraction = (tokens.toFloat() / busiestTokens.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
    ) {
        LinearProgressIndicator(
            modifier = Modifier.weight(1f),
            progress = fraction,
            colors =
                ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = usageColor(fraction),
                    backgroundColor = colors.onSurface.copy(alpha = 0.08f),
                ),
            height = UiConsts.ProgressHeightRow,
        )
        Text(
            text =
                if (tokens > 0) {
                    formatTokens(tokens.toLong())
                } else {
                    stringResource(R.string.agents_overview_tokens_none)
                },
            modifier = Modifier.width(UiConsts.TokenValueWidth),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            color = colors.onSurfaceVariantSummary,
            maxLines = 1,
        )
    }
}

@Composable
private fun AgentActions(
    agent: AgentRosterEntry,
    onStop: () -> Unit,
    onUsage: (() -> Unit)?,
    onRename: () -> Unit,
    onArchive: () -> Unit,
) {
    val destructive = agent.role == AgentRole.Sub
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = UiConsts.Space4),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (agent.canStop()) {
            Button(
                onClick = onStop,
                modifier = Modifier,
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.agents_action_stop), maxLines = 1)
            }
        }
        if (onUsage != null) {
            Button(onClick = onUsage, colors = ButtonDefaults.buttonColors()) {
                Text(text = stringResource(R.string.session_status_estimated_usage), maxLines = 1)
            }
        }
        Button(
            onClick = onRename,
            modifier = Modifier,
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.agents_action_rename), maxLines = 1)
        }
        if (destructive) {
            Button(
                onClick = onArchive,
                modifier = Modifier,
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.agents_action_archive), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SheetActions(
    confirm: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8, Alignment.End),
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier,
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.agents_cancel), maxLines = 1)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier,
            enabled = confirmEnabled,
            colors =
                if (destructive) {
                    ButtonDefaults.buttonColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
        ) {
            Text(text = confirm, maxLines = 1)
        }
    }
}
