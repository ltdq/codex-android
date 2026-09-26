package com.cy.codex.chatwidget

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.app.PathSheet
import com.cy.codex.protocol.protocol.v2.MarketplaceEntry
import com.cy.codex.protocol.protocol.v2.PluginShareDiscoverability
import com.cy.codex.protocol.protocol.v2.PluginShareEntry
import com.cy.codex.protocol.protocol.v2.PluginSharePrincipal
import com.cy.codex.protocol.protocol.v2.PluginShareTarget
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Plugin sharing and marketplaces, mirroring codex-rs/tui/src/chatwidget/plugins.rs. Writes leave
 * via [AppEvent]: publishing and marketplace changes outlive this page instance.
 */
@Composable
fun PluginSharesScreen(
    catalog: CatalogState,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val shares = catalog.pluginShares
    val marketplaces = catalog.marketplaces
    val reconciled = catalog.reconciledPlugins
    val upgraded = catalog.upgradedMarketplaces
    var updatingTargets by remember { mutableStateOf<PluginShareEntry?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var addingMarketplace by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.plugin_shares_screen_title),
            summary =
                stringResource(
                    R.string.plugin_shares_screen_subtitle,
                    shares.size,
                    marketplaces.size,
                ),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.plugin_shares_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                // One refresh for both catalogs: a user who suspects one is stale cannot tell which.
                IconButton(
                    onClick = { onEvent(AppEvent.ReloadPluginShares) },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.plugin_shares_screen_refresh),
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
            SharesCard(
                shares = shares,
                onEvent = onEvent,
                onUpdateTargets = { updatingTargets = it },
            )
            PublishCard(onPublish = { publishing = true })
            MarketplacesCard(
                marketplaces = marketplaces,
                onEvent = onEvent,
                onAdd = { addingMarketplace = true },
            )
            ReconcileButton(onEvent = onEvent)
            if (reconciled.isNotEmpty() || upgraded.isNotEmpty()) {
                LastRunCard(
                    reconciledPlugins = reconciled,
                    upgradedMarketplaces = upgraded,
                )
            }
        }
    }

    updatingTargets?.let { share ->
        ShareTargetsSheet(
            share = share,
            onDismiss = { updatingTargets = null },
            onSubmit = { discoverability, targets ->
                onEvent(
                    AppEvent.UpdatePluginShareTargets(
                        remotePluginId = share.remotePluginId.orEmpty(),
                        discoverability = discoverability,
                        targets = targets,
                    )
                )
                updatingTargets = null
            },
        )
    }
    if (publishing) {
        PathSheet(
            title = stringResource(R.string.plugin_shares_publish_title),
            label = stringResource(R.string.plugin_shares_publish_label),
            confirm = stringResource(R.string.plugin_shares_publish_confirm),
            help = stringResource(R.string.plugin_shares_publish_help),
            onDismiss = { publishing = false },
            onSubmit = { path ->
                // Null remote id is the protocol's "create": the server mints the id; this sheet never updates.
                onEvent(AppEvent.SavePluginShare(path, null))
                publishing = false
            },
        )
    }
    if (addingMarketplace) {
        MarketplaceFormSheet(
            onDismiss = { addingMarketplace = false },
            onSubmit = { source, ref ->
                onEvent(AppEvent.AddMarketplace(source, ref))
                addingMarketplace = false
            },
        )
    }
}

/** The account's published shares; delete lives here — a share is not the plugin it was copied from. */
@Composable
private fun SharesCard(
    shares: List<PluginShareEntry>,
    onEvent: (AppEvent) -> Unit,
    onUpdateTargets: (PluginShareEntry) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    Card(
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.plugin_shares_shares_title),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = shares.size.toString(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (shares.isEmpty()) {
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
                        imageVector = MiuixIcons.Link,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.plugin_shares_shares_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.plugin_shares_shares_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        shares.forEachIndexed { index, share ->
            if (index > 0)
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            PluginShareRow(
                share = share,
                expanded = selected == share.remotePluginId,
                onToggle = {
                    selected = if (selected == share.remotePluginId) null else share.remotePluginId
                },
                onUpdateTargets = { onUpdateTargets(share) },
                onCheckout = {
                    share.remotePluginId?.let { onEvent(AppEvent.CheckoutPluginShare(it)) }
                },
                onDelete = {
                    share.remotePluginId?.let { onEvent(AppEvent.DeletePluginShare(it)) }
                },
            )
        }
    }
}

/** Remote id, null until published; the wire carries the sharing context on the plugin summary. */
private val PluginShareEntry.remotePluginId: String?
    get() = plugin.remotePluginId

private val PluginShareEntry.discoverability: PluginShareDiscoverability
    get() = plugin.shareContext?.discoverability ?: PluginShareDiscoverability.Private

private val PluginShareEntry.principals: List<PluginSharePrincipal>
    get() = plugin.shareContext?.sharePrincipals.orEmpty()

/**
 * Parse one target token: `type:id` is the protocol's own addressing; a bare id means a user;
 * `type:id:role` carries an explicit role.
 */
private fun parseShareTarget(token: String): PluginShareTarget? {
    if (token.isEmpty()) return null
    val parts = token.split(':', limit = 3)
    return when (parts.size) {
        1 -> PluginShareTarget(principalType = "user", principalId = parts[0])
        2 -> PluginShareTarget(principalType = parts[0], principalId = parts[1])
        else -> PluginShareTarget(principalType = parts[0], principalId = parts[1], role = parts[2])
    }
}

/** One published share; a share is the published copy, not the plugin — delete removes only the copy. */
@Composable
private fun PluginShareRow(
    share: PluginShareEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdateTargets: () -> Unit,
    onCheckout: () -> Unit,
    onDelete: () -> Unit,
) {
    val discoverability = discoverabilityLabel(share.discoverability)
    val targets = share.principals.joinToString(", ") { it.principalId }
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = share.plugin.name,
            endActions = {
                Text(
                    text = share.remotePluginId.orEmpty(),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    maxLines = 1,
                )
            },
            onClick = onToggle,
        )
        if (expanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.Space4)
                        .padding(bottom = UiConsts.Space8),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6)) {
                    Button(
                        onClick = onUpdateTargets,
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
                            text = stringResource(R.string.plugin_shares_update_targets),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = onCheckout,
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
                            text = stringResource(R.string.plugin_shares_checkout),
                            fontSize = UiType.Action,
                            lineHeight = UiType.ActionLine,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onDelete,
                        modifier =
                            Modifier.squircleBorder(
                                width = UiConsts.OutlineThickness,
                                color = MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                            ),
                        colors =
                            ButtonDefaults.buttonColors(
                                color = Color.Transparent,
                                disabledColor =
                                    MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                                contentColor = MiuixTheme.colorScheme.error,
                                disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                            ),
                        cornerRadius = UiConsts.ButtonHeightCompact / 2,
                        minHeight = UiConsts.ButtonHeightCompact,
                        insideMargin =
                            PaddingValues(
                                horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                vertical = 0.dp,
                            ),
                    ) {
                        Text(
                            text = stringResource(R.string.plugin_shares_delete),
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
    }
}

/** One comma-separated field: updateTargets takes the whole list in a single call. */
@Composable
private fun ShareTargetsSheet(
    share: PluginShareEntry,
    onDismiss: () -> Unit,
    onSubmit: (PluginShareDiscoverability, List<PluginShareTarget>) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.plugin_shares_targets_title),
        subtitle = share.plugin.name,
        fields =
            listOf(
                FormField(
                    key = "targets",
                    label = stringResource(R.string.plugin_shares_targets_label),
                    placeholder = stringResource(R.string.plugin_shares_targets_placeholder),
                    initial =
                        share.principals.joinToString(", ") {
                            "${it.principalType}:${it.principalId}"
                        },
                    // An empty target list is a legal (private) share; clearing the field narrows back to nobody.
                    required = false,
                    help = stringResource(R.string.plugin_shares_targets_help),
                )
            ),
        confirmLabel = stringResource(R.string.plugin_shares_targets_confirm),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                share.discoverability,
                values["targets"].orEmpty().split(',').mapNotNull { parseShareTarget(it.trim()) },
            )
        },
    )
}

/** Path-only form: no targets/discoverability means private — the safe default for copying off the machine. */
@Composable
private fun PublishCard(onPublish: () -> Unit) {
    Card(
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.plugin_shares_publish_title),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.UploadCloud,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
        )

        ArrowPreference(
            title = stringResource(R.string.plugin_shares_publish_row),
            summary = stringResource(R.string.plugin_shares_publish_row_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.UploadCloud,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onPublish,
        )
    }
}

@Composable
internal fun MarketplacesCard(
    marketplaces: List<MarketplaceEntry>,
    onEvent: (AppEvent) -> Unit,
    onAdd: () -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }

    Card(
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.plugin_shares_marketplaces_title),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Store,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = marketplaces.size.toString(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        ArrowPreference(
            title = stringResource(R.string.plugin_shares_marketplace_add),
            summary = stringResource(R.string.plugin_shares_marketplace_add_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onAdd,
        )
        if (marketplaces.isEmpty()) {
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
                        imageVector = MiuixIcons.Store,
                        contentDescription = null,
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Spacer(Modifier.height(UiConsts.Space12))
                Text(
                    text = stringResource(R.string.plugin_shares_marketplaces_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.plugin_shares_marketplaces_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            marketplaces.forEachIndexed { index, marketplace ->
                if (index > 0)
                    HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                MarketplaceRow(
                    marketplace = marketplace,
                    expanded = selected == marketplace.name,
                    onToggle = {
                        selected = if (selected == marketplace.name) null else marketplace.name
                    },
                    onRemove = { onEvent(AppEvent.RemoveMarketplace(marketplace.name)) },
                )
            }
            Spacer(Modifier.height(UiConsts.Space8))
            UpgradeAllButton(onEvent = onEvent)
        }
    }
}

@Composable
private fun MarketplaceRow(
    marketplace: MarketplaceEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    val source = marketplace.path.ifBlank { marketplace.description }.takeIf { it.isNotBlank() }
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = marketplace.name,
            summary = source,
            onClick = onToggle,
        )
        if (expanded) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.Space4)
                        .padding(bottom = UiConsts.Space8)
            ) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onRemove,
                    modifier =
                        Modifier.squircleBorder(
                            width = UiConsts.OutlineThickness,
                            color = MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                            cornerRadius = UiConsts.ButtonHeightCompact / 2,
                        ),
                    colors =
                        ButtonDefaults.buttonColors(
                            color = Color.Transparent,
                            disabledColor =
                                MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                            contentColor = MiuixTheme.colorScheme.error,
                            disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                        ),
                    cornerRadius = UiConsts.ButtonHeightCompact / 2,
                    minHeight = UiConsts.ButtonHeightCompact,
                    insideMargin =
                        PaddingValues(
                            horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                            vertical = 0.dp,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.plugin_shares_marketplace_remove),
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
}

/** Adding a marketplace by source; a blank ref becomes `null` — the protocol's "not specified". */
@Composable
internal fun MarketplaceFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (source: String, ref: String?) -> Unit,
) {
    FormSheet(
        title = stringResource(R.string.plugin_shares_marketplace_add),
        fields =
            listOf(
                FormField(
                    key = "source",
                    label = stringResource(R.string.plugin_shares_marketplace_source),
                    placeholder =
                        stringResource(R.string.plugin_shares_marketplace_source_placeholder),
                    keyboardType = KeyboardType.Uri,
                ),
                FormField(
                    key = "ref",
                    label = stringResource(R.string.plugin_shares_marketplace_ref),
                    placeholder =
                        stringResource(R.string.plugin_shares_marketplace_ref_placeholder),
                    required = false,
                    help = stringResource(R.string.plugin_shares_marketplace_ref_help),
                ),
            ),
        confirmLabel = stringResource(R.string.plugin_shares_marketplace_add_confirm),
        onDismiss = onDismiss,
        onSubmit = { values ->
            onSubmit(
                values["source"].orEmpty().trim(),
                values["ref"].orEmpty().trim().ifEmpty { null },
            )
        },
    )
}

/** `null` means *all* marketplaces — the protocol's spelling of "upgrade everything". */
@Composable
private fun UpgradeAllButton(onEvent: (AppEvent) -> Unit) {
    Button(
        onClick = { onEvent(AppEvent.UpgradeMarketplace(null)) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(),
        cornerRadius = UiConsts.ButtonHeight / 2,
        minHeight = UiConsts.ButtonHeight,
        insideMargin =
            PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
    ) {
        Text(
            text = stringResource(R.string.plugin_shares_upgrade_all),
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** plugin/reconcile re-resolves every installed plugin; the run is reported back so a shrinking catalog is not read as a small one. */
@Composable
private fun ReconcileButton(onEvent: (AppEvent) -> Unit) {
    Button(
        onClick = { onEvent(AppEvent.ReconcilePlugins) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(),
        cornerRadius = UiConsts.ButtonHeight / 2,
        minHeight = UiConsts.ButtonHeight,
        insideMargin =
            PaddingValues(horizontal = UiConsts.ButtonPaddingHorizontal, vertical = 0.dp),
    ) {
        Text(
            text = stringResource(R.string.plugin_shares_reconcile),
            fontSize = UiType.Action,
            lineHeight = UiType.ActionLine,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The only proof a reconcile/upgrade did anything; no notification follows either call. */
@Composable
private fun LastRunCard(
    reconciledPlugins: List<String>,
    upgradedMarketplaces: List<String>,
) {
    Card(
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
    ) {
        BasicComponent(
            title = stringResource(R.string.plugin_shares_last_run_title),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = (reconciledPlugins.size + upgradedMarketplaces.size).toString(),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (reconciledPlugins.isNotEmpty()) {
            BasicComponent(
                title = stringResource(R.string.plugin_shares_last_run_reconciled),
                endActions = {
                    Text(
                        text = reconciledPlugins.joinToString(", ").ifEmpty { "—" },
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        fontSize = UiType.Detail,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
        }
        if (reconciledPlugins.isNotEmpty() && upgradedMarketplaces.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        }
        if (upgradedMarketplaces.isNotEmpty()) {
            BasicComponent(
                title = stringResource(R.string.plugin_shares_last_run_upgraded),
                endActions = {
                    Text(
                        text = upgradedMarketplaces.joinToString(", ").ifEmpty { "—" },
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        fontSize = UiType.Detail,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
        }
    }
}

@Composable
private fun discoverabilityLabel(discoverability: PluginShareDiscoverability): String =
    stringResource(
        when (discoverability) {
            PluginShareDiscoverability.Private -> R.string.plugin_shares_discoverability_private
            PluginShareDiscoverability.Unlisted -> R.string.plugin_shares_discoverability_unlisted
            PluginShareDiscoverability.Listed -> R.string.plugin_shares_discoverability_listed
        }
    )
