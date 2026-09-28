package com.cy.codex.bottom_pane

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.chatwidget.PendingSteer
import com.cy.codex.protocol.protocol.v2.UserInput
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Pending steers stay visible until committed (`codex/codex-rs/tui/src/bottom_pane/pending_input_preview.rs`). */
@Composable
fun PendingInputPreview(messages: List<PendingSteer>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiConsts.Space4)) {
        Text(
            stringResource(R.string.pending_steer_header, messages.size),
            color = MiuixTheme.colorScheme.primary,
            fontSize = UiType.Meta,
        )
        messages.takeLast(3).forEach { message ->
            Text(
                message.preview.ifBlank { stringResource(R.string.pending_input_attachments) },
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = UiType.Meta,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RunningInputActions(canQueue: Boolean, onQueue: () -> Unit, onInterrupt: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(UiConsts.Space12)) {
        if (canQueue) InputAction(stringResource(R.string.pending_input_queue), onQueue)
        InputAction(stringResource(R.string.composer_interrupt_turn), onInterrupt)
    }
}

@Composable
fun RestoredInputAttachments(inputs: List<UserInput>, onRemove: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (inputs.isEmpty()) return
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8)) {
        inputs.forEachIndexed { index, input ->
            val label = when (input) {
                is UserInput.Image -> stringResource(R.string.pending_input_image)
                is UserInput.Audio, is UserInput.LocalAudio -> stringResource(R.string.pending_input_audio)
                is UserInput.Skill -> "$" + input.name
                is UserInput.Mention -> "@" + input.name
                else -> return@forEachIndexed
            }
            InputAction(stringResource(R.string.pending_input_remove, label)) { onRemove(index) }
        }
    }
}

@Composable
private fun InputAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = UiConsts.Space8),
        color = MiuixTheme.colorScheme.primary,
        fontSize = UiType.Meta,
    )
}
