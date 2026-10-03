package com.cy.codex.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.CodexApp
import com.cy.codex.CodexPage
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.chatwidget.Transcript
import com.cy.codex.history_cell.LocalHookMetadata

/** Transcript and prompt editing, mirroring `codex/codex-rs/tui/src/app_backtrack.rs`. */
@Composable
fun ThreadHistoryScreen(app: CodexApp, onBack: () -> Unit) {
    val session = app.widget.state
    CodexPage(
        title = stringResource(R.string.history_ui_title),
        description = session.config.displayName,
        onBack = onBack,
        // The transcript is the page's own lazy list; the frame must not scroll it.
        scroll = false,
    ) {
        // Hook cells join run ids against LocalHookMetadata (app.kt); without it the overlay shows hooks unnamed.
        CompositionLocalProvider(LocalHookMetadata provides app.catalog.hooks) {
            Transcript(
                items = session.items,
                diagnostics = session.diagnostics,
                hookRuns = session.hookRuns,
                approvalReceipts = session.approvalReceipts,
                // Snapshot of the live transcript behind it: nothing streams into the overlay.
                isStreaming = { false },
                streamFor = { null },
                plan = session.plan,
                loading = false,
                empty = session.items.isEmpty() && session.diagnostics.isEmpty() &&
                    session.hookRuns.isEmpty() && session.approvalReceipts.isEmpty(),
                cwd = session.config.cwd,
                onOpenAgent = { threadId ->
                    app.selectAgentPage(threadId)
                    onBack()
                },
                onOpenAgentInfo = { threadId -> app.openAgentSummary(threadId) },
                onAnswerQuestion = { text -> app.onAppEvent(AppEvent.AnswerAsyncQuestion(text)) },
                onEditPrompt = if (session.open && !session.loading && !session.running &&
                    !app.widget.backtracking && !session.config.blocksDirectInput && app.sideParentOf(session.threadId) == null
                ) {
                    { prompt ->
                        app.onAppEvent(AppEvent.RevertSessionForPromptEdit(session.threadId, prompt))
                        onBack()
                    }
                } else null,
                canLoadEarlier = app.widget.canLoadEarlier,
                loadingEarlier = app.widget.loadingEarlier,
                onLoadEarlier = app.widget::loadEarlier,
                // The page's rail is the transcript's own gutter; only its vertical insets stay.
                contentPadding =
                    PaddingValues(
                        top = UiConsts.Space8,
                        bottom = UiConsts.PageBottomInset,
                    ),
            )
        }
    }
}
