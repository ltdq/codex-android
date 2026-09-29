package com.cy.codex

/** Canonical ownership of every page/action id: exactly one owner set, enforced by a JVM test. */
object DestinationCatalog {
    object Id {
        const val Settings = "settings"
        const val New = "new"
        const val Workspace = "workspace"
        const val Sessions = "sessions"
        const val Archived = "archived"
        const val Projects = "projects"
        const val History = "history"
        const val Files = "files"
        const val Exec = "exec"
        const val Terminals = "terminals"
        const val Review = "review"
        const val Worktree = "worktree"
        const val Diff = "diff"
        const val Goal = "goal"
        const val Realtime = "realtime"
        const val Mcp = "mcp"
        const val Skills = "skills"
        const val Plugins = "plugins"
        const val Apps = "apps"
        const val Hooks = "hooks"
        const val PluginShares = "plugin_shares"
        const val Account = "account"
        const val Memories = "memories"
        const val Migration = "migration"
        const val RemoteControl = "remote_control"
        const val Verification = "verification"
        const val Bedrock = "bedrock"
        const val Sandbox = "sandbox"
        const val Diagnostics = "diagnostics"
        const val Status = "status"
    }

    /** The rail's bottom item: chrome, not a page of the open session. */
    val Chrome = setOf(Id.Settings)

    /** Pages a rail item opens on its own, with no row of its own menu to name them. */
    val RailPages = setOf(Id.Projects)

    /**
     * The shell's second segment, per rail item; the project rows come from `thread/list` and have
     * no id, so they are not listed here.
     */
    val HomeMenu = setOf(Id.New)
    val PluginsMenu = setOf(Id.Skills, Id.Mcp, Id.Plugins, Id.Apps, Id.Hooks, Id.PluginShares)

    /** The floating session panel: the thread library, then the tools that act on the open thread. */
    val SessionLibrary = setOf(Id.Sessions, Id.Archived, Id.Workspace)
    val SessionTools = setOf(
        Id.History,
        Id.Files,
        Id.Exec,
        Id.Terminals,
        Id.Review,
        Id.Worktree,
        Id.Diff,
        Id.Goal,
        Id.Realtime,
    )

    /** Pages the settings menu opens directly; its section rows are `SettingsSection`. */
    val SettingsMenu = setOf(
        Id.Account,
        Id.Memories,
        Id.Migration,
        Id.RemoteControl,
        Id.Verification,
        Id.Sandbox,
        Id.Diagnostics,
        Id.Status,
    )

    val Onboarding = setOf(Id.Bedrock)

    val Owned =
        buildSet {
            addAll(Chrome)
            addAll(RailPages)
            addAll(HomeMenu)
            addAll(PluginsMenu)
            addAll(SessionLibrary)
            addAll(SessionTools)
            addAll(SettingsMenu)
            addAll(Onboarding)
        }

    /** Every id has one owner; the sets are deliberately flat so a duplicate cannot hide in a map. */
    fun duplicateIds(): Set<String> {
        val groups = listOf(
            Chrome,
            RailPages,
            HomeMenu,
            PluginsMenu,
            SessionLibrary,
            SessionTools,
            SettingsMenu,
            Onboarding,
        )
        return groups.flatten().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
}
