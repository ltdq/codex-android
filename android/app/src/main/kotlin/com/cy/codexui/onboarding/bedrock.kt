package com.cy.codexui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cy.codexui.AppEvent
import com.cy.codexui.CodexButton
import com.cy.codexui.CodexDivider
import com.cy.codexui.EmptyState
import com.cy.codexui.R
import com.cy.codexui.SectionCard
import com.cy.codexui.SurfaceBackButton
import com.cy.codexui.SurfaceHeader
import com.cy.codexui.UiConsts
import com.cy.codexui.UiType
import com.cy.codexui.app.FormField
import com.cy.codexui.app.FormSheet
import com.cy.codexui.pressableRow
import com.cy.codexui.protocol.AppServerClient
import com.cy.codexui.protocol.protocol.v2.BedrockAwsProfile
import com.cy.codexui.protocol.protocol.v2.BedrockEnvironmentCredential
import com.cy.codexui.protocol.protocol.v2.BedrockSetupParams
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Signing in through Amazon Bedrock instead of ChatGPT.
 *
 * Mirrors `codex-rs/tui/src/onboarding/bedrock.rs`: the two calls split reading from writing.
 * `account/bedrock/discover` is a read — it lists the AWS profiles and environment credentials the
 * server can see — so it is safe to run on entry and again from the header.
 * `account/bedrock/setup` is the write: it moves the account onto the picked credential, which is
 * why it is a button of its own and why nothing performs it as a side effect of opening the page.
 *
 * A discovered credential may not carry a region, and setup always needs one, so picking such a row
 * opens a form for the region rather than sending a half-filled setup. The picked credential lives
 * in this screen: it only means something to the setup about to be sent, and one held above the
 * screen would outlive the visit that made it.
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
    // The credential a region is being typed for; non-null while the region sheet is open.
    var awaitingRegion by remember { mutableStateOf<BedrockSetupParams?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    // Bumped by the header's refresh. The effect keys on it, so a refresh runs the same code path
    // as the first read instead of a second one that could drift away from it.
    var generation by remember { mutableStateOf(0) }

    fun discover() {
        scope.launch {
            loading = true
            client.bedrockDiscover()
                .onSuccess { response ->
                    profiles = response.profiles
                    environment = response.environmentCredentials
                    failure = null
                    // A credential the server no longer offers must not stay selected: setup would
                    // otherwise send one it has just said it cannot serve.
                    selected = selected?.takeIf { pick ->
                        response.profiles.any { it.name == (pick as? BedrockSetupParams.Profile)?.profile } ||
                            (pick is BedrockSetupParams.Environment)
                    }
                }
                .onFailure { failure = it.message }
            loading = false
        }
    }

    // A discovery entry without a region opens the form instead of selecting; setup always needs one.
    fun pick(credential: BedrockSetupParams) {
        val region = when (credential) {
            is BedrockSetupParams.Profile -> credential.region
            is BedrockSetupParams.Environment -> credential.region
        }
        if (region.isBlank()) awaitingRegion = credential else selected = credential
    }

    LaunchedEffect(generation) { discover() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        SurfaceHeader(
            title = stringResource(R.string.bedrock_screen_title),
            subtitle = stringResource(R.string.bedrock_screen_subtitle),
            leading = { SurfaceBackButton(stringResource(R.string.bedrock_screen_back), onBack) },
            trailing = {
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
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiConsts.ScreenMargin)
                .padding(bottom = UiConsts.PageBottomInset),
            verticalArrangement = Arrangement.spacedBy(UiConsts.SectionGap),
        ) {
            val methods = profiles.size + environment.size
            SectionCard(
                title = stringResource(R.string.bedrock_methods_title),
                icon = MiuixIcons.Layers,
                trailing = if (methods == 0) null else methods.toString(),
            ) {
                when {
                    loading && methods == 0 -> Text(
                        text = stringResource(R.string.bedrock_regions_loading),
                        modifier = Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space8,
                        ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )

                    methods == 0 -> EmptyState(
                        icon = MiuixIcons.Layers,
                        title = stringResource(R.string.bedrock_regions_empty),
                        detail = stringResource(R.string.bedrock_regions_empty_detail),
                    )

                    else -> {
                        profiles.forEachIndexed { index, profile ->
                            if (index > 0) CodexDivider()
                            CredentialRow(
                                title = profile.name,
                                subtitle = profile.region ?: stringResource(R.string.bedrock_region_required),
                                selected = (selected as? BedrockSetupParams.Profile)?.profile == profile.name,
                                onClick = { pick(BedrockSetupParams.Profile(profile.name, profile.region.orEmpty())) },
                            )
                        }
                        environment.forEachIndexed { index, credential ->
                            if (profiles.isNotEmpty() || index > 0) CodexDivider()
                            CredentialRow(
                                title = environmentCredentialLabel(credential.type),
                                subtitle = credential.region ?: stringResource(R.string.bedrock_region_required),
                                selected = selected is BedrockSetupParams.Environment,
                                onClick = { pick(BedrockSetupParams.Environment(credential.region.orEmpty())) },
                            )
                        }
                    }
                }
            }
            if (failure != null) {
                SectionCard(
                    title = stringResource(R.string.bedrock_failed_title),
                    icon = MiuixIcons.Info,
                ) {
                    Text(
                        text = failure.orEmpty(),
                        modifier = Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space8,
                        ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.error,
                    )
                }
            }
            SectionCard(
                title = stringResource(R.string.bedrock_setup_title),
                icon = MiuixIcons.Settings,
                trailing = selected?.let { credentialSummary(it) },
            ) {
                Text(
                    text = stringResource(R.string.bedrock_setup_detail),
                    modifier = Modifier.padding(
                        horizontal = UiConsts.Space4,
                        vertical = UiConsts.Space4,
                    ),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = colors.onSurfaceVariantSummary,
                )
                CodexButton(
                    text = stringResource(R.string.bedrock_setup_action),
                    onClick = { selected?.let { onEvent(AppEvent.BedrockSetup(it)) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UiConsts.Space6),
                    enabled = selected != null,
                )
            }
        }
    }

    // A credential without a region cannot be sent, so the row opens this instead of selecting.
    if (awaitingRegion != null) {
        RegionSheet(
            credential = awaitingRegion!!,
            onDismiss = { awaitingRegion = null },
            onConfirm = { credential, region ->
                selected = when (credential) {
                    is BedrockSetupParams.Profile -> credential.copy(region = region)
                    is BedrockSetupParams.Environment -> credential.copy(region = region)
                }
                awaitingRegion = null
            },
        )
    }
}

/** The one-field form the region-less credentials use. */
@Composable
private fun RegionSheet(
    credential: BedrockSetupParams,
    onDismiss: () -> Unit,
    onConfirm: (BedrockSetupParams, String) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.bedrock_region_form_title),
        subtitle = credentialSummary(credential),
        fields = listOf(
            FormField(
                key = "region",
                label = stringResource(R.string.bedrock_region_form_label),
                placeholder = stringResource(R.string.bedrock_region_form_placeholder),
                required = true,
            ),
        ),
        confirmLabel = stringResource(R.string.bedrock_region_form_confirm),
        onDismiss = onDismiss,
        onSubmit = { values -> onConfirm(credential, values["region"].orEmpty().trim()) },
    )
}

/** The label for a discovered environment credential's type. */
@Composable
private fun environmentCredentialLabel(type: String): String = when (type) {
    "accessKeys" -> stringResource(R.string.bedrock_kind_access_keys)
    "bedrockApiKey" -> stringResource(R.string.bedrock_kind_bedrock_api_key)
    else -> type
}

/** The summary line for a picked credential: its profile or kind, plus the region to use. */
@Composable
private fun credentialSummary(credential: BedrockSetupParams): String = when (credential) {
    is BedrockSetupParams.Profile -> stringResource(R.string.bedrock_summary_profile, credential.profile, credential.region)
    is BedrockSetupParams.Environment -> stringResource(R.string.bedrock_summary_environment, credential.region)
}

/**
 * One discovered credential, as a row that can be picked.
 *
 * A row rather than a dropdown because the list is short and the choice is the whole page: the
 * selected credential is what the button below sends, and a collapsed control would hide the
 * alternatives the user is deciding between. The tick is the same one the model picker uses, so
 * "this is the selected one" reads the same way in both places.
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
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(
                shape = shape,
                container = if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent,
                onClick = onClick,
            )
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
