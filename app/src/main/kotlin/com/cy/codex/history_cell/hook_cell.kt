package com.cy.codex.history_cell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.codeSurface
import com.cy.codex.successColor
import com.cy.codex.protocol.protocol.v2.HookMetadata
import com.cy.codex.protocol.protocol.v2.HookRunSummary
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Hook run output projection and identity joins; mirrors `codex-rs/tui/src/history_cell/hook_cell.rs`.

// Wire literals of `HookOutputEntry.kind` and `HookRunSummary.status` (codex-rs/app-server-protocol/src/protocol/v2/hook.rs).
private const val KindWarning = "warning"
private const val KindContext = "context"
private const val StatusCompleted = "completed"
private const val StatusFailed = "failed"
private const val StatusBlocked = "blocked"
private const val StatusStopped = "stopped"

private const val HookOutputIndent = "  "
private const val HookOutputBodyIndent = "    "

/**
 * The wording upstream hardcodes in `output_lines` (codex-rs/tui/src/history_cell/hook_cell.rs);
 * passed in because UI strings live in resources the reducer cannot read.
 */
data class HookCellLabels(
    val completed: String,
    val failed: String,
    val blocked: String,
    val stopped: String,
    val running: String,
    /** `String.format` template taking the warning entry's first line. */
    val provenanceFormat: String,
)

/** Upstream `hook_run_is_quiet_success`: a success with nothing user-facing leaves no cell. */
fun hookRunIsQuietSuccess(run: HookRunSummary): Boolean =
    run.status == StatusCompleted && run.entries.all { entry -> entry.kind == KindContext }

/**
 * The user-facing lines of a failed run, in entry order. `statusMessage` is not among them (upstream
 * never renders it, codex-rs/config/src/hook_config.rs); callers fall back to it only when this is empty.
 */
fun hookFailureLines(run: HookRunSummary): List<String> =
    run.entries.filter { entry -> entry.kind != KindContext }.flatMap { entry -> entry.text.lines() }

/** Upstream `output_lines` over several runs; blank line between blocks. */
fun hookOutputLines(runs: List<HookRunSummary>, labels: HookCellLabels): List<String> {
    val lines = mutableListOf<String>()
    for (run in runs) {
        if (lines.isNotEmpty()) lines += ""
        lines += hookRunOutputLines(run, labels)
    }
    return lines
}

/** Upstream `output_lines` for one run. */
fun hookRunOutputLines(run: HookRunSummary, labels: HookCellLabels): List<String> {
    val lines = mutableListOf<String>()
    // Only the first `warning` is a system message; later ones are skipped whole.
    val warningLines = run.entries.firstOrNull { entry -> entry.kind == KindWarning }
        ?.text?.split('\n')
    val firstWarningLine = warningLines?.firstOrNull()
    if (run.status == StatusCompleted && firstWarningLine != null) {
        // A success with a warning rewrites the header into a provenance line.
        lines += String.format(labels.provenanceFormat, firstWarningLine)
    } else {
        lines += "• ${statusHeader(run.status, labels)}"
        if (firstWarningLine != null) {
            lines += "$HookOutputIndent└ $firstWarningLine"
        }
    }
    for (line in warningLines.orEmpty().drop(1)) {
        lines += if (line.isEmpty()) "" else "$HookOutputBodyIndent$line"
    }
    for (entry in run.entries) {
        if (entry.kind == KindWarning || entry.kind == KindContext) continue
        pushFullHookOutputEntry(lines, entry.text)
    }
    return lines
}

private fun statusHeader(status: String, labels: HookCellLabels): String = when (status) {
    StatusCompleted -> labels.completed
    StatusFailed -> labels.failed
    StatusBlocked -> labels.blocked
    StatusStopped -> labels.stopped
    // `running` is the only other wire value; a new one must not crash the cell.
    else -> labels.running
}

/** Upstream `push_full_hook_output_entry`; blank lines are kept. */
private fun pushFullHookOutputEntry(lines: MutableList<String>, text: String) {
    val outputLines = text.split('\n')
    lines += "$HookOutputIndent└ ${outputLines.first()}"
    for (line in outputLines.drop(1)) {
        lines += if (line.isEmpty()) "" else "$HookOutputBodyIndent$line"
    }
}

/** Join key of one configured hook onto `hooks/list` metadata. */
data class HookRunKey(
    val eventName: String,
    val displayOrder: Long,
    val sourcePath: String,
)

/**
 * Kebab run-id event labels (codex-rs/hooks/src/engine/mod.rs) mapped to the camelCase wire
 * values of `HookMetadata.eventName`.
 */
private val HookEventIdLabels = mapOf(
    "pre-tool-use" to "preToolUse",
    "permission-request" to "permissionRequest",
    "post-tool-use" to "postToolUse",
    "pre-compact" to "preCompact",
    "post-compact" to "postCompact",
    "session-start" to "sessionStart",
    "session-end" to "sessionEnd",
    "user-prompt-submit" to "userPromptSubmit",
    "subagent-start" to "subagentStart",
    "subagent-stop" to "subagentStop",
    "stop" to "stop",
    "interrupt" to "interrupt",
)

/**
 * Parses a `ConfiguredHandler::run_id` ("{event-kebab}:{display_order}:{source_path}", codex-rs/hooks/src/engine/mod.rs);
 * the tail may carry a `:suffix` and colons inside the path, so it is matched against known source paths, longest prefix wins.
 */
fun parseHookRunId(id: String, knownSourcePaths: Collection<String> = emptyList()): HookRunKey? {
    val fields = id.split(":", limit = 3)
    if (fields.size < 3) return null
    val eventName = HookEventIdLabels[fields[0]] ?: return null
    val displayOrder = fields[1].toLongOrNull() ?: return null
    val tail = fields[2]
    val sourcePath = knownSourcePaths
        .filter { known -> tail == known || tail.startsWith("$known:") }
        .maxByOrNull { known -> known.length }
        ?: tail
    return HookRunKey(eventName, displayOrder, sourcePath)
}

/** The `(eventName, displayOrder, sourcePath)` join onto `hooks/list` metadata. */
fun findHookMetadata(key: HookRunKey, hooks: List<HookMetadata>): HookMetadata? =
    hooks.firstOrNull { hook ->
        hook.eventName == key.eventName &&
            hook.displayOrder == key.displayOrder &&
            hook.sourcePath == key.sourcePath
    }

/** Android-only: upstream names no hook; label reuses what the hooks browser shows. */
fun hookDisplayName(hook: HookMetadata): String = when {
    !hook.command.isNullOrBlank() -> hook.command
    !hook.server.isNullOrBlank() && !hook.tool.isNullOrBlank() -> "${hook.server}/${hook.tool}"
    else -> hook.key
}

/** Identity of a finished run, or null when it joins to nothing (builtins are absent from `hooks/list` yet still emit runs). */
fun resolveHookLabel(run: HookRunSummary, hooks: List<HookMetadata>): String? =
    findHookMetadata(HookRunKey(run.eventName, run.displayOrder, run.sourcePath), hooks)
        ?.let { hook -> hookDisplayName(hook) }

/** Identity behind a `HookPromptFragment.hookRunId`, or null when it joins to nothing. */
fun resolveHookLabel(hookRunId: String, hooks: List<HookMetadata>): String? {
    if (hookRunId.isBlank()) return null
    val key = parseHookRunId(hookRunId, hooks.map { hook -> hook.sourcePath }) ?: return null
    return findHookMetadata(key, hooks)?.let { hook -> hookDisplayName(hook) }
}

/** The `hooks/list` mirror cells join run ids against; empty until the first answer lands. */
val LocalHookMetadata = staticCompositionLocalOf<List<HookMetadata>> { emptyList() }

/** One completed hook run as upstream draws it (codex-rs/tui/src/history_cell/hook_cell.rs). */
@Composable
fun HookRunCell(
    run: HookRunSummary,
    modifier: Modifier = Modifier,
) {
    val labels = HookCellLabels(
        completed = stringResource(R.string.hook_cell_completed),
        failed = stringResource(R.string.hook_cell_failed),
        blocked = stringResource(R.string.hook_cell_blocked),
        stopped = stringResource(R.string.hook_cell_stopped),
        running = stringResource(R.string.hook_cell_running),
        provenanceFormat = stringResource(R.string.hook_cell_provenance),
    )
    val colors = MiuixTheme.colorScheme
    val bullet = when (run.status) {
        StatusCompleted -> successColor()
        StatusFailed, StatusBlocked, StatusStopped -> colors.error
        else -> colors.onSurfaceVariantSummary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(codeSurface())
            .padding(horizontal = UiConsts.Space16, vertical = UiConsts.Space12),
    ) {
        for (line in hookRunOutputLines(run, labels)) {
            Text(
                text = if (line.startsWith("• ")) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = bullet, fontWeight = FontWeight.Bold)) { append("•") }
                        append(line.drop(1))
                    }
                } else {
                    AnnotatedString(line)
                },
                fontSize = UiType.RowDetail,
                lineHeight = UiType.Message,
                color = colors.onSurface,
            )
        }
    }
}
