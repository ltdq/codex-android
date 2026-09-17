package com.cy.codexui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.v2.AgentRunStatus
import com.cy.codexui.protocol.protocol.v2.SubAgentActivityKind
import com.cy.codexui.status.formatTokens
import com.cy.codexui.ModalSheet
import com.cy.codexui.ThreadStatusTone
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.statusDotColor
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Full-screen agent dashboard, plus the roster presentation helpers the picker shares with it.
 *
 * Mirrors `app/agents_overview.rs`: one card per entry of the derived roster, so the screen shows
 * exactly the agents the thread's collab items mention — never a fixture list. Tapping a card
 * selects that agent and closes the dashboard.
 *
 * Token bars are relative: the widest bar is the busiest agent, and the main agent's own usage is
 * the thread total the caller passes in. Per-agent usage is unknown to the item stream, so an
 * agent without usage renders an empty track instead of an invented number.
 */
@Composable
fun AgentsOverview(
    show: Boolean,
    roster: List<AgentRosterEntry>,
    activeThreadId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    totalTokens: Int = 0,
) {
    val colors = MiuixTheme.colorScheme
    val shown = remember(roster, totalTokens) {
        roster.map { agent -> agent to tokensOf(agent, totalTokens) }
    }
    val busiest = shown.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val close = LocalDismissState.current
    ModalSheet(
        show = show,
        onDismiss = {
            onDismiss()
            onDismissFinished()
        },
        onDismissFinished = onDismissFinished,
        title = stringResource(R.string.agents_overview_title),
        subtitle = stringResource(
            R.string.agents_overview_subtitle,
            roster.size,
            formatTokens(totalTokens),
        ),
        maxHeightFraction = UiConsts.SheetHeightFractionTall,
    ) {
        if (roster.isEmpty()) {
            Text(
                text = stringResource(R.string.agents_overview_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = UiConsts.Space20),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
            )
        } else {
            shown.forEach { (agent, tokens) ->
                AgentRosterRow(
                    entry = agent,
                    selected = agent.threadId == activeThreadId,
                    onClick = {
                        onSelect(agent.threadId)
                        close?.invoke()
                    },
                    tokens = tokens,
                    busiestTokens = busiest,
                )
            }
        }
    }
}

/** The main agent's usage *is* the thread total; subagent usage is what the stream reported. */
private fun tokensOf(agent: AgentRosterEntry, totalTokens: Int): Int =
    if (agent.role == AgentRole.Main && totalTokens > 0) totalTokens else agent.tokens

/** Label for a server agent state, in the wording the TUI dashboard uses. */
@Composable
@ReadOnlyComposable
internal fun AgentRunStatus.label(): String = when (this) {
    AgentRunStatus.PendingInit -> stringResource(R.string.agents_overview_status_pending_init)
    AgentRunStatus.Running -> stringResource(R.string.agents_overview_status_running)
    AgentRunStatus.Interrupted -> stringResource(R.string.agents_overview_status_interrupted)
    AgentRunStatus.Completed -> stringResource(R.string.agents_overview_status_completed)
    AgentRunStatus.Errored -> stringResource(R.string.agents_overview_status_errored)
    AgentRunStatus.Shutdown -> stringResource(R.string.agents_overview_status_shutdown)
    AgentRunStatus.NotFound -> stringResource(R.string.agents_overview_status_not_found)
}

/** Label for a subagent activity event. */
@Composable
@ReadOnlyComposable
internal fun SubAgentActivityKind.label(): String = when (this) {
    SubAgentActivityKind.Started -> stringResource(R.string.agents_overview_activity_started)
    SubAgentActivityKind.Interacted -> stringResource(R.string.agents_overview_activity_interacted)
    SubAgentActivityKind.Interrupted -> stringResource(R.string.agents_overview_activity_interrupted)
    SubAgentActivityKind.Completed -> stringResource(R.string.agents_overview_activity_completed)
}

/**
 * What the row should say. The collab `agentsStates` map is the server's own last word on an
 * agent, so it wins over the coarser activity event; activity only fills the gap before the first
 * collab update arrives.
 */
@Composable
@ReadOnlyComposable
internal fun AgentRosterEntry.statusLabel(): String = when {
    status != null -> status.label()
    activity != null -> activity.label()
    role == AgentRole.Main -> stringResource(R.string.agents_overview_status_main_thread)
    else -> stringResource(R.string.agents_overview_status_idle)
}

/** Colour tone of an agent, used for its status dot and label. */
internal fun AgentRosterEntry.tone(): ThreadStatusTone = when {
    activity == SubAgentActivityKind.Interrupted -> ThreadStatusTone.Failed
    status == AgentRunStatus.Errored || status == AgentRunStatus.NotFound -> ThreadStatusTone.Failed
    status == AgentRunStatus.Completed -> ThreadStatusTone.Done
    status == AgentRunStatus.Running -> ThreadStatusTone.Running
    status == AgentRunStatus.PendingInit -> ThreadStatusTone.Waiting
    status == AgentRunStatus.Interrupted -> ThreadStatusTone.Waiting
    status == AgentRunStatus.Shutdown -> ThreadStatusTone.Idle
    activity == SubAgentActivityKind.Completed -> ThreadStatusTone.Done
    activity != null -> ThreadStatusTone.Running
    else -> ThreadStatusTone.Idle
}

/** Small round agent/environment status dot shared by the overview and the picker. */
@Composable
internal fun StatusDot(tone: ThreadStatusTone, size: Dp = UiConsts.DotSize) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(statusDotColor(tone)),
    )
}

/** "主" / "子" tag that marks which side of the collab relation an entry sits on. */
@Composable
internal fun RoleTag(role: AgentRole) {
    val colors = MiuixTheme.colorScheme
    val accent = if (role == AgentRole.Main) colors.primary else colors.onSurfaceVariantSummary
    Text(
        text = role.tag,
        modifier = Modifier
            .clip(RoundedCornerShape(UiConsts.BadgeCorner))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = UiConsts.Space5, vertical = UiConsts.Space1),
        fontSize = UiType.Badge,
        lineHeight = UiType.BadgeLine,
        fontWeight = FontWeight.Medium,
        color = accent,
        maxLines = 1,
    )
}
