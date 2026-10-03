package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.AppServerEvent
import com.cy.codex.protocol.protocol.v2.WindowsSandboxReadiness
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupCompletedNotification
import com.cy.codex.protocol.protocol.v2.WindowsSandboxSetupMode
import com.cy.codex.statusDotColor
import com.cy.codex.successColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Windows sandbox: whether the host can confine a turn, and the two setup modes. Mirrors
 * codex-rs/tui/src/chatwidget/windows_sandbox_prompts.rs: elevated raises a UAC prompt and can
 * install what the sandbox needs; unelevated only starts what is installed. Windows-only.
 *
 * Readiness is kept locally — the catalog copy only moves on a completion notification.
 */
@Composable
fun WindowsSandboxScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var readiness by remember { mutableStateOf<WindowsSandboxReadiness?>(null) }
    var outcome by remember { mutableStateOf<WindowsSandboxSetupCompletedNotification?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<WindowsSandboxSetupMode?>(null) }
    var loading by remember { mutableStateOf(true) }
    // Recheck bumps this; the effect keys on it, so a recheck runs the page's own read path.
    var generation by remember { mutableStateOf(0) }

    fun read() {
        scope.launch {
            loading = true
            client
                .windowsSandboxReadiness()
                .onSuccess {
                    readiness = it.status
                    failure = null
                }
                .onFailure { failure = it.message }
            loading = false
        }
    }

    LaunchedEffect(generation) { read() }

    // setupStart answers when the attempt begins, so the outcome arrives only via the completion
    // notification; the same event re-reads readiness, since a finished setup is what changes it.
    LaunchedEffect(client) {
        client.events.collect { event ->
            if (event !is AppServerEvent.WindowsSandboxSetupCompleted) return@collect
            outcome = event.delta
            pending = null
            read()
        }
    }

    // Both modes stay pressable while an attempt is in flight: setupStart answers before the work
    // is done, and a refused start never sends the completion that would re-enable them.
    val request: (WindowsSandboxSetupMode) -> Unit = { mode ->
        pending = mode
        outcome = null
        onEvent(AppEvent.WindowsSandboxSetupStart(mode, null))
    }

    // Local answer wins; the catalog is the fallback until this page's own read lands.
    val status = readiness ?: catalog.windowsSandboxReadiness
    val completed = outcome
    val started = pending
    val setupValue =
        when {
            completed != null && completed.success ->
                stringResource(R.string.windows_sandbox_setup_ok, completed.mode.label())

            completed != null ->
                completed.error ?: stringResource(R.string.windows_sandbox_setup_failed)

            started != null ->
                stringResource(R.string.windows_sandbox_setup_starting, started.label())
            else -> ""
        }
    val setupTint =
        when {
            completed?.success == true -> successColor()
            completed != null -> colors.error
            else -> null
        }
    CodexPage(
        title = stringResource(R.string.windows_sandbox_title),
        description = stringResource(R.string.windows_sandbox_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        CodexSection(stringResource(R.string.windows_sandbox_readiness_card)) {
            CodexRow(
                title = stringResource(R.string.windows_sandbox_fact_readiness),
                endAction = {
                    Text(
                        text =
                            when {
                                status != null -> status.label()
                                loading -> stringResource(R.string.windows_sandbox_reading)
                                else -> stringResource(R.string.windows_sandbox_unknown)
                            }.ifEmpty { "—" },
                        fontSize = UiType.Value,
                        lineHeight = UiType.ValueLine,
                        color =
                            (if (status != null) statusDotColor(status.tone()) else null)
                                ?: colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.windows_sandbox_fact_meaning),
                value = status?.detail() ?: "—",
            )
            CodexRowDivider()
            CodexRow(
                title = stringResource(R.string.windows_sandbox_fact_setup),
                endAction = {
                    Text(
                        text = setupValue.ifEmpty { "—" },
                        fontSize = UiType.Value,
                        lineHeight = UiType.ValueLine,
                        color = setupTint ?: colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            if (failure != null) {
                CodexRowDivider()
                Text(
                    text = failure.orEmpty(),
                    modifier =
                        Modifier.padding(
                            start = UiConsts.RowInset,
                            end = UiConsts.RowInset,
                            top = UiConsts.Space8,
                            bottom = UiConsts.Space8,
                        ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.error,
                )
            }
        }
        val modes = listOf(WindowsSandboxSetupMode.Elevated, WindowsSandboxSetupMode.Unelevated)
        Column(modifier = Modifier.fillMaxWidth()) {
            CodexGroupTitle(stringResource(R.string.windows_sandbox_setup_card))
            Text(
                text = stringResource(R.string.windows_sandbox_setup_detail),
                modifier = Modifier.padding(bottom = UiConsts.Space10),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            CodexCardGrid(count = modes.size) { index ->
                val mode = modes[index]
                CodexCatalogCard(
                    title = mode.label(),
                    description = null,
                    icon =
                        if (mode == WindowsSandboxSetupMode.Elevated) {
                            MiuixIcons.Lock
                        } else {
                            MiuixIcons.Settings
                        },
                    onClick = { request(mode) },
                )
            }
            Text(
                text = stringResource(R.string.windows_sandbox_setup_host_note),
                modifier = Modifier.padding(top = UiConsts.Space8),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.onSurfaceVariantSummary,
            )
        }
        Button(
            onClick = { generation++ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(),
            cornerRadius = UiConsts.ButtonHeight / 2,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.windows_sandbox_recheck),
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

/**
 * Readiness labels: the wire values (`ready`, `notConfigured`, `updateRequired`) are the
 * protocol's spelling; this is the one place that turns them into words.
 */
@Composable
@ReadOnlyComposable
private fun WindowsSandboxReadiness.label(): String =
    stringResource(
        when (this) {
            WindowsSandboxReadiness.Ready -> R.string.windows_sandbox_readiness_ready
            WindowsSandboxReadiness.NotConfigured ->
                R.string.windows_sandbox_readiness_not_configured
            WindowsSandboxReadiness.UpdateRequired ->
                R.string.windows_sandbox_readiness_update_required
        }
    )

@Composable
@ReadOnlyComposable
private fun WindowsSandboxReadiness.detail(): String =
    stringResource(
        when (this) {
            WindowsSandboxReadiness.Ready -> R.string.windows_sandbox_detail_ready
            WindowsSandboxReadiness.NotConfigured -> R.string.windows_sandbox_detail_not_configured
            WindowsSandboxReadiness.UpdateRequired ->
                R.string.windows_sandbox_detail_update_required
        }
    )

private fun WindowsSandboxReadiness.tone(): ThreadStatusTone =
    when (this) {
        WindowsSandboxReadiness.Ready -> ThreadStatusTone.Done
        WindowsSandboxReadiness.NotConfigured -> ThreadStatusTone.Waiting
        WindowsSandboxReadiness.UpdateRequired -> ThreadStatusTone.Failed
    }

/** Setup mode labels; wire values are `elevated` and `unelevated`. */
@Composable
@ReadOnlyComposable
private fun WindowsSandboxSetupMode.label(): String =
    stringResource(
        when (this) {
            WindowsSandboxSetupMode.Elevated -> R.string.windows_sandbox_mode_elevated
            WindowsSandboxSetupMode.Unelevated -> R.string.windows_sandbox_mode_unelevated
        }
    )
