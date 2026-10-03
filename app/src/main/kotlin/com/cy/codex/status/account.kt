package com.cy.codex.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValue
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.canReadRateLimits
import com.cy.codex.hasCodexBackendAuth
import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AccountRateLimits
import com.cy.codex.protocol.protocol.v2.AccountReadResponse
import com.cy.codex.protocol.protocol.v2.AccountUsage
import com.cy.codex.protocol.protocol.v2.CreditsSnapshot
import com.cy.codex.protocol.protocol.v2.ForcedLoginMethod
import com.cy.codex.protocol.protocol.v2.LoginAccountParams
import com.cy.codex.protocol.protocol.v2.LoginAccountResponse
import com.cy.codex.protocol.protocol.v2.RateLimitResetCredit
import com.cy.codex.protocol.protocol.v2.RateLimitWindow
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import com.cy.codex.usageColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** Account page: login state, rate-limit windows, daily token chart; mirrors
 * `codex-rs/tui/src/status/card.rs`, `status/account.rs`, `status/rate_limits.rs`. */
@Composable
fun AccountScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    onOpenBedrock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = catalog.account
    val rateLimits = catalog.rateLimits
    val usage = catalog.usage

    CodexPage(
        title = stringResource(R.string.account_screen_title),
        description =
            if (account.signedIn) {
                account.email
                    ?: account.planType
                    ?: stringResource(R.string.account_screen_signed_in)
            } else if (!account.requiresOpenaiAuth) {
                stringResource(R.string.account_screen_no_sign_in_required)
            } else {
                stringResource(R.string.account_screen_not_signed_in)
            },
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = {
                    onEvent(AppEvent.ReloadAccount)
                },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    MiuixIcons.Refresh,
                    stringResource(R.string.account_screen_refresh),
                    Modifier.size(UiConsts.IconRefresh),
                    MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) {
        if (account.signedIn || account.requiresOpenaiAuth) AccountLoginSection(account)
        if (account.signedIn) {
            if (account.canReadRateLimits) {
                AccountLimitSection(rateLimits)
                AccountResetCreditsSection(rateLimits, onEvent)
            }
            if (account.hasCodexBackendAuth && catalog.usageLoaded) AccountUsageSection(usage)
            AccountLogoutSection(loggedIn = true, onLogout = { onEvent(AppEvent.Logout) })
        } else if (account.requiresOpenaiAuth) {
            AccountSignIn(catalog, onEvent, onOpenBedrock)
        }
    }
}

@Composable
private fun AccountSignIn(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onOpenBedrock: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var browserError by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val pending = catalog.pendingLogin
    val url =
        when (pending) {
            is LoginAccountResponse.Chatgpt -> pending.authUrl
            is LoginAccountResponse.ChatgptDeviceCode -> pending.verificationUrl
            else -> null
        }
    val loginId =
        when (pending) {
            is LoginAccountResponse.Chatgpt -> pending.loginId
            is LoginAccountResponse.ChatgptDeviceCode -> pending.loginId
            else -> null
        }
    fun openBrowser() {
        url?.let { it ->
            runCatching { uriHandler.openUri(it) }
                .onSuccess { catalog.openedLoginId = loginId }
                .onFailure { browserError = it.message }
        }
    }
    LaunchedEffect(url) {
        browserError = null
        if (url != null && loginId != catalog.openedLoginId) openBrowser()
    }
    Column(verticalArrangement = Arrangement.spacedBy(UiConsts.Space12)) {
        if (pending != null) {
            if (pending is LoginAccountResponse.ChatgptDeviceCode) {
                SelectionContainer {
                    Text(
                        text = pending.userCode,
                        fontSize = UiType.SheetTitle,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                stringResource(R.string.runtime_login_waiting),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (url != null) {
                Button(
                    onClick = ::openBrowser,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = stringResource(R.string.runtime_open_browser), maxLines = 1)
                }
            }
            if (loginId != null) {
                Button(
                    onClick = { onEvent(AppEvent.CancelLogin(loginId)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.runtime_cancel_login), maxLines = 1)
                }
            }
        } else {
            // The in-process app-server serves the callback on localhost, reachable by the device's
            // browser, so no custom scheme or onNewIntent handoff is needed.
            if (catalog.isLoginMethodAllowed(ForcedLoginMethod.Chatgpt)) {
                Button(
                    onClick = {
                        onEvent(
                            AppEvent.Login(
                                LoginAccountParams.Chatgpt(useHostedLoginSuccessPage = false)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !catalog.loginLoading,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = stringResource(R.string.runtime_login_chatgpt), maxLines = 1)
                }
                Button(
                    onClick = { onEvent(AppEvent.Login(LoginAccountParams.ChatgptDeviceCode)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !catalog.loginLoading,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.runtime_login_device_code), maxLines = 1)
                }
            }
            if (catalog.isLoginMethodAllowed(ForcedLoginMethod.Api)) {
                TextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.runtime_api_key),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    enabled = !catalog.loginLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(
                    onClick = {
                        onEvent(AppEvent.Login(LoginAccountParams.ApiKey(key.trim())))
                        key = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = key.isNotBlank() && !catalog.loginLoading,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.runtime_login_api_key), maxLines = 1)
                }
            }
            if (catalog.shouldShowBedrockSetupWizard) {
                Button(
                    onClick = onOpenBedrock,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !catalog.loginLoading,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.sidebar_library_bedrock), maxLines = 1)
                }
            }
        }
        if (catalog.loginLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        (catalog.loginError ?: browserError)?.let {
            Text(it, color = MiuixTheme.colorScheme.error, fontSize = UiType.Meta)
        }
    }
}

private val AccountReadResponse.email: String?
    get() = (account as? Account.Chatgpt)?.email

@Composable
@ReadOnlyComposable
private fun accountIdentity(response: AccountReadResponse): String =
    when (val account = response.account) {
        is Account.Chatgpt -> account.email ?: stringResource(R.string.account_screen_email_unbound)
        Account.ApiKey -> stringResource(R.string.account_screen_api_key)
        is Account.AmazonBedrock -> stringResource(R.string.account_screen_bedrock)
        null -> stringResource(R.string.account_screen_email_unbound)
    }

private val AccountReadResponse.planType: String?
    get() = (account as? Account.Chatgpt)?.planType

private val AccountReadResponse.signedIn: Boolean
    get() = account != null

@Composable
private fun AccountLoginSection(account: AccountReadResponse) {
    val colors = MiuixTheme.colorScheme
    CodexSection(stringResource(R.string.account_screen_sign_in_status)) {
        CodexRow(
            title = accountIdentity(account),
            summary = account.planType ?: stringResource(R.string.account_screen_plan_none),
            endAction = {
                AccountChip(
                    text =
                        if (account.signedIn) {
                            stringResource(R.string.account_screen_online)
                        } else {
                            stringResource(R.string.account_screen_offline)
                        },
                    tint = if (account.signedIn) colors.primary else colors.disabledOnSurface,
                )
            },
        )
        if (account.signedIn) {
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.account_screen_plan),
                value = account.planType ?: stringResource(R.string.account_screen_plan_chatgpt),
            )
        }
    }
}

/** The two rate-limit windows as meters, with the credit balance and the queueing note under them. */
@Composable
private fun AccountLimitSection(limits: AccountRateLimits) {
    val snapshot = limits.rateLimits
    val windows =
        listOfNotNull(
            snapshot.primary?.let {
                it to
                    (snapshot.limitName
                        ?: stringResource(R.string.account_screen_rate_limit_primary))
            },
            snapshot.secondary?.let {
                it to stringResource(R.string.account_screen_rate_limit_secondary)
            },
        )
    Column(modifier = Modifier.fillMaxWidth()) {
        CodexGroupTitle(stringResource(R.string.account_screen_rate_limits))
        if (windows.isEmpty()) {
            Text(
                text = stringResource(R.string.account_screen_rate_limits_empty),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.disabledOnSurface,
            )
        } else {
            CodexCardGrid(count = windows.size) { index ->
                val (window, label) = windows[index]
                AccountRateCard(label, window)
            }
        }
    }

    CodexSection {
        snapshot.credits?.let { credits ->
            CodexValueRow(
                title = stringResource(R.string.account_screen_credits),
                value = accountCreditsText(credits),
            )
        }
        AccountNote(stringResource(R.string.account_screen_rate_limit_note))
    }
}

/** `unlimited` outranks the balance, and no credits at all is its own sentence. */
@Composable
@ReadOnlyComposable
private fun accountCreditsText(credits: CreditsSnapshot): String =
    when {
        credits.unlimited -> stringResource(R.string.account_screen_credits_unlimited)
        !credits.hasCredits -> stringResource(R.string.account_screen_credits_none)
        credits.balance != null ->
            stringResource(R.string.account_screen_credits_balance, credits.balance)
        else -> stringResource(R.string.account_screen_credits_none)
    }

/**
 * Mirrors the `/usage` reset picker (`chatwidget/usage.rs`): sorted by expiry, consumed
 * credits not actionable, redemption confirmed because a consumed credit cannot be returned.
 */
@Composable
private fun AccountResetCreditsSection(limits: AccountRateLimits, onEvent: (AppEvent) -> Unit) {
    val summary = limits.rateLimitResetCredits ?: return
    val credits = (summary.credits.orEmpty()).sortedBy { it.expiresAt ?: Long.MAX_VALUE }
    if (summary.availableCount <= 0 && credits.isEmpty()) return
    var pending by remember { mutableStateOf<RateLimitResetCredit?>(null) }

    CodexSection {
        CodexValueRow(
            title = stringResource(R.string.account_screen_reset_credits),
            value = summary.availableCount.toString(),
        )
        AccountNote(stringResource(R.string.account_screen_reset_credits_note))
    }
    CodexCardGrid(count = credits.size) { index ->
        ResetCreditCard(credit = credits[index], onUse = { pending = credits[index] })
    }

    pending?.let { credit ->
        ResetCreditSheet(
            credit = credit,
            onDismiss = { pending = null },
            onConfirm = {
                onEvent(AppEvent.ConsumeResetCredit(credit.id))
                pending = null
            },
        )
    }
}

/** One reset credit as a catalogue card: what it is, when it expires, and how it is spent. */
@Composable
private fun ResetCreditCard(credit: RateLimitResetCredit, onUse: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val available = credit.status.equals("available", ignoreCase = true)
    CodexCatalogCard(
        title = credit.title ?: stringResource(R.string.account_screen_reset_credit_untitled),
        description = credit.description?.takeIf { it.isNotBlank() },
        icon = MiuixIcons.Refresh,
        enabled = available,
        trailing = {
            if (available) {
                Button(
                    onClick = onUse,
                    enabled = true,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    cornerRadius = UiConsts.ButtonHeightCompact / 2,
                    minWidth = 0.dp,
                    minHeight = UiConsts.ButtonHeightCompact,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.account_screen_reset_credit_use),
                        fontSize = UiType.Action,
                        lineHeight = UiType.ActionLine,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                AccountChip(resetCreditStatusLabel(credit.status), colors.disabledOnSurface)
            }
        },
        footer = {
            Text(
                text = resetCreditExpiry(credit),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.disabledOnSurface,
            )
        },
    )
}

/** Expiry in local time, or the explicit "does not expire" the TUI prints. */
@Composable
@ReadOnlyComposable
private fun resetCreditExpiry(credit: RateLimitResetCredit): String =
    credit.expiresAt?.let {
        stringResource(R.string.account_screen_reset_credit_expires, accountFormatReset(it * 1000))
    } ?: stringResource(R.string.account_screen_reset_credit_no_expiry)

@Composable
@ReadOnlyComposable
private fun resetCreditStatusLabel(status: String): String =
    when {
        status.equals("redeeming", ignoreCase = true) ->
            stringResource(R.string.account_screen_reset_credit_redeeming)
        status.equals("redeemed", ignoreCase = true) ->
            stringResource(R.string.account_screen_reset_credit_redeemed)
        else -> stringResource(R.string.account_screen_reset_credit_unknown)
    }

/** The irreversibility is the whole reason this confirmation exists. */
@Composable
private fun ResetCreditSheet(
    credit: RateLimitResetCredit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismiss,
        title = stringResource(R.string.account_screen_reset_credit_confirm_title),
        backgroundColor = sheetColor(),
        cornerRadius = UiConsts.SheetCorner,
        sheetMaxWidth = UiConsts.SheetMaxWidth,
        outsideMargin = DpSize(sheetSideMargin(), 0.dp),
        insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(
                        max =
                            LocalWindowInfo.current.containerDpSize.height *
                                UiConsts.SheetHeightFraction
                    )
        ) {
            Text(
                text =
                    credit.title ?: stringResource(R.string.account_screen_reset_credit_untitled),
                fontSize = UiType.RowDetail,
                lineHeight = UiType.RowDetailLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = UiConsts.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                val colors = MiuixTheme.colorScheme
                Text(
                    text = stringResource(R.string.account_screen_reset_credit_confirm_body),
                    modifier = Modifier.padding(horizontal = UiConsts.Space4),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space4),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            text =
                                stringResource(R.string.account_screen_reset_credit_confirm_cancel),
                            maxLines = 1,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text =
                                stringResource(R.string.account_screen_reset_credit_confirm_action),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** One rate-limit window as a catalogue card: its used share, its meter, and when it resets. */
@Composable
private fun AccountRateCard(label: String, window: RateLimitWindow) {
    val colors = MiuixTheme.colorScheme
    val fraction = (window.usedPercent / 100f).coerceIn(0f, 1f)
    CodexCatalogCard(
        title = label,
        description = null,
        icon = MiuixIcons.Store,
        trailing = {
            CodexValue(
                stringResource(R.string.account_screen_percent, (fraction * 100).roundToInt()),
                monospace = false,
            )
        },
        footer = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = usageColor(fraction),
                            backgroundColor = colors.onBackground.copy(alpha = 0.08f),
                        ),
                    height = UiConsts.ProgressHeight,
                )
                if (window.resetsAt != null) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    AccountText(
                        text =
                            stringResource(
                                R.string.account_screen_resets_at,
                                accountFormatReset(window.resetsAt),
                            ),
                        size = UiType.Footnote,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        },
    )
}

// One rounded bar per day, drawn by hand because it is a single series.
@Composable
private fun AccountUsageSection(usage: AccountUsage) {
    val colors = MiuixTheme.colorScheme
    CodexSection {
        CodexValueRow(
            title = stringResource(R.string.account_screen_usage),
            value = formatTokens(usage.totalTokens),
        )
        if (usage.dailyBuckets.isEmpty())
            AccountNote(stringResource(R.string.account_screen_usage_empty))
        if (usage.dailyBuckets.isNotEmpty()) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.RowInset)
                        .padding(top = UiConsts.Space10, bottom = UiConsts.Space8),
            ) {
                AccountUsageChart(
                    buckets = usage.dailyBuckets.map { it.tokens },
                    modifier = Modifier.fillMaxWidth().height(UiConsts.UsageChartHeight),
                )
                Spacer(Modifier.height(UiConsts.Space6))
                Row(modifier = Modifier.fillMaxWidth()) {
                    usage.dailyBuckets.forEach { bucket ->
                        AccountText(
                            text = accountShortDay(bucket.day),
                            modifier = Modifier.weight(1f),
                            size = UiType.Tick,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 1,
                            align = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(UiConsts.Space6))
                AccountText(
                    text =
                        stringResource(
                            R.string.account_screen_usage_peak,
                            // Server's own peak; the charted buckets are the fallback for a series-only response.
                            formatTokens(
                                usage.peakDailyTokens.takeIf { it > 0 }
                                    ?: usage.dailyBuckets.maxOf { it.tokens }.toLong()
                            ),
                            usage.dailyBuckets.size,
                        ),
                    size = UiType.Footnote,
                    color = colors.disabledOnSurface,
                )
                if (usage.currentStreakDays > 0 || usage.longestStreakDays > 0) {
                    AccountText(
                        text =
                            stringResource(
                                R.string.account_screen_usage_streak,
                                usage.currentStreakDays,
                                usage.longestStreakDays,
                            ),
                        size = UiType.Footnote,
                        color = colors.disabledOnSurface,
                    )
                }
                if (usage.longestRunningTurnSec > 0) {
                    AccountText(
                        text =
                            stringResource(
                                R.string.account_screen_usage_longest_turn,
                                accountTurnDuration(usage.longestRunningTurnSec),
                            ),
                        size = UiType.Footnote,
                        color = colors.disabledOnSurface,
                    )
                }
            }
        }
    }
}

private fun accountTurnDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

@Composable
private fun AccountUsageChart(buckets: List<Int>, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val peak = (buckets.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier = modifier) {
        if (buckets.isEmpty()) return@Canvas
        val slot = size.width / buckets.size
        val barWidth = (slot * 0.5f).coerceIn(UiConsts.Space2.toPx(), UiConsts.Space24.toPx())
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        val minHeight = UiConsts.Space3.toPx()
        buckets.forEachIndexed { index, tokens ->
            val barHeight =
                (size.height * (tokens.toFloat() / peak.toFloat())).coerceIn(minHeight, size.height)
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
private fun AccountLogoutSection(loggedIn: Boolean, onLogout: () -> Unit) {
    CodexSection(stringResource(R.string.account_screen_credentials)) {
        CodexRow(
            title = stringResource(R.string.account_screen_sign_out),
            summary =
                if (loggedIn) {
                    stringResource(R.string.account_screen_remove_credentials)
                } else {
                    stringResource(R.string.account_screen_not_signed_in)
                },
            enabled = loggedIn,
            onClick = onLogout,
        )
    }
}

@Composable
private fun AccountText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = UiType.Body,
    color: Color = MiuixTheme.colorScheme.onSurface,
    weight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        lineHeight = size * UiType.LineRatio,
        fontWeight = weight,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AccountChip(text: String, tint: Color) {
    val shape = RoundedCornerShape(UiConsts.BadgeCorner)
    Box(
        Modifier.clip(shape)
            .background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
            .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2)
    ) {
        AccountText(
            text,
            size = UiType.Chip,
            color = tint,
            weight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Footnote on a card's inner rail; always the page's own copy, never a value off the wire. */
@Composable
private fun AccountNote(text: String) {
    AccountText(
        text,
        Modifier.padding(
            start = UiConsts.RowInset,
            end = UiConsts.RowInset,
            top = UiConsts.Space8,
            bottom = UiConsts.Space8,
        ),
        size = UiType.Meta,
        color = MiuixTheme.colorScheme.disabledOnSurface,
    )
}

@Composable
@ReadOnlyComposable
private fun accountFormatReset(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(
            DateTimeFormatter.ofPattern(
                stringResource(R.string.account_screen_reset_format),
                Locale.getDefault(),
            )
        )

/** A localized month-day label is too wide for a chart tick; the day number alone is enough. */
private fun accountShortDay(day: String): String {
    val tail = day.substringAfter('月', day)
    return tail.removeSuffix("日").ifEmpty { day }
}
