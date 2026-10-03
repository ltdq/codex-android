package com.cy.codex.chatwidget

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cy.codex.AppEvent
import com.cy.codex.BuildConfig
import com.cy.codex.CodexButtonRow
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexSelectRow
import com.cy.codex.CodexSwitchRow
import com.cy.codex.CodexValueRow
import com.cy.codex.DestinationCatalog
import com.cy.codex.R
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.AskForApproval
import com.cy.codex.protocol.protocol.v2.SandboxMode
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * 配置: the desktop's agent defaults, model features and workspace dependencies, over the app's own
 * config sources, data links and about card.
 *
 * A row the port cannot act on keeps the desktop's value and stays disabled, so the page reads as
 * the desktop's and the gap stays visible (see docs/TODO.md, 4. 设置).
 */
@Composable
internal fun SettingsAgentPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_agent_group_defaults)) {
        // The app reads the one user config, and has no editor to open its path in.
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_user_configuration),
            options = listOf(stringResource(R.string.settings_agent_user)),
            selected = 0,
            enabled = false,
            onSelect = {},
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_agent_open_config),
            summary = context.configPath,
            actionLabel = stringResource(R.string.settings_agent_open),
            enabled = false,
            onAction = {},
        )
        CodexRowDivider()
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_approval_policy),
            summary = stringResource(R.string.settings_agent_approval_policy_summary),
            options = AskForApproval.entries.map { it.label() },
            selected = AskForApproval.entries.indexOf(context.thread.approvalPolicy),
            onSelect = { context.write("approval_policy", AskForApproval.entries[it].wire) },
        )
        CodexRowDivider()
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_sandbox),
            summary = stringResource(R.string.settings_agent_sandbox_summary),
            options = SandboxMode.entries.map { it.label() },
            selected = SandboxMode.entries.indexOf(context.thread.sandboxPolicy.mode),
            onSelect = { context.write("sandbox_mode", SandboxMode.entries[it].wire) },
        )
        CodexRowDivider()
        val webSearchWires = listOf("disabled", "cached", "live")
        // An unset key means the live default.
        val webSearch = context.catalog.config.displayValue("web_search") ?: "live"
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_web_search),
            summary = stringResource(R.string.settings_agent_web_search_summary),
            options =
                listOf(
                    stringResource(R.string.settings_agent_web_search_off),
                    stringResource(R.string.settings_agent_web_search_cached),
                    stringResource(R.string.settings_agent_web_search_live),
                ),
            selected = webSearchWires.indexOf(webSearch).coerceAtLeast(0),
            onSelect = { context.write("web_search", webSearchWires[it]) },
        )
        CodexRowDivider()
        val verbosityWires = listOf<String?>(null, "low", "medium", "high")
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_verbosity),
            summary = stringResource(R.string.settings_agent_verbosity_summary),
            options =
                listOf(
                    stringResource(R.string.settings_agent_verbosity_model_default),
                    stringResource(R.string.settings_agent_verbosity_low),
                    stringResource(R.string.settings_agent_verbosity_medium),
                    stringResource(R.string.settings_agent_verbosity_high),
                ),
            selected =
                verbosityWires.indexOf(context.config.modelVerbosity).takeIf { it >= 0 } ?: 0,
            onSelect = {
                val wire = verbosityWires[it]
                context.write(
                    "model_verbosity",
                    wire?.let { level -> JsonPrimitive(level) } ?: JsonNull,
                )
            },
        )
        CodexRowDivider()
        val summaryWires = listOf("auto", "concise", "detailed", "none")
        val reasoningSummary = context.config.modelReasoningSummary ?: "auto"
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_reasoning_summary),
            summary = stringResource(R.string.settings_agent_reasoning_summary_summary),
            options =
                listOf(
                    stringResource(R.string.settings_agent_reasoning_summary_auto),
                    stringResource(R.string.settings_agent_reasoning_summary_concise),
                    stringResource(R.string.settings_agent_reasoning_summary_detailed),
                    stringResource(R.string.settings_agent_reasoning_summary_off),
                ),
            selected = summaryWires.indexOf(reasoningSummary).coerceAtLeast(0),
            onSelect = { context.write("model_reasoning_summary", summaryWires[it]) },
        )
    }

    CodexSection(stringResource(R.string.settings_agent_group_model_features)) {
        // The desktop's row is a multi-select over the levels the model control offers; the port
        // writes the one the session runs at, so a preset without efforts has nothing to pick.
        val efforts = context.preset?.supportedReasoningEfforts.orEmpty()
        val current = context.thread.reasoningEffort
        CodexSelectRow(
            title = stringResource(R.string.settings_agent_available_efforts),
            summary = stringResource(R.string.settings_agent_available_efforts_summary),
            options = efforts.map { it.label() }.ifEmpty { listOfNotNull(current?.label()) },
            selected = efforts.indexOfFirst { it == current }.coerceAtLeast(0),
            enabled = efforts.isNotEmpty(),
            onSelect = { context.write("model_reasoning_effort", efforts[it].wire) },
        )
    }

    CodexSection(stringResource(R.string.settings_agent_group_workspace_dependencies)) {
        CodexSwitchRow(
            title = stringResource(R.string.settings_agent_dependencies),
            summary = stringResource(R.string.settings_agent_dependencies_summary),
            checked = true,
            enabled = false,
            onCheckedChange = {},
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_agent_diagnose),
            summary = stringResource(R.string.settings_agent_diagnose_summary),
            actionLabel = stringResource(R.string.settings_agent_diagnose_action),
            onAction = { context.onOpenEntry(DestinationCatalog.Id.Diagnostics) },
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_agent_reset_workspace),
            summary = stringResource(R.string.settings_agent_reset_workspace_summary),
            actionLabel = stringResource(R.string.settings_agent_reinstall_action),
            enabled = false,
            destructive = true,
            onAction = {},
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.settings_agent_current_version),
            value = BuildConfig.VERSION_NAME,
        )
    }

    // The app's own groups, so the permission profiles, the experimental switches, the config
    // layers, the data stores and the version stay reachable.
    SettingsGranularApprovalSection(context.thread)
    SettingsPermissionProfilesGroup(
        context.thread,
        context.catalog,
        context.session.permissionSelectionError,
        context.onEvent,
    )
    SettingsExperimentalSection(context.catalog, context.onEvent)
    SettingsConfigSourcesSection(context.catalog, context.configPath)
    SettingsDataSection(context.onOpenEntry)
    SettingsAboutSection()
}

/** 个性化: the memory switches and the instructions file, over the app's memory store. */
@Composable
internal fun SettingsPersonalizationPage(context: SettingsPageContext) {
    CodexSection(stringResource(R.string.settings_personalization_group_memory)) {
        // The desktop hangs this line off the group title, in the row that also picks the host; the
        // port has no host picker, so the line stands as the card's first row.
        CodexRow(
            title = stringResource(R.string.settings_personalization_group_memory_summary),
            enabled = false,
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_personalization_use_memories),
            summary = stringResource(R.string.settings_personalization_use_memories_summary),
            checked = context.config.useMemories ?: false,
            onCheckedChange = { context.write("memories.use_memories", it) },
        )
        CodexRowDivider()
        CodexSwitchRow(
            title = stringResource(R.string.settings_personalization_generate_memories),
            summary = stringResource(R.string.settings_personalization_generate_memories_summary),
            checked = context.config.generateMemories ?: false,
            onCheckedChange = { context.write("memories.generate_memories", it) },
        )
        CodexRowDivider()
        CodexButtonRow(
            title = stringResource(R.string.settings_personalization_delete_memories),
            summary = stringResource(R.string.settings_personalization_delete_memories_summary),
            actionLabel = stringResource(R.string.settings_personalization_delete_action),
            destructive = true,
            onAction = { context.onEvent(AppEvent.ResetMemory) },
        )
    }

    CodexSection(stringResource(R.string.settings_personalization_group_instructions)) {
        // No AGENTS.md editor on the device; the file lives on the machine the session runs on.
        CodexNavRow(
            title = stringResource(R.string.settings_personalization_instructions),
            summary = stringResource(R.string.settings_personalization_instructions_summary),
            enabled = false,
            onClick = {},
        )
    }

    CodexSection {
        CodexNavRow(
            title = stringResource(R.string.sidebar_library_memories),
            onClick = { context.onOpenEntry(DestinationCatalog.Id.Memories) },
        )
    }
}

/** Mini 与虚拟宠物: no desktop capture covers the page, and the shape has no Android counterpart. */
@Composable
internal fun SettingsPetsPage(context: SettingsPageContext) {
    CodexEmptyRow(stringResource(R.string.settings_pets_unavailable))
}

/** 键盘快捷键: the app's own reference, which is the only shortcut surface the device has. */
@Composable
internal fun SettingsShortcutsPage(context: SettingsPageContext) {
    SettingsShortcutsSection(context.onOpenShortcuts)
}
