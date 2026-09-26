package com.cy.codex.protocol.protocol.v2

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * The catalog families: plugins, marketplaces, apps, skills config, MCP tools and memory
 * (schema/typescript/v2/{Plugin*, Marketplace*, Apps*, Skills*, Mcp*}.ts).
 *
 * "Catalog" here means anything the settings surfaces *list and then act on*: the list response is
 * the flat row type the screen renders (see [PluginEntry] and friends in `thread_data.kt`), while
 * the action requests carry the ids needed to address one row.
 */

enum class PluginListMarketplaceKind(val wire: String) {
    Local("local"),
    Vertical("vertical"),
    WorkspaceDirectory("workspace-directory"),
    SharedWithMe("shared-with-me"),
    CreatedByMeRemote("created-by-me-remote"),
}

data class PluginListParams(
    /**
     * Working directories used to discover repo marketplaces; when omitted, only home-scoped
     * and the official curated marketplace are considered.
     */
    val cwds: List<String>? = null,
    val forceRefetch: Boolean = false,
    /**
     * Marketplace kind filter; when omitted, only local marketplaces are queried, plus the
     * default remote catalog when the feature flag enables it.
     */
    val marketplaceKinds: List<PluginListMarketplaceKind>? = null,
)

/**
 * `plugin/list`: there is deliberately no `marketplace/list` in the protocol — the marketplace
 * catalog *is* this response, and every plugin row hangs off one of its entries.
 */
data class PluginListResponse(
    val marketplaces: List<MarketplaceEntry> = emptyList(),
    val featuredPluginIds: List<String> = emptyList(),
    val marketplaceLoadErrors: List<MarketplaceLoadErrorInfo> = emptyList(),
)

data class MarketplaceLoadErrorInfo(
    val marketplacePath: String = "",
    val message: String = "",
)

data class PluginInstallParams(
    val pluginName: String,
    /** Local marketplace path; mutually exclusive with [remoteMarketplaceName]. */
    val marketplacePath: String? = null,
    val remoteMarketplaceName: String? = null,
    val installAttemptId: String? = null,
)

/**
 * `plugin/install` response: the install is done; what comes back is the follow-up — the auth
 * policy and the connectors the plugin needs set up before it can run (mirrors `v2::PluginInstallResponse`).
 */
data class PluginInstallResponse(
    val authPolicy: PluginAuthPolicy = PluginAuthPolicy.OnUse,
    val appsNeedingAuth: List<AppSummary> = emptyList(),
)

/** `v2::PluginAuthPolicy`: when the plugin's connectors are asked to authorize. */
enum class PluginAuthPolicy(val wire: String) {
    OnInstall("ON_INSTALL"),
    OnUse("ON_USE"),
    ;

    companion object {
        fun fromWire(value: String?): PluginAuthPolicy =
            entries.firstOrNull { it.wire == value } ?: OnUse
    }
}

data class AppSummary(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val installUrl: String? = null,
    val category: String? = null,
)

data class PluginUninstallParams(val pluginId: String)

data class PluginReadParams(
    val pluginName: String,
    val marketplacePath: String? = null,
    val remoteMarketplaceName: String? = null,
)

data class PluginReadResponse(val plugin: PluginDetail = PluginDetail())

/** One plugin's full record, as the detail page renders it. */
data class PluginDetail(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    val marketplace: String = "",
    val installed: Boolean = false,
    val author: String? = null,
    val homepage: String? = null,
    val skills: List<SkillEntry> = emptyList(),
    val mcpServers: List<McpServerStatusEntry> = emptyList(),
    val apps: List<AppInfo> = emptyList(),
    val readme: String? = null,
) {
    /** Fold back to the list row, so one plugin has one representation per screen. */
    fun toEntry(): PluginEntry = PluginEntry(
        id = id.ifEmpty { name },
        name = name.ifEmpty { id },
        description = description,
        installed = installed,
        version = version,
        marketplace = marketplace,
    )
}

/**
 * `plugin/installed` — what is present, again the marketplace catalog: a plugin only ever exists
 * *inside* a marketplace, and installed rows are read off [PluginEntry.installed].
 */
data class PluginInstalledParams(
    val cwds: List<String>? = null,
    /**
     * Uninstalled plugin names to return when present locally, so mention surfaces can offer
     * an install entrypoint for something the account has not installed.
     */
    val installSuggestionPluginNames: List<String>? = null,
)

data class PluginInstalledResponse(
    val marketplaces: List<MarketplaceEntry> = emptyList(),
    val marketplaceLoadErrors: List<MarketplaceLoadErrorInfo> = emptyList(),
)

data class PluginReconcileParams(val reason: String? = null)

data class PluginReconcileResponse(
    val changed: List<PluginEntry> = emptyList(),
    val summary: String? = null,
)

data class PluginSearchParams(
    val searchTerm: String,
    val cursor: String? = null,
    val limit: Int? = null,
    val scope: String? = null,
    val cwds: List<String>? = null,
)

data class PluginSearchResponse(
    val data: List<PluginEntry> = emptyList(),
    val nextCursor: String? = null,
)

data class PluginSkillReadParams(
    val remoteMarketplaceName: String,
    val remotePluginId: String,
    val skillName: String,
)

data class PluginSkillReadResponse(val contents: String? = null)

/** How widely a shared plugin is discoverable; the wire values are uppercase. */
enum class PluginShareDiscoverability(val wire: String) {
    Listed("LISTED"),
    Unlisted("UNLISTED"),
    Private("PRIVATE"),
    ;

    companion object {
        fun fromWire(value: String?): PluginShareDiscoverability =
            entries.firstOrNull { it.wire == value } ?: Private
    }
}

data class PluginShareTarget(
    val principalType: String,
    val principalId: String,
    val role: String = "reader",
)

data class PluginSharePrincipal(
    val principalType: String = "",
    val principalId: String = "",
    val role: String = "reader",
    val name: String = "",
)

data class PluginShareContext(
    val shareUrl: String? = null,
    val discoverability: PluginShareDiscoverability? = null,
    val sharePrincipals: List<PluginSharePrincipal>? = null,
)

data class PluginShareEntry(
    val plugin: PluginEntry,
    val localPluginPath: String? = null,
)

data class PluginShareListResponse(val data: List<PluginShareEntry> = emptyList())

/**
 * `plugin/share/save` params: [pluginPath] is the local plugin package; the rest is present only
 * when updating an existing share.
 */
data class PluginShareSaveParams(
    val pluginPath: String,
    val remotePluginId: String? = null,
    val discoverability: PluginShareDiscoverability? = null,
    val shareTargets: List<PluginShareTarget>? = null,
)

data class PluginShareSaveResponse(
    val remotePluginId: String = "",
    val shareUrl: String = "",
    val canPublishToWorkspace: Boolean? = null,
)

data class PluginShareDeleteParams(val remotePluginId: String)

data class PluginShareCheckoutParams(val remotePluginId: String)

data class PluginShareCheckoutResponse(
    val remotePluginId: String = "",
    val pluginId: String = "",
    val pluginName: String = "",
    val pluginPath: String = "",
    val marketplaceName: String = "",
    val marketplacePath: String = "",
    val remoteVersion: String? = null,
)

data class PluginShareUpdateTargetsParams(
    val remotePluginId: String,
    val discoverability: PluginShareDiscoverability,
    val shareTargets: List<PluginShareTarget> = emptyList(),
)

data class PluginShareUpdateTargetsResponse(
    val principals: List<PluginSharePrincipal> = emptyList(),
    val discoverability: PluginShareDiscoverability = PluginShareDiscoverability.Private,
)

/**
 * One marketplace the account can install from: a flattened projection of the protocol's
 * `PluginMarketplaceEntry`. [plugins] keeps the protocol's association (what is installed);
 * [pluginCount] stays separate because a marketplace advertises more plugins than the client has
 * records for.
 */
data class MarketplaceEntry(
    val name: String,
    val path: String = "",
    val isRemote: Boolean = false,
    val pluginCount: Int = 0,
    val sharedWithMe: Boolean = false,
    val description: String = "",
    val plugins: List<PluginEntry> = emptyList(),
)

data class MarketplaceAddParams(
    val source: String,
    val refName: String? = null,
    val sparsePaths: List<String>? = null,
)

data class MarketplaceAddResponse(val marketplace: MarketplaceEntry = MarketplaceEntry(name = ""))

data class MarketplaceRemoveParams(val marketplaceName: String)

/** `marketplace/upgrade`; `null` upgrades every marketplace. */
data class MarketplaceUpgradeParams(val marketplaceName: String? = null)

data class MarketplaceUpgradeResponse(
    val upgraded: List<String> = emptyList(),
    val summary: String? = null,
)

data class AppsReadParams(
    val appIds: List<String>,
    val includeTools: Boolean = false,
    val threadId: String? = null,
)

data class AppsReadResponse(
    val apps: List<AppInfo> = emptyList(),
    val missingAppIds: List<String> = emptyList(),
)

data class AppsInstalledParams(
    val forceRefresh: Boolean = false,
    val threadId: String? = null,
)

data class AppsInstalledResponse(val apps: List<AppInfo> = emptyList())

data class SkillsConfigWriteParams(
    val enabled: Boolean,
    val name: String? = null,
    val path: String? = null,
)

data class SkillsExtraRootsSetParams(val extraRoots: List<String> = emptyList())

data class McpServerOauthLoginParams(
    val name: String,
    val scopes: List<String>? = null,
    val threadId: String? = null,
    val timeoutSecs: Int? = null,
)

data class McpServerOauthLoginResponse(val authorizationUrl: String = "")

data class McpServerToolCallParams(
    val server: String,
    val tool: String,
    val arguments: String = "{}",
    val threadId: String? = null,
)

data class McpServerToolCallResponse(
    val result: String = "",
    val isError: Boolean = false,
)

data class McpResourceReadParams(
    val server: String,
    val uri: String,
    val threadId: String? = null,
)

/** One resource body. Mirrors `ResourceContent`: either inline [text] or base64 [blob]. */
data class ResourceContent(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null,
)

/** `mcpServer/resource/read` response: a list of contents, not one flat body. */
data class McpResourceReadResponse(
    val contents: List<ResourceContent>,
    val originCallId: String? = null,
)

data class McpServerOauthLoginCompletedNotification(
    val name: String,
    val success: Boolean = true,
    val error: String? = null,
)

data class McpServerEventStreamStartParams(
    val server: String,
    val threadId: String? = null,
)

data class McpServerEventStreamStopParams(
    val server: String,
    val threadId: String? = null,
)

/** `mcpServer/event/stream/notification`: one server-pushed JSON-RPC notification. */
data class McpServerEventStreamNotification(
    val subscriptionId: String,
    val notification: McpServerEventNotification,
)

/** The inner `{method, params}` pair of an event-stream notification, verbatim from the server. */
data class McpServerEventNotification(
    val method: String,
    val params: JsonElement = JsonNull,
)

data class MemoryStatusParams(val minConsolidatedThreads: Int? = null)

data class MemoryStatusResponse(
    val v2Ready: Boolean = false,
    val v2ConsolidatedThreads: Int = 0,
)
