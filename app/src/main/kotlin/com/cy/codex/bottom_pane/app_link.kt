package com.cy.codex.bottom_pane

import com.cy.codex.protocol.protocol.v2.McpElicitationRequest
import java.net.URI
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** App-link flow of URL-mode elicitation (codex-rs/tui/src/bottom_pane/app_link_view.rs); the URL is validated before opening. */
internal enum class AppLinkKind { Auth, ExternalAction }

internal enum class AppLinkScreen { Link, Confirmation }

internal data class AppLinkPrompt(
    val kind: AppLinkKind,
    val connectorName: String? = null,
    val connectorId: String? = null,
    val serverName: String,
    val message: String,
    val url: String,
)

/** The connector fields this client reads out of `_codex_apps.connector_auth_failure`. */
internal data class ConnectorAuthFailure(
    val connectorId: String?,
    val connectorName: String?,
)

private const val CodexAppsServerName = "codex_apps"
private const val McpToolCodexAppsMetaKey = "_codex_apps"
private const val ConnectorAuthFailureMetaKey = "connector_auth_failure"
private const val ConnectorAuthFailureIsAuthFailureKey = "is_auth_failure"
private const val ConnectorAuthFailureConnectorIdKey = "connector_id"
private const val ConnectorAuthFailureConnectorNameKey = "connector_name"

/** Auth-failure metadata, or null unless `is_auth_failure` is exactly true — the flag check mirrors `codex-mcp/src/auth_elicitation.rs`. */
internal fun connectorAuthFailure(meta: JsonElement?): ConnectorAuthFailure? {
    val failure = ((meta as? JsonObject)?.get(McpToolCodexAppsMetaKey) as? JsonObject)
        ?.get(ConnectorAuthFailureMetaKey) as? JsonObject
        ?: return null
    if ((failure[ConnectorAuthFailureIsAuthFailureKey] as? JsonPrimitive)?.booleanOrNull != true) {
        return null
    }
    return ConnectorAuthFailure(
        connectorId = (failure[ConnectorAuthFailureConnectorIdKey] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
        connectorName = (failure[ConnectorAuthFailureConnectorNameKey] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
    )
}

/** Validate before opening; `requireChatgptHost` pins `codex_apps` sign-in URLs to ChatGPT hosts. */
internal fun validateAppLinkUrl(url: String, requireChatgptHost: Boolean): String? {
    val parsed = runCatching { URI(url) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true)) return null
    val host = parsed.host ?: return null
    if (!parsed.userInfo.isNullOrEmpty()) return null
    if (requireChatgptHost && !isAllowedChatgptAuthHost(host)) return null
    return url
}

internal fun isAllowedChatgptAuthHost(host: String): Boolean {
    val lower = host.lowercase()
    return lower == "chatgpt.com" ||
        lower == "chatgpt-staging.com" ||
        lower.endsWith(".chatgpt.com") ||
        lower.endsWith(".chatgpt-staging.com")
}

/** Build the prompt, or null when the URL must not be opened; `codex_apps` requires connector metadata, as upstream. */
internal fun appLinkPrompt(payload: McpElicitationRequest.Url): AppLinkPrompt? {
    if (payload.serverName == CodexAppsServerName) {
        val failure = connectorAuthFailure(payload.meta) ?: return null
        val url = validateAppLinkUrl(payload.url, requireChatgptHost = true) ?: return null
        return AppLinkPrompt(
            kind = AppLinkKind.Auth,
            connectorName = failure.connectorName,
            connectorId = failure.connectorId ?: payload.elicitationId,
            serverName = payload.serverName,
            message = payload.message,
            url = url,
        )
    }
    val url = validateAppLinkUrl(payload.url, requireChatgptHost = false) ?: return null
    return AppLinkPrompt(
        kind = AppLinkKind.ExternalAction,
        serverName = payload.serverName,
        message = payload.message,
        url = url,
    )
}

internal fun McpElicitationRequest.isConnectorAuth(): Boolean =
    serverName == CodexAppsServerName && connectorAuthFailure(meta) != null
