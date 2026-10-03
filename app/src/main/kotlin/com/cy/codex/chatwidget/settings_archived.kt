package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.DestinationCatalog
import com.cy.codex.R

/**
 * 已归档的聊天: the desktop's empty state over the row that opens the app's archived list.
 *
 * The app's archived list is a page of its own (`Surface.Archived`), so this settings page points at
 * it rather than listing the threads a second time.
 */
@Composable
internal fun SettingsArchivedChatsPage(context: SettingsPageContext) {
    CodexSection {
        CodexRow(title = stringResource(R.string.settings_archived_empty), enabled = false)
        CodexRowDivider()
        CodexNavRow(
            title = stringResource(R.string.sidebar_library_archived),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Archived) },
        )
    }
}
