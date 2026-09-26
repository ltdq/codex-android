package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.history_cell.ThreadItemCell
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.status.BackChevron
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A subagent conversation as a page, not a thread switch: switching clears the parent's
 * items while the new thread loads and collapses the roster.
 */
@Composable
fun SubAgentThreadScreen(
    threadId: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Parent's roster in spawn order; navigation wraps at both ends
     * (`codex-rs/.../app/agent_navigation.rs`).
     */
    roster: List<AgentRosterEntry> = emptyList(),
    onSwitchAgent: (String) -> Unit = {},
    contentPadding: PaddingValues =
        PaddingValues(
            start = UiConsts.TranscriptGutter,
            end = UiConsts.TranscriptGutter,
            bottom = UiConsts.PageBottomInset,
        ),
) {
    val colors = MiuixTheme.colorScheme
    var items by remember(threadId) { mutableStateOf<List<ThreadItem>>(emptyList()) }
    var name by remember(threadId) { mutableStateOf<String?>(null) }
    var loaded by remember(threadId) { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val index = roster.indexOfFirst { it.threadId == threadId }
    val navigable = roster.size > 1 && index >= 0

    LaunchedEffect(threadId) {
        client.readThread(com.cy.codex.protocol.protocol.v2.ThreadReadParams(threadId)).onSuccess {
            response ->
            items = response.items
            name = response.thread.name
        }
        loaded = true
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = name ?: stringResource(R.string.sub_agent_thread_fallback_title),
            summary = stringResource(R.string.sub_agent_thread_subtitle),
            startAction = { BackChevron(onClick = onBack) },
            endActions = {
                if (navigable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AgentNavButton(
                            icon = MiuixIcons.ChevronBackward,
                            label = stringResource(R.string.sub_agent_thread_previous),
                            onClick = {
                                onSwitchAgent(
                                    roster[(index - 1 + roster.size) % roster.size].threadId
                                )
                            },
                        )
                        AgentNavButton(
                            icon = MiuixIcons.ChevronForward,
                            label = stringResource(R.string.sub_agent_thread_next),
                            onClick = { onSwitchAgent(roster[(index + 1) % roster.size].threadId) },
                        )
                        AgentNavButton(
                            icon = MiuixIcons.Basic.Search,
                            label = stringResource(R.string.sub_agent_thread_pick),
                            onClick = { pickerOpen = true },
                        )
                    }
                }
            },
        )
        if (loaded && items.isEmpty()) {
            Text(
                text = stringResource(R.string.sub_agent_thread_empty),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                color = colors.onSurfaceVariantSummary,
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(TranscriptGap),
        ) {
            items(items, key = { it.id }) { item ->
                ThreadItemCell(
                    item = item,
                    assistantLabel = stringResource(R.string.sub_agent_thread_assistant),
                )
            }
        }
    }

    if (pickerOpen) {
        AgentPickerSheet(
            show = true,
            roster = roster,
            selectedThreadId = threadId,
            onSelect = { picked ->
                pickerOpen = false
                if (picked != threadId) onSwitchAgent(picked)
            },
            onDismiss = { pickerOpen = false },
            onDismissFinished = { pickerOpen = false },
        )
    }
}

@Composable
private fun AgentNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        minWidth = UiConsts.IconButtonSize,
        minHeight = UiConsts.IconButtonSize,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(UiConsts.IconHeader),
            tint = MiuixTheme.colorScheme.primary,
        )
    }
}

private val TranscriptGap: Dp = 18.dp
