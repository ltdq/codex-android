package com.cy.codex

import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.WorkspaceMessagesResponse
import com.cy.codex.protocol.protocol.v2.WorkspaceMessageType

// The workspace headline `account/workspaceMessages/read` carries for one account; mirrors `codex-rs/tui/src/workspace_messages.rs`.
// Upstream keeps the cache and fetch gate on `ChatWidget`; here they are pure functions so JVM tests can walk the transitions without a widget.

/** Upstream `WORKSPACE_HEADLINE_REFRESH_INTERVAL` (codex-rs/tui/src/workspace_messages.rs). */
const val WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS: Long = 5 * 60 * 1000L

/** Mirrors upstream `WorkspaceHeadlineFetchResult`: what one read says about the headline slot. */
sealed interface WorkspaceHeadlineFetchResult {
    /** The feature is on; [headline] is the body to show, or null when nothing says anything. */
    data class Available(val headline: String?) : WorkspaceHeadlineFetchResult

    /** The feature is off for this account: show nothing and stop asking (until the account changes). */
    data object FeatureDisabled : WorkspaceHeadlineFetchResult
}

/**
 * Upstream `workspace_headline_from_response` (codex-rs/tui/src/workspace_messages.rs). A `featureEnabled=false`
 * wins over any messages still carried alongside it: the server may archive old notices with the flag.
 */
fun workspaceHeadlineFromResponse(response: WorkspaceMessagesResponse): WorkspaceHeadlineFetchResult {
    if (!response.featureEnabled) return WorkspaceHeadlineFetchResult.FeatureDisabled
    val headline = response.messages.asSequence()
        .filter { it.messageType == WorkspaceMessageType.Headline }
        .map { it.messageBody.trim() }
        .firstOrNull { it.isNotEmpty() }
    return WorkspaceHeadlineFetchResult.Available(headline)
}

/**
 * The account-scoped headline cache (codex-rs/tui/src/chatwidget/constructor.rs). [lastRequestedAtMs] stamps
 * when a request is *sent*, not when it lands, so the cadence ignores round-trip time; `0` means never.
 */
data class WorkspaceHeadlineCache(
    val headline: String? = null,
    val featureDisabled: Boolean = false,
    val lastRequestedAtMs: Long = 0L,
    val pendingRequestId: Long? = null,
    val nextRequestId: Long = 0L,
)

/**
 * Upstream `has_codex_backend_auth` (codex-rs/tui/src/chatwidget/settings.rs): only Codex-backend auth
 * may read workspace messages (else `invalid_request` per poll), approximated as "signed in with ChatGPT".
 */
val AccountReadResponse.hasCodexBackendAuth: Boolean
    get() = account is Account.Chatgpt

/** Upstream `status_line_workspace_headline_should_fetch` (codex-rs/tui/src/chatwidget/status_surfaces.rs). */
fun workspaceHeadlineShouldFetch(
    cache: WorkspaceHeadlineCache,
    nowMs: Long,
    hasCodexBackendAuth: Boolean,
): Boolean {
    if (cache.pendingRequestId != null || cache.featureDisabled || !hasCodexBackendAuth) return false
    // Never asked is always due (upstream `is_none_or` over `last_requested_at`).
    if (cache.lastRequestedAtMs == 0L) return true
    return nowMs - cache.lastRequestedAtMs >= WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS
}

/** A request the caller must now send: its id, plus the cache that records it as pending. */
data class WorkspaceHeadlineRequest(val cache: WorkspaceHeadlineCache, val requestId: Long)

/** Upstream `request_status_line_workspace_headline_if_due` (codex-rs/tui/src/chatwidget/status_surfaces.rs). */
fun beginWorkspaceHeadlineFetch(
    cache: WorkspaceHeadlineCache,
    nowMs: Long,
    hasCodexBackendAuth: Boolean,
): WorkspaceHeadlineRequest? {
    if (!workspaceHeadlineShouldFetch(cache, nowMs, hasCodexBackendAuth)) return null
    val requestId = cache.nextRequestId
    return WorkspaceHeadlineRequest(
        cache = cache.copy(
            pendingRequestId = requestId,
            lastRequestedAtMs = nowMs,
            nextRequestId = requestId + 1,
        ),
        requestId = requestId,
    )
}

/**
 * Upstream `set_status_line_workspace_headline` (codex-rs/tui/src/chatwidget/status_surfaces.rs): a response
 * matching no pending id is stale and dropped; a failure keeps the shown value, and `Available` re-enables a disabled feature.
 */
fun applyWorkspaceHeadlineResponse(
    cache: WorkspaceHeadlineCache,
    requestId: Long,
    result: Result<WorkspaceHeadlineFetchResult>,
): WorkspaceHeadlineCache {
    if (cache.pendingRequestId != requestId) return cache
    val settled = cache.copy(pendingRequestId = null)
    return result.fold(
        onSuccess = { fetch ->
            when (fetch) {
                is WorkspaceHeadlineFetchResult.Available -> settled.copy(
                    headline = fetch.headline,
                    featureDisabled = false,
                )

                WorkspaceHeadlineFetchResult.FeatureDisabled -> settled.copy(
                    headline = null,
                    featureDisabled = true,
                )
            }
        },
        onFailure = { settled },
    )
}

/**
 * Upstream `update_account_state`'s headline half (codex-rs/tui/src/chatwidget/settings.rs): an account change
 * drops the cache but keeps [WorkspaceHeadlineCache.nextRequestId] so a stale response never matches — and unlike upstream, this is the only reset.
 */
fun resetWorkspaceHeadlineCache(previous: WorkspaceHeadlineCache): WorkspaceHeadlineCache =
    WorkspaceHeadlineCache(nextRequestId = previous.nextRequestId)
