package com.cy.codexui

import androidx.annotation.StringRes

/**
 * One slash command's metadata.
 *
 * Mirrors the parts of `codex-rs/tui/src/slash_command.rs` this client can honor: the name, the
 * description shown in the popup, whether an argument is spliced into the draft instead of being
 * dispatched, and whether the command may run while a turn is in flight
 * (`SlashCommand::available_during_task`). Feature gating stays at the call site because it needs
 * catalog data — `/plan` needs the mode list to have answered — while this table is static.
 */
internal data class SlashCommandSpec(
    val name: String,
    @StringRes val descriptionRes: Int,
    val takesArgument: Boolean = false,
    val availableDuringTask: Boolean = true,
)

/**
 * The command catalog, in popup order.
 *
 * This is the single source of truth for [CodexApp.ComposerCommands] and for the popup: a command
 * that is missing here is neither recognized on submission nor suggested. Upstream's alias table
 * (`clean`→Stop, `cwd`→Pwd, `pet`→Pets) has no targets in this client, so no aliases are declared.
 */
internal object SlashCommands {
    val All: List<SlashCommandSpec> = listOf(
        SlashCommandSpec("new", R.string.runtime_new_thread, availableDuringTask = false),
        SlashCommandSpec("resume", R.string.sidebar_library_all_sessions),
        SlashCommandSpec("fork", R.string.session_list_fork, availableDuringTask = false),
        SlashCommandSpec("archive", R.string.session_list_archive, availableDuringTask = false),
        SlashCommandSpec("compact", R.string.slash_desc_compact, availableDuringTask = false),
        SlashCommandSpec("revert", R.string.slash_desc_revert, takesArgument = true),
        SlashCommandSpec("model", R.string.settings_tab_model),
        SlashCommandSpec("approvals", R.string.settings_tab_approval),
        SlashCommandSpec("permissions", R.string.settings_group_permissions),
        SlashCommandSpec("plan", R.string.slash_desc_plan, availableDuringTask = false),
        SlashCommandSpec("goal", R.string.goal_sheet_title, takesArgument = true),
        SlashCommandSpec("review", R.string.sidebar_library_review),
        SlashCommandSpec("diff", R.string.git_diff_screen_description),
        SlashCommandSpec("status", R.string.slash_desc_status),
        SlashCommandSpec("usage", R.string.account_screen_title),
        SlashCommandSpec("settings", R.string.settings_screen_title),
        SlashCommandSpec("shell", R.string.exec_command_title, takesArgument = true),
        SlashCommandSpec("mcp", R.string.sidebar_library_mcp_servers),
        SlashCommandSpec("skills", R.string.sidebar_library_skills),
        SlashCommandSpec("plugins", R.string.sidebar_library_plugins),
        SlashCommandSpec("apps", R.string.sidebar_library_apps),
        SlashCommandSpec("hooks", R.string.hooks_screen_title),
        SlashCommandSpec("memories", R.string.memories_screen_title),
        SlashCommandSpec("init", R.string.slash_desc_init, availableDuringTask = false),
    )

    fun find(name: String): SlashCommandSpec? = All.firstOrNull { it.name == name }

    /**
     * The popup rows for [query] (which includes the leading `/`).
     *
     * Upstream `command_popup.rs` puts exact matches before prefix matches and preserves the
     * catalog's own order inside each group; a bare `/` matches everything in declared order.
     */
    fun filter(query: String): List<SlashCommandSpec> {
        val needle = query.removePrefix("/")
        if (needle.isEmpty()) return All
        val exact = mutableListOf<SlashCommandSpec>()
        val prefix = mutableListOf<SlashCommandSpec>()
        for (spec in All) {
            when {
                spec.name.equals(needle, ignoreCase = true) -> exact += spec
                spec.name.startsWith(needle, ignoreCase = true) -> prefix += spec
            }
        }
        return exact + prefix
    }
}
