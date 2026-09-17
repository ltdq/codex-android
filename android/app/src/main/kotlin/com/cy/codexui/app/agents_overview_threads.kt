package com.cy.codexui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codexui.R
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.protocol.item.ThreadItem
import com.cy.codexui.status.BackChevron
import com.cy.codexui.history_cell.ThreadItemCell
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A subagent's conversation, read on its own.
 *
 * Deliberately a *page* rather than a thread switch. A subagent does have a thread of its own —
 * `thread/read` answers for it — but making it the shell's current thread tore the parent session
 * apart on the way in: the parent's items were cleared while the new thread loaded, so the roster
 * (and the card that had just been tapped) collapsed, and the subagent's own session naturally
 * contains no subagents, so "entering the agent" looked like the agent had disappeared. Reading it
 * into a page leaves the session the user came from exactly where it was.
 *
 * The transcript is rendered with the same cells as the chat, so a subagent's messages, commands and
 * patches look like they do everywhere else.
 */
@Composable
fun SubAgentThreadScreen(
    threadId: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = UiConsts.TranscriptGutter,
        end = UiConsts.TranscriptGutter,
        bottom = UiConsts.PageBottomInset,
    ),
) {
    val colors = MiuixTheme.colorScheme
    var items by remember(threadId) { mutableStateOf<List<ThreadItem>>(emptyList()) }
    var name by remember(threadId) { mutableStateOf<String?>(null) }
    var loaded by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        client.readThread(com.cy.codexui.protocol.protocol.v2.ThreadReadParams(threadId)).onSuccess { response ->
            items = response.items
            name = response.thread.name
        }
        loaded = true
    }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = name ?: stringResource(R.string.sub_agent_thread_fallback_title),
            subtitle = stringResource(R.string.sub_agent_thread_subtitle),
            leading = { BackChevron(onClick = onBack) },
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
                ThreadItemCell(item = item, assistantLabel = stringResource(R.string.sub_agent_thread_assistant))
            }
        }
    }
}

private val TranscriptGap: Dp = 18.dp
