package com.cy.codex.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexPage
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValue
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsGauge
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsProcess
import com.cy.codex.protocol.protocol.v2.ServerDiagnosticsResponse
import com.cy.codex.raisedSurface
import com.cy.codex.usageColor
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tasks
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
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val diagnostics = catalog.diagnostics
    var reporting by remember { mutableStateOf(false) }

    CodexPage(
        title = stringResource(R.string.diagnostics_screen_title),
        description = diagnosticsSubtitle(diagnostics),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { onEvent(AppEvent.ReloadDiagnostics) },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(R.string.diagnostics_screen_refresh),
                    modifier = Modifier.size(UiConsts.IconRefresh),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) {
        if (diagnostics == null) {
            DiagnosticsEmptySection(onRead = { onEvent(AppEvent.ReloadDiagnostics) })
        } else {
            DiagnosticsProcessSection(diagnostics.process)
            DiagnosticsGaugesSection(diagnostics.gauges)
        }
        // Always shown: an unanswered probe is exactly when a report is worth most.
        DiagnosticsFeedbackSection(onReport = { reporting = true })
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
private fun DiagnosticsEmptySection(onRead: () -> Unit) {
    CodexSection(stringResource(R.string.diagnostics_screen_section_server)) {
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

@Composable
private fun DiagnosticsProcessSection(process: ServerDiagnosticsProcess) {
    CodexSection(stringResource(R.string.diagnostics_process_section)) {
        CodexValueRow(
            title = stringResource(R.string.diagnostics_process_pid),
            value = formatGaugeValue(process.id.toDouble()).ifEmpty { "—" },
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.diagnostics_process_resident),
            value = process.residentMemoryBytes?.let(::formatBytes).orEmpty().ifEmpty { "—" },
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.diagnostics_process_footprint),
            value =
                process.physicalFootprintBytes?.let(::formatBytes).orEmpty().ifEmpty { "—" },
        )
    }
}

// `server/…` gauges carry no unit or ceiling: bars are relative to the same reading's peak.
@Composable
private fun DiagnosticsGaugesSection(gauges: List<ServerDiagnosticsGauge>) {
    // Peak zero means no scale: bars stay empty and divide-by-zero is avoided.
    val peak = gauges.maxOfOrNull { it.value }?.takeIf { it > 0L } ?: 0L

    CodexSection {
        CodexValueRow(
            title = stringResource(R.string.diagnostics_gauges_section),
            value = gauges.size.toString(),
        )
        if (gauges.isEmpty()) {
            DiagnosticsNote(stringResource(R.string.diagnostics_gauges_empty))
        } else {
            DiagnosticsNote(
                stringResource(
                    R.string.diagnostics_gauges_note,
                    formatGaugeValue(peak.toDouble()),
                )
            )
        }
    }
    CodexCardGrid(count = gauges.size) { index ->
        DiagnosticsGaugeCard(gauge = gauges[index], peak = peak)
    }
}

@Composable
private fun DiagnosticsGaugeCard(gauge: ServerDiagnosticsGauge, peak: Long) {
    val colors = MiuixTheme.colorScheme
    val fraction =
        if (peak > 0L) {
            (gauge.value.toFloat() / peak.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    CodexCatalogCard(
        title = gauge.name,
        description = null,
        icon = MiuixIcons.Tasks,
        trailing = { CodexValue(formatGaugeValue(gauge.value.toDouble()).ifEmpty { "—" }) },
        footer = {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = usageColor(fraction),
                        backgroundColor = colors.onBackground.copy(alpha = 0.08f),
                    ),
                height = UiConsts.ProgressHeightRow,
            )
        },
    )
}

/** Footnote on a card's inner rail; always the page's own copy, never a value off the wire. */
@Composable
private fun DiagnosticsNote(text: String) {
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
private fun DiagnosticsFeedbackSection(onReport: () -> Unit) {
    CodexSection(stringResource(R.string.diagnostics_feedback_section)) {
        DiagnosticsNote(stringResource(R.string.diagnostics_feedback_body))
        Button(
            onClick = onReport,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space4,
                        bottom = UiConsts.RowInset,
                    ),
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
