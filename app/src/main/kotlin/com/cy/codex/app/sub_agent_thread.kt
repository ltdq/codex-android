package com.cy.codex.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.history_cell.ThreadItemCell
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.ThreadReadParams
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One page of the chat pager: a spawned agent's own conversation. The parent transcript only ever
 * mentions the agent, so its thread is read on demand and never bound to the session.
 */
@Composable
internal fun SubAgentTranscriptPane(
    threadId: String,
    client: AppServerClient,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(
            start = UiConsts.TranscriptGutter,
            end = UiConsts.TranscriptGutter,
            top = UiConsts.TranscriptTopInset,
            bottom = UiConsts.PageBottomInset,
        ),
) {
    val colors = MiuixTheme.colorScheme
    val layoutDirection = LocalLayoutDirection.current
    var items by remember(threadId) { mutableStateOf<List<ThreadItem>>(emptyList()) }
    var loaded by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        client.readThread(ThreadReadParams(threadId)).onSuccess { response -> items = response.items }
        loaded = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = topInset + contentPadding.calculateTopPadding(),
                bottom = bottomInset + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(TranscriptGap),
    ) {
        if (loaded && items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.sub_agent_thread_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = UiConsts.Space8),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
            }
        }
        items(items, key = { it.id }) { item ->
            ThreadItemCell(
                item = item,
                assistantLabel = stringResource(R.string.sub_agent_thread_assistant),
            )
        }
    }
}

private val TranscriptGap: Dp = 18.dp
