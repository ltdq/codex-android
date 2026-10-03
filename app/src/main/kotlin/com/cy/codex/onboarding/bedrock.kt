package com.cy.codex.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CodexNavRow
import com.cy.codex.CodexPage
import com.cy.codex.CodexRadioRow
import com.cy.codex.CodexRowDivider
import com.cy.codex.CodexSection
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.BedrockAwsProfile
import com.cy.codex.protocol.protocol.v2.BedrockEnvironmentCredential
import com.cy.codex.protocol.protocol.v2.BedrockSetupParams
import com.cy.codex.protocol.protocol.v2.LoginAccountParams
import com.cy.codex.raisedSurface
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Signing in through Amazon Bedrock; mirrors `codex-rs/tui/src/onboarding/bedrock.rs`.
 * `account/bedrock/discover` is a read, `account/bedrock/setup` the only write; a credential
 * without a region opens a form for one.
 */
@Composable
fun BedrockScreen(
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<BedrockAwsProfile>>(emptyList()) }
    var environment by remember { mutableStateOf<List<BedrockEnvironmentCredential>>(emptyList()) }
    var selected by remember { mutableStateOf<BedrockSetupParams?>(null) }
    var awaitingRegion by remember { mutableStateOf<BedrockSetupParams?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var manualForm by remember { mutableStateOf<ManualBedrockForm?>(null) }
    var generation by remember { mutableStateOf(0) }

    fun discover() {
        scope.launch {
            loading = true
            client
                .bedrockDiscover()
                .onSuccess { response ->
                    profiles = response.profiles
                    environment = response.environmentCredentials
                    failure = null
                    // A credential the server no longer offers must not stay selected.
                    selected = selected?.takeIf { pick ->
                        response.profiles.any {
                            it.name == (pick as? BedrockSetupParams.Profile)?.profile
                        } || (pick is BedrockSetupParams.Environment)
                    }
                }
                .onFailure { failure = it.message }
            loading = false
        }
    }

    // A discovery entry without a region opens the form instead of selecting; setup always needs
    // one.
    fun pick(credential: BedrockSetupParams) {
        val region =
            when (credential) {
                is BedrockSetupParams.Profile -> credential.region
                is BedrockSetupParams.Environment -> credential.region
            }
        if (region.isBlank()) awaitingRegion = credential else selected = credential
    }

    LaunchedEffect(generation) { discover() }

    CodexPage(
        title = stringResource(R.string.bedrock_screen_title),
        description = stringResource(R.string.bedrock_screen_subtitle),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { generation++ },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(R.string.bedrock_screen_refresh),
                    modifier = Modifier.size(UiConsts.IconRefresh),
                    tint = colors.primary,
                )
            }
        },
    ) {
        BedrockCredentialsSection(
            profiles = profiles,
            environment = environment,
            selected = selected,
            loading = loading,
            onPick = { pick(it) },
        )
        // Methods the server cannot discover: a typed profile reuses `account/bedrock/setup`; access
        // keys and an API key establish the credential themselves.
        CodexSection(stringResource(R.string.bedrock_manual_title)) {
            CodexNavRow(
                title = stringResource(R.string.bedrock_manual_profile),
                summary = stringResource(R.string.bedrock_manual_profile_detail),
                onClick = { manualForm = ManualBedrockForm.Profile },
            )
            CodexRowDivider()
            CodexNavRow(
                title = stringResource(R.string.bedrock_manual_access_keys),
                summary = stringResource(R.string.bedrock_manual_access_keys_detail),
                onClick = { manualForm = ManualBedrockForm.AccessKeys },
            )
            CodexRowDivider()
            CodexNavRow(
                title = stringResource(R.string.bedrock_manual_api_key),
                summary = stringResource(R.string.bedrock_manual_api_key_detail),
                onClick = { manualForm = ManualBedrockForm.ApiKey },
            )
        }
        if (failure != null) {
            CodexSection(stringResource(R.string.bedrock_failed_title)) {
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
        CodexSection(stringResource(R.string.bedrock_setup_title)) {
            Text(
                text = stringResource(R.string.bedrock_setup_detail),
                modifier =
                    Modifier.padding(
                        start = UiConsts.RowInset,
                        end = UiConsts.RowInset,
                        top = UiConsts.Space8,
                        bottom = UiConsts.Space4,
                    ),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
            )
            Button(
                onClick = { selected?.let { onEvent(AppEvent.BedrockSetup(it)) } },
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = UiConsts.RowInset,
                            end = UiConsts.RowInset,
                            top = UiConsts.Space6,
                            bottom = UiConsts.RowInset,
                        ),
                enabled = selected != null,
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
                    text = stringResource(R.string.bedrock_setup_action),
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

    if (awaitingRegion != null) {
        RegionSheet(
            credential = awaitingRegion!!,
            onDismiss = { awaitingRegion = null },
            onConfirm = { credential, region ->
                selected =
                    when (credential) {
                        is BedrockSetupParams.Profile -> credential.copy(region = region)
                        is BedrockSetupParams.Environment -> credential.copy(region = region)
                    }
                awaitingRegion = null
            },
        )
    }

    when (manualForm) {
        ManualBedrockForm.Profile ->
            FormSheet(
                title = stringResource(R.string.bedrock_profile_form_title),
                subtitle = stringResource(R.string.bedrock_manual_profile_detail),
                fields =
                    listOf(
                        FormField(
                            key = "profile",
                            label = stringResource(R.string.bedrock_profile_form_name),
                            placeholder =
                                stringResource(R.string.bedrock_profile_form_name_placeholder),
                        ),
                        FormField(
                            key = "region",
                            label = stringResource(R.string.bedrock_region_form_label),
                            placeholder = stringResource(R.string.bedrock_region_form_placeholder),
                        ),
                    ),
                confirmLabel = stringResource(R.string.bedrock_region_form_confirm),
                onDismiss = { manualForm = null },
                onSubmit = { values ->
                    selected =
                        BedrockSetupParams.Profile(
                            profile = values["profile"].orEmpty().trim(),
                            region = values["region"].orEmpty().trim(),
                        )
                    manualForm = null
                },
            )

        ManualBedrockForm.AccessKeys ->
            FormSheet(
                title = stringResource(R.string.bedrock_access_keys_form_title),
                subtitle = stringResource(R.string.bedrock_manual_access_keys_detail),
                fields =
                    listOf(
                        FormField(
                            key = "accessKeyId",
                            label = stringResource(R.string.bedrock_access_key_id),
                        ),
                        FormField(
                            key = "secretAccessKey",
                            label = stringResource(R.string.bedrock_secret_access_key),
                            masked = true,
                            keyboardType = KeyboardType.Password,
                        ),
                        FormField(
                            key = "sessionToken",
                            label = stringResource(R.string.bedrock_session_token),
                            required = false,
                            masked = true,
                            keyboardType = KeyboardType.Password,
                            help = stringResource(R.string.bedrock_session_token_help),
                        ),
                        FormField(
                            key = "region",
                            label = stringResource(R.string.bedrock_region_form_label),
                            placeholder = stringResource(R.string.bedrock_region_form_placeholder),
                        ),
                    ),
                confirmLabel = stringResource(R.string.bedrock_region_form_confirm),
                onDismiss = { manualForm = null },
                onSubmit = { values ->
                    onEvent(
                        AppEvent.Login(
                            LoginAccountParams.AmazonBedrockAccessKeys(
                                accessKeyId = values["accessKeyId"].orEmpty().trim(),
                                secretAccessKey = values["secretAccessKey"].orEmpty().trim(),
                                region = values["region"].orEmpty().trim(),
                                sessionToken =
                                    values["sessionToken"].orEmpty().trim().takeIf {
                                        it.isNotEmpty()
                                    },
                            )
                        )
                    )
                    manualForm = null
                },
            )

        ManualBedrockForm.ApiKey ->
            FormSheet(
                title = stringResource(R.string.bedrock_api_key_form_title),
                subtitle = stringResource(R.string.bedrock_manual_api_key_detail),
                fields =
                    listOf(
                        FormField(
                            key = "apiKey",
                            label = stringResource(R.string.bedrock_api_key),
                            masked = true,
                            keyboardType = KeyboardType.Password,
                        ),
                        FormField(
                            key = "region",
                            label = stringResource(R.string.bedrock_region_form_label),
                            placeholder = stringResource(R.string.bedrock_region_form_placeholder),
                        ),
                    ),
                confirmLabel = stringResource(R.string.bedrock_region_form_confirm),
                onDismiss = { manualForm = null },
                onSubmit = { values ->
                    onEvent(
                        AppEvent.Login(
                            LoginAccountParams.AmazonBedrock(
                                apiKey = values["apiKey"].orEmpty().trim(),
                                region = values["region"].orEmpty().trim(),
                            )
                        )
                    )
                    manualForm = null
                },
            )

        null -> Unit
    }
}

private enum class ManualBedrockForm {
    Profile,
    AccessKeys,
    ApiKey,
}

@Composable
private fun RegionSheet(
    credential: BedrockSetupParams,
    onDismiss: () -> Unit,
    onConfirm: (BedrockSetupParams, String) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.bedrock_region_form_title),
        subtitle = credentialSummary(credential),
        fields =
            listOf(
                FormField(
                    key = "region",
                    label = stringResource(R.string.bedrock_region_form_label),
                    placeholder = stringResource(R.string.bedrock_region_form_placeholder),
                    required = true,
                )
            ),
        confirmLabel = stringResource(R.string.bedrock_region_form_confirm),
        onDismiss = onDismiss,
        onSubmit = { values -> onConfirm(credential, values["region"].orEmpty().trim()) },
    )
}

@Composable
private fun environmentCredentialLabel(type: String): String =
    when (type) {
        "accessKeys" -> stringResource(R.string.bedrock_kind_access_keys)
        "bedrockApiKey" -> stringResource(R.string.bedrock_kind_bedrock_api_key)
        else -> type
    }

@Composable
private fun credentialSummary(credential: BedrockSetupParams): String =
    when (credential) {
        is BedrockSetupParams.Profile ->
            stringResource(R.string.bedrock_summary_profile, credential.profile, credential.region)
        is BedrockSetupParams.Environment ->
            stringResource(R.string.bedrock_summary_environment, credential.region)
    }

/**
 * The discovered credentials as pickable rows, not a dropdown: the choice is the whole page and the
 * alternatives must stay visible.
 */
@Composable
private fun BedrockCredentialsSection(
    profiles: List<BedrockAwsProfile>,
    environment: List<BedrockEnvironmentCredential>,
    selected: BedrockSetupParams?,
    loading: Boolean,
    onPick: (BedrockSetupParams) -> Unit,
) {
    val methods = profiles.size + environment.size
    CodexSection(stringResource(R.string.bedrock_methods_title)) {
        when {
            loading && methods == 0 ->
                Text(
                    text = stringResource(R.string.bedrock_regions_loading),
                    modifier =
                        Modifier.padding(
                            start = UiConsts.RowInset,
                            end = UiConsts.RowInset,
                            top = UiConsts.Space8,
                            bottom = UiConsts.Space8,
                        ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

            methods == 0 ->
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                vertical = UiConsts.Space24,
                                horizontal = UiConsts.Space16,
                            ),
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
                            imageVector = MiuixIcons.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.bedrock_regions_empty),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = stringResource(R.string.bedrock_regions_empty_detail),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }

            else -> {
                profiles.forEachIndexed { index, profile ->
                    if (index > 0) CodexRowDivider()
                    CodexRadioRow(
                        title = profile.name,
                        summary =
                            profile.region
                                ?: stringResource(R.string.bedrock_region_required),
                        selected =
                            (selected as? BedrockSetupParams.Profile)?.profile == profile.name,
                        onClick = {
                            onPick(
                                BedrockSetupParams.Profile(
                                    profile.name,
                                    profile.region.orEmpty(),
                                )
                            )
                        },
                    )
                }
                environment.forEachIndexed { index, credential ->
                    if (profiles.isNotEmpty() || index > 0) CodexRowDivider()
                    CodexRadioRow(
                        title = environmentCredentialLabel(credential.type),
                        summary =
                            credential.region
                                ?: stringResource(R.string.bedrock_region_required),
                        selected = selected is BedrockSetupParams.Environment,
                        onClick = {
                            onPick(BedrockSetupParams.Environment(credential.region.orEmpty()))
                        },
                    )
                }
            }
        }
    }
}
