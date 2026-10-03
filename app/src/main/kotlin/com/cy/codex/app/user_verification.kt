package com.cy.codex.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.CodexPage
import com.cy.codex.CodexRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.CodexValueRow
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.protocol.v2.UserVerificationEnrollResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationStatusResponse
import com.cy.codex.protocol.protocol.v2.UserVerificationVerifyParams
import com.cy.codex.successColor
import com.cy.codex.warningColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * User verification, mirroring `codex-rs/tui/src/app/user_verification.rs` and
 * `codex-rs/.../bottom_pane/user_verification.rs`. Wire facts: no state field (three
 * shapes), enroll registers nothing, verify signs a pasted challenge (no keystore binding).
 */
@Composable
fun UserVerificationScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val status = catalog.userVerification
    val shape = status.shape()
    // Server message over the shape label: the server knows why it is unavailable.
    val serverMessage = status?.unavailableMessage
    val subtitle =
        when {
            !serverMessage.isNullOrBlank() -> serverMessage
            status == null -> stringResource(R.string.user_verification_page_reading)
            else -> shape.label()
        }
    var signing by remember { mutableStateOf(false) }

    CodexPage(
        title = stringResource(R.string.user_verification_page_title),
        description = subtitle,
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { onEvent(AppEvent.ReloadUserVerification) },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(R.string.user_verification_page_refresh),
                    modifier = Modifier.size(UiConsts.IconRefresh),
                    tint = colors.primary,
                )
            }
        },
    ) {
        UserVerificationStatusSection(shape = shape, status = status)
        when (shape) {
            UserVerificationShape.Unavailable -> UnavailableSection(onEvent = onEvent)
            UserVerificationShape.NotEnrolled -> EnrollSection(onEvent = onEvent)
            UserVerificationShape.Enrolled ->
                EnrolledSection(
                    credential = catalog.userVerificationCredential,
                    onEvent = onEvent,
                    onSign = { signing = true },
                )
        }
    }

    if (signing) {
        val pageTitle = stringResource(R.string.user_verification_page_title)
        UserVerificationSignSheet(
            onDismiss = { signing = false },
            onSubmit = { challenge, description ->
                onEvent(
                    AppEvent.VerifyUserVerification(
                        UserVerificationVerifyParams(
                            challenge = challenge,
                            // The page title stands in for the display context a platform prompt would show.
                            title = pageTitle,
                            description = description,
                        )
                    )
                )
                signing = false
            },
        )
    }
}

private enum class UserVerificationShape {
    Unavailable,
    NotEnrolled,
    Enrolled,
}

@Composable
private fun UserVerificationShape.label(): String =
    stringResource(
        when (this) {
            UserVerificationShape.Unavailable -> R.string.user_verification_page_state_unavailable
            UserVerificationShape.NotEnrolled -> R.string.user_verification_page_state_not_enrolled
            UserVerificationShape.Enrolled -> R.string.user_verification_page_state_enrolled
        }
    )

// Unavailable outranks a present credential id: signing with it always fails.
private fun UserVerificationStatusResponse?.shape(): UserVerificationShape =
    when {
        this?.unavailableReason != null -> UserVerificationShape.Unavailable
        this?.credentialId.isNullOrEmpty() -> UserVerificationShape.NotEnrolled
        else -> UserVerificationShape.Enrolled
    }

// Credential id monospace: compared by eye against what the server holds.
@Composable
private fun UserVerificationStatusSection(
    shape: UserVerificationShape,
    status: UserVerificationStatusResponse?,
) {
    val tint =
        when (shape) {
            // Colours carry the state meaning, so the row reads as a state, not a label/value pair.
            UserVerificationShape.Enrolled -> successColor()
            UserVerificationShape.Unavailable -> warningColor()
            UserVerificationShape.NotEnrolled -> MiuixTheme.colorScheme.onSurface
        }
    CodexSection(stringResource(R.string.user_verification_page_status)) {
        CodexRow(
            title = stringResource(R.string.user_verification_page_state_label),
            endAction = {
                Text(
                    text = shape.label().ifEmpty { "—" },
                    fontSize = UiType.Value,
                    lineHeight = UiType.ValueLine,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        if (shape == UserVerificationShape.Unavailable) {
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.user_verification_page_reason),
                value = status?.unavailableReason?.label().orEmpty().ifEmpty { "—" },
                monospace = false,
            )
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.user_verification_page_message),
                value = status?.unavailableMessage.orEmpty().ifEmpty { "—" },
                monospace = false,
            )
        }
        if (shape == UserVerificationShape.Enrolled) {
            CodexRowDivider()
            CodexValueRow(
                title = stringResource(R.string.user_verification_page_credential_id),
                value = status?.credentialId.orEmpty().ifEmpty { "—" },
            )
        }
    }
}

// Enroll and sign are hidden: the capability lives on the device; re-read alone can change the answer.
@Composable
private fun UnavailableSection(onEvent: (AppEvent) -> Unit) {
    CodexSection(stringResource(R.string.user_verification_page_unavailable)) {
        UserVerificationNote(stringResource(R.string.user_verification_page_unavailable_detail))
        Button(
            onClick = { onEvent(AppEvent.ReloadUserVerification) },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        bottom = UiConsts.RowInset,
                    ),
            enabled = true,
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(text = stringResource(R.string.user_verification_page_refresh), maxLines = 1)
        }
    }
}

// `userVerification/enroll` mints or reuses a local credential; it signs, registers or prompts for nothing.
@Composable
private fun EnrollSection(onEvent: (AppEvent) -> Unit) {
    CodexSection(stringResource(R.string.user_verification_page_enroll)) {
        UserVerificationNote(stringResource(R.string.user_verification_page_enroll_note))
        Button(
            onClick = { onEvent(AppEvent.EnrollUserVerification) },
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        bottom = UiConsts.RowInset,
                    ),
            enabled = true,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(text = stringResource(R.string.user_verification_page_enroll), maxLines = 1)
        }
    }
}

// Public metadata comes from the enroll answer; the status never carries it.
@Composable
private fun EnrolledSection(
    credential: UserVerificationEnrollResponse?,
    onEvent: (AppEvent) -> Unit,
    onSign: () -> Unit,
) {
    CodexSection(stringResource(R.string.user_verification_page_credential)) {
        if (credential != null) {
            credential.algorithm
                ?.takeIf { it.isNotBlank() }
                ?.let { algorithm ->
                    CodexValueRow(
                        title = stringResource(R.string.user_verification_page_algorithm),
                        value = algorithm.ifEmpty { "—" },
                    )
                    CodexRowDivider()
                }
            credential.publicKey
                ?.takeIf { it.isNotBlank() }
                ?.let { key ->
                    val ellipsis = stringResource(R.string.user_verification_page_ellipsis)
                    CodexValueRow(
                        title = stringResource(R.string.user_verification_page_public_key),
                        value = truncated(key, ellipsis).ifEmpty { "—" },
                    )
                    CodexRowDivider()
                }
            if (credential.algorithm.isNullOrBlank() || credential.publicKey.isNullOrBlank()) {
                UserVerificationNote(
                    stringResource(R.string.user_verification_page_metadata_absent)
                )
            }
        }
        Button(
            onClick = onSign,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space10,
                    ),
            enabled = true,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(text = stringResource(R.string.user_verification_page_sign), maxLines = 1)
        }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                    ),
            verticalArrangement = Arrangement.spacedBy(UiConsts.Space8),
        ) {
            Button(
                onClick = { onEvent(AppEvent.CancelUserVerification) },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.user_verification_page_cancel), maxLines = 1)
            }
            Button(
                onClick = { onEvent(AppEvent.DeleteUserVerification) },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                colors =
                    ButtonDefaults.buttonColors(
                        color = Color.Transparent,
                        contentColor = MiuixTheme.colorScheme.error,
                    ),
            ) {
                Text(text = stringResource(R.string.user_verification_page_delete), maxLines = 1)
            }
        }
        // Cancel is not undo: it stops an in-flight verification; delete removes the local credential.
        UserVerificationNote(stringResource(R.string.user_verification_page_cancel_note))
    }
}

@Composable
private fun UserVerificationNote(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.RowInset, vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

/**
 * Sign form: `userVerification/verify` takes challenge plus display context, but only the
 * challenge reaches the server; `cancel` takes no params, so one verification at a time.
 */
@Composable
private fun UserVerificationSignSheet(
    onDismiss: () -> Unit,
    onSubmit: (challenge: String, description: String) -> Unit,
) {
    val dismiss = LocalDismissState.current
    CompositionLocalProvider(LocalDismissState provides null) {
        FormSheet(
            title = stringResource(R.string.user_verification_page_sign),
            subtitle = stringResource(R.string.user_verification_page_sign_detail),
            fields =
                listOf(
                    FormField(
                        key = "challenge",
                        label = stringResource(R.string.user_verification_page_challenge),
                        placeholder =
                            stringResource(R.string.user_verification_page_challenge_placeholder),
                        help = stringResource(R.string.user_verification_page_challenge_help),
                    ),
                    FormField(
                        key = "description",
                        label = stringResource(R.string.user_verification_page_description),
                        placeholder =
                            stringResource(R.string.user_verification_page_description_placeholder),
                        required = false,
                        help = stringResource(R.string.user_verification_page_description_help),
                    ),
                ),
            confirmLabel = stringResource(R.string.user_verification_page_sign_confirm),
            onDismiss = {
                onDismiss()
                dismiss?.invoke()
            },
            onSubmit = { values ->
                onSubmit(
                    values["challenge"].orEmpty().trim(),
                    values["description"].orEmpty().trim(),
                )
            },
        )
    }
}

// The middle of a public key is never read; the row only confirms a key arrived.
private fun truncated(value: String, ellipsis: String, keep: Int = 36): String =
    if (value.length <= keep * 2 + ellipsis.length) {
        value
    } else {
        value.take(keep) + ellipsis + value.takeLast(keep)
    }
