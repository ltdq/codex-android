package com.cy.codex.chatwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexPage
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.v2.ThreadRealtimeAudioChunk
import com.cy.codex.raisedSurface
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The realtime voice session of one thread, mirroring codex-rs/tui/src/chatwidget/realtime.rs.
 * Every card states which half of the TUI surface is missing: no peer connection, no transcript,
 * no voice control, no recorder.
 */
@Composable
fun RealtimeScreen(
    threadId: String,
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The page's memory of what it asked for, not evidence a session is running — started/closed/
    // error never reach this page.
    var requested by remember(threadId) { mutableStateOf(false) }
    // Local by design: the protocol has no call that sets a voice.
    var voice by remember(threadId) { mutableStateOf<String?>(null) }
    // Capture flag, held here so it resets with the thread; no audio is read behind it.
    var capturing by remember(threadId) { mutableStateOf(false) }
    // Fold target for captions; deltas arrive on the client's event flow, which the page is not given.
    val transcript = remember(threadId) { mutableStateListOf<String>() }

    // realtimeVoices is only filled by thread/realtime/listVoices, so ask once per thread.
    LaunchedEffect(threadId) { onEvent(AppEvent.ReloadRealtimeVoices) }

    CodexPage(
        title = stringResource(R.string.realtime_page_title),
        description = stringResource(R.string.realtime_page_subtitle),
        onBack = onBack,
        modifier = modifier,
    ) {
        RealtimeSessionSection(
            threadId = threadId,
            requested = requested,
            onToggle = { start ->
                if (start) {
                    onEvent(AppEvent.StartRealtime(threadId, null))
                } else {
                    onEvent(AppEvent.StopRealtime(threadId))
                }
                // Moved on the tap, not on the answer: the page does not receive the notification.
                requested = start
            },
        )
        RealtimeCaptionsSection(lines = transcript)
        RealtimeVoicesSection(
            voices = catalog.realtimeVoices,
            selected = voice,
            onSelect = { voice = it },
            onEvent = onEvent,
        )
        RealtimeTextSection(threadId = threadId, onEvent = onEvent)
        RealtimeMicrophoneSection(capturing = capturing, onToggle = { capturing = it })
    }
}

/**
 * Start / stop the session. No peer connection is built here, so the offer is `null` and the
 * server falls back to its own transport; [requested] is the page's record of the tap, never
 * session state.
 */
@Composable
private fun RealtimeSessionSection(
    threadId: String,
    requested: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    CodexSection(stringResource(R.string.realtime_session_title)) {
        Text(
            text =
                if (requested) {
                    stringResource(R.string.realtime_session_state_requested)
                } else {
                    stringResource(R.string.realtime_session_state_idle)
                },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                    ),
            fontSize = UiType.Detail,
            lineHeight = UiType.DetailLine,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        CodexValueRow(
            title = stringResource(R.string.realtime_session_thread),
            value = threadId.ifEmpty { "—" },
        )
        CodexRowDivider()
        CardNote(stringResource(R.string.realtime_session_transport_note))
        Button(
            onClick = { onToggle(!requested) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset),
            colors =
                if (requested) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            cornerRadius = UiConsts.ButtonHeight / 2,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text =
                    if (requested) {
                        stringResource(R.string.realtime_session_stop)
                    } else {
                        stringResource(R.string.realtime_session_start)
                    },
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CardNote(
            if (requested) {
                stringResource(R.string.realtime_session_requested_note)
            } else {
                stringResource(R.string.realtime_session_idle_note)
            }
        )
    }
}

/**
 * Where captions would be: the transcript is a stream, so it lives in the app-level reducer —
 * a list here would die on back — and [lines] is a fold target nothing appends to yet, since the
 * page is not handed the client.
 */
@Composable
private fun RealtimeCaptionsSection(lines: List<String>) {
    CodexSection(stringResource(R.string.realtime_transcript_title)) {
        if (lines.isEmpty()) {
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
                        imageVector = MiuixIcons.Messages,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.realtime_transcript_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.realtime_transcript_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            lines.forEachIndexed { index, line ->
                if (index > 0) CodexRowDivider()
                Text(
                    text = line,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = UiConsts.RowInset,
                                vertical = UiConsts.Space7,
                            ),
                    fontSize = UiType.Body,
                    lineHeight = UiType.BodyLine,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Server voices with a local-only selection: no setVoice exists, so a tap moves the tick, nothing is sent. */
@Composable
private fun RealtimeVoicesSection(
    voices: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onEvent: (AppEvent) -> Unit,
) {
    CodexSection(stringResource(R.string.realtime_voices_title)) {
        CodexNavRow(
            title = stringResource(R.string.realtime_voices_refresh),
            summary = stringResource(R.string.realtime_voices_refresh_detail),
            onClick = { onEvent(AppEvent.ReloadRealtimeVoices) },
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )
        CodexRowDivider()
        if (voices.isEmpty()) {
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
                        imageVector = MiuixIcons.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.realtime_voices_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.realtime_voices_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            CardNote(stringResource(R.string.realtime_voices_selection_note))
        }
    }
    if (voices.isNotEmpty()) {
        CodexCardGrid(count = voices.size) { index ->
            VoiceCard(
                name = voices[index],
                selected = voices[index] == selected,
                onClick = { onSelect(voices[index]) },
            )
        }
    }
}

/** A tick, not a radio: a radio would promise a commit the page cannot make. */
@Composable
private fun VoiceCard(name: String, selected: Boolean, onClick: () -> Unit) {
    CodexCatalogCard(
        title = name,
        description = null,
        icon = MiuixIcons.Tune,
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = stringResource(R.string.realtime_voices_selected),
                    modifier = Modifier.size(UiConsts.IconRow),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * appendText adds a user conversation item the model answers; appendSpeech voices client text the
 * model does not answer. Both events carry one string, so appendText always takes the default role.
 */
@Composable
private fun RealtimeTextSection(threadId: String, onEvent: (AppEvent) -> Unit) {
    var typed by remember(threadId) { mutableStateOf("") }
    var speech by remember(threadId) { mutableStateOf("") }
    val sendTyped: () -> Unit = {
        val text = typed.trim()
        if (text.isNotEmpty()) {
            onEvent(AppEvent.AppendRealtimeText(threadId, text))
            // Cleared only on a send the page actually made, so a blank tap cannot wipe a draft.
            typed = ""
        }
    }
    val sendSpeech: () -> Unit = {
        val text = speech.trim()
        if (text.isNotEmpty()) {
            onEvent(AppEvent.AppendRealtimeSpeech(threadId, text))
            speech = ""
        }
    }

    CodexSection(stringResource(R.string.realtime_inject_title)) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                    )
        ) {
            Text(
                text = stringResource(R.string.realtime_inject_text_label),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            TextField(
                value = typed,
                onValueChange = { typed = it },
                label = (stringResource(R.string.realtime_inject_text_placeholder)).orEmpty(),
                useLabelAsPlaceholder = true,
                keyboardActions =
                    KeyboardActions(
                        onDone = { sendTyped?.invoke() },
                        onGo = { sendTyped?.invoke() },
                        onSend = { sendTyped?.invoke() },
                    ),
                singleLine = true,
            )
        }
        CardNote(stringResource(R.string.realtime_inject_text_note))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = sendTyped,
                enabled = typed.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.realtime_inject_text_send),
                    fontSize = UiType.Action,
                    lineHeight = UiType.ActionLine,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CodexRowDivider()
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset)) {
            Text(
                text = stringResource(R.string.realtime_inject_speech_label),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
            Spacer(Modifier.height(UiConsts.Space4))
            TextField(
                value = speech,
                onValueChange = { speech = it },
                label = (stringResource(R.string.realtime_inject_speech_placeholder)).orEmpty(),
                useLabelAsPlaceholder = true,
                keyboardActions =
                    KeyboardActions(
                        onDone = { sendSpeech?.invoke() },
                        onGo = { sendSpeech?.invoke() },
                        onSend = { sendSpeech?.invoke() },
                    ),
                singleLine = true,
            )
        }
        CardNote(stringResource(R.string.realtime_inject_speech_note))
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        bottom = UiConsts.RowInset,
                    ),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = sendSpeech,
                enabled = speech.isNotBlank(),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.realtime_inject_speech_send),
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
 * No recorder is bound: no AudioRecord, no chunk, and [AppEvent.AppendRealtimeAudio] is never
 * emitted; the button toggles a page-local flag.
 */
@Composable
private fun RealtimeMicrophoneSection(capturing: Boolean, onToggle: (Boolean) -> Unit) {
    CodexSection(stringResource(R.string.realtime_mic_title)) {
        Text(
            text =
                if (capturing) {
                    stringResource(R.string.realtime_mic_state_on)
                } else {
                    stringResource(R.string.realtime_mic_state_off)
                },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                    ),
            fontSize = UiType.Detail,
            lineHeight = UiType.DetailLine,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        CardNote(stringResource(R.string.realtime_mic_format_note))
        CodexValueRow(
            title = stringResource(R.string.realtime_mic_sample_rate),
            value =
                stringResource(R.string.realtime_mic_sample_rate_value, CaptureFormat.sampleRate)
                    .ifEmpty { "—" },
            monospace = false,
        )
        CodexRowDivider()
        CodexValueRow(
            title = stringResource(R.string.realtime_mic_channels),
            value = CaptureFormat.numChannels.toString().ifEmpty { "—" },
            monospace = false,
        )
        CodexRowDivider()
        // Dash: the chunk's item id is unset because this client never builds a chunk.
        CodexValueRow(
            title = stringResource(R.string.realtime_mic_item_id),
            value = "—",
            monospace = false,
        )
        Button(
            onClick = { onToggle(!capturing) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiConsts.RowInset),
            colors =
                if (capturing) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            cornerRadius = UiConsts.ButtonHeight / 2,
            minHeight = UiConsts.ButtonHeight,
            insideMargin =
                PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
        ) {
            Text(
                text =
                    if (capturing) {
                        stringResource(R.string.realtime_mic_stop)
                    } else {
                        stringResource(R.string.realtime_mic_start)
                    },
                fontSize = UiType.Action,
                lineHeight = UiType.ActionLine,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CardNote(
            text =
                if (capturing) {
                    stringResource(R.string.realtime_mic_capturing_note)
                } else {
                    stringResource(R.string.realtime_mic_idle_note)
                },
            tint = if (capturing) warningColor() else null,
        )
    }
}

/** Shared note paragraph; [tint] marks the one note not to be skimmed as body text. */
@Composable
private fun CardNote(text: String, tint: Color? = null) {
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = UiConsts.RowInset,
                    end = UiConsts.RowInset,
                    top = UiConsts.Space8,
                    bottom = UiConsts.Space8,
                ),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = tint ?: MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * Capture format read off the protocol type — a literal would be a second source of truth for
 * [ThreadRealtimeAudioChunk.sampleRate]. This empty chunk is the instance Kotlin needs to read
 * the data class defaults.
 */
private val CaptureFormat = ThreadRealtimeAudioChunk(data = "")
