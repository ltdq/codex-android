package com.cy.codex.chatwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
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
    onBack: () -> Unit,
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

    // Buttons stay enabled while an attempt is in flight: setupStart answers before the work is
    // done, and a refused start never sends the completion that would re-enable a button.
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
    val statusLabel = if (status != null) status.label() else null

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.windows_sandbox_title),
            summary = stringResource(R.string.windows_sandbox_subtitle),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.windows_sandbox_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            insideMargin = PaddingValues(14.dp, 10.dp),
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiConsts.ScreenMargin)
                    .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_readiness_card),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        if (statusLabel != null) {
                            Text(
                                text = statusLabel,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_readiness),
                    endActions = {
                        Text(
                            text =
                                when {
                                    status != null -> status.label()
                                    loading -> stringResource(R.string.windows_sandbox_reading)
                                    else -> stringResource(R.string.windows_sandbox_unknown)
                                }.ifEmpty { "—" },
                            color =
                                (if (status != null) statusDotColor(status.tone()) else null)
                                    ?: MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_meaning),
                    endActions = {
                        Text(
                            text = if (status != null) status.detail() else "".ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_fact_setup),
                    endActions = {
                        Text(
                            text = setupValue.ifEmpty { "—" },
                            color = (setupTint) ?: MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            fontSize = UiType.Detail,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
                if (failure != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                    Text(
                        text = failure.orEmpty(),
                        modifier =
                            Modifier.padding(
                                horizontal = UiConsts.Space4,
                                vertical = UiConsts.Space8,
                            ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                }
            }
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.windows_sandbox_setup_card),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                Text(
                    text = stringResource(R.string.windows_sandbox_setup_detail),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space4,
                        ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space6),
                    horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                ) {
                    Button(
                        onClick = { request(WindowsSandboxSetupMode.Elevated) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.windows_sandbox_setup_elevated),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { request(WindowsSandboxSetupMode.Unelevated) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                        cornerRadius = UiConsts.ButtonHeight / 2,
                        minHeight = UiConsts.ButtonHeight,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontal,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.windows_sandbox_setup_unelevated),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.windows_sandbox_setup_host_note),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space2,
                        ),
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
