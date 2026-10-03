package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexButtonRow
import com.cy.codex.CodexFieldRow
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSwitchRow
import com.cy.codex.DestinationCatalog
import com.cy.codex.R

/**
 * 钩子: the desktop's configured-hook list over the app's own hooks browser.
 *
 * Reviewing and trusting a hook happens on the app's page, so the desktop's list row is the row that
 * opens it; with nothing configured the desktop's empty state stands on its own.
 */
@Composable
internal fun SettingsHooksPage(context: SettingsPageContext) {
    if (context.catalog.hooks.isEmpty()) {
        CodexSection {
            CodexRow(
                title = stringResource(R.string.settings_hooks_empty),
                summary = stringResource(R.string.settings_hooks_empty_summary),
                enabled = false,
            )
        }
        // The desktop's list has no row to open here, so the app's browser needs one of its own.
        CodexSection { HooksReviewRow(context) }
    } else {
        CodexSection(stringResource(R.string.settings_hooks_group)) { HooksReviewRow(context) }
    }
}

/** The app's hooks browser: the desktop's list row leads to the page where a hook is trusted. */
@Composable
private fun HooksReviewRow(context: SettingsPageContext) {
    CodexNavRow(
        title = stringResource(R.string.settings_hooks_review),
        summary = stringResource(R.string.settings_hooks_count, context.catalog.hooks.size),
        onClick = { context.onOpenEntry(DestinationCatalog.Id.Hooks) },
    )
}

/**
 * 连接: the desktop's SSH connection list, which the app has no manager for, over the app's own
 * remote-control page.
 */
@Composable
internal fun SettingsConnectionsPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_connections_group_ssh)) {
        CodexButtonRow(
            title = stringResource(R.string.settings_connections_add),
            actionLabel = stringResource(R.string.settings_connections_add),
            enabled = false,
            onAction = {},
        )
        CodexRowDivider()
        CodexRow(
            title = stringResource(R.string.settings_connections_empty),
            summary = stringResource(R.string.settings_connections_empty_summary),
            enabled = false,
        )
    }
    CodexSection(stringResource(R.string.sidebar_library_remote)) {
        CodexNavRow(
            title = stringResource(R.string.sidebar_library_remote),
            summary = stringResource(R.string.remote_control_relay_detail),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.RemoteControl) },
        )
    }
}

/**
 * 代码审查: the app's own review entry over the one row the port can state for this page.
 *
 * No capture shows the desktop's rows here, so the disabled row names the gap instead of holding a
 * value the port never had.
 */
@Composable
internal fun SettingsReviewPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_review_group)) {
        CodexNavRow(
            title = stringResource(R.string.sidebar_library_review),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Review) },
        )
        CodexRowDivider()
        CodexRow(title = stringResource(R.string.settings_review_unavailable), enabled = false)
    }
}

/**
 * Git: the desktop's branch, merge and prompt rows, none of which the port has a config key for.
 *
 * Every row stays disabled holding the desktop's value, so the page reads as the desktop's and the
 * gap is visible (docs/TODO.md, 4. 设置); [context] goes unused because none of them writes.
 */
@Composable
internal fun SettingsGitPage(context: SettingsPageContext) {
    // The capture shows no group title over the branch, merge and review rows.
    CodexSection {
        CodexFieldRow(
            title = stringResource(R.string.settings_git_branch_prefix),
            summary = stringResource(R.string.settings_git_branch_prefix_summary),
            value = "codex/",
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_git_field_confirm),
            enabled = false,
        )
        CodexRowDivider()
        FixedSegmentedRow(
            title = R.string.settings_git_merge_method,
            summary = R.string.settings_git_merge_method_summary,
            options = listOf(R.string.settings_git_merge, R.string.settings_git_squash),
            selected = 0,
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_git_force_push),
            summary = stringResource(R.string.settings_git_force_push_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_git_draft_pull_request),
            summary = stringResource(R.string.settings_git_draft_pull_request_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        FixedSegmentedRow(
            title = R.string.settings_git_review_presentation,
            summary = R.string.settings_git_review_presentation_summary,
            options =
                listOf(
                    R.string.settings_git_review_inline,
                    R.string.settings_git_review_separate,
                ),
            selected = 0,
        )
    }

    CodexSection(stringResource(R.string.settings_git_group_watch)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_git_auto_merge),
            summary = stringResource(R.string.settings_git_auto_merge_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexFieldRow(
            title = stringResource(R.string.settings_git_watch_instructions),
            value = "",
            placeholder = stringResource(R.string.settings_git_watch_instructions_placeholder),
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_git_field_confirm),
            enabled = false,
        )
    }

    // The group's own summary has no place on the card, so the field row carries it.
    CodexSection(stringResource(R.string.settings_git_group_commit)) {
        CodexFieldRow(
            title = stringResource(R.string.settings_git_commit_guidance),
            summary = stringResource(R.string.settings_git_group_commit_summary),
            value = "",
            placeholder = stringResource(R.string.settings_git_commit_placeholder),
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_git_field_confirm),
            enabled = false,
        )
    }

    CodexSection(stringResource(R.string.settings_git_group_pull_request)) {
        CodexFieldRow(
            title = stringResource(R.string.settings_git_pull_request_guidance),
            summary = stringResource(R.string.settings_git_group_pull_request_summary),
            value = "",
            placeholder = stringResource(R.string.settings_git_pull_request_placeholder),
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_git_field_confirm),
            enabled = false,
        )
    }
}

/** 环境: the desktop's project list, whose 添加项目 is the app's own workspace picker. */
@Composable
internal fun SettingsEnvironmentPage(context: SettingsPageContext) {
    val projects = context.catalog.projects
    CodexSection(stringResource(R.string.settings_environment_group_projects)) {
        CodexButtonRow(
            title = stringResource(R.string.settings_environment_add_project),
            actionLabel = stringResource(R.string.settings_environment_add_project),
            onAction = context.onOpenWorkspacePicker,
        )
        if (projects.isEmpty()) {
            CodexRowDivider()
            CodexRow(
                title = stringResource(R.string.settings_environment_no_projects),
                enabled = false,
            )
        } else {
            projects.forEach { project ->
                CodexRowDivider()
                CodexNavRow(
                    title = project.name,
                    onClick = { context.onOpenEntry(DestinationCatalog.Id.Projects) },
                )
            }
        }
    }

    // The app's own working directory and writable roots; the desktop capture has no row for them.
    SettingsWorkspaceSection(
        cwd = context.thread.cwd,
        roots = context.thread.workspaceRoots,
        onOpenWorkspacePicker = context.onOpenWorkspacePicker,
    )
}

/**
 * Worktrees: the desktop's preferences for the worktrees ChatGPT manages, none of which the app
 * keeps, over the app's own worktree page.
 */
@Composable
internal fun SettingsWorktreesPage(context: SettingsPageContext) {
    // The capture shows no group title over the preference rows.
    CodexSection {
        CodexFieldRow(
            title = stringResource(R.string.settings_worktrees_root),
            summary =
                listOf(
                        stringResource(R.string.settings_worktrees_root_summary),
                        DesktopWorktreeRoot,
                    )
                    .joinToString(" · "),
            value = "",
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_worktrees_field_confirm),
            enabled = false,
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_worktrees_fetch),
            summary = stringResource(R.string.settings_worktrees_fetch_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_worktrees_auto_delete),
            summary = stringResource(R.string.settings_worktrees_auto_delete_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexFieldRow(
            title = stringResource(R.string.settings_worktrees_limit),
            summary = stringResource(R.string.settings_worktrees_limit_summary),
            value = "15",
            onCommit = {},
            confirmLabel = stringResource(R.string.settings_worktrees_field_confirm),
            enabled = false,
        )
    }

    // The group title already reads 尚无工作树, so the card holds the desktop's line under it.
    CodexSection(stringResource(R.string.settings_worktrees_empty)) {
        CodexRow(
            title = stringResource(R.string.settings_worktrees_empty_summary),
            enabled = false,
        )
        CodexRowDivider()
        CodexNavRow(
            title = stringResource(R.string.worktrees_title),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Worktree) },
        )
    }
}

/** The desktop capture's worktree root; the app keeps no preference of its own to show instead. */
private const val DesktopWorktreeRoot = "C:/Users/cyzi7/.codex/worktrees"
