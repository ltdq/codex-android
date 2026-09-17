package com.cy.codexui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cy.codexui.R
import com.cy.codexui.protocol.protocol.v2.Account
import com.cy.codexui.protocol.protocol.v2.AccountRateLimits
import com.cy.codexui.protocol.protocol.v2.AccountReadResponse
import com.cy.codexui.protocol.protocol.v2.AccountUsage
import com.cy.codexui.protocol.protocol.v2.CreditsSnapshot
import com.cy.codexui.protocol.protocol.v2.RateLimitWindow
import com.cy.codexui.AppEvent
import com.cy.codexui.CatalogState
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexTextField
import com.cy.codexui.ButtonRole
import com.cy.codexui.protocol.protocol.v2.LoginAccountParams
import com.cy.codexui.protocol.protocol.v2.LoginAccountResponse
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.status.formatTokens
import com.cy.codexui.UiType
import com.cy.codexui.codeSurface
import com.cy.codexui.pressableRow
import com.cy.codexui.usageColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Account page: login state, rate-limit windows and the daily token chart.
 *
 * Mirrors the account block of `codex-rs/tui/src/status/card.rs` plus `status/account.rs` and
 * `status/rate_limits.rs`: the TUI prints these as one `/status` card, the phone splits the same
 * fields into meters and a bar chart.
 */
@Composable
fun AccountScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = catalog.account
    val rateLimits = catalog.rateLimits
    val usage = catalog.usage

    Column(modifier = modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        SurfaceHeader(
            title = stringResource(R.string.account_screen_title),
            subtitle = if (account.signedIn) {
                account.email ?: account.planType ?: stringResource(R.string.account_screen_signed_in)
            } else {
                stringResource(R.string.account_screen_not_signed_in)
            },
            leading = { AccountBackButton(onBack) },
            trailing = {
                IconButton(
                    onClick = {
                        // Three reloads rather than one "refresh the page": the three answers come
                        // from three endpoints and any one of them can fail on its own, which is
                        // why the app re-reads them independently.
                        onEvent(AppEvent.ReloadAccount)
                        if (account.signedIn) {
                            onEvent(AppEvent.ReloadRateLimits)
                            onEvent(AppEvent.ReloadUsage)
                        }
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
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin).padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            AccountLoginSection(account)
            if (account.signedIn) {
                AccountLimitSection(rateLimits)
                if (catalog.usageLoaded) AccountUsageSection(usage)
                AccountLogoutSection(loggedIn = true, onLogout = { onEvent(AppEvent.Logout) })
            } else {
                AccountSignIn(catalog, onEvent)
            }
        }
    }
}

@Composable
private fun AccountSignIn(catalog: CatalogState, onEvent: (AppEvent) -> Unit) {
    var key by remember { mutableStateOf("") }
    var browserError by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val pending = catalog.pendingLogin
    val url = when (pending) {
        is LoginAccountResponse.Chatgpt -> pending.authUrl
        is LoginAccountResponse.ChatgptDeviceCode -> pending.verificationUrl
        else -> null
    }
    val loginId = when (pending) {
        is LoginAccountResponse.Chatgpt -> pending.loginId
        is LoginAccountResponse.ChatgptDeviceCode -> pending.loginId
        else -> null
    }
    fun openBrowser() {
        url?.let {
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
                    Text(text = pending.userCode, fontSize = UiType.SheetTitle, fontFamily = FontFamily.Monospace)
                }
            }
            Text(stringResource(R.string.runtime_login_waiting), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (url != null) {
                CodexButton(stringResource(R.string.runtime_open_browser), ::openBrowser, Modifier.fillMaxWidth())
            }
            if (loginId != null) {
                CodexButton(
                    stringResource(R.string.runtime_cancel_login),
                    { onEvent(AppEvent.CancelLogin(loginId)) },
                    Modifier.fillMaxWidth(),
                    role = ButtonRole.Secondary,
                )
            }
        } else {
            CodexButton(
                stringResource(R.string.runtime_login_chatgpt),
                { onEvent(AppEvent.Login(LoginAccountParams.ChatgptDeviceCode)) },
                Modifier.fillMaxWidth(),
                enabled = !catalog.loginLoading,
            )
            CodexTextField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.runtime_api_key),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !catalog.loginLoading,
            )
            CodexButton(
                stringResource(R.string.runtime_login_api_key),
                { onEvent(AppEvent.Login(LoginAccountParams.ApiKey(key.trim()))); key = "" },
                Modifier.fillMaxWidth(),
                role = ButtonRole.Secondary,
                enabled = key.isNotBlank() && !catalog.loginLoading,
            )
        }
        if (catalog.loginLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        (catalog.loginError ?: browserError)?.let {
            Text(it, color = MiuixTheme.colorScheme.error, fontSize = UiType.Meta)
        }
    }
}

/** The address of a ChatGPT account; the other variants have no identity to show. */
private val AccountReadResponse.email: String? get() = (account as? Account.Chatgpt)?.email

/** The first line of the account card: a ChatGPT address, or the variant's own name. */
@Composable
@ReadOnlyComposable
private fun accountIdentity(response: AccountReadResponse): String = when (val account = response.account) {
    is Account.Chatgpt -> account.email ?: stringResource(R.string.account_screen_email_unbound)
    Account.ApiKey -> stringResource(R.string.account_screen_api_key)
    is Account.AmazonBedrock -> stringResource(R.string.account_screen_bedrock)
    null -> stringResource(R.string.account_screen_email_unbound)
}

/** The plan slug of a ChatGPT account; API-key and Bedrock accounts do not carry one. */
private val AccountReadResponse.planType: String? get() = (account as? Account.Chatgpt)?.planType

private val AccountReadResponse.signedIn: Boolean get() = account != null

/** 登录状态: mirrors the status card's `Account:` row (`{email} ({plan})`). */
@Composable
private fun AccountLoginSection(account: AccountReadResponse) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.account_screen_sign_in_status),
        icon = MiuixIcons.Community,
        trailing = if (account.signedIn) {
            stringResource(R.string.account_screen_signed_in)
        } else {
            stringResource(R.string.account_screen_not_signed_in)
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(AccountRowShape).background(codeSurface())
                .padding(horizontal = UiConsts.Space9, vertical = UiConsts.Space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AccountText(
                    accountIdentity(account),
                    size = UiType.RowTitle,
                    weight = FontWeight.Medium,
                    maxLines = 1,
                )
                AccountText(
                    account.planType ?: stringResource(R.string.account_screen_plan_none),
                    size = UiType.Meta,
                    color = colors.onSurfaceVariantSummary,
                )
            }
            AccountChip(
                text = if (account.signedIn) {
                    stringResource(R.string.account_screen_online)
                } else {
                    stringResource(R.string.account_screen_offline)
                },
                tint = if (account.signedIn) colors.primary else colors.disabledOnSurface,
            )
        }
        if (account.signedIn) {
            Spacer(Modifier.height(UiConsts.Space6))
            AccountInfoLine(
                stringResource(R.string.account_screen_plan),
                account.planType ?: stringResource(R.string.account_screen_plan_chatgpt),
            )
        }
    }
}

/** 用量限额: the primary/secondary windows, drawn as meters instead of the TUI's 20-cell bar. */
@Composable
private fun AccountLimitSection(limits: AccountRateLimits) {
    val colors = MiuixTheme.colorScheme
    val snapshot = limits.rateLimits
    SectionCard(title = stringResource(R.string.account_screen_rate_limits), icon = MiuixIcons.Store) {
        val windows = listOfNotNull(
            snapshot.primary?.let { it to (snapshot.limitName ?: stringResource(R.string.account_screen_rate_limit_primary)) },
            snapshot.secondary?.let { it to stringResource(R.string.account_screen_rate_limit_secondary) },
        )
        if (windows.isEmpty()) AccountNote(stringResource(R.string.account_screen_rate_limits_empty))
        windows.forEachIndexed { index, (window, label) ->
            if (index > 0) Spacer(Modifier.height(UiConsts.Space11))
            AccountRateMeter(label, window)
        }
        snapshot.credits?.let { credits ->
            Spacer(Modifier.height(UiConsts.Space11))
            AccountInfoLine(
                stringResource(R.string.account_screen_credits),
                accountCreditsText(credits),
            )
        }
        Spacer(Modifier.height(UiConsts.Space2))
        AccountText(
            stringResource(R.string.account_screen_rate_limit_note),
            size = UiType.Footnote,
            color = colors.disabledOnSurface,
        )
    }
}

/** `unlimited` outranks the balance, and no credits at all is its own sentence. */
@Composable
@ReadOnlyComposable
private fun accountCreditsText(credits: CreditsSnapshot): String = when {
    credits.unlimited -> stringResource(R.string.account_screen_credits_unlimited)
    !credits.hasCredits -> stringResource(R.string.account_screen_credits_none)
    credits.balance != null -> stringResource(R.string.account_screen_credits_balance, credits.balance)
    else -> stringResource(R.string.account_screen_credits_none)
}

@Composable
private fun AccountRateMeter(label: String, window: RateLimitWindow) {
    val colors = MiuixTheme.colorScheme
    val fraction = (window.usedPercent / 100f).coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccountText(label, Modifier.weight(1f), size = UiType.Body, weight = FontWeight.Medium)
            AccountText(
                stringResource(R.string.account_screen_percent, (fraction * 100).roundToInt()),
                size = UiType.Body,
                weight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(UiConsts.Space6))
        LinearProgressIndicator(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = usageColor(fraction),
                backgroundColor = colors.onBackground.copy(alpha = 0.08f),
            ),
            height = UiConsts.ProgressHeight,
        )
        if (window.resetsAt != null) {
            Spacer(Modifier.height(UiConsts.Space5))
            AccountText(
                text = stringResource(R.string.account_screen_resets_at, accountFormatReset(window.resetsAt)),
                size = UiType.Footnote,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}

/** 用量趋势: one rounded bar per day, drawn by hand because it is a single series. */
@Composable
private fun AccountUsageSection(usage: AccountUsage) {
    val colors = MiuixTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.account_screen_usage),
        icon = MiuixIcons.Store,
        trailing = formatTokens(usage.totalTokens.toLong()),
    ) {
        if (usage.dailyBuckets.isEmpty()) AccountNote(stringResource(R.string.account_screen_usage_empty))
        if (usage.dailyBuckets.isNotEmpty()) {
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
                text = stringResource(
                    R.string.account_screen_usage_peak,
                    formatTokens(usage.dailyBuckets.maxOf { it.tokens }.toLong()),
                    usage.dailyBuckets.size,
                ),
                size = UiType.Footnote,
                color = colors.disabledOnSurface,
            )
        }
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
            val barHeight = (size.height * (tokens.toFloat() / peak.toFloat())).coerceIn(minHeight, size.height)
            drawRoundRect(
                color = colors.primary,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

/** 退出登录: disabled while the account is not logged in. */
@Composable
private fun AccountLogoutSection(loggedIn: Boolean, onLogout: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    SectionCard(title = stringResource(R.string.account_screen_credentials), icon = MiuixIcons.Community) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(
                    if (loggedIn) Modifier.pressableRow(AccountRowShape, Color.Transparent, onLogout)
                    else Modifier.clip(AccountRowShape),
                )
                .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountText(
                text = stringResource(R.string.account_screen_sign_out),
                modifier = Modifier.weight(1f),
                size = UiType.RowTitle,
                color = if (loggedIn) colors.error else colors.disabledOnSurface,
                weight = FontWeight.Medium,
            )
            AccountText(
                text = if (loggedIn) {
                    stringResource(R.string.account_screen_remove_credentials)
                } else {
                    stringResource(R.string.account_screen_not_signed_in)
                },
                size = UiType.Meta,
                color = colors.disabledOnSurface,
            )
        }
    }
}

// ---- row vocabulary, private to this screen --------------------------------------------------

private val AccountRowShape = RoundedCornerShape(UiConsts.RowCorner)

/** One place for this page's type ramp. */
@Composable
private fun AccountText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = UiType.Body,
    color: Color = MiuixTheme.colorScheme.onSurface,
    weight: FontWeight? = null,
    mono: Boolean = false,
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
        fontFamily = if (mono) FontFamily.Monospace else null,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AccountBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack, minWidth = UiConsts.IconButtonSize, minHeight = UiConsts.IconButtonSize) {
        Icon(
            MiuixIcons.ChevronBackward,
            stringResource(R.string.account_screen_back),
            Modifier.size(UiConsts.IconHeader),
            MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AccountInfoLine(label: String, value: String, labelWidth: Dp = 72.dp) {
    val colors = MiuixTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = UiConsts.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountText(label, Modifier.width(labelWidth), size = UiType.Body, color = colors.onSurfaceVariantSummary)
        AccountText(value, Modifier.weight(1f), mono = true, maxLines = 1)
    }
}

@Composable
private fun AccountChip(text: String, tint: Color) {
    val shape = RoundedCornerShape(UiConsts.BadgeCorner)
    Box(
        Modifier.clip(shape).background(tint.copy(alpha = UiConsts.BadgeTintAlpha))
            .padding(horizontal = UiConsts.Space6, vertical = UiConsts.Space2),
    ) {
        AccountText(text, size = UiType.Chip, color = tint, weight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun AccountNote(text: String) {
    AccountText(
        text,
        Modifier.padding(vertical = UiConsts.Space4),
        size = UiType.Meta,
        color = MiuixTheme.colorScheme.disabledOnSurface,
    )
}

@Composable
@ReadOnlyComposable
private fun accountFormatReset(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(
        DateTimeFormatter.ofPattern(stringResource(R.string.account_screen_reset_format), Locale.getDefault()),
    )

/** `9月15日` is too wide for a chart tick; the day number alone is enough. */
private fun accountShortDay(day: String): String {
    val tail = day.substringAfter('月', day)
    return tail.removeSuffix("日").ifEmpty { day }
}
