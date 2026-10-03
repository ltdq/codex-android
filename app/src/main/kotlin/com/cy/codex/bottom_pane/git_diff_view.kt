package com.cy.codex.bottom_pane

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.DiffBody
import com.cy.codex.FileDiff
import com.cy.codex.FileKindBadge
import com.cy.codex.FileStatText
import com.cy.codex.GitDiff
import com.cy.codex.GitDiffResult
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.markdown_render.displayDiffPath
import com.cy.codex.markdown_render.runtimeHome
import com.cy.codex.parseTurnDiff
import com.cy.codex.protocol.AppServerClient
import com.cy.codex.raisedSurface
import com.cy.codex.render.languageFromPath
import com.cy.codex.shortenedParent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `/diff`: the working tree, tracked and untracked, computed on entry because the tree changes
 * without the app hearing about it (codex-rs/tui/src/get_git_diff.rs, plus `slash_dispatch.rs`).
 * A page rather than a transcript cell: `turn/diff/updated` never mentions untracked files, and a
 * page can carry a reload.
 */
@Composable
fun GitDiffScreen(
    cwd: String,
    client: AppServerClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var result by remember(cwd) { mutableStateOf<GitDiffResult?>(null) }
    var reloadToken by remember(cwd) { mutableIntStateOf(0) }
    // Keyed on the token so refresh re-runs the read; the old answer stays on screen meanwhile.
    LaunchedEffect(cwd, reloadToken) { result = GitDiff.load(client, cwd) }

    val files =
        remember(result) {
            (result as? GitDiffResult.Changes)?.let { parseTurnDiff(it.diff) }.orEmpty()
        }
    // Every file starts collapsed; a page of hundreds must not lay out each first screenful up front.
    val expanded = remember(result) { mutableStateMapOf<String, Boolean>() }

    CodexPage(
        title = stringResource(R.string.git_diff_screen_title),
        description = cwd,
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(
                onClick = { reloadToken++ },
                minWidth = UiConsts.IconButtonSize,
                minHeight = UiConsts.IconButtonSize,
            ) {
                Icon(
                    imageVector = MiuixIcons.Refresh,
                    contentDescription = stringResource(R.string.git_diff_screen_reload),
                    modifier = Modifier.size(UiConsts.IconRefresh),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) {
        when (val current = result) {
            null ->
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
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.git_diff_screen_loading),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

            is GitDiffResult.Clean ->
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
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.git_diff_screen_empty),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

            is GitDiffResult.NotARepository ->
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
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.git_diff_screen_not_repository),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

            is GitDiffResult.Failed ->
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
                            imageVector = MiuixIcons.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(UiConsts.IconHeader),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(UiConsts.Space12))
                    Text(
                        text = stringResource(R.string.git_diff_screen_failed, current.message),
                        fontSize = UiType.RowTitle,
                        lineHeight = UiType.RowTitleLine,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }

            is GitDiffResult.Changes -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CodexGroupTitle(
                        stringResource(
                            R.string.git_diff_screen_summary,
                            files.size,
                            files.sumOf { it.additions },
                            files.sumOf { it.removals },
                        )
                    )
                    CodexCardGrid(count = files.size) { index ->
                        val file = files[index]
                        GitDiffCard(
                            file = file,
                            expanded = expanded[file.path] == true,
                            onToggle = { expanded[file.path] = expanded[file.path] != true },
                            cwd = cwd,
                        )
                    }
                }
            }
        }
    }
}

/** How many lines of one file's diff a card shows before its expander. */
private const val GitDiffBodyLines = 400

/** One changed file as a catalogue card: its name over its directory, its kind and counts, its body once opened. */
@Composable
private fun GitDiffCard(
    file: FileDiff,
    expanded: Boolean,
    onToggle: () -> Unit,
    cwd: String,
) {
    val home = runtimeHome()
    val shownPath = displayDiffPath(file.path, cwd, home)
    val shownOld = file.oldPath?.let { displayDiffPath(it, cwd, home) }
    val title =
        if (shownOld != null && shownOld != shownPath) {
            "${shownOld.substringAfterLast('/')} → ${shownPath.substringAfterLast('/')}"
        } else {
            shownPath.substringAfterLast('/')
        }
    CodexCatalogCard(
        title = title,
        description = shortenedParent(shownPath).ifEmpty { null },
        icon = MiuixIcons.File,
        onClick = onToggle,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileKindBadge(file)
                Spacer(Modifier.width(UiConsts.Space8))
                FileStatText(file.additions, file.removals)
            }
        },
        footer =
            if (expanded) {
                {
                    DiffBody(
                        lines = file.lines,
                        maxLines = GitDiffBodyLines,
                        language = languageFromPath(file.path),
                        modifier = Modifier.padding(start = UiConsts.Space6),
                    )
                }
            } else {
                null
            },
    )
}
