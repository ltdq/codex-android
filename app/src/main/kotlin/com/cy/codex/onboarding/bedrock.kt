package com.cy.codex.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
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

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.bedrock_screen_title),
            summary = stringResource(R.string.bedrock_screen_subtitle),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.bedrock_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
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
            val methods = profiles.size + environment.size
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.bedrock_methods_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        (if (methods == 0) null else methods.toString())?.let {
                            Text(
                                text = it,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    },
                )

                when {
                    loading && methods == 0 ->
                        Text(
                            text = stringResource(R.string.bedrock_regions_loading),
                            modifier =
                                Modifier.padding(
                                    horizontal = UiConsts.Space4,
                                    vertical = UiConsts.Space8,
                                ),
                            fontSize = UiType.Meta,
                            lineHeight = UiType.MetaLine,
                            color = colors.onSurfaceVariantSummary,
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
                            if (index > 0)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = UiConsts.Space1)
                                )
                            CredentialRow(
                                title = profile.name,
                                subtitle =
                                    profile.region
                                        ?: stringResource(R.string.bedrock_region_required),
                                selected =
                                    (selected as? BedrockSetupParams.Profile)?.profile ==
                                        profile.name,
                                onClick = {
                                    pick(
                                        BedrockSetupParams.Profile(
                                            profile.name,
                                            profile.region.orEmpty(),
                                        )
                                    )
                                },
                            )
                        }
                        environment.forEachIndexed { index, credential ->
                            if (profiles.isNotEmpty() || index > 0)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = UiConsts.Space1)
                                )
                            CredentialRow(
                                title = environmentCredentialLabel(credential.type),
                                subtitle =
                                    credential.region
                                        ?: stringResource(R.string.bedrock_region_required),
                                selected = selected is BedrockSetupParams.Environment,
                                onClick = {
                                    pick(
                                        BedrockSetupParams.Environment(credential.region.orEmpty())
                                    )
                                },
                            )
                        }
                    }
                }
            }
            // Methods the server cannot discover: a typed profile reuses `account/bedrock/setup`; access
            // keys and an API key establish the credential themselves.
            Card(
                cornerRadius = UiConsts.SectionCorner,
                insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
            ) {
                BasicComponent(
                    title = stringResource(R.string.bedrock_manual_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                CredentialRow(
                    title = stringResource(R.string.bedrock_manual_profile),
                    subtitle = stringResource(R.string.bedrock_manual_profile_detail),
                    selected = false,
                    onClick = { manualForm = ManualBedrockForm.Profile },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                CredentialRow(
                    title = stringResource(R.string.bedrock_manual_access_keys),
                    subtitle = stringResource(R.string.bedrock_manual_access_keys_detail),
                    selected = false,
                    onClick = { manualForm = ManualBedrockForm.AccessKeys },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                CredentialRow(
                    title = stringResource(R.string.bedrock_manual_api_key),
                    subtitle = stringResource(R.string.bedrock_manual_api_key_detail),
                    selected = false,
                    onClick = { manualForm = ManualBedrockForm.ApiKey },
                )
            }
            if (failure != null) {
                Card(
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.bedrock_failed_title),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                    )

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
                    title = stringResource(R.string.bedrock_setup_title),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                    endActions = {
                        (selected?.let { credentialSummary(it) })?.let {
                            Text(
                                text = it,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    },
                )

                Text(
                    text = stringResource(R.string.bedrock_setup_detail),
                    modifier =
                        Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space4,
                        ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
                Button(
                    onClick = { selected?.let { onEvent(AppEvent.BedrockSetup(it)) } },
                    modifier = Modifier.fillMaxWidth().padding(top = UiConsts.Space6),
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
 * One discovered credential as a pickable row, not a dropdown: the choice is the whole page and
 * the alternatives must stay visible.
 */
@Composable
private fun CredentialRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val shape = remember { RoundedCornerShape(UiConsts.RowCorner) }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                    shape,
                )
                .clip(shape)
                .combinedClickable(onClick = onClick)
                .padding(horizontal = UiConsts.Space12, vertical = UiConsts.Space9),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.SheetRowTitle,
                lineHeight = UiType.SheetRowTitleLine,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(UiConsts.Space2))
            Text(
                text = subtitle,
                modifier = Modifier.fillMaxWidth(),
                fontSize = UiType.Meta,
                lineHeight = UiType.MetaLine,
                color = colors.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(UiConsts.Space8))
            Icon(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = stringResource(R.string.bedrock_region_selected),
                modifier = Modifier.size(UiConsts.IconRow),
                tint = colors.primary,
            )
        }
    }
}
