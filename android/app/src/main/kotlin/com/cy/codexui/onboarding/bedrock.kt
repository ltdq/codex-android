package com.cy.codexui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cy.codexui.pressableRow
import com.cy.codexui.protocol.AppServerClient
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
 * Mirrors `codex-rs/tui/src/onboarding/bedrock.rs`: the account's model provider can be Bedrock,
 * and the region is the one thing that decision needs from the user.
 *
 * The page is two steps because the two halves cost different things. `account/bedrock/discover` is
 * a read: it asks which regions the server can serve and changes nothing, so it is safe to run on
 * entry and safe to run again from the header. `account/bedrock/setup` is the write: it moves the
 * account off ChatGPT and onto Bedrock, which is why it is a button of its own, why it stays
 * disabled until a region is picked, and why nothing else on the page performs it as a side effect
 * of the page being opened.
 *
 * The picked region lives in this screen rather than in `CatalogState`, which has no bedrock field
 * to put it in. A choice only means something to the setup that is about to be sent, and one held
 * above the screen would outlive the visit that made it.
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
    var regions by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
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
                    regions = response.regions
                    failure = null
                    // A region the server no longer offers must not stay selected: the setup call
                    // would otherwise send one it has just said it cannot serve.
                    if (response.regions.none { it == selected }) selected = null
                }
                .onFailure { failure = it.message }
            loading = false
        }
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
            SectionCard(
                title = stringResource(R.string.bedrock_regions_title),
                icon = MiuixIcons.Layers,
                trailing = if (regions.isEmpty()) null else regions.size.toString(),
            ) {
                when {
                    loading && regions.isEmpty() -> Text(
                        text = stringResource(R.string.bedrock_regions_loading),
                        modifier = Modifier.padding(
                            horizontal = UiConsts.Space4,
                            vertical = UiConsts.Space8,
                        ),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = colors.onSurfaceVariantSummary,
                    )

                    regions.isEmpty() -> EmptyState(
                        icon = MiuixIcons.Layers,
                        title = stringResource(R.string.bedrock_regions_empty),
                        detail = stringResource(R.string.bedrock_regions_empty_detail),
                    )

                    else -> regions.forEachIndexed { index, region ->
                        if (index > 0) CodexDivider()
                        RegionRow(
                            region = region,
                            selected = region == selected,
                            onClick = { selected = region },
                        )
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
                trailing = selected,
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
}

/**
 * One region, as a row that can be picked.
 *
 * A row rather than a dropdown because the list is short and the choice is the whole page: the
 * selected region is what the button below sends, and a collapsed control would hide the
 * alternatives the user is deciding between. The tick is the same one the model picker uses, so
 * "this is the selected one" reads the same way in both places.
 */
@Composable
private fun RegionRow(
    region: String,
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
        Text(
            text = region,
            modifier = Modifier.weight(1f),
            fontSize = UiType.SheetRowTitle,
            lineHeight = UiType.SheetRowTitleLine,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
