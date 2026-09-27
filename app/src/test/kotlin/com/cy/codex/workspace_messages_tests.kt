package com.cy.codex

import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.WorkspaceMessage
import com.cy.codex.protocol.protocol.v2.WorkspaceMessagesResponse
import com.cy.codex.protocol.protocol.v2.WorkspaceMessageType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The headline pick and the cache transitions upstream keeps on `ChatWidget` (codex-rs/tui/src/workspace_messages.rs). */
class WorkspaceMessagesTest {

    private fun message(
        type: WorkspaceMessageType,
        body: String,
        id: String = "message-id",
    ) = WorkspaceMessage(messageId = id, messageType = type, messageBody = body)

    @Test
    fun `the first non-empty headline wins over announcements and blanks`() {
        val response = WorkspaceMessagesResponse(
            featureEnabled = true,
            messages = listOf(
                message(WorkspaceMessageType.Announcement, "Announcement body", "announcement-id"),
                message(WorkspaceMessageType.Headline, "   ", "empty-headline-id"),
                message(WorkspaceMessageType.Headline, " Workspace headline ", "headline-id"),
            ),
        )
        assertEquals(
            WorkspaceHeadlineFetchResult.Available("Workspace headline"),
            workspaceHeadlineFromResponse(response),
        )
    }

    @Test
    fun `a disabled feature is reported even when messages exist`() {
        val response = WorkspaceMessagesResponse(
            featureEnabled = false,
            messages = listOf(message(WorkspaceMessageType.Headline, "Workspace headline")),
        )
        assertEquals(
            WorkspaceHeadlineFetchResult.FeatureDisabled,
            workspaceHeadlineFromResponse(response),
        )
    }

    @Test
    fun `an enabled feature with only blank or non-headline messages has no headline`() {
        val response = WorkspaceMessagesResponse(
            featureEnabled = true,
            messages = listOf(
                message(WorkspaceMessageType.Announcement, "Announcement body"),
                message(WorkspaceMessageType.Headline, "   "),
            ),
        )
        assertEquals(
            WorkspaceHeadlineFetchResult.Available(null),
            workspaceHeadlineFromResponse(response),
        )
    }

    @Test
    fun `an unknown message type never becomes the headline`() {
        // A kind this client does not model decodes to Unknown and is skipped like an announcement.
        assertEquals(WorkspaceMessageType.Unknown, WorkspaceMessageType.fromWire("future-kind"))
        val response = WorkspaceMessagesResponse(
            featureEnabled = true,
            messages = listOf(message(WorkspaceMessageType.fromWire("future-kind"), "Sneaky body")),
        )
        assertEquals(
            WorkspaceHeadlineFetchResult.Available(null),
            workspaceHeadlineFromResponse(response),
        )
    }

    @Test
    fun `a fetch reserves one request id and stamps the request clock`() {
        val first = requireNotNull(
            beginWorkspaceHeadlineFetch(WorkspaceHeadlineCache(), nowMs = 1_000L, hasCodexBackendAuth = true),
        )
        assertEquals(0L, first.requestId)
        assertEquals(0L, first.cache.pendingRequestId)
        assertEquals(1_000L, first.cache.lastRequestedAtMs)
        assertEquals(1L, first.cache.nextRequestId)

        // One request at a time: the second begin is refused until the first settles.
        assertNull(
            beginWorkspaceHeadlineFetch(first.cache, nowMs = 2_000L, hasCodexBackendAuth = true),
        )
        val settled = applyWorkspaceHeadlineResponse(
            first.cache,
            first.requestId,
            Result.success(WorkspaceHeadlineFetchResult.Available("Workspace headline")),
        )
        val second = requireNotNull(
            beginWorkspaceHeadlineFetch(
                settled,
                nowMs = 1_000L + WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS,
                hasCodexBackendAuth = true,
            ),
        )
        assertEquals(1L, second.requestId)
    }

    @Test
    fun `a settled request stores the headline and clears the pending id`() {
        val cache = WorkspaceHeadlineCache(pendingRequestId = 3L, lastRequestedAtMs = 1_000L, nextRequestId = 4L)
        val settled = applyWorkspaceHeadlineResponse(
            cache,
            requestId = 3L,
            result = Result.success(WorkspaceHeadlineFetchResult.Available("Workspace headline")),
        )
        assertEquals("Workspace headline", settled.headline)
        assertNull(settled.pendingRequestId)
        assertFalse(settled.featureDisabled)
        // The cadence is measured from when the request was sent, not from when it landed.
        assertEquals(1_000L, settled.lastRequestedAtMs)
    }

    @Test
    fun `an empty available answer clears the headline and re-enables fetching`() {
        val cache = WorkspaceHeadlineCache(headline = "Old", featureDisabled = true, pendingRequestId = 3L)
        val settled = applyWorkspaceHeadlineResponse(
            cache,
            requestId = 3L,
            result = Result.success(WorkspaceHeadlineFetchResult.Available(null)),
        )
        assertNull(settled.headline)
        assertFalse(settled.featureDisabled)
    }

    @Test
    fun `a disabled feature stops further fetches until the account changes`() {
        val cache = WorkspaceHeadlineCache(headline = "Old", pendingRequestId = 2L, nextRequestId = 3L)
        val disabled = applyWorkspaceHeadlineResponse(
            cache,
            requestId = 2L,
            result = Result.success(WorkspaceHeadlineFetchResult.FeatureDisabled),
        )
        assertTrue(disabled.featureDisabled)
        assertNull(disabled.headline)
        assertNull(disabled.pendingRequestId)

        // Even with the clock zeroed, the disabled flag alone keeps the gate shut.
        val later = disabled.copy(lastRequestedAtMs = 0L)
        assertFalse(
            workspaceHeadlineShouldFetch(later, WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS, hasCodexBackendAuth = true),
        )
        assertNull(
            beginWorkspaceHeadlineFetch(later, WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS, hasCodexBackendAuth = true),
        )

        // Only the identity boundary re-opens it (upstream `update_account_state`).
        assertTrue(
            workspaceHeadlineShouldFetch(
                resetWorkspaceHeadlineCache(later),
                WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS,
                hasCodexBackendAuth = true,
            ),
        )
    }

    @Test
    fun `a response that answers a superseded request is dropped`() {
        val cache = WorkspaceHeadlineCache(headline = "Kept", pendingRequestId = 7L, nextRequestId = 8L)
        val settled = applyWorkspaceHeadlineResponse(
            cache,
            requestId = 6L,
            result = Result.success(WorkspaceHeadlineFetchResult.Available("Too late")),
        )
        assertEquals(cache, settled)
    }

    @Test
    fun `an account change resets the cache and ages out the in-flight response`() {
        val previous = WorkspaceHeadlineCache(headline = "Old", pendingRequestId = 5L, nextRequestId = 6L)
        val fresh = resetWorkspaceHeadlineCache(previous)
        // Only the request-id counter survives the reset, so ids stay unique across accounts.
        assertEquals(WorkspaceHeadlineCache(nextRequestId = 6L), fresh)
        assertEquals(
            fresh,
            applyWorkspaceHeadlineResponse(
                fresh,
                requestId = 5L,
                result = Result.success(WorkspaceHeadlineFetchResult.Available("Late")),
            ),
        )
    }

    @Test
    fun `a response in flight across a reset can never match the new account's request`() {
        val stale = requireNotNull(
            beginWorkspaceHeadlineFetch(WorkspaceHeadlineCache(), nowMs = 1_000L, hasCodexBackendAuth = true),
        )
        assertEquals(0L, stale.requestId)

        // The reset hands the counter over and the new account starts at request 1, not 0.
        val fresh = resetWorkspaceHeadlineCache(stale.cache)
        val current = requireNotNull(
            beginWorkspaceHeadlineFetch(fresh, nowMs = 2_000L, hasCodexBackendAuth = true),
        )
        assertEquals(1L, current.requestId)

        val afterStale = applyWorkspaceHeadlineResponse(
            current.cache,
            stale.requestId,
            Result.success(WorkspaceHeadlineFetchResult.FeatureDisabled),
        )
        assertEquals(current.cache, afterStale)

        val settled = applyWorkspaceHeadlineResponse(
            afterStale,
            current.requestId,
            Result.success(WorkspaceHeadlineFetchResult.Available("New account headline")),
        )
        assertEquals("New account headline", settled.headline)
        assertFalse(settled.featureDisabled)
        assertNull(settled.pendingRequestId)
    }

    @Test
    fun `a failed fetch keeps the previously shown headline`() {
        val cache = WorkspaceHeadlineCache(headline = "Old", pendingRequestId = 3L, nextRequestId = 4L)
        val settled = applyWorkspaceHeadlineResponse(
            cache,
            requestId = 3L,
            result = Result.failure(RuntimeException("network down")),
        )
        assertEquals("Old", settled.headline)
        assertFalse(settled.featureDisabled)
        assertNull(settled.pendingRequestId)
    }

    @Test
    fun `the fetch gate wants codex backend auth and a full interval between requests`() {
        val cache = WorkspaceHeadlineCache(lastRequestedAtMs = 1_000L)
        assertFalse(
            workspaceHeadlineShouldFetch(
                cache,
                nowMs = 1_000L + WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS - 1,
                hasCodexBackendAuth = false,
            ),
        )
        assertFalse(
            workspaceHeadlineShouldFetch(
                cache,
                nowMs = 1_000L + WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS - 1,
                hasCodexBackendAuth = true,
            ),
        )
        assertTrue(
            workspaceHeadlineShouldFetch(
                cache,
                nowMs = 1_000L + WORKSPACE_HEADLINE_REFRESH_INTERVAL_MS,
                hasCodexBackendAuth = true,
            ),
        )
        // A cache that never asked is due at once.
        assertTrue(
            workspaceHeadlineShouldFetch(
                WorkspaceHeadlineCache(),
                nowMs = 1L,
                hasCodexBackendAuth = true,
            ),
        )
    }

    @Test
    fun `only a chatgpt account counts as codex backend auth`() {
        assertTrue(
            AccountReadResponse(
                requiresOpenaiAuth = true,
                account = Account.Chatgpt(email = "user@example.com", planType = "pro"),
            ).hasCodexBackendAuth,
        )
        assertFalse(AccountReadResponse(requiresOpenaiAuth = true).hasCodexBackendAuth)
        assertFalse(AccountReadResponse(requiresOpenaiAuth = false, account = Account.ApiKey).hasCodexBackendAuth)
        assertFalse(
            AccountReadResponse(
                requiresOpenaiAuth = false,
                account = Account.AmazonBedrock(usesCodexManagedCredentials = true),
            ).hasCodexBackendAuth,
        )
    }
}
