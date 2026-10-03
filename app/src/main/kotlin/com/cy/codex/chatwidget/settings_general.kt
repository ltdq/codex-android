package com.cy.codex.chatwidget

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexButtonRow
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSegmentedRow
import com.cy.codex.CodexSelectRow
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValue
import com.cy.codex.DestinationCatalog
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.SandboxMode
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

/**
 * 常规: the permissions pair over the app's own preferences, the editor, the popup window, the
 * notifications and the desktop's fun experiment.
 *
 * Rows the Android port has no equivalent for stay on the page — disabled, showing what the
 * desktop's demo had — rather than being dropped, so the page reads as the desktop's and the gap is
 * visible (see docs/TODO.md, 4. 设置).
 */
@Composable
internal fun SettingsGeneralPage(context: SettingsPageContext) {
    val thread = context.thread
    val fullAccess = thread.sandboxPolicy.mode == SandboxMode.DangerFullAccess

    CodexSection(stringResource(R.string.settings_general_group_permissions)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_default_permissions),
            summary = stringResource(R.string.settings_general_default_permissions_summary),
            checked = !fullAccess,
            onCheckedChange = {
                context.write(
                    "sandbox_mode",
                    if (it) SandboxMode.WorkspaceWrite.wire else SandboxMode.ReadOnly.wire,
                )
            },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_full_access),
            summary = stringResource(R.string.settings_general_full_access_summary),
            checked = fullAccess,
            onCheckedChange = { enabled ->
                context.write(
                    "sandbox_mode",
                    if (enabled) SandboxMode.DangerFullAccess.wire else SandboxMode.WorkspaceWrite.wire,
                )
                context.write(
                    "approval_policy",
                    if (enabled) AskForApproval.Never.wire else AskForApproval.OnRequest.wire,
                )
            },
        )
    }

    CodexSection(stringResource(R.string.settings_general_group_general)) {
        CodexRow(
            title = stringResource(R.string.settings_general_tasks_folder),
            summary = stringResource(R.string.settings_general_tasks_folder_summary),
            endAction = {
                CodexValue(
                    thread.cwd.ifEmpty { stringResource(R.string.settings_screen_no_directory) },
                    monospace = false,
                )
                Spacer(Modifier.width(UiConsts.Space8))
                Button(
                    onClick = context.onOpenWorkspacePicker,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_general_change),
                        fontSize = UiType.Action,
                        maxLines = 1,
                    )
                }
            },
        )
        FixedChoiceRow(
            title = R.string.settings_general_default_editor,
            summary = R.string.settings_general_default_editor_summary,
            value = "VS Code",
        )
        FixedChoiceRow(
            title = R.string.settings_general_terminal_shell,
            summary = R.string.settings_general_terminal_shell_summary,
            value = "PowerShell",
        )
        FixedChoiceRow(
            title = R.string.settings_general_language,
            summary = R.string.settings_general_language_summary,
            valueRes = R.string.settings_general_language_auto,
        )
        FixedChoiceRow(
            title = R.string.settings_general_confirm_close,
            summary = R.string.settings_general_confirm_close_summary,
            value = "Close shortcut only",
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_full_view),
            summary = stringResource(R.string.settings_general_full_view_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_bottom_panel),
            summary = stringResource(R.string.settings_general_bottom_panel_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSegmentedRow(
            title = stringResource(R.string.settings_general_terminal_position),
            summary = stringResource(R.string.settings_general_terminal_position_summary),
            options =
                listOf(
                    stringResource(R.string.settings_general_terminal_bottom),
                    stringResource(R.string.settings_general_terminal_side),
                ),
            selected = 0,
            enabled = false,
            onSelect = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_compress_history),
            summary = stringResource(R.string.settings_general_compress_history_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_general_licenses),
            summary = stringResource(R.string.settings_general_licenses_summary),
            actionLabel = stringResource(R.string.settings_general_view),
            enabled = false,
            onAction = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_plugins),
            summary = stringResource(R.string.settings_general_plugins_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
    }

    CodexSection(stringResource(R.string.settings_general_group_editor)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_plain_text),
            summary = stringResource(R.string.settings_general_plain_text_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_context_usage),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        FixedChoiceRow(
            title = R.string.settings_general_send_shortcut,
            summary = R.string.settings_general_send_shortcut_summary,
            valueRes = R.string.settings_general_send_enter,
        )
        CodexRowDivider()
        FixedSegmentedRow(
            title = R.string.settings_general_follow_up,
            summary = R.string.settings_general_follow_up_summary,
            options =
                listOf(
                    R.string.settings_general_follow_up_queue,
                    R.string.settings_general_follow_up_steer,
                ),
            selected = 1,
        )
    }

    CodexSection(stringResource(R.string.settings_general_group_popup)) {
        CodexRow(
            title = stringResource(R.string.settings_general_popup_shortcut),
            summary = stringResource(R.string.settings_general_popup_shortcut_summary),
            enabled = false,
            endAction = { CodexValue(stringResource(R.string.settings_general_popup_shortcut_value), monospace = false) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_standalone_chat),
            summary = stringResource(R.string.settings_general_standalone_chat_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
    }

    SettingsGeneralNotificationSection()

    // Automatic recaps are the app's own automation; the desktop capture has no row for them.
    SettingsRecapSection()

    CodexSection(stringResource(R.string.settings_general_group_experiments)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_confetti),
            summary = stringResource(R.string.settings_general_confetti_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
    }
}

/**
 * 通知: the three notifications the Android shell posts, over the two desktop-only rows.
 *
 * The desktop's 轮次完成通知 is a three-way choice; Android's own switch has two states, so the row
 * offers the two it can express and the reminder-only-when-asked case has no entry (docs/TODO.md).
 */
@Composable
private fun SettingsGeneralNotificationSection() {
    val context = LocalContext.current
    val types = NotificationSettings.types
    val master = NotificationSettings.enabled
    CodexSection(stringResource(R.string.settings_general_group_notifications)) {
        CodexSelectRow(
            title = stringResource(R.string.settings_general_turn_notifications),
            summary = stringResource(R.string.settings_general_turn_notifications_summary),
            options =
                listOf(
                    stringResource(R.string.settings_general_notify_off),
                    stringResource(R.string.settings_general_notify_unfocused),
                ),
            selected = if (AgentNotification.TurnComplete in types) 1 else 0,
            enabled = master,
            onSelect = {
                NotificationSettings.setType(context, AgentNotification.TurnComplete, it == 1)
            },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_permission_notifications),
            summary = stringResource(R.string.settings_general_permission_notifications_summary),
            checked = AgentNotification.ApprovalRequested in types,
            enabled = master,
            onCheckedChange = { NotificationSettings.setType(context, AgentNotification.ApprovalRequested, it) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_question_notifications),
            summary = stringResource(R.string.settings_general_question_notifications_summary),
            checked = AgentNotification.AsyncQuestion in types,
            enabled = master,
            onCheckedChange = { NotificationSettings.setType(context, AgentNotification.AsyncQuestion, it) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_general_dot_notifications),
            summary = stringResource(R.string.settings_general_dot_notifications_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        FixedChoiceRow(
            title = R.string.settings_general_notification_sound,
            summary = R.string.settings_general_notification_sound_summary,
            valueRes = R.string.settings_general_notification_sound_default,
        )
    }
}

/**
 * 导入: the automatic sync the desktop's import keeps, over the one import the app can run.
 *
 * The app's import (`external_agent_config_migration/`) reads another agent's configuration once;
 * the desktop's ongoing sync and its content picker have no counterpart.
 */
@Composable
internal fun SettingsImportPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_import_group_sync)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_import_keep_sync),
            summary = stringResource(R.string.settings_import_keep_sync_summary),
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexSelectRow(
            title = stringResource(R.string.settings_import_sync_content),
            summary = stringResource(R.string.settings_import_sync_content_summary),
            options = listOf(stringResource(R.string.settings_import_sync_custom)),
            selected = 0,
            enabled = false,
            onSelect = {},
        )
    }

    CodexSection(stringResource(R.string.settings_import_group_from_other)) {
        CodexRow(
            title = stringResource(R.string.settings_import_from_other_summary),
            enabled = false,
        )
        CodexRowDivider()
        CodexNavRow(
            title = stringResource(R.string.settings_import_claude_code),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Migration) },
        )
    }
}

/** A picker the port cannot act on: the row keeps the desktop's value where its own is missing. */
@Composable
internal fun FixedChoiceRow(
    @StringRes title: Int,
    @StringRes summary: Int = 0,
    value: String? = null,
    @StringRes valueRes: Int = 0,
) {
    val shown = value ?: stringResource(valueRes)
    CodexSelectRow(
        title = stringResource(title),
        summary = if (summary == 0) null else stringResource(summary),
        options = listOf(shown),
        selected = 0,
        enabled = false,
        onSelect = {},
    )
}

/** The same, for a two-way choice the desktop shows as a segmented picker. */
@Composable
internal fun FixedSegmentedRow(
    @StringRes title: Int,
    @StringRes summary: Int = 0,
    options: List<Int>,
    selected: Int,
) {
    CodexSegmentedRow(
        title = stringResource(title),
        summary = if (summary == 0) null else stringResource(summary),
        options = options.map { stringResource(it) },
        selected = selected,
        enabled = false,
        onSelect = {},
    )
}
