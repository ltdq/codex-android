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
        const val Scheduled = "scheduled"
        const val NewTask = "new_task"
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
    val RailPages = setOf(Id.Projects, Id.Scheduled)

    /**
     * The shell's second segment, per rail item; the project rows come from `thread/list` and have
     * no id, so they are not listed here.
     *
     * The chat's own menu lists the whole thread library and the tools that act on the open
     * session; the archived half of the library hangs off the settings menu instead.
     */
    val HomeMenu = setOf(Id.New, Id.Sessions)

    /** What the rail's 自定义 item opens: the catalogs the plugins page reads. */
    val CustomizeMenu = setOf(Id.Plugins, Id.Skills, Id.Mcp, Id.Apps, Id.PluginShares)

    /** What the rail's 项目 item opens: the project page, plus the action that adds a workspace. */
    val ProjectsMenu = setOf(Id.Workspace)

    /** The rail's 定时任务 item: the page it opens, and the action that would schedule a task. */
    val ScheduledMenu = setOf(Id.NewTask)

    /** What the chat's menu adds under its own groups: the tools that act on the open session. */
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

    /**
     * Pages of the app's own that a settings page links to.
     *
     * The desktop's settings sidebar is four groups of pages; these are the app's pages the ported
     * pages carry rows for (账户, 记忆, 导入, 连接, 诊断 …), so every one of them stays reachable.
     */
    val SettingsMenu = setOf(
        Id.Account,
        Id.Memories,
        Id.Migration,
        Id.RemoteControl,
        Id.Verification,
        Id.Sandbox,
        Id.Diagnostics,
        Id.Status,
        Id.Archived,
        Id.Hooks,
    )

    val Onboarding = setOf(Id.Bedrock)

    val Owned =
        buildSet {
            addAll(Chrome)
            addAll(RailPages)
            addAll(HomeMenu)
            addAll(CustomizeMenu)
            addAll(ProjectsMenu)
            addAll(ScheduledMenu)
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
            CustomizeMenu,
            ProjectsMenu,
            ScheduledMenu,
            SessionTools,
            SettingsMenu,
            Onboarding,
        )
        return groups.flatten().groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
}
