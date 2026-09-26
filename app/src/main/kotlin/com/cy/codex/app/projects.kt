package com.cy.codex.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cy.codex.AppEvent
import com.cy.codex.CatalogState
import com.cy.codex.R
import com.cy.codex.ThreadStatusTone
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.label
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.protocol.protocol.v2.EnvironmentInfoResponse
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusKind
import com.cy.codex.protocol.protocol.v2.EnvironmentStatusResponse
import com.cy.codex.protocol.protocol.v2.ProjectEntry
import com.cy.codex.raisedSurface
import com.cy.codex.statusDotColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Saved projects and environments; neither has a TUI surface, so this page is the
 * protocol's own surface for both.
 */
@Composable
fun ProjectsScreen(
    catalog: CatalogState,
    client: AppServerClient,
    onEvent: (AppEvent) -> Unit,
    onBack: () -> Unit,
    onOpenEnvironment: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    // Re-read by id: project/list answers whole-list, too heavy for one row's count.
    var rechecked by remember { mutableStateOf<ProjectEntry?>(null) }
    var editing by remember { mutableStateOf<ProjectEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var addingEnvironment by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = stringResource(R.string.projects_screen_title),
            summary = stringResource(R.string.runtime_project_count, catalog.projects.size),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.projects_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
            endActions = {
                IconButton(
                    onClick = { creating = true },
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.projects_screen_new),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = colors.primary,
                    )
                }
            },
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
            ProjectsCard(
                projects = catalog.projects,
                onEvent = onEvent,
                onEdit = { editing = it },
                onImport = { importing = true },
                onOpen = { onOpenProject(it.path) },
                onRecheck = { project ->
                    scope.launch {
                        client.readProject(project.id).onSuccess { rechecked = it }
                    }
                },
            )
            rechecked?.let { fresh ->
                // Only the count can move while the page is open, so the card reports that and nothing else.
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
                        title = stringResource(R.string.projects_screen_rechecked, fresh.name),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.FolderFill,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                    )

                    BasicComponent(
                        title = stringResource(R.string.projects_screen_path_label),
                        endActions = {
                            Text(
                                text = fresh.path.ifEmpty { "—" },
                                fontFamily = FontFamily.Monospace,
                                color = MiuixTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                            )
                        },
                    )
                }
            }
        }
    }

    if (creating) {
        ProjectFormSheet(
            title = stringResource(R.string.projects_screen_new),
            initial = null,
            onDismiss = { creating = false },
            onSubmit = { name, path ->
                onEvent(AppEvent.CreateProject(name, path))
                creating = false
            },
        )
    }
    editing?.let { project ->
        ProjectFormSheet(
            title = stringResource(R.string.projects_screen_edit),
            initial = project,
            onDismiss = { editing = null },
            onSubmit = { name, path ->
                onEvent(AppEvent.UpdateProject(project.id, name = name, path = path))
                editing = null
            },
        )
    }
    if (importing) {
        PathSheet(
            title = stringResource(R.string.projects_screen_import),
            label = stringResource(R.string.projects_screen_path_label),
            confirm = stringResource(R.string.projects_screen_import),
            onDismiss = { importing = false },
            onSubmit = { path ->
                onEvent(AppEvent.ImportProject(path))
                importing = false
            },
        )
    }
    if (addingEnvironment) {
        EnvironmentFormSheet(
            onDismiss = { addingEnvironment = false },
            onSubmit = { id, url ->
                onEvent(AppEvent.AddEnvironment(id, url))
                addingEnvironment = false
            },
        )
    }
}

@Composable
private fun ProjectsCard(
    projects: List<ProjectEntry>,
    onEvent: (AppEvent) -> Unit,
    onEdit: (ProjectEntry) -> Unit,
    onImport: () -> Unit,
    onRecheck: (ProjectEntry) -> Unit,
    onOpen: (ProjectEntry) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    var selected by remember { mutableStateOf<String?>(null) }

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
            title = stringResource(R.string.projects_screen_projects),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.FolderFill,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                Text(
                    text = projects.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (projects.isEmpty()) {
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
                    text = stringResource(R.string.projects_screen_empty),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.projects_screen_empty_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        projects.forEachIndexed { index, project ->
            if (index > 0)
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
            ProjectRow(
                project = project,
                expanded = selected == project.id,
                first = index == 0,
                last = index == projects.lastIndex,
                onToggle = { selected = if (selected == project.id) null else project.id },
                onEdit = { onEdit(project) },
                onMove = { delta -> onEvent(AppEvent.MoveProject(project.id, index + delta)) },
                onDelete = { onEvent(AppEvent.DeleteProject(project.id)) },
                onRecheck = { onRecheck(project) },
                onOpen = { onOpen(project) },
            )
        }
        ArrowPreference(
            title = stringResource(R.string.projects_screen_import),
            summary = stringResource(R.string.projects_screen_import_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Link,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint =
                        if (true) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.disabledOnSurface,
                )
            },
            onClick = onImport,
        )
    }
}

@Composable
private fun ProjectRow(
    project: ProjectEntry,
    expanded: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onRecheck: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = project.name,
            summary = project.path.ifEmpty { null },
            onClick = onToggle,
        )
        if (expanded) {
            FlowRow(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = UiConsts.Space4,
                            end = UiConsts.Space4,
                            bottom = UiConsts.Space8,
                        ),
                horizontalArrangement = Arrangement.spacedBy(UiConsts.Space6),
                verticalArrangement = Arrangement.spacedBy(UiConsts.Space6),
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier,
                    enabled = true,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(text = stringResource(R.string.runtime_new_thread), maxLines = 1)
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier,
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.projects_screen_edit), maxLines = 1)
                }
                Button(
                    onClick = { onMove(-1) },
                    modifier = Modifier,
                    enabled = !first,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.projects_screen_move_up), maxLines = 1)
                }
                Button(
                    onClick = { onMove(1) },
                    modifier = Modifier,
                    enabled = !last,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.projects_screen_move_down), maxLines = 1)
                }
                Button(
                    onClick = onRecheck,
                    modifier = Modifier,
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(text = stringResource(R.string.projects_screen_recheck), maxLines = 1)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier,
                    enabled = true,
                    colors =
                        ButtonDefaults.buttonColors(
                            color = Color.Transparent,
                            contentColor = MiuixTheme.colorScheme.error,
                        ),
                ) {
                    Text(text = stringResource(R.string.projects_screen_delete), maxLines = 1)
                }
            }
        }
    }
    if (!expanded) Spacer(Modifier.height(0.dp))
}

// `environment/info` and `environment/status` are addressed by id; no call enumerates them.
@Composable
private fun EnvironmentsCard(
    environments: List<String>,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
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
            title = stringResource(R.string.projects_screen_environments),
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
                    text = environments.size.toString(),
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            },
        )

        if (environments.isEmpty()) {
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
                    text = stringResource(R.string.projects_screen_no_environments),
                    fontSize = UiType.RowTitle,
                    lineHeight = UiType.RowTitleLine,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                Text(
                    text = stringResource(R.string.projects_screen_no_environments_detail),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        environments.forEach { id ->
            ArrowPreference(
                title = id,
                summary = stringResource(R.string.projects_screen_environment_detail),
                onClick = { onOpen(id) },
            )
        }
        ArrowPreference(
            title = stringResource(R.string.projects_screen_add_environment),
            summary = stringResource(R.string.projects_screen_add_environment_detail),
            startAction = {
                Icon(
                    imageVector = MiuixIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(UiConsts.IconPreference),
                    tint =
                        if (true) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.disabledOnSurface,
                )
            },
            onClick = onAdd,
        )
    }
}

// Two reads with different costs; both run and report side by side.
@Composable
fun EnvironmentDetailScreen(
    environmentId: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<EnvironmentInfoResponse?>(null) }
    var status by remember { mutableStateOf<EnvironmentStatusResponse?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }

    fun read() {
        scope.launch {
            client
                .readEnvironmentInfo(environmentId)
                .onSuccess { info = it }
                .onFailure { failed = it.message }
            client
                .readEnvironmentStatus(environmentId)
                .onSuccess {
                    status = it
                    failed = null
                }
                .onFailure { failed = it.message }
        }
    }

    LaunchedEffect(environmentId) { read() }

    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        BasicComponent(
            title = environmentId,
            summary =
                status?.status?.label() ?: stringResource(R.string.environment_detail_loading),
            startAction = {
                IconButton(
                    onClick = onBack,
                    minWidth = UiConsts.IconButtonSize,
                    minHeight = UiConsts.IconButtonSize,
                ) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = stringResource(R.string.projects_screen_back),
                        modifier = Modifier.size(UiConsts.IconHeader),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            },
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
                    title = stringResource(R.string.environment_detail_state),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Link,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                val kind = status?.status
                BasicComponent(
                    title = stringResource(R.string.environment_detail_status),
                    endActions = {
                        Text(
                            text =
                                kind?.label()
                                    ?: stringResource(R.string.environment_detail_unknown).ifEmpty {
                                        "—"
                                    },
                            color =
                                kind?.let { statusDotColor(it.tone()) }
                                    ?: MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.environment_detail_error),
                    endActions = {
                        Text(
                            text = status?.error.orEmpty().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }
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
                    title = stringResource(R.string.environment_detail_shell),
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.FolderFill,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    },
                )

                BasicComponent(
                    title = stringResource(R.string.environment_detail_shell_name),
                    endActions = {
                        Text(
                            text = info?.shell?.name.orEmpty().ifEmpty { "—" },
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.environment_detail_shell_path),
                    endActions = {
                        Text(
                            text = info?.shell?.path.orEmpty().ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = UiConsts.Space1))
                BasicComponent(
                    title = stringResource(R.string.environment_detail_cwd),
                    endActions = {
                        Text(
                            text = info?.cwd.orEmpty().ifEmpty { "—" },
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    },
                )
            }
            if (failed != null) {
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
                        title = stringResource(R.string.environment_detail_unreachable),
                        startAction = {
                            Icon(
                                imageVector = MiuixIcons.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                    )

                    Text(
                        text = failed.orEmpty(),
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
            Button(
                onClick = { read() },
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text(text = stringResource(R.string.environment_detail_recheck), maxLines = 1)
            }
        }
    }
}

private fun EnvironmentStatusKind.tone(): ThreadStatusTone =
    when (this) {
        EnvironmentStatusKind.Ready -> ThreadStatusTone.Done
        EnvironmentStatusKind.Pending -> ThreadStatusTone.Running
        EnvironmentStatusKind.Disconnected -> ThreadStatusTone.Waiting
        EnvironmentStatusKind.Unknown -> ThreadStatusTone.Failed
    }
