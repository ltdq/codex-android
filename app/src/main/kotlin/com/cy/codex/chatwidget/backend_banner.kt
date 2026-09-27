package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.glassTint
import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.BackendBanner
import com.cy.codex.protocol.protocol.v2.BannerPresentation
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** The banner type that offers Reserve (codex-rs/tui/src/backend_banners.rs). */
const val LUNA_RESERVE_BANNER = "luna_reserve"

/**
 * Account-banner lifecycle for one open task (codex-rs/tui/src/chatwidget/backend_banners.rs).
 * Dismissal survives only the same occurrence; [accountId] is stamped from each usage read.
 */
data class BackendBannerState(
    val accountId: String? = null,
    val ordinaryUsageRecovered: Boolean = false,
    val banner: BackendBanner? = null,
    val shown: Boolean = false,
    val dismissed: Boolean = false,
    val pickerDismissed: Boolean = false,
    /** Model the last applied switch landed on; lets the banner describe the replacement. */
    val noticeModel: String? = null,
)

/**
 * Mirrors `ordinary_usage_recovered` (codex-rs/tui/src/chatwidget/backend_banners.rs); percentages
 * and reset times must never stand in for the explicit verdict.
 */
fun ordinaryUsageRecovered(response: AccountRateLimits): Boolean {
    val credits = response.rateLimits.credits
    val hasUsableCredits = credits != null && (credits.unlimited || credits.hasCredits)
    return response.ordinaryUsageAllowed != null &&
        (response.ordinaryUsageAllowed == true || hasUsableCredits) &&
        // Raw presence, not the parsed banner: an unreadable upsell still blocks recovery
        // (upstream keys on `rate_limit_upsell.is_none()` before `BackendBanner::parse`).
        !response.rateLimitUpsellPresent &&
        response.rateLimits.spendControlReached != true &&
        response.rateLimits.rateLimitReachedType == null
}

/** The occurrence identity a new read must match to keep dismissal state (codex-rs/tui/src/chatwidget/backend_banners.rs). */
fun bannerOccurrenceKey(accountId: String?, banner: BackendBanner?): String? = banner?.let {
    listOf(
        accountId.orEmpty(),
        it.bannerType,
        it.resetAt?.toString().orEmpty(),
        it.presentation.wire,
        (it.blockedModelSlug ?: it.modelSlug).orEmpty(),
        it.fallbackModelSlugs.joinToString(separator = "\u001f"),
    ).joinToString(separator = "\u001f")
}

fun reserveNoticeAlreadyShown(
    banner: BackendBanner?,
    accountId: String?,
    noticeAccountId: String?,
): Boolean =
    banner?.bannerType == LUNA_RESERVE_BANNER &&
        noticeAccountId != null &&
        noticeAccountId == accountId

/** Reserve offered but not taken: submissions wait for the switch (codex-rs/tui/src/chatwidget/rate_limits.rs). */
fun waitingForLunaReserve(state: BackendBannerState, currentModel: String): Boolean =
    currentModel != LUNA_RESERVE_MODEL && state.banner?.bannerType == LUNA_RESERVE_BANNER

data class BackendBannerUpdate(
    val state: BackendBannerState,
    /** Account whose entry notice this read consumes; null clears the memory. */
    val noticeAccountId: String?,
    val holdRecovery: Boolean,
)

/**
 * Folds one full usage read into the banner lifecycle (codex-rs/tui/src/chatwidget/backend_banners.rs
 * `update_backend_banner`); the remembered notice suppresses only Reserve panels.
 */
fun updateBackendBanner(
    state: BackendBannerState,
    response: AccountRateLimits,
    noticeAccountId: String?,
    currentModel: String,
): BackendBannerUpdate {
    val accountId = response.accountId
    val recovered = ordinaryUsageRecovered(response)
    val notice = if (recovered) null else noticeAccountId
    val banner = response.rateLimitUpsell
    val sameOccurrence = banner != null &&
        bannerOccurrenceKey(state.accountId, state.banner) == bannerOccurrenceKey(accountId, banner)
    val fresh = BackendBannerState(
        accountId = accountId,
        ordinaryUsageRecovered = recovered,
        banner = banner,
        shown = if (sameOccurrence) state.shown else false,
        dismissed = if (sameOccurrence) {
            state.dismissed
        } else {
            reserveNoticeAlreadyShown(banner, accountId, notice)
        },
        pickerDismissed = if (sameOccurrence) state.pickerDismissed else false,
        noticeModel = if (sameOccurrence) state.noticeModel else null,
    )
    return BackendBannerUpdate(fresh, notice, waitingForLunaReserve(fresh, currentModel))
}

/**
 * The banner that survives the presentation filter (codex-rs/tui/src/chatwidget/backend_banners.rs
 * `refresh_backend_banner_visibility`); Reserve stays visible so recovery actions stay reachable.
 */
fun backendBannerVisible(state: BackendBannerState, currentModel: String): BackendBanner? {
    val banner = state.banner ?: return null
    if (state.dismissed) return null
    if (banner.bannerType == LUNA_RESERVE_BANNER) return banner
    val matchesSelectedModel = when {
        banner.blockedModelSlug != null && banner.fallbackModelSlugs.isNotEmpty() ->
            banner.blockedModelSlug != currentModel && currentModel in banner.fallbackModelSlugs

        else ->
            banner.modelSlug == null ||
                banner.modelSlug == currentModel ||
                state.noticeModel == currentModel
    }
    return banner.takeIf { matchesSelectedModel }
}

fun hasApplicableBackendBanner(state: BackendBannerState, currentModel: String): Boolean =
    backendBannerVisible(state, currentModel) != null

/**
 * A `dismissible` banner collapses on a new turn (codex-rs/tui/src/chatwidget/input_submission.rs);
 * visibility stands in for the TUI's render-side `shown` flag.
 */
fun dismissBackendBannerForNewTurn(state: BackendBannerState, currentModel: String): BackendBannerState {
    val banner = backendBannerVisible(state, currentModel) ?: return state
    return if (banner.presentation == BannerPresentation.Dismissible) {
        state.copy(dismissed = true)
    } else {
        state
    }
}

/**
 * A failed Reserve switch still shows recovery actions (codex-rs/tui/src/chatwidget/backend_banners.rs
 * `show_unavailable_reserve_recovery`); the switch never happened, so the return target goes first.
 */
fun showUnavailableReserveRecovery(
    state: BackendBannerState,
    store: ReserveReturnStore,
    threadId: String,
): BackendBannerState {
    clearReserveReturn(store, threadId)
    return state.copy(dismissed = false, pickerDismissed = false, noticeModel = null)
}

/** A committed switch keeps its post-switch notice distinct from the blocked state (codex-rs/tui/src/chatwidget/backend_banners.rs). */
fun finishBackendBannerFallback(
    state: BackendBannerState,
    targetModel: String,
    noticeAccountId: String?,
): BackendBannerState =
    state.copy(
        shown = false,
        dismissed = reserveNoticeAlreadyShown(state.banner, state.accountId, noticeAccountId),
        pickerDismissed = false,
        noticeModel = targetModel,
    )

/**
 * The account to remember after showing the Reserve panel on Reserve
 * (codex-rs/tui/src/chatwidget/backend_banners.rs), so the entry notice is not repeated.
 */
fun reserveNoticeAccountToRemember(state: BackendBannerState, currentModel: String): String? {
    val banner = backendBannerVisible(state, currentModel) ?: return null
    if (banner.bannerType != LUNA_RESERVE_BANNER || currentModel != LUNA_RESERVE_MODEL) return null
    return state.accountId
}

/** What a backend CTA resolves to (codex-rs/tui/src/backend_banners/actions.rs). */
sealed interface BannerAction {
    data class OpenUrl(val url: String) : BannerAction
    data class NotifyOwner(val creditType: AddCreditsNudgeCreditType) : BannerAction
    data object ResetUsage : BannerAction
}

private const val UsageUrl = "https://chatgpt.com/codex/settings/usage"
private const val WorkspaceUsageUrl = "https://chatgpt.com/admin/usage-limits/workspace"

/** Wire spellings of `PlanType::is_workspace_account` (codex-rs/protocol/src/account.rs). */
private val WorkspacePlanTypes = setOf(
    "team",
    "self_serve_business_prolite",
    "self_serve_business_usage_based",
    "business",
    "ent26",
    "enterprise_cbp_automation",
    "enterprise_cbp_usage_based",
    "enterprise",
    "edu",
    "edu_plus",
    "edu_pro",
)

/** `BackendBanner::resolve_action` (codex-rs/tui/src/backend_banners/actions.rs); unknown verbs and unusable destinations drop. */
fun resolveBannerAction(
    banner: BackendBanner,
    accountId: String?,
    planType: String?,
    action: String,
): BannerAction? {
    when (action) {
        "notify_owner", "contact_owner" ->
            return BannerAction.NotifyOwner(AddCreditsNudgeCreditType.Credits)

        "request_increase" -> {
            val destination = banner.requestUrl
            if (destination.isNullOrBlank()) {
                return BannerAction.NotifyOwner(AddCreditsNudgeCreditType.UsageLimit)
            }
            return validHttpUrl(destination)?.let { BannerAction.OpenUrl(it) }
        }

        // The existing reset-credit picker keeps its explicit confirmation before consuming one.
        "reset_usage" -> return BannerAction.ResetUsage
        else -> Unit
    }
    val workspace = planType in WorkspacePlanTypes
    val destination = when (action) {
        "add_credits", "buy_credits" ->
            if (workspace) {
                "https://chatgpt.com/admin/billing?codex_credit_action=add_credits"
            } else {
                "$UsageUrl?credits_modal=true"
            }

        "buy_reset" -> "https://chatgpt.com/codex/purchase/reset"
        "view_usage", "request_increase_usage_settings" -> UsageUrl
        "view_workspace_usage", "increase_spend_cap" -> WorkspaceUsageUrl
        "open_plus_pricing_web" -> "https://chatgpt.com/explore/plus"
        "open_pro_pricing_web" -> "https://chatgpt.com/explore/pro"
        "open_pricing_dialog" -> {
            // Plus and ProLite upsell to Pro; everything else is steered to Plus.
            val target = if (planType == "plus" || planType == "prolite") "pro" else "plus"
            val variant = if (planType == "prolite") "&pro_variant=2x" else ""
            "https://chatgpt.com/?cta_tab=personal&highlight_plan=$target$variant#pricing"
        }

        else -> return null
    }
    val url = if (URI(destination).path.orEmpty().startsWith("/admin/")) {
        // The admin route selects the requested workspace before opening the modal.
        val account = accountId?.takeIf { it.isNotEmpty() } ?: return null
        "$destination&account_id=$account"
    } else {
        destination
    }
    return BannerAction.OpenUrl(url)
}

/** http/https with a host and no userinfo (codex-rs/tui/src/backend_banners/actions.rs). */
private fun validHttpUrl(destination: String): String? {
    val parsed = runCatching { URI(destination) }.getOrNull() ?: return null
    if (!parsed.scheme.equals("https", ignoreCase = true) &&
        !parsed.scheme.equals("http", ignoreCase = true)
    ) {
        return null
    }
    if (parsed.host.isNullOrEmpty()) return null
    if (!parsed.userInfo.isNullOrEmpty()) return null
    return destination
}

data class BannerCtaAction(val label: String, val action: BannerAction)

/** Renderable CTA rows (codex-rs/tui/src/backend_banners/render.rs): unusable labels and unresolvable verbs drop. */
fun bannerCtas(banner: BackendBanner, accountId: String?, planType: String?): List<BannerCtaAction> =
    banner.ctas.mapNotNull { cta ->
        val label = cta.label
        val usable = label.isNotBlank() &&
            label.encodeToByteArray().size <= 256 &&
            label.none { it.isISOControl() }
        if (!usable) return@mapNotNull null
        resolveBannerAction(banner, accountId, planType, cta.action)
            ?.let { BannerCtaAction(label, it) }
    }

/** Backend-owned copy (codex-rs/tui/src/backend_banners/render.rs): control characters stripped, `{time}` becomes the reset timestamp. */
fun bannerCopy(text: String, resetTime: String?): String {
    val filtered = text.filter { it == '\n' || !it.isISOControl() }
    return if (resetTime == null) filtered else filtered.replace("{time}", resetTime)
}

/** `format_reset_timestamp` (codex-rs/tui/src/status/helpers.rs) over the banner's own `reset_at`, never a rate-limit window's. */
fun bannerResetTime(resetAtMillis: Long?, nowMillis: Long): String? {
    val reset = resetAtMillis ?: return null
    val dayPattern = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return if (dayPattern.format(Date(reset)) == dayPattern.format(Date(nowMillis))) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reset))
    } else {
        SimpleDateFormat("HH:mm 'on' d MMM", Locale.getDefault()).format(Date(reset))
    }
}

data class RecoveryOption(val label: String, val action: BannerAction?)

/** What one surface shows; Reserve is a focused choice panel, everything else an inline banner. */
sealed interface BannerSurface {
    val title: String
    val description: String
    val dismissible: Boolean

    data class Inline(
        override val title: String,
        override val description: String,
        override val dismissible: Boolean,
        val ctas: List<BannerCtaAction>,
    ) : BannerSurface

    data class Recovery(
        override val title: String,
        override val description: String,
        override val dismissible: Boolean,
        val options: List<RecoveryOption>,
        val continueWithReserve: Boolean,
    ) : BannerSurface
}

/** The content half of `refresh_backend_banner_visibility` (codex-rs/tui/src/chatwidget/backend_banners.rs). */
fun backendBannerSurface(
    state: BackendBannerState,
    currentModel: String,
    resetTime: String?,
    planType: String?,
    usageLimitTitle: String,
    usageLimitDescription: String,
    continueWithReserveLabel: String,
): BannerSurface? {
    val banner = backendBannerVisible(state, currentModel) ?: return null
    val dismissible = banner.presentation == BannerPresentation.Dismissible
    val title = bannerCopy(banner.title, resetTime)
    val description = bannerCopy(banner.description, resetTime)
    val ctas = bannerCtas(banner, state.accountId, planType)
    if (banner.bannerType != LUNA_RESERVE_BANNER) {
        return BannerSurface.Inline(title, description, dismissible, ctas)
    }
    val inReserve = currentModel == LUNA_RESERVE_MODEL
    return BannerSurface.Recovery(
        title = if (inReserve) title else usageLimitTitle,
        description = if (inReserve) description else usageLimitDescription,
        dismissible = dismissible,
        options = ctas.map { RecoveryOption(it.label, it.action) } +
            if (inReserve) listOf(RecoveryOption(continueWithReserveLabel, null)) else emptyList(),
        continueWithReserve = inReserve,
    )
}

/**
 * One banner on screen: Reserve is a focused choice sheet, every other banner an inline card.
 * Selecting any recovery row dismisses the panel (the TUI's `dismiss_on_select`).
 */
@Composable
fun BackendBannerSurface(
    surface: BannerSurface?,
    onAction: (BannerAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (surface) {
        null -> Unit
        is BannerSurface.Recovery -> ReserveRecoverySheet(surface, onAction, onDismiss)
        is BannerSurface.Inline -> InlineBackendBanner(surface, onAction, onDismiss, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineBackendBanner(
    surface: BannerSurface.Inline,
    onAction: (BannerAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.PanelCorner) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(glassTint(0.94f), shape)
            .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = surface.title,
                fontSize = UiType.Body,
                lineHeight = UiType.BodyLine,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
            )
            Text(
                text = surface.description,
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            if (surface.ctas.isNotEmpty()) {
                // Wrap: a plain Row would crush every CTA after the first to near-zero width.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                    verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                ) {
                    surface.ctas.forEach { cta ->
                        Button(
                            onClick = { onAction(cta.action) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            cornerRadius = UiConsts.ButtonHeightCompact / 2,
                            minHeight = UiConsts.ButtonHeightCompact,
                            insideMargin = PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                vertical = 0.dp,
                            ),
                        ) {
                            Text(
                                text = cta.label,
                                fontSize = UiType.Action,
                                lineHeight = UiType.ActionLine,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (surface.dismissible) {
            Spacer(Modifier.width(UiConsts.Space10))
            IconButton(
                onClick = onDismiss,
                minWidth = UiConsts.IconButtonCompact,
                minHeight = UiConsts.IconButtonCompact,
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Close,
                    contentDescription = stringResource(R.string.luna_banner_dismiss),
                    modifier = Modifier.size(UiConsts.IconInline),
                    tint = colors.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ReserveRecoverySheet(
    surface: BannerSurface.Recovery,
    onAction: (BannerAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = surface.title,
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    max = LocalWindowInfo.current.containerDpSize.height * UiConsts.SheetHeightFraction,
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = UiConsts.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
        ) {
            Text(
                text = surface.description,
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.SheetBody,
                lineHeight = UiType.SheetBodyLine,
                color = colors.onSurface,
            )
            surface.options.forEach { option ->
                Button(
                    onClick = {
                        option.action?.let(onAction)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space12),
                    colors = if (option.action == null) {
                        ButtonDefaults.buttonColorsPrimary()
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    cornerRadius = UiConsts.ButtonHeight / 2,
                    minHeight = UiConsts.ButtonHeight,
                    insideMargin = PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontal,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = option.label,
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
