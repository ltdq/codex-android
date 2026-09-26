package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsGauge
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsProcess
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codex.raisedSurface
import com.cy.codex.usageColor
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Server self-report plus its feedback path on one page, mirrored from
 * `codex-rs/tui/src/debug_config.rs` and `codex-rs/.../bottom_pane/feedback_view.rs`.
 */
@Composable
fun DiagnosticsScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val diagnostics = catalog.diagnostics
    var reporting by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.diagnostics_screen_title),
            summary = diagnosticsSubtitle(diagnostics),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.diagnostics_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadDiagnostics) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.diagnostics_screen_refresh),
                        modifier = Modifier.size(UiConsts.IconRefresh),
                        tint = colors.primary,
                    )
                }
            },
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
            if (diagnostics == null) {
                DiagnosticsEmptyCard(onRead = { onEvent(AppEvent.ReloadDiagnostics) })
            } else {
                DiagnosticsProcessCard(diagnostics.process)
                DiagnosticsGaugesCard(diagnostics.gauges)
            }
            // Always shown: an unanswered probe is exactly when a report is worth most.
            DiagnosticsFeedbackCard(onReport = { reporting = true })
        }
    }

    if (reporting) {
        FeedbackFormSheet(
            onDismiss = { reporting = false },
            onSubmit = { classification, reason, includeLogs ->
                onEvent(AppEvent.UploadFeedback(classification, reason, null, includeLogs))
                reporting = false
            },
        )
    }
}

@Composable
private fun diagnosticsSubtitle(diagnostics: ServerDiagnosticsResponse?): String =
    when {
        diagnostics == null -> stringResource(R.string.diagnostics_screen_subtitle_unknown)
        diagnostics.process.id <= 0L ->
            stringResource(R.string.diagnostics_screen_subtitle_unversioned)

        else -> stringResource(R.string.diagnostics_screen_subtitle, diagnostics.process.id)
    }

// Distinguishes "client has not asked" from "server reported nothing".
@Composable
private fun DiagnosticsEmptyCard(onRead: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.diagnostics_screen_section_server),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(vertical = UiConsts.Space24, horizontal = UiConsts.Space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(UiConsts.IconBoxLarge)
                        .squircleBackground(
                            color = raisedSurface(),
                            cornerRadius = UiConsts.CornerCard,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconHeader),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(UiConsts.Space12))
            Text(
                text = stringResource(R.string.diagnostics_screen_not_asked),
                fontSize = UiType.RowTitle,
                lineHeight = UiType.RowTitleLine,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            Text(
                text = stringResource(R.string.diagnostics_screen_not_asked_detail),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(UiConsts.Space16))
            Button(
                onClick = onRead,
                modifier = Modifier,
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.diagnostics_screen_check), maxLines = 1)
            }
        }
    }
}

// pid/version are monospace: copied into reports; proportional faces blur l and 1.
@Composable
private fun DiagnosticsProcessCard(process: ServerDiagnosticsProcess) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.diagnostics_process_section),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        BasicComponent(
            title = stringResource(R.string.diagnostics_process_pid),
            endActions = {
                Text(
                    text = formatGaugeValue(process.id.toDouble()).ifEmpty { "—" },
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.diagnostics_process_resident),
            endActions = {
                Text(
                    text =
                        process.residentMemoryBytes?.let(::formatBytes).orEmpty().ifEmpty { "—" },
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        BasicComponent(
            title = stringResource(R.string.diagnostics_process_footprint),
            endActions = {
                Text(
                    text =
                        process.physicalFootprintBytes?.let(::formatBytes).orEmpty().ifEmpty {
                            "—"
                        },
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            },
        )
    }
}

// `server/…` gauges carry no unit or ceiling: bars are relative to the same reading's peak.
@Composable
private fun DiagnosticsGaugesCard(gauges: List<ServerDiagnosticsGauge>) {
    val colors = MiuixTheme.colorScheme
    // Peak zero means no scale: bars stay empty and divide-by-zero is avoided.
    val peak = gauges.maxOfOrNull { it.value }?.takeIf { it > 0L } ?: 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.diagnostics_gauges_section),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Tasks,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = gauges.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (gauges.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_gauges_empty),
                modifier =
                    Modifier.padding(
                        horizontal = UiConsts.Space4,
                        vertical = UiConsts.Space6,
                    ),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.disabledOnSurface,
            )
        } else {
            gauges.forEachIndexed { index, gauge ->
                if (index > 0)
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                DiagnosticsGaugeRow(gauge = gauge, peak = peak)
            }
            Spacer(Modifier.height(UiConsts.Space8))
            Text(
                text =
                    stringResource(
                        R.string.diagnostics_gauges_note,
                        formatGaugeValue(peak.toDouble()),
                    ),
                modifier = Modifier.padding(horizontal = UiConsts.Space4),
                fontSize = UiType.Footnote,
                lineHeight = UiType.FootnoteLine,
                color = colors.disabledOnSurface,
            )
        }
    }
}

@Composable
private fun DiagnosticsGaugeRow(gauge: ServerDiagnosticsGauge, peak: Long) {
    val colors = MiuixTheme.colorScheme
    val fraction =
        if (peak > 0L) {
            (gauge.value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = gauge.name,
            endActions = {
                Text(
                    text = formatGaugeValue(gauge.value.toDouble()).ifEmpty { "—" },
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            },
        )
        LinearProgressIndicator(
            progress = fraction,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = UiConsts.Space4)
                    .padding(bottom = UiConsts.Space8),
            colors =
                ProgressIndicatorDefaults.progressIndicatorColors(
                    foregroundColor = usageColor(fraction),
                    backgroundColor = colors.onBackground.copy(alpha = 0.08f),
                ),
            height = UiConsts.ProgressHeightRow,
        )
    }
}

// Locale pinned so the decimal point cannot shift under a translated build.
private fun formatGaugeValue(value: Double): String {
    val whole = value.toLong()
    return if (whole.toDouble() == value) {
        whole.toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

/** Raw bytes from the server as binary units; a null field renders as a dash, not a zero. */
private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "$bytes ${units[0]}"
    else String.format(Locale.US, "%.2f %s", value, units[unit])
}

/**
 * Feedback, mirroring `codex-rs/.../bottom_pane/feedback_view.rs`: classification
 * required (the server files under it), reason optional.
 */
@Composable
private fun DiagnosticsFeedbackCard(onReport: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.diagnostics_feedback_section),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.UploadCloud,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        Text(
            text = stringResource(R.string.diagnostics_feedback_body),
            modifier = Modifier.padding(horizontal = UiConsts.Space4),
            fontSize = UiType.Meta,
            lineHeight = UiType.MetaLine,
            color = colors.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(UiConsts.Space10))
        Button(
            onClick = onReport,
            modifier = Modifier.fillMaxWidth(),
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.diagnostics_feedback_action), maxLines = 1)
        }
    }
}

/**
 * The report form: fixed wire categories, optional reason, logs on explicit consent;
 * thread id stays null, as diagnostics is reachable with no thread open.
 */
@Composable
private fun FeedbackFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (classification: String, reason: String?, includeLogs: Boolean) -> Unit,
) {
    val categories =
        listOf(
            "bad_result" to stringResource(R.string.diagnostics_feedback_category_bad_result),
            "good_result" to stringResource(R.string.diagnostics_feedback_category_good_result),
            "bug" to stringResource(R.string.diagnostics_feedback_category_bug),
            "safety_check" to stringResource(R.string.diagnostics_feedback_category_safety_check),
            "other" to stringResource(R.string.diagnostics_feedback_category_other),
        )
    FormSheet(
        title = stringResource(R.string.diagnostics_feedback_form_title),
        subtitle = stringResource(R.string.diagnostics_feedback_form_subtitle),
        fields =
            listOf(
                FormField(
                    key = "classification",
                    label = stringResource(R.string.diagnostics_feedback_classification),
                    initial = "bug",
                    choices = categories,
                    help = stringResource(R.string.diagnostics_feedback_classification_help),
                ),
                FormField(
                    key = "reason",
                    label = stringResource(R.string.diagnostics_feedback_reason),
                    placeholder = stringResource(R.string.diagnostics_feedback_reason_placeholder),
                    required = false,
                    help = stringResource(R.string.diagnostics_feedback_reason_help),
                ),
                FormField(
                    key = "includeLogs",
                    label = stringResource(R.string.diagnostics_feedback_include_logs),
                    initial = "false",
                    choices =
                        listOf(
                            "false" to stringResource(R.string.diagnostics_feedback_logs_no),
                            "true" to stringResource(R.string.diagnostics_feedback_logs_yes),
                        ),
                    help = stringResource(R.string.diagnostics_feedback_include_logs_help),
                ),
            ),
        confirmLabel = stringResource(R.string.diagnostics_feedback_send),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                values["classification"].orEmpty().trim(),
                values["reason"].orEmpty().trim().takeIf { it.isNotEmpty() },
                values["includeLogs"] == "true",
            )
        },
    )
}
