package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.BackendBanner
import com.cy.codex.protocol.protocol.v2.BackendBannerCta
import com.cy.codex.protocol.protocol.v2.BannerPresentation
import com.cy.codex.protocol.protocol.v2.CreditsSnapshot
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.RateLimitSnapshot
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Banner lifecycle (codex-rs/tui/src/chatwidget/backend_banners.rs). */
class BackendBannerTest {

    private fun banner(
        bannerType: String = "selected_model_limit",
        title: String = "Backend title",
        description: String = "Backend description",
        ctas: List<BackendBannerCta> = emptyList(),
        resetAt: Long? = null,
        modelSlug: String? = null,
        blockedModelSlug: String? = null,
        fallbackModelSlugs: List<String> = emptyList(),
        presentation: BannerPresentation = BannerPresentation.Inline,
        requestUrl: String? = null,
    ) = BackendBanner(
        bannerType = bannerType,
        title = title,
        description = description,
        ctas = ctas,
        resetAt = resetAt,
        modelSlug = modelSlug,
        blockedModelSlug = blockedModelSlug,
        fallbackModelSlugs = fallbackModelSlugs,
        presentation = presentation,
        requestUrl = requestUrl,
    )

    private fun response(
        ordinaryUsageAllowed: Boolean? = null,
        upsell: BackendBanner? = null,
        upsellPresent: Boolean = upsell != null,
        accountId: String? = "account-a",
        credits: CreditsSnapshot? = null,
        spendControlReached: Boolean? = null,
        rateLimitReachedType: String? = null,
    ) = AccountRateLimits(
        rateLimits = RateLimitSnapshot(
            credits = credits,
            spendControlReached = spendControlReached,
            rateLimitReachedType = rateLimitReachedType,
        ),
        accountId = accountId,
        ordinaryUsageAllowed = ordinaryUsageAllowed,
        rateLimitUpsell = upsell,
        rateLimitUpsellPresent = upsellPresent,
    )

    @Test
    fun `recovery needs an explicit ordinary usage verdict`() {
        // Null is unknown: percentages and reset times may never stand in for it.
        assertFalse(
            ordinaryUsageRecovered(response(ordinaryUsageAllowed = null, credits = CreditsSnapshot(true, true, null))),
        )
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = null)))
        assertTrue(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true)))
    }

    @Test
    fun `a false verdict recovers only when credits cover the usage`() {
        assertTrue(ordinaryUsageRecovered(response(ordinaryUsageAllowed = false, credits = CreditsSnapshot(true, false, "5"))))
        assertTrue(ordinaryUsageRecovered(response(ordinaryUsageAllowed = false, credits = CreditsSnapshot(false, true, null))))
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = false, credits = CreditsSnapshot(false, false, "5"))))
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = false)))
    }

    @Test
    fun `a banner or a hard stop blocks recovery even with a true verdict`() {
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, upsell = banner())))
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, spendControlReached = true)))
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, rateLimitReachedType = "primary")))
        assertTrue(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, spendControlReached = false)))
    }

    @Test
    fun `an unparsable upsell that is still on the wire blocks recovery`() {
        // Upstream keys on the raw field's absence, before `BackendBanner::parse` runs.
        assertFalse(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, upsellPresent = true)))
        assertTrue(ordinaryUsageRecovered(response(ordinaryUsageAllowed = true, upsellPresent = false)))
    }

    @Test
    fun `the same occurrence keeps its dismissal state`() {
        val first = updateBackendBanner(
            BackendBannerState(),
            response(upsell = banner(resetAt = 10L)),
            noticeAccountId = null,
            currentModel = "gpt-5.6",
        ).state.copy(dismissed = true, shown = true)
        val second = updateBackendBanner(
            first,
            response(upsell = banner(resetAt = 10L)),
            noticeAccountId = null,
            currentModel = "gpt-5.6",
        ).state
        assertTrue(second.dismissed)
        assertTrue(second.shown)
    }

    @Test
    fun `a changed occurrence resets shown and dismissed`() {
        val previous = BackendBannerState(
            accountId = "account-a",
            banner = banner(resetAt = 10L, blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("gpt-5.6-luna")),
            shown = true,
            dismissed = true,
            pickerDismissed = true,
            noticeModel = "gpt-5.6-luna",
        )
        val replacements = listOf(
            banner(resetAt = 20L, blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("gpt-5.6-luna")),
            banner(resetAt = 10L, blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("other")),
            banner(
                resetAt = 10L,
                blockedModelSlug = "gpt-5.6",
                fallbackModelSlugs = listOf("gpt-5.6-luna"),
                presentation = BannerPresentation.Dismissible,
            ),
        )
        for (replacement in replacements) {
            val fresh = updateBackendBanner(
                previous,
                response(upsell = replacement),
                noticeAccountId = null,
                currentModel = "gpt-5.6",
            ).state
            assertFalse(fresh.dismissed, "a replacement occurrence restarts dismissal")
            assertFalse(fresh.shown)
            assertFalse(fresh.pickerDismissed)
            assertNull(fresh.noticeModel)
        }
    }

    @Test
    fun `the occurrence key treats blocked and model slug as one identity slot`() {
        val blocked = banner(blockedModelSlug = "gpt-5.6")
        val named = banner(modelSlug = "gpt-5.6")
        assertEquals(bannerOccurrenceKey("account-a", blocked), bannerOccurrenceKey("account-a", named))
    }

    @Test
    fun `a remembered account dismisses only its own Reserve occurrence`() {
        val reserve = banner(bannerType = LUNA_RESERVE_BANNER)
        val fresh = updateBackendBanner(
            BackendBannerState(),
            response(upsell = reserve, accountId = "account-a"),
            noticeAccountId = "account-a",
            currentModel = "gpt-5.6",
        ).state
        assertTrue(fresh.dismissed, "the entry notice already shown suppresses the panel")
        val other = updateBackendBanner(
            BackendBannerState(),
            response(upsell = reserve, accountId = "account-b"),
            noticeAccountId = "account-a",
            currentModel = "gpt-5.6",
        ).state
        assertFalse(other.dismissed)
    }

    @Test
    fun `a recovered read clears the remembered notice account`() {
        val update = updateBackendBanner(
            BackendBannerState(),
            response(ordinaryUsageAllowed = true),
            noticeAccountId = "account-a",
            currentModel = "gpt-5.6",
        )
        assertTrue(update.state.ordinaryUsageRecovered)
        assertNull(update.noticeAccountId)
    }

    @Test
    fun `waiting for Reserve holds submissions and is settled on Reserve`() {
        val state = BackendBannerState(accountId = "account-a", banner = banner(bannerType = LUNA_RESERVE_BANNER))
        assertTrue(waitingForLunaReserve(state, "gpt-5.6"))
        val update = updateBackendBanner(
            BackendBannerState(),
            response(upsell = banner(bannerType = LUNA_RESERVE_BANNER)),
            noticeAccountId = null,
            currentModel = "gpt-5.6",
        )
        assertTrue(update.holdRecovery, "held input waits while Reserve is offered but not taken")
        assertFalse(waitingForLunaReserve(state, LUNA_RESERVE_MODEL))
        assertFalse(waitingForLunaReserve(BackendBannerState(banner = banner()), "gpt-5.6"))
    }

    @Test
    fun `a dismissible banner collapses for a new turn, an inline one does not`() {
        val dismissible = BackendBannerState(
            accountId = "account-a",
            banner = banner(presentation = BannerPresentation.Dismissible),
        )
        assertTrue(dismissBackendBannerForNewTurn(dismissible, "gpt-5.6").dismissed)
        val inline = BackendBannerState(accountId = "account-a", banner = banner())
        assertFalse(dismissBackendBannerForNewTurn(inline, "gpt-5.6").dismissed)
        assertFalse(dismissBackendBannerForNewTurn(BackendBannerState(), "gpt-5.6").dismissed)
    }

    @Test
    fun `an unavailable recovery reopens the panel and drops the return target`() {
        val store = InMemoryReserveReturnStore()
        prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", ReasoningEffort.High)
        val state = BackendBannerState(
            banner = banner(bannerType = LUNA_RESERVE_BANNER),
            dismissed = true,
            pickerDismissed = true,
            noticeModel = "gpt-reserve",
        )
        val reopened = showUnavailableReserveRecovery(state, store, "thread-1")
        assertFalse(reopened.dismissed)
        assertFalse(reopened.pickerDismissed)
        assertNull(reopened.noticeModel)
        assertNull(
            loadReserveReturn(store, "thread-1", "account-a"),
            "a failed switch must leave no return target",
        )
    }

    @Test
    fun `copy replaces the reset time and strips control characters`() {
        val bell = Char(7)
        val line = "Resets at {time}$bell now"
        assertEquals("Resets at 12:00 now", bannerCopy(line, "12:00"))
        assertEquals("Resets at {time} now", bannerCopy(line, null))
        val newline = Char(10)
        assertEquals("kept${newline}lines", bannerCopy("kept${newline}lines", null))
    }

    @Test
    fun `the reserve panel replaces copy before Reserve and adds the continue row on it`() {
        val state = BackendBannerState(
            accountId = "account-a",
            banner = banner(
                bannerType = LUNA_RESERVE_BANNER,
                ctas = listOf(BackendBannerCta("view_usage", "View usage")),
            ),
        )
        val pending = backendBannerSurface(
            state,
            currentModel = "gpt-5.6",
            resetTime = null,
            planType = null,
            usageLimitTitle = "Usage limit reached",
            usageLimitDescription = "Choose an option below to continue.",
            continueWithReserveLabel = "Continue with Luna Reserve",
        )
        val pendingPanel = pending as BannerSurface.Recovery
        assertEquals("Usage limit reached", pendingPanel.title)
        assertEquals("Choose an option below to continue.", pendingPanel.description)
        assertFalse(pendingPanel.continueWithReserve)
        assertEquals(listOf("View usage"), pendingPanel.options.map { it.label })

        val active = backendBannerSurface(
            state,
            currentModel = LUNA_RESERVE_MODEL,
            resetTime = null,
            planType = null,
            usageLimitTitle = "Usage limit reached",
            usageLimitDescription = "Choose an option below to continue.",
            continueWithReserveLabel = "Continue with Luna Reserve",
        ) as BannerSurface.Recovery
        assertEquals("Backend title", active.title)
        assertTrue(active.continueWithReserve)
        assertEquals("Continue with Luna Reserve", active.options.last().label)
        assertNull(active.options.last().action, "the continue row is a local dismissal")
    }

    @Test
    fun `a model fallback banner follows the selected model`() {
        val state = BackendBannerState(
            accountId = "account-a",
            banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("gpt-5.6-luna")),
        )
        // The banner describes the replacement once the switch landed, not the blocked model.
        assertNull(backendBannerVisible(state, "gpt-5.6"))
        assertEquals(state.banner, backendBannerVisible(state, "gpt-5.6-luna"))
        assertFalse(hasApplicableBackendBanner(state, "gpt-5.6"))
        assertTrue(hasApplicableBackendBanner(state, "gpt-5.6-luna"))
    }

    @Test
    fun `CTA verbs map to the existing client actions`() {
        val owner = banner()
        assertEquals(
            BannerAction.NotifyOwner(AddCreditsNudgeCreditType.Credits),
            resolveBannerAction(owner, "account-a", null, "notify_owner"),
        )
        assertEquals(
            BannerAction.NotifyOwner(AddCreditsNudgeCreditType.Credits),
            resolveBannerAction(owner, "account-a", null, "contact_owner"),
        )
        assertEquals(
            BannerAction.NotifyOwner(AddCreditsNudgeCreditType.UsageLimit),
            resolveBannerAction(owner, "account-a", null, "request_increase"),
        )
        assertEquals(
            BannerAction.OpenUrl("https://example.test/increase"),
            resolveBannerAction(
                banner(requestUrl = "https://example.test/increase"),
                "account-a",
                null,
                "request_increase",
            ),
        )
        assertNull(
            resolveBannerAction(
                banner(requestUrl = "https://user:pass@example.test/increase"),
                "account-a",
                null,
                "request_increase",
            ),
            "destinations with credentials never open",
        )
        assertEquals(BannerAction.ResetUsage, resolveBannerAction(owner, "account-a", null, "reset_usage"))
    }

    @Test
    fun `destination verbs pick workspace routes for workspace plans`() {
        assertEquals(
            BannerAction.OpenUrl("https://chatgpt.com/codex/settings/usage?credits_modal=true"),
            resolveBannerAction(banner(), "account-a", "plus", "add_credits"),
        )
        assertEquals(
            BannerAction.OpenUrl(
                "https://chatgpt.com/admin/billing?codex_credit_action=add_credits&account_id=account-a",
            ),
            resolveBannerAction(banner(), "account-a", "team", "add_credits"),
        )
        assertNull(
            resolveBannerAction(banner(), null, "team", "view_workspace_usage"),
            "admin routes need the account id to select the workspace",
        )
        assertEquals(
            BannerAction.OpenUrl("https://chatgpt.com/codex/purchase/reset"),
            resolveBannerAction(banner(), null, null, "buy_reset"),
        )
        assertEquals(
            BannerAction.OpenUrl("https://chatgpt.com/?cta_tab=personal&highlight_plan=pro&pro_variant=2x#pricing"),
            resolveBannerAction(banner(), null, "prolite", "open_pricing_dialog"),
        )
        assertEquals(
            BannerAction.OpenUrl("https://chatgpt.com/?cta_tab=personal&highlight_plan=plus#pricing"),
            resolveBannerAction(banner(), null, null, "open_pricing_dialog"),
        )
        assertNull(resolveBannerAction(banner(), null, null, "desktop_referral"), "unknown verbs drop")
    }

    @Test
    fun `unusable CTA labels are dropped while the rest survive`() {
        val bell = Char(7)
        val ctas = bannerCtas(
            banner(
                ctas = listOf(
                    BackendBannerCta("view_usage", "  "),
                    BackendBannerCta("view_usage", "View usage"),
                    BackendBannerCta("desktop_referral", "Dead row"),
                    BackendBannerCta("view_usage", "Bad${bell}label"),
                ),
            ),
            accountId = null,
            planType = null,
        )
        assertEquals(listOf("View usage"), ctas.map { it.label })
    }

    @Test
    fun `the picker collapses to one borrowed Reserve row while ordinary usage is blocked`() {
        val models = listOf(
            preset("gpt-5.6", hidden = false, efforts = listOf(ReasoningEffort.Low, ReasoningEffort.High)),
            preset("gpt-5.6-luna", hidden = false),
            preset("gpt-reserve", hidden = true),
        )
        val limitId = mapOf(
            "reserve" to RateLimitSnapshot(limitName = LUNA_RESERVE_MODEL, normalModelSlug = "gpt-5.6"),
        )
        val mode = modelPickerMode(models, LUNA_RESERVE_MODEL, ordinaryUsageRecovered = false, limitId)
        val restricted = mode as ModelPickerMode.Restricted
        assertEquals(LUNA_RESERVE_MODEL, restricted.row.model, "the row keeps Reserve routing")
        assertEquals("GPT-5.6", restricted.row.displayName, "metadata comes from the normal model")
        assertEquals(listOf(ReasoningEffort.Low, ReasoningEffort.High), restricted.row.supportedReasoningEfforts)

        val fallback = modelPickerMode(models, LUNA_RESERVE_MODEL, false, emptyMap()) as ModelPickerMode.Restricted
        assertEquals(LUNA_RESERVE_MODEL, fallback.row.model)
        assertEquals("GPT-5.6-LUNA", fallback.row.displayName, "metadata falls back to the Luna model")
    }

    @Test
    fun `the picker reports unavailable when no metadata can be borrowed`() {
        val models = listOf(preset("gpt-5.6", hidden = false))
        assertEquals(
            ModelPickerMode.RestrictedUnavailable,
            modelPickerMode(models, LUNA_RESERVE_MODEL, false, emptyMap()),
        )
    }

    @Test
    fun `the ordinary picker hides Reserve and keeps set-as-default rows`() {
        val models = listOf(
            preset("gpt-5.6", hidden = false, isDefault = true),
            preset("gpt-reserve", hidden = true),
        )
        val mode = modelPickerMode(models, "gpt-5.6", ordinaryUsageRecovered = true, emptyMap())
        assertEquals(listOf("gpt-5.6"), (mode as ModelPickerMode.Normal).rows.map { it.model })
    }

    @Test
    fun `the rate limit nudge stands down for banners and the Luna models`() {
        assertTrue(rateLimitNudgeBlocked(bannerApplicable = true, currentModel = "gpt-5.6"))
        assertTrue(rateLimitNudgeBlocked(false, LUNA_MODEL))
        assertTrue(rateLimitNudgeBlocked(false, LUNA_RESERVE_MODEL))
        assertFalse(rateLimitNudgeBlocked(false, "gpt-5.6"))
    }

    @Test
    fun `the reserve panel remembers its account while Reserve is active`() {
        val state = BackendBannerState(accountId = "account-a", banner = banner(bannerType = LUNA_RESERVE_BANNER))
        assertEquals("account-a", reserveNoticeAccountToRemember(state, LUNA_RESERVE_MODEL))
        assertNull(reserveNoticeAccountToRemember(state, "gpt-5.6"))
    }

    private fun preset(
        model: String,
        hidden: Boolean = false,
        isDefault: Boolean = false,
        efforts: List<ReasoningEffort> = listOf(ReasoningEffort.Medium),
    ) = ModelPreset(
        id = model,
        model = model,
        displayName = model.uppercase(),
        description = "description of $model",
        defaultReasoningEffort = efforts.first(),
        supportedReasoningEfforts = efforts,
        isDefault = isDefault,
        hidden = hidden,
    )
}
