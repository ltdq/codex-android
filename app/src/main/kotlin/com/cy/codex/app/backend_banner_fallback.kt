package com.cy.codex.app

import com.cy.codex.chatwidget.LUNA_RESERVE_BANNER
import com.cy.codex.chatwidget.LUNA_RESERVE_MODEL
import com.cy.codex.chatwidget.ReserveReturnStore
import com.cy.codex.chatwidget.inheritReserveReturn
import com.cy.codex.chatwidget.loadReserveReturn
import com.cy.codex.chatwidget.lunaReserveDisplayName
import com.cy.codex.protocol.protocol.v2.BackendBanner
import com.cy.codex.protocol.protocol.v2.CollaborationMode
import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import com.cy.codex.protocol.protocol.v2.ThreadSettingsUpdateParams

enum class AutomaticModelSwitchReason { UsageLimit, UsageRecovered }

/** A backend-authorized transition of the thread's model (codex-rs/tui/src/chatwidget/backend_banners.rs). */
data class AutomaticModelSwitch(
    val model: ModelPreset,
    val effort: ReasoningEffort?,
    val reason: AutomaticModelSwitchReason,
)

/** Inputs for the fallback decision; grouped so the decision stays pure. */
data class BannerFallbackContext(
    val threadId: String,
    val forkedFromId: String? = null,
    val accountId: String? = null,
    val currentModel: String,
    val currentEffort: ReasoningEffort? = null,
    val collaborationMode: CollaborationMode = CollaborationMode.Default,
    val ordinaryUsageRecovered: Boolean = false,
    val banner: BackendBanner? = null,
    val models: List<ModelPreset> = emptyList(),
)

data class BannerFallbackPlan(
    /** `thread/settings/update` carrying model/effort/mode only — never permission defaults. */
    val params: ThreadSettingsUpdateParams,
    val target: ModelPreset,
    val effort: ReasoningEffort,
    val enteringReserve: Boolean,
    /** Post-switch transcript notice, already resolved against its format template. */
    val notice: String,
)

sealed interface BannerFallbackOutcome {
    data class Apply(val plan: BannerFallbackPlan) : BannerFallbackOutcome

    /** The return target could not be prepared: keep the recovery panel up instead of switching. */
    data object UnavailableRecovery : BannerFallbackOutcome

    data object None : BannerFallbackOutcome
}

/** Picks the backend-authorized transition for this task (codex-rs/tui/src/chatwidget/backend_banners.rs `backend_banner_fallback`). */
internal fun selectBannerSwitch(
    store: ReserveReturnStore,
    context: BannerFallbackContext,
): AutomaticModelSwitch? {
    val current = context.currentModel
    if (current == LUNA_RESERVE_MODEL) {
        // Forks need their own return target: the parent's recovery deletes the parent's entry.
        val returnModel = loadReserveReturn(store, context.threadId, context.accountId)
            ?: inheritReserveReturn(store, context.threadId, context.forkedFromId, context.accountId)
        if (context.ordinaryUsageRecovered) {
            val previous = returnModel ?: return null
            val model = context.models.firstOrNull { !it.hidden && it.model == previous.model }
                ?: return null
            return AutomaticModelSwitch(model, previous.effort, AutomaticModelSwitchReason.UsageRecovered)
        }
    }
    val banner = context.banner ?: return null
    if (banner.bannerType == LUNA_RESERVE_BANNER) {
        // Reserve is deliberately hidden from manual selection; the banner authorizes entering it.
        val model = context.models.firstOrNull {
            it.model == LUNA_RESERVE_MODEL &&
                it.model != current &&
                (banner.blockedModelSlug == null || banner.blockedModelSlug == current)
        } ?: return null
        return AutomaticModelSwitch(model, context.currentEffort, AutomaticModelSwitchReason.UsageLimit)
    }
    if (banner.blockedModelSlug != current) return null
    val model = banner.fallbackModelSlugs.firstNotNullOfOrNull { candidate ->
        context.models.firstOrNull { !it.hidden && it.model == candidate && it.model != current }
    } ?: return null
    return AutomaticModelSwitch(model, context.currentEffort, AutomaticModelSwitchReason.UsageLimit)
}

/**
 * The decision half of `apply_backend_banner_fallback` (codex-rs/tui/src/app/backend_banner_fallback.rs):
 * entering Reserve persists the return target first, and a failure there keeps the recovery UI up.
 */
fun bannerFallback(
    store: ReserveReturnStore,
    context: BannerFallbackContext,
    prepareReturn: () -> Boolean,
    switchedToTemplate: String,
    switchedBackTemplate: String,
): BannerFallbackOutcome {
    val switch = selectBannerSwitch(store, context) ?: return BannerFallbackOutcome.None
    val enteringReserve = switch.model.model == LUNA_RESERVE_MODEL
    if (enteringReserve && !prepareReturn()) return BannerFallbackOutcome.UnavailableRecovery
    val effort = switch.effort
        ?.takeIf { candidate -> switch.model.supportedReasoningEfforts.any { it == candidate } }
        ?: switch.model.defaultReasoningEffort
    val params = ThreadSettingsUpdateParams(
        threadId = context.threadId,
        model = switch.model.model,
        effort = effort,
        // Automatic recovery must not apply the permission defaults of manual model selection.
        collaborationMode = context.collaborationMode,
    )
    val template = when (switch.reason) {
        AutomaticModelSwitchReason.UsageLimit -> switchedToTemplate
        AutomaticModelSwitchReason.UsageRecovered -> switchedBackTemplate
    }
    val notice = String.format(template, lunaReserveDisplayName(switch.model.model))
    return BannerFallbackOutcome.Apply(
        BannerFallbackPlan(
            params = params,
            target = switch.model,
            effort = effort,
            enteringReserve = enteringReserve,
            notice = notice,
        ),
    )
}
