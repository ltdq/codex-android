package com.cy.codex.app

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.CodexApp
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.copyToClipboard
import com.cy.codex.canReadThreadUsage
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.Account
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeCreditType
import com.cy.codex.protocol.protocol.v2.AddCreditsNudgeEmailStatus
import com.cy.codex.protocol.protocol.v2.ThreadUsage
import com.cy.codex.status.accessSummary
import com.cy.codex.status.agentsSummary
import com.cy.codex.status.formatCreditMicros
import com.cy.codex.status.formatEstimatedUsdMicros
import com.cy.codex.status.formatTokens
import com.cy.codex.warningColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val ChatGptUsageUrl = "https://chatgpt.com/codex/settings/usage"

/**
 * `/status` page over the open thread: facts the status card lacks (thread id, cli version,
 * cwd, per-thread usage from `account/usage/read`); the terminal surface is
 * `codex-rs/.../status/card.rs`.
 */
@Composable
fun SessionStatusScreen(
    app: CodexApp,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val session = app.widget.state
    val config = session.config
    val usage = session.usage
    val knownThreads = app.catalog.agentThreads + app.threads.threads
    val thread = knownThreads.firstOrNull { it.id == session.threadId }
    val serverVersion =
        knownThreads.maxByOrNull { it.updatedAt }?.cliVersion?.takeIf { it.isNotBlank() }
    val accountState = app.catalog.account
    var estimate by remember(session.threadId, app.accountGeneration) { mutableStateOf<ThreadUsage?>(null) }
    var estimateFailed by remember(session.threadId, app.accountGeneration) { mutableStateOf(false) }
    var nudging by remember { mutableStateOf(false) }
    var nudgeMessage by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.threadId, app.accountGeneration) {
        if (session.threadId.isBlank() || !accountState.canReadThreadUsage) return@LaunchedEffect
        app.client
            .readThreadUsage(session.threadId)
            .onSuccess { estimate = it }
            .onFailure { estimateFailed = true }
    }

    val contextWindow = usage.modelContextWindow?.takeIf { it > 0 }
    val spend = app.catalog.rateLimits.rateLimits
    val account = accountState.account
    val accountValue = statusAccountDisplay(account, stringResource(R.string.session_status_api_key))
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val report = sessionStatusReport(app)
    val copyLabel = stringResource(R.string.clipboard_copy_status)

    CodexPage(
        title = stringResource(R.string.session_status_title),
        description = config.displayName,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (report != null) {
                IconButton(
                    onClick = { copyToClipboard(context, report, copyLabel) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Copy,
                        contentDescription = copyLabel,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = colors.primary,
                    )
                }
            }
        },
    ) {
        CodexSection(stringResource(R.string.session_status_title)) {
            CodexValueRow(
                title = stringResource(R.string.session_status_thread_id),
                value = session.threadId.ifEmpty { "—" },
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.session_status_status),
                value = session.status.label().ifEmpty { "—" },
                monospace = false,
            )
            thread
                ?.cliVersion
                ?.takeIf { it.isNotBlank() }
                ?.let { version ->
                    // A thread remembers the version that created it; flag when the server has moved on.
                    val stale = serverVersion != null && serverVersion != version
                    CodexRowDivider()
                    CodexRow(
                        title = stringResource(R.string.session_status_created_by),
                        endAction = {
                            Text(
                                text =
                                    if (stale) {
                                            stringResource(
                                                R.string.session_status_version_mismatch,
                                                version,
                                                serverVersion,
                                            )
                                        } else {
                                            version
                                        }
                                        .ifEmpty { "—" },
                                fontSize = UiType.Value,
                                lineHeight = UiType.ValueLine,
                                fontFamily = FontFamily.Monospace,
                                color =
                                    if (stale) warningColor()
                                    else null ?: MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            config.forkedFromId?.let { origin ->
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.status_card_forked_from_label),
                    value = origin.ifEmpty { "—" },
                )
            }
        }

        CodexSection(stringResource(R.string.session_status_directory)) {
            CodexValueRow(
                title = stringResource(R.string.session_status_directory),
                value = config.cwd.ifEmpty { "—" },
            )
            config.gitBranch
                ?.takeIf { it.isNotBlank() }
                ?.let { branch ->
                    CodexRowDivider()
                    CodexValueRow(
                        title = stringResource(R.string.session_status_branch),
                        value = branch.ifEmpty { "—" },
                    )
                }
            if (config.workspaceRoots.isNotEmpty()) {
                CodexRowDivider()
                CodexRow(
                    title = stringResource(R.string.session_status_workspace_roots),
                    endAction = {
                        // One root per line: the value is a list, so it keeps its own line breaks.
                        Text(
                            text = config.workspaceRoots.joinToString("\n").ifEmpty { "—" },
                            fontSize = UiType.Value,
                            lineHeight = UiType.ValueLine,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_agents_md_label),
                value = agentsSummary(config).ifEmpty { "—" },
                monospace = false,
            )
        }

        CodexSection(stringResource(R.string.status_card_model_title)) {
            CodexValueRow(
                title = stringResource(R.string.status_card_model_label),
                value = config.modelDisplayName.ifEmpty { config.model }.ifEmpty { "—" },
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_model_provider_label),
                value = config.modelProviderId.ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_reasoning_label),
                value = config.reasoningEffort?.label().orEmpty().ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_service_tier_label),
                value =
                    config.serviceTier
                        ?: stringResource(R.string.status_card_service_tier_default)
                            .ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_collaboration_label),
                value = config.collaborationMode.label().ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_approval_label),
                value = config.approvalPolicy.label().ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_reviewer_label),
                value = config.approvalsReviewer.label().ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.status_card_access_label),
                value = accessSummary(config).ifEmpty { "—" },
                monospace = false,
            )
        }

        CodexSection(stringResource(R.string.session_status_usage_title)) {
            if (account !is Account.Chatgpt) {
                CodexValueRow(
                    title = stringResource(R.string.session_status_tokens_total),
                    value = formatTokens(usage.total.totalTokens).ifEmpty { "—" },
                    monospace = false,
                )
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.session_status_tokens_input),
                    value = formatTokens(usage.total.inputTokens).ifEmpty { "—" },
                    monospace = false,
                )
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.session_status_tokens_output),
                    value = formatTokens(usage.total.outputTokens).ifEmpty { "—" },
                    monospace = false,
                )
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.session_status_tokens_cached),
                    value = formatTokens(usage.total.cachedInputTokens).ifEmpty { "—" },
                    monospace = false,
                )
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.session_status_tokens_reasoning),
                    value = formatTokens(usage.total.reasoningOutputTokens).ifEmpty { "—" },
                    monospace = false,
                )
                CodexRowDivider()
            }
            CodexValueRow(
                title = stringResource(R.string.session_status_context),
                value =
                    stringResource(
                        R.string.status_card_usage_percent,
                        (usage.usedFraction * 100).toInt(),
                    ) +
                        " / " +
                        (contextWindow?.let { formatTokens(it) }
                                ?: stringResource(R.string.status_card_none))
                            .ifEmpty { "—" },
                monospace = false,
            )
            val estimateValue = estimate?.let { line ->
                val credits = formatCreditMicros(line.estimatedUsageCreditsMicros)
                formatEstimatedUsdMicros(line.estimatedUsageUsdMicros)?.let { usd ->
                    "$credits credits · $usd"
                } ?: "$credits credits"
            }
            if (estimateValue != null) {
                CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.session_status_estimated_usage),
                    value = estimateValue.ifEmpty { "—" },
                    monospace = false,
                )
            } else if (estimateFailed) {
                StatusNote(stringResource(R.string.session_status_load_failed))
            }
            spend.individualLimit?.let { limit ->
                CodexRowDivider()
                CodexRow(
                    title = stringResource(R.string.session_status_spend_control),
                    endAction = {
                        Text(
                            text =
                                stringResource(
                                        R.string.session_status_spend_control_value,
                                        limit.used,
                                        limit.limit,
                                        limit.remainingPercent,
                                    )
                                    .ifEmpty { "—" },
                            fontSize = UiType.Value,
                            lineHeight = UiType.ValueLine,
                            color =
                                if (spend.spendControlReached == true) colors.error
                                else null ?: MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            if (spend.spendControlReached == true && spend.individualLimit == null) {
                CodexRowDivider()
                CodexRow(
                    title = stringResource(R.string.session_status_spend_control),
                    endAction = {
                        Text(
                            text =
                                stringResource(R.string.session_status_spend_control_reached)
                                    .ifEmpty { "—" },
                            fontSize = UiType.Value,
                            lineHeight = UiType.ValueLine,
                            color = colors.error ?: MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }

        CodexSection(stringResource(R.string.session_status_account)) {
            accountValue?.let { value ->
                CodexRow(
                    title = stringResource(R.string.session_status_account),
                    summary = value,
                )
            }
            if (accountState.requiresOpenaiAuth) {
                Button(
                    onClick = { runCatching { uriHandler.openUri(ChatGptUsageUrl) } },
                    modifier = Modifier.padding(horizontal = UiConsts.RowInset),
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Link,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                    )
                    Spacer(Modifier.size(UiConsts.Space6))
                    Text(text = stringResource(R.string.session_status_chatgpt_usage))
                }
            }
            spend.credits?.let { credits ->
                if (accountValue != null) CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.status_card_credits_label),
                    value =
                        if (credits.unlimited) {
                                stringResource(R.string.status_card_credits_unlimited)
                            } else {
                                credits.balance
                                    ?: stringResource(R.string.status_card_credits_none)
                            }
                            .ifEmpty { "—" },
                    monospace = false,
                )
            }
            spend.primary?.let { window ->
                if (accountValue != null || spend.credits != null) CodexRowDivider()
                CodexValueRow(
                    title = stringResource(R.string.status_card_rate_primary),
                    value =
                        stringResource(
                                R.string.status_card_usage_percent,
                                window.usedPercent.toInt(),
                            )
                            .ifEmpty { "—" },
                    monospace = false,
                )
            }
            spend.secondary?.let { window ->
                if (accountValue != null || spend.credits != null || spend.primary != null) {
                    CodexRowDivider()
                }
                CodexValueRow(
                    title = stringResource(R.string.status_card_rate_secondary),
                    value =
                        stringResource(
                                R.string.status_card_usage_percent,
                                window.usedPercent.toInt(),
                            )
                            .ifEmpty { "—" },
                    monospace = false,
                )
            }
            // The TUI's nudge CTA comes from a backend banner this client does not parse; the state it
            // reacts to is already local, so the action lives next to it.
            val nudgeType =
                when {
                    spend.spendControlReached == true -> AddCreditsNudgeCreditType.UsageLimit
                    spend.credits?.let { !it.unlimited && !it.hasCredits } == true ->
                        AddCreditsNudgeCreditType.Credits
                    else -> null
                }
            if (nudgeType != null) {
                Spacer(Modifier.height(UiConsts.Space10))
                Button(
                    onClick = {
                        nudging = true
                        nudgeMessage = null
                        scope.launch {
                            app.client
                                .sendAddCreditsNudgeEmail(nudgeType)
                                .onSuccess { response ->
                                    nudgeMessage =
                                        when (response.status) {
                                            AddCreditsNudgeEmailStatus.Sent ->
                                                R.string.status_card_add_credits_sent
                                            AddCreditsNudgeEmailStatus.CooldownActive ->
                                                R.string.status_card_add_credits_cooldown
                                        }
                                }
                                .onFailure {
                                    nudgeMessage = R.string.status_card_add_credits_failed
                                }
                            nudging = false
                        }
                    },
                    modifier = Modifier.padding(horizontal = UiConsts.RowInset),
                    enabled = !nudging,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = stringResource(R.string.status_card_add_credits), maxLines = 1)
                }
                nudgeMessage?.let { message -> StatusNote(stringResource(message)) }
            }
        }
    }
}

/**
 * Clipboard snapshot mirroring the TUI's "Whole status" copy entry
 * (`codex-rs/.../chatwidget/interaction.rs`); the async estimate is skipped.
 */
@Composable
fun sessionStatusReport(app: CodexApp): String? {
    val session = app.widget.state
    if (session.threadId.isBlank()) return null
    val context = LocalContext.current
    fun text(id: Int) = context.getString(id)
    val config = session.config
    val usage = session.usage
    val spend = app.catalog.rateLimits.rateLimits
    val thread =
        (app.catalog.agentThreads + app.threads.threads).firstOrNull { it.id == session.threadId }
    val accountState = app.catalog.account
    val account = accountState.account
    val accountValue = statusAccountDisplay(account, text(R.string.session_status_api_key))
    return buildString {
        appendLine(text(R.string.session_status_title))
        appendLine("${text(R.string.session_status_thread_id)}: ${session.threadId}")
        appendLine("${text(R.string.session_status_status)}: ${session.status.label()}")
        thread
            ?.cliVersion
            ?.takeIf { it.isNotBlank() }
            ?.let { version ->
                appendLine("${text(R.string.session_status_created_by)}: $version")
            }
        config.forkedFromId?.let {
            appendLine("${text(R.string.status_card_forked_from_label)}: $it")
        }
        appendLine("${text(R.string.session_status_directory)}: ${config.cwd}")
        config.gitBranch
            ?.takeIf { it.isNotBlank() }
            ?.let {
                appendLine("${text(R.string.session_status_branch)}: $it")
            }
        appendLine(
            "${text(R.string.status_card_model_label)}: ${config.modelDisplayName.ifEmpty { config.model }}"
        )
        appendLine("${text(R.string.status_card_model_provider_label)}: ${config.modelProviderId}")
        appendLine(
            "${text(R.string.status_card_reasoning_label)}: ${config.reasoningEffort?.label() ?: "—"}"
        )
        appendLine("${text(R.string.status_card_approval_label)}: ${config.approvalPolicy.label()}")
        appendLine("${text(R.string.status_card_access_label)}: ${accessSummary(config)}")
        if (account !is Account.Chatgpt) {
            appendLine(
                "${text(R.string.session_status_tokens_total)}: ${formatTokens(usage.total.totalTokens)}"
            )
        }
        appendLine(
            "${text(R.string.session_status_context)}: " + "${(usage.usedFraction * 100).toInt()}%"
        )
        spend.primary?.let {
            appendLine("${text(R.string.status_card_rate_primary)}: ${it.usedPercent.toInt()}%")
        }
        spend.secondary?.let {
            appendLine("${text(R.string.status_card_rate_secondary)}: ${it.usedPercent.toInt()}%")
        }
        spend.credits?.let { credits ->
            val balance =
                when {
                    credits.unlimited -> text(R.string.status_card_credits_unlimited)
                    else -> credits.balance ?: text(R.string.status_card_credits_none)
                }
            appendLine("${text(R.string.status_card_credits_label)}: $balance")
        }
        accountValue?.let { appendLine("${text(R.string.session_status_account)}: $it") }
        if (accountState.requiresOpenaiAuth) {
            appendLine("${text(R.string.session_status_chatgpt_usage)}: $ChatGptUsageUrl")
        }
    }
}

internal fun statusAccountDisplay(account: Account?, apiKeyMessage: String): String? =
    when (account) {
        is Account.Chatgpt -> {
            val email = account.email?.takeIf { it.isNotBlank() }
            val plan = account.planType.takeIf { it.isNotBlank() }?.let(::statusPlanDisplay)
            when {
                email != null && plan != null -> "$email ($plan)"
                email != null -> email
                plan != null -> plan
                else -> "ChatGPT"
            }
        }
        is Account.ApiKey -> apiKeyMessage
        is Account.AmazonBedrock, null -> null
    }

private fun statusPlanDisplay(plan: String): String =
    when (plan) {
        "enterprise_cbp_automation" -> "Enterprise (Automation)"
        "self_serve_business_prolite" -> "Business Premium"
        "team", "self_serve_business_usage_based" -> "Business"
        "business", "ent26", "enterprise_cbp_usage_based" -> "Enterprise"
        "pro" -> "Pro (More)"
        "promax" -> "Pro (Max)"
        "prolite" -> "Pro"
        "edu_plus" -> "Edu Plus"
        "edu_pro" -> "Edu Pro"
        else -> plan.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

/** Footnote on a card's inner rail; always the page's own copy, never a value off the wire. */
@Composable
private fun StatusNote(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.padding(
                start = UiConsts.RowInset,
                end = UiConsts.RowInset,
                top = UiConsts.Space8,
                bottom = UiConsts.Space8,
            ),
        fontSize = UiType.Footnote,
        lineHeight = UiType.FootnoteLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
