package com.cy.codex.app

import com.cy.codex.chatwidget.InMemoryReserveReturnStore
import com.cy.codex.chatwidget.LUNA_RESERVE_BANNER
import com.cy.codex.chatwidget.LUNA_RESERVE_MODEL
import com.cy.codex.chatwidget.ReserveReturnModel
import com.cy.codex.chatwidget.ReserveReturnStore
import com.cy.codex.chatwidget.loadReserveReturn
import com.cy.codex.chatwidget.prepareReserveReturn
import com.cy.codex.protocol.protocol.v2.BackendBanner
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The decision half of codex-rs/tui/src/app/backend_banner_fallback.rs. */
class BackendBannerFallbackTest {

    private val store = InMemoryReserveReturnStore()

    private val models = listOf(
        preset("gpt-5.6", efforts = listOf(ReasoningEffort.Low, ReasoningEffort.High)),
        preset("gpt-5.6-luna", efforts = listOf(ReasoningEffort.Medium)),
        preset("gpt-reserve", hidden = true, efforts = listOf(ReasoningEffort.Medium, ReasoningEffort.High)),
        preset("cheaper", efforts = listOf(ReasoningEffort.Low)),
    )

    private fun preset(
        model: String,
        hidden: Boolean = false,
        efforts: List<ReasoningEffort> = listOf(ReasoningEffort.Medium),
    ) = ModelPreset(
        id = model,
        model = model,
        displayName = model,
        description = "",
        defaultReasoningEffort = efforts.first(),
        supportedReasoningEfforts = efforts,
        isDefault = false,
        hidden = hidden,
    )

    private fun banner(
        bannerType: String = "selected_model_limit",
        blockedModelSlug: String? = null,
        fallbackModelSlugs: List<String> = emptyList(),
    ) = BackendBanner(
        bannerType = bannerType,
        title = "title",
        description = "description",
        blockedModelSlug = blockedModelSlug,
        fallbackModelSlugs = fallbackModelSlugs,
    )

    private fun context(
        currentModel: String = "gpt-5.6",
        currentEffort: ReasoningEffort? = ReasoningEffort.High,
        ordinaryUsageRecovered: Boolean = false,
        banner: BackendBanner? = null,
        forkedFromId: String? = null,
        accountId: String? = "account-a",
        collaborationMode: CollaborationMode = CollaborationMode.Default,
    ) = BannerFallbackContext(
        threadId = "thread-1",
        forkedFromId = forkedFromId,
        accountId = accountId,
        currentModel = currentModel,
        currentEffort = currentEffort,
        collaborationMode = collaborationMode,
        ordinaryUsageRecovered = ordinaryUsageRecovered,
        banner = banner,
        models = models,
    )

    private fun decide(
        context: BannerFallbackContext,
        prepared: Boolean = true,
        store: ReserveReturnStore = this.store,
    ): BannerFallbackOutcome = bannerFallback(
        store = store,
        context = context,
        prepareReturn = {
            prepared && prepareReserveReturn(
                store,
                context.threadId,
                context.accountId,
                context.currentModel,
                context.currentEffort,
            )
        },
        switchedToTemplate = "Automatically switched to %1\$s due to usage limits.",
        switchedBackTemplate = "Automatically switched back to %1\$s because ordinary usage is available again.",
    )

    @Test
    fun `a luna_reserve banner switches into Reserve and prepares the return target`() {
        val outcome = decide(
            context(banner = banner(bannerType = LUNA_RESERVE_BANNER)),
        ) as BannerFallbackOutcome.Apply
        assertTrue(outcome.plan.enteringReserve)
        assertEquals(LUNA_RESERVE_MODEL, outcome.plan.params.model)
        assertEquals(CollaborationMode.Default, outcome.plan.params.collaborationMode)
        assertNull(outcome.plan.params.permissions, "automatic switches never carry permission defaults")
        assertEquals(
            ReserveReturnModel("account-a", "gpt-5.6", ReasoningEffort.High),
            loadReserveReturn(store, "thread-1", "account-a"),
            "the return target is stored before any server state changes",
        )
        assertEquals("Automatically switched to Luna Reserve due to usage limits.", outcome.plan.notice)
    }

    @Test
    fun `a luna_reserve banner only fires while the blocked model is selected`() {
        val blocked = banner(bannerType = LUNA_RESERVE_BANNER, blockedModelSlug = "other-model")
        assertEquals(BannerFallbackOutcome.None, decide(context(banner = blocked)))
        val matching = banner(bannerType = LUNA_RESERVE_BANNER, blockedModelSlug = "gpt-5.6")
        assertTrue(decide(context(banner = matching)) is BannerFallbackOutcome.Apply)
        assertEquals(
            BannerFallbackOutcome.None,
            decide(context(currentModel = LUNA_RESERVE_MODEL, banner = banner(bannerType = LUNA_RESERVE_BANNER))),
            "already on Reserve there is nothing to enter",
        )
    }

    @Test
    fun `a failed return save keeps the recovery panel instead of switching`() {
        val outcome = decide(context(banner = banner(bannerType = LUNA_RESERVE_BANNER)), prepared = false)
        assertEquals(BannerFallbackOutcome.UnavailableRecovery, outcome)
        assertNull(loadReserveReturn(store, "thread-1", "account-a"))
    }

    @Test
    fun `an exhausted Reserve task switches back once ordinary usage recovers`() {
        prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", ReasoningEffort.Low)
        val outcome = decide(
            context(currentModel = LUNA_RESERVE_MODEL, ordinaryUsageRecovered = true),
        ) as BannerFallbackOutcome.Apply
        assertFalse(outcome.plan.enteringReserve)
        assertEquals("gpt-5.6", outcome.plan.params.model)
        assertEquals(ReasoningEffort.Low, outcome.plan.effort, "the saved effort comes back with the model")
        assertEquals(ReasoningEffort.Low, outcome.plan.params.effort)
        assertEquals(
            "Automatically switched back to gpt-5.6 because ordinary usage is available again.",
            outcome.plan.notice,
        )
    }

    @Test
    fun `recovery without a usable return target switches nothing`() {
        assertEquals(
            BannerFallbackOutcome.None,
            decide(context(currentModel = LUNA_RESERVE_MODEL, ordinaryUsageRecovered = true)),
        )
        prepareReserveReturn(store, "thread-1", "account-a", "vanished-model", null)
        assertEquals(
            BannerFallbackOutcome.None,
            decide(context(currentModel = LUNA_RESERVE_MODEL, ordinaryUsageRecovered = true)),
        )
        // A forked task inherits the parent's target when the account matches.
        prepareReserveReturn(store, "thread-1", "account-a", "cheaper", ReasoningEffort.Low)
        val forked = decide(
            context(currentModel = LUNA_RESERVE_MODEL, ordinaryUsageRecovered = true, forkedFromId = "thread-1")
                .copy(threadId = "thread-2"),
        ) as BannerFallbackOutcome.Apply
        assertEquals("cheaper", forked.plan.params.model)
        assertEquals(
            ReserveReturnModel("account-a", "cheaper", ReasoningEffort.Low),
            loadReserveReturn(store, "thread-2", "account-a"),
            "the fork stores its own copy of the return target",
        )
    }

    @Test
    fun `a model banner walks its fallback list in order`() {
        val outcome = decide(
            context(
                banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("gone", "hidden-model", "cheaper", "gpt-5.6-luna")),
            ),
        ) as BannerFallbackOutcome.Apply
        assertEquals("cheaper", outcome.plan.params.model, "hidden and missing presets are skipped")
        assertEquals("Automatically switched to cheaper due to usage limits.", outcome.plan.notice)
    }

    @Test
    fun `a model banner only replaces the model it names`() {
        assertEquals(
            BannerFallbackOutcome.None,
            decide(context(banner = banner(blockedModelSlug = "other", fallbackModelSlugs = listOf("cheaper")))),
        )
        assertEquals(
            BannerFallbackOutcome.None,
            decide(context(banner = banner(fallbackModelSlugs = listOf("cheaper")))),
        )
        val models = this.models + preset("hidden-model", hidden = true)
        assertEquals(
            BannerFallbackOutcome.None,
            decide(
                context(banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("hidden-model")))
                    .copy(models = models),
            ),
        )
    }

    @Test
    fun `the effort follows the target's supported list`() {
        // `cheaper` supports Low only, so High cannot come along.
        val outcome = decide(
            context(
                currentEffort = ReasoningEffort.High,
                banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("cheaper")),
            ),
        ) as BannerFallbackOutcome.Apply
        assertEquals(ReasoningEffort.Low, outcome.plan.effort)
        assertEquals(ReasoningEffort.Low, outcome.plan.params.effort)

        // `gpt-5.6-luna` supports Medium only; no effort given falls back to its default too.
        val noEffort = decide(
            context(
                currentEffort = null,
                banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("gpt-5.6-luna")),
            ),
        ) as BannerFallbackOutcome.Apply
        assertEquals(ReasoningEffort.Medium, noEffort.plan.effort)
    }

    @Test
    fun `the switch keeps the thread's collaboration mode`() {
        val outcome = decide(
            context(
                collaborationMode = CollaborationMode.Plan,
                banner = banner(blockedModelSlug = "gpt-5.6", fallbackModelSlugs = listOf("cheaper")),
            ),
        ) as BannerFallbackOutcome.Apply
        assertEquals("thread-1", outcome.plan.params.threadId)
        assertEquals(CollaborationMode.Plan, outcome.plan.params.collaborationMode)
    }

    @Test
    fun `no banner and no recovery means no switch`() {
        assertEquals(BannerFallbackOutcome.None, decide(context()))
    }
}
