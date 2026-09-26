package com.cy.codex.bottom_pane

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.app.FormField
import com.cy.codex.app.FormSheet
import com.cy.codex.codeSurface
import com.cy.codex.fileName
import com.cy.codex.parentPath
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.FileMetadata
import com.cy.codex.raisedSurface
import com.cy.codex.sheetColor
import com.cy.codex.sheetSideMargin
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * The `fs/…` family as a page (codex-rs/tui/src/bottom_pane/file_search_popup.rs,
 * app_server_session/fs.rs). [picking] hides every write — a chooser that can delete will
 * eventually delete the wrong folder.
 */
@Composable
fun FileBrowserScreen(
    path: String,
    picking: Boolean,
    client: AppServerClient,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // The walk lives here; a pushed page per folder would make back mean two things one tap apart.
    var current by remember(path) { mutableStateOf(path) }
    var entries by remember { mutableStateOf<List<FileMetadata>>(emptyList()) }
    var reading by remember { mutableStateOf(true) }
    var readFailure by remember { mutableStateOf<String?>(null) }
    var writeFailure by remember { mutableStateOf<String?>(null) }
    var previewPath by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<FilePreview?>(null) }
    // `fs/getMetadata` answers for paths this client may not be allowed to open.
    var metadata by remember { mutableStateOf<FileMetadata?>(null) }
    var watched by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sheet by remember { mutableStateOf<FileSheet?>(null) }
    // Bumped after every successful write to re-run the read below without moving [current].
    var revision by remember { mutableStateOf(0) }

    val readFailed = stringResource(R.string.file_browser_read_failed)
    val writeFailed = stringResource(R.string.file_browser_action_failed)

    LaunchedEffect(current, revision) {
        reading = true
        readFailure = null
        // A re-read may have removed the file; drop the stale preview.
        previewPath = null
        client
            .readDirectory(current)
            .onSuccess { entries = it }
            .onFailure {
                entries = emptyList()
                readFailure = it.message ?: readFailed
            }
        reading = false
    }

    LaunchedEffect(previewPath) {
        val target = previewPath
        preview = null
        metadata = null
        if (target != null) {
            client.getMetadata(target).onSuccess { metadata = it }
        }
        if (target == null) return@LaunchedEffect
        client
            .readFile(target)
            .onSuccess { preview = fileBrowserPreview(it) }
            .onFailure {
                preview = FilePreview(bytes = 0L, text = null, failure = it.message ?: readFailed)
            }
    }

    val rows =
        remember(entries) {
            entries.sortedWith(
                compareBy<FileMetadata>({ !it.isDirectory }, { it.name.lowercase() })
            )
        }

    // Every write re-reads here, so a refusal is reported and the listing refreshed in one place.
    fun mutate(action: suspend () -> Result<Unit>, onSuccess: () -> Unit = {}) {
        scope.launch {
            action()
                .onSuccess {
                    writeFailure = null
                    onSuccess()
                    revision++
                }
                .onFailure { writeFailure = it.message ?: writeFailed }
        }
    }

    // `watchId` is the path itself: `fs/unwatch` only echoes the id, and a recomputable id is the only kind that survives recomposition.
    fun toggleWatch(target: String) {
        val registered = target in watched
        scope.launch {
            val result =
                if (registered) {
                    client.unwatchPath(target)
                } else {
                    client.watchPath(target, target)
                }
            result
                .onSuccess { watched = if (registered) watched - target else watched + target }
                .onFailure { writeFailure = it.message ?: writeFailed }
        }
    }

    val back: () -> Unit = {
        if (current == path) {
            onBack()
        } else {
            current = fileBrowserParent(current)
        }
    }
    BackHandler(enabled = current != path) { current = fileBrowserParent(current) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = fileBrowserName(current),
            summary = parentPath(current).ifEmpty { null },
            startAction = {
                IconButton(
                    onClick = back,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.file_browser_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { revision++ },
                        minWidth = UiConsts.IconButtonSize,
                        minHeight = UiConsts.IconButtonSize,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.file_browser_refresh),
                            modifier = Modifier.size(UiConsts.IconRefresh),
                            tint = colors.primary,
                        )
                    }
                    if (picking) {
                        Spacer(Modifier.width(UiConsts.Space6))
                        Button(
                            onClick = { onPick(current) },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            cornerRadius = UiConsts.ButtonHeightCompact / 2,
                            minWidth = 0.dp,
                            minHeight = UiConsts.ButtonHeightCompact,
                            insideMargin =
                                PaddingValues(
                                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                                    vertical = 0.dp,
                                ),
                        ) {
                            Text(
                                text = stringResource(R.string.file_browser_use_directory),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
            if (writeFailure != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = UiConsts.SectionCorner,
                    insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                    colors =
                        CardDefaults.defaultColors(
                            color = raisedSurface(),
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.file_browser_action_failed_title),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(0.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    FileBrowserNote(writeFailure.orEmpty(), error = true)
                }
            }
            FileListingCard(
                rows = rows,
                reading = reading,
                failure = readFailure,
                onOpen = { entry ->
                    if (entry.isDirectory) current = entry.path else previewPath = entry.path
                },
            )
            previewPath?.let { open ->
                FilePreviewCard(
                    path = open,
                    preview = preview,
                    metadata = metadata,
                    picking = picking,
                    watched = open in watched,
                    onToggleWatch = { toggleWatch(open) },
                    onRename = { sheet = FileSheet.Rename(open, isDirectory = false) },
                    onCopy = { sheet = FileSheet.Copy(open, isDirectory = false) },
                    onDelete = { sheet = FileSheet.Delete(open, isDirectory = false) },
                )
            }
            if (!picking) {
                FolderActionsCard(
                    watched = current in watched,
                    onNewFolder = { sheet = FileSheet.NewFolder(current) },
                    onNewFile = { sheet = FileSheet.NewFile(current) },
                    onToggleWatch = { toggleWatch(current) },
                    onRename = { sheet = FileSheet.Rename(current, isDirectory = true) },
                    onCopy = { sheet = FileSheet.Copy(current, isDirectory = true) },
                    onDelete = { sheet = FileSheet.Delete(current, isDirectory = true) },
                )
            }
        }
    }

    when (val open = sheet) {
        null -> Unit

        is FileSheet.NewFolder ->
            FormSheet(
                title = stringResource(R.string.file_browser_new_folder),
                subtitle = open.directory,
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            placeholder = stringResource(R.string.file_browser_name_placeholder),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_create),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    sheet = null
                    val destination = fileBrowserJoin(open.directory, name)
                    mutate({ client.createDirectory(destination, recursive = true) })
                },
            )

        is FileSheet.NewFile ->
            FormSheet(
                title = stringResource(R.string.file_browser_new_file),
                subtitle = open.directory,
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            placeholder = stringResource(R.string.file_browser_name_placeholder),
                        ),
                        FormField(
                            key = ContentField,
                            label = stringResource(R.string.file_browser_content),
                            required = false,
                            help = stringResource(R.string.file_browser_content_help),
                        ),
                    ),
                confirmLabel = stringResource(R.string.file_browser_create),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    val bytes = values[ContentField].orEmpty().toByteArray(Charsets.UTF_8)
                    sheet = null
                    mutate({ client.writeFile(fileBrowserJoin(open.directory, name), bytes) })
                },
            )

        is FileSheet.Rename ->
            FormSheet(
                title =
                    stringResource(R.string.file_browser_rename_title, fileBrowserName(open.path)),
                fields =
                    listOf(
                        FormField(
                            key = NameField,
                            label = stringResource(R.string.file_browser_name),
                            initial = fileBrowserName(open.path),
                            help = stringResource(R.string.file_browser_rename_help),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_rename),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val name = values[NameField].orEmpty().trim()
                    val destination = fileBrowserJoin(fileBrowserParent(open.path), name)
                    sheet = null
                    mutate({
                        client.copyPath(open.path, destination, recursive = open.isDirectory)
                    })
                },
            )

        is FileSheet.Copy ->
            FormSheet(
                title =
                    stringResource(R.string.file_browser_copy_title, fileBrowserName(open.path)),
                fields =
                    listOf(
                        FormField(
                            key = DestinationField,
                            label = stringResource(R.string.file_browser_copy_destination),
                            initial =
                                open.path +
                                    stringResource(R.string.file_browser_copy_default_suffix),
                            help = stringResource(R.string.file_browser_copy_help),
                        )
                    ),
                confirmLabel = stringResource(R.string.file_browser_copy),
                onDismiss = { sheet = null },
                onSubmit = { values ->
                    val destination = values[DestinationField].orEmpty().trim()
                    sheet = null
                    mutate({
                        client.copyPath(open.path, destination, recursive = open.isDirectory)
                    })
                },
            )

        is FileSheet.Delete ->
            WindowBottomSheet(
                show = true,
                onDismissRequest = { sheet = null },
                onDismissFinished = { sheet = null },
                title =
                    stringResource(R.string.file_browser_delete_title, fileBrowserName(open.path)),
                backgroundColor = sheetColor(),
                cornerRadius = UiConsts.SheetCorner,
                sheetMaxWidth = UiConsts.SheetMaxWidth,
                outsideMargin = DpSize(sheetSideMargin(), 0.dp),
                insideMargin = DpSize(UiConsts.SheetPadding, 0.dp),
                dragHandleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(
                                max =
                                    LocalWindowInfo.current.containerDpSize.height *
                                        UiConsts.SheetHeightFraction
                            )
                ) {
                    Text(
                        text = stringResource(R.string.file_browser_delete_detail),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = UiConsts.SheetPadding),
                        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                    ) {
                        Text(
                            text = open.path,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(UiConsts.RowCorner))
                                    .background(codeSurface())
                                    .padding(
                                        horizontal = UiConsts.Space8,
                                        vertical = UiConsts.Space7,
                                    ),
                            fontSize = UiType.Code,
                            lineHeight = UiType.CodeLine,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(UiConsts.Space8),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = { sheet = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(),
                                cornerRadius = UiConsts.ButtonHeight / 2,
                                minWidth = 0.dp,
                                minHeight = UiConsts.ButtonHeight,
                                insideMargin =
                                    PaddingValues(
                                        horizontal = UiConsts.ButtonPaddingHorizontal,
                                        vertical = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.file_browser_cancel),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Button(
                                onClick = {
                                    // Read out now so the write hits the path the user confirmed.
                                    val target = open.path
                                    val recursive = open.isDirectory
                                    sheet = null
                                    mutate(
                                        action = {
                                            client.removePath(target, recursive = recursive)
                                        },
                                        onSuccess = {
                                            if (target == current)
                                                current = fileBrowserParent(target)
                                        },
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                                        .squircleBorder(
                                            UiConsts.OutlineThickness,
                                            MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                                            UiConsts.ButtonHeight / 2,
                                        ),
                                colors =
                                    ButtonColors(
                                        color = Color.Transparent,
                                        disabledColor =
                                            MiuixTheme.colorScheme.disabledOnSurface.copy(
                                                alpha = 0.1f
                                            ),
                                        contentColor = MiuixTheme.colorScheme.error,
                                        disabledContentColor =
                                            MiuixTheme.colorScheme.disabledOnSurface,
                                    ),
                                cornerRadius = UiConsts.ButtonHeight / 2,
                                minWidth = 0.dp,
                                minHeight = UiConsts.ButtonHeight,
                                insideMargin =
                                    PaddingValues(
                                        horizontal = UiConsts.ButtonPaddingHorizontal,
                                        vertical = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.file_browser_delete),
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
}

/** Directory listing; a failure is drawn where an empty folder would be, because `fs/readDirectory` answers both with the same empty list. */
@Composable
private fun FileListingCard(
    rows: List<FileMetadata>,
    reading: Boolean,
    failure: String?,
    onOpen: (FileMetadata) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.file_browser_contents),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.FolderFill,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                (if (failure == null) rows.size.toString() else null)?.let {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        when {
            failure != null ->
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
                        text = stringResource(R.string.file_browser_read_failed),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = failure,
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }

            rows.isEmpty() && reading ->
                FileBrowserNote(stringResource(R.string.file_browser_reading))

            rows.isEmpty() ->
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
                            imageVector = MiuixIcons.FolderFill,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.file_browser_empty),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(UiConsts.Space4))
                    Text(
                        text = stringResource(R.string.file_browser_empty_detail),
                        fontSize = UiType.Meta,
                        lineHeight = UiType.MetaLine,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }

            else ->
                rows.forEach { entry ->
                    ArrowPreference(
                        title = entry.name,
                        summary =
                            if (entry.isDirectory) {
                                stringResource(R.string.file_browser_folder)
                            } else {
                                fileBrowserSize(entry.size)
                            },
                        startAction = {
                            Icon(
                                imageVector =
                                    if (entry.isDirectory) MiuixIcons.FolderFill
                                    else MiuixIcons.ConvertFile,
                                contentDescription = null,
                                modifier = Modifier.size(UiConsts.IconPreference),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = { onOpen(entry) },
                    )
                }
        }
    }
}

/** One open file, on a bounded code surface so a long file cannot push the actions off the page. */
@Composable
private fun FilePreviewCard(
    path: String,
    preview: FilePreview?,
    metadata: FileMetadata?,
    picking: Boolean,
    watched: Boolean,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val body = preview
    val text = body?.text
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = fileBrowserName(path),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                (body?.let { fileBrowserSize(it.bytes) })?.let {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (metadata != null) {
            BasicComponent(
                title = stringResource(R.string.file_browser_meta_kind),
                endActions = {
                    Text(
                        text =
                            (stringResource(
                                    if (metadata.isDirectory) {
                                        R.string.file_browser_meta_directory
                                    } else {
                                        R.string.file_browser_meta_file
                                    }
                                ))
                                .ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            BasicComponent(
                title = stringResource(R.string.file_browser_meta_size),
                endActions = {
                    Text(
                        text = (fileBrowserSize(metadata.size)).ifEmpty { "—" },
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = UiType.Detail,
                        lineHeight = UiType.DetailLine,
                        fontFamily = null,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                },
                insideMargin =
                    PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
            )
            if (metadata.modifiedAt > 0L) {
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.file_browser_meta_modified),
                    endActions = {
                        Text(
                            text =
                                (android.text.format.DateUtils.getRelativeTimeSpanString(
                                            metadata.modifiedAt
                                        )
                                        .toString())
                                    .ifEmpty { "—" },
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = UiType.Detail,
                            lineHeight = UiType.DetailLine,
                            fontFamily = null,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    },
                    insideMargin =
                        PaddingValues(horizontal = UiConsts.Space4, vertical = UiConsts.Space7),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        }
        when {
            body == null -> FileBrowserNote(stringResource(R.string.file_browser_preview_reading))

            body.failure != null -> FileBrowserNote(body.failure.orEmpty(), error = true)

            text == null ->
                FileBrowserNote(
                    stringResource(
                        R.string.file_browser_preview_binary,
                        fileBrowserSize(body.bytes),
                    )
                )

            text.isEmpty() -> FileBrowserNote(stringResource(R.string.file_browser_preview_empty))

            else -> {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = PreviewMaxHeight)
                            .clip(RoundedCornerShape(UiConsts.RowCorner))
                            .background(codeSurface())
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = UiConsts.Space8, vertical = UiConsts.Space7)
                ) {
                    Text(
                        text = text,
                        fontSize = UiType.Code,
                        lineHeight = UiType.CodeLine,
                        fontFamily = FontFamily.Monospace,
                        color = colors.onSurface,
                    )
                }
                if (body.clipped) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    FileBrowserNote(
                        stringResource(R.string.file_browser_preview_clipped, PreviewCharLimit)
                    )
                }
            }
        }
        if (!picking && body != null && body.failure == null) {
            Spacer(Modifier.height(UiConsts.Space8))
            EntryActions(
                watched = watched,
                onToggleWatch = onToggleWatch,
                onRename = onRename,
                onCopy = onCopy,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun FolderActionsCard(
    watched: Boolean,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = UiConsts.SectionCorner,
        insideMargin = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
        colors =
            CardDefaults.defaultColors(
                color = raisedSurface(),
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
    ) {
        BasicComponent(
            title = stringResource(R.string.file_browser_folder_actions),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            insideMargin = PaddingValues(0.dp),
        )
        Spacer(Modifier.height(8.dp))
        ArrowPreference(
            title = stringResource(R.string.file_browser_new_folder),
            summary = stringResource(R.string.file_browser_new_folder_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.AddFolder,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onNewFolder,
        )
        ArrowPreference(
            title = stringResource(R.string.file_browser_new_file),
            summary = stringResource(R.string.file_browser_new_file_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.ConvertFile,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            onClick = onNewFile,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
        EntryActions(
            watched = watched,
            onToggleWatch = onToggleWatch,
            onRename = onRename,
            onCopy = onCopy,
            onDelete = onDelete,
        )
    }
}

/** The four writes one entry has: watch, rename, copy, delete. Watch only registers; this page reads no `fs/changed` stream. */
@Composable
private fun EntryActions(
    watched: Boolean,
    onToggleWatch: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
    ) {
        Button(
            onClick = onToggleWatch,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(),
            cornerRadius = UiConsts.ButtonHeightCompact / 2,
            minWidth = 0.dp,
            minHeight = UiConsts.ButtonHeightCompact,
            insideMargin =
                PaddingValues(
                    horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                    vertical = 0.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        if (watched) R.string.file_browser_watch_stop
                        else R.string.file_browser_watch
                    ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onRename,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_rename),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onCopy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_copy),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onDelete,
                modifier =
                    Modifier.weight(1f)
                        .squircleBorder(
                            UiConsts.OutlineThickness,
                            MiuixTheme.colorScheme.error.copy(alpha = 0.5f),
                            UiConsts.ButtonHeightCompact / 2,
                        ),
                colors =
                    ButtonColors(
                        color = Color.Transparent,
                        disabledColor = MiuixTheme.colorScheme.disabledOnSurface.copy(alpha = 0.1f),
                        contentColor = MiuixTheme.colorScheme.error,
                        disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
                    ),
                cornerRadius = UiConsts.ButtonHeightCompact / 2,
                minWidth = 0.dp,
                minHeight = UiConsts.ButtonHeightCompact,
                insideMargin =
                    PaddingValues(
                        horizontal = UiConsts.ButtonPaddingHorizontalCompact,
                        vertical = 0.dp,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_delete),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FileBrowserNote(text: String, error: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = UiConsts.Space4, vertical = UiConsts.Space8),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color = if (error) colors.error else colors.onSurfaceVariantSummary,
    )
}

/** A byte count in the unit a person reads, one decimal above a kilobyte. */
@Composable
private fun fileBrowserSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> stringResource(R.string.file_browser_size_mb, bytes / 1_048_576f)
        bytes >= 1_024 -> stringResource(R.string.file_browser_size_kb, bytes / 1_024f)
        else -> stringResource(R.string.file_browser_size_bytes, bytes)
    }

/** One file decoded far enough to show; the cap keeps a 40 MB log from becoming 40 MB of text in one composition. */
private data class FilePreview(
    val bytes: Long,
    val text: String?,
    val failure: String? = null,
    val clipped: Boolean = false,
)

/** The form the page has open, if any; one sealed type makes the compiler say when a seventh write is missing. */
private sealed interface FileSheet {
    /** Create a directory inside [directory]. `recursive` is always true, so one name is enough. */
    data class NewFolder(val directory: String) : FileSheet

    data class NewFile(val directory: String) : FileSheet

    /** Rename [path] in its own directory; there is no move, so this is `fs/copy` and the original stays, which the form says out loud. */
    data class Rename(val path: String, val isDirectory: Boolean) : FileSheet

    data class Copy(val path: String, val isDirectory: Boolean) : FileSheet

    /** Delete [path] once the user has confirmed, because `fs/remove` cannot be undone. */
    data class Delete(val path: String, val isDirectory: Boolean) : FileSheet
}

private const val PreviewCharLimit = 4000

/** Bytes scanned for a NUL before a file is called binary, the way the `file` tool decides. */
private const val PreviewByteScan = 512

/** Ceiling on the preview box, so one long file cannot push every other card off the page. */
private val PreviewMaxHeight = 320.dp

private const val NameField = "name"

private const val ContentField = "content"

private const val DestinationField = "destination"

/** Decode [bytes] for the preview; a NUL in the first [PreviewByteScan] bytes decides binary, not UTF-8 validity. */
private fun fileBrowserPreview(bytes: ByteArray): FilePreview {
    val binary = bytes.take(PreviewByteScan).any { it == 0.toByte() }
    if (binary) return FilePreview(bytes = bytes.size.toLong(), text = null)
    val decoded = bytes.toString(Charsets.UTF_8)
    return FilePreview(
        bytes = bytes.size.toLong(),
        text = decoded.take(PreviewCharLimit),
        clipped = decoded.length > PreviewCharLimit,
    )
}

private fun fileBrowserJoin(directory: String, name: String): String =
    directory.trimEnd('/') + "/" + name.trimStart('/')

private fun fileBrowserParent(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    return trimmed.substringBeforeLast('/', "").ifEmpty { "/" }
}

private fun fileBrowserName(path: String): String = fileName(path).ifEmpty { path }
