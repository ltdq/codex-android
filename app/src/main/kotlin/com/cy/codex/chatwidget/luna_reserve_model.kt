package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.v2.ModelPreset
import com.cy.codex.protocol.protocol.v2.RateLimitSnapshot

/** Reserve routing slug (codex-rs/tui/src/model_catalog.rs). Deliberately hidden from manual picks. */
const val LUNA_RESERVE_MODEL = "gpt-reserve"

/** The lower-cost model whose metadata the Reserve-only picker borrows. */
const val LUNA_MODEL = "gpt-5.6-luna"

/** `model_display_name` (codex-rs/tui/src/model_catalog.rs): Reserve is shown by product name. */
fun lunaReserveDisplayName(model: String): String =
    if (model.equals(LUNA_RESERVE_MODEL, ignoreCase = true)) "Luna Reserve" else model

/** Gate is the live recovery verdict, not a saved return entry (codex-rs/tui/src/chatwidget/backend_banners.rs). */
fun restrictModelPickerToLunaReserve(currentModel: String, ordinaryUsageRecovered: Boolean): Boolean =
    currentModel == LUNA_RESERVE_MODEL && !ordinaryUsageRecovered

/**
 * Display metadata for the Reserve-only row (codex-rs/tui/src/chatwidget/luna_reserve_model.rs):
 * the normal model's fields with the Reserve routing slug.
 */
fun lunaReserveDisplayPreset(
    models: List<ModelPreset>,
    rateLimitsByLimitId: Map<String, RateLimitSnapshot>?,
): ModelPreset? {
    val normalSlug = rateLimitsByLimitId?.values
        ?.firstOrNull { it.limitName == LUNA_RESERVE_MODEL }
        ?.normalModelSlug
    val borrowed = normalSlug?.let { slug -> models.firstOrNull { it.model == slug } }
        ?: models.firstOrNull { it.model == LUNA_MODEL }
        ?: return null
    return borrowed.copy(model = LUNA_RESERVE_MODEL)
}

/** What the model group renders; [Restricted] rows are session-only, never set-as-default. */
sealed interface ModelPickerMode {
    data class Normal(val rows: List<ModelPreset>) : ModelPickerMode
    data class Restricted(val row: ModelPreset) : ModelPickerMode
    data object RestrictedUnavailable : ModelPickerMode
}

/**
 * The model group's shape for one read (codex-rs/tui/src/chatwidget/luna_reserve_model.rs);
 * Reserve selections stay session-scoped (codex-rs/tui/src/chatwidget/session_model_selection.rs).
 */
fun modelPickerMode(
    models: List<ModelPreset>,
    currentModel: String,
    ordinaryUsageRecovered: Boolean,
    rateLimitsByLimitId: Map<String, RateLimitSnapshot>?,
): ModelPickerMode {
    val visible = models.filterNot { it.hidden }
    if (!restrictModelPickerToLunaReserve(currentModel, ordinaryUsageRecovered)) {
        return ModelPickerMode.Normal(visible)
    }
    val row = lunaReserveDisplayPreset(visible, rateLimitsByLimitId)
    return if (row == null) ModelPickerMode.RestrictedUnavailable else ModelPickerMode.Restricted(row)
}

/** The rate-limit model nudge stands down when a banner owns the remedy or a Luna model is offered (codex-rs/tui/src/chatwidget/rate_limits.rs). */
fun rateLimitNudgeBlocked(bannerApplicable: Boolean, currentModel: String): Boolean =
    bannerApplicable ||
        currentModel == LUNA_MODEL ||
        currentModel == LUNA_RESERVE_MODEL
