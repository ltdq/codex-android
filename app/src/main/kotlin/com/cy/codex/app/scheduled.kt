package com.cy.codex.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexEmptyState
import com.cy.codex.CodexPage
import com.cy.codex.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm

/**
 * The rail's 定时任务 page.
 *
 * Scheduled tasks are the desktop app's own feature: the app-server has no method for them, so the
 * page is the empty state the desktop shows and its action cannot be taken (docs/TODO.md, 4. 设置).
 */
@Composable
fun ScheduledScreen(modifier: Modifier = Modifier) {
    CodexPage(title = stringResource(R.string.nav_rail_scheduled), modifier = modifier) {
        CodexEmptyState(
            icon = MiuixIcons.Alarm,
            title = stringResource(R.string.scheduled_empty_title),
            description = stringResource(R.string.scheduled_empty_detail),
            actionLabel = stringResource(R.string.nav_menu_new_task),
        )
    }
}
