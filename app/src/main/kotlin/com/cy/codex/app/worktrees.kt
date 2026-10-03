package com.cy.codex.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cy.codex.CodexCardGrid
import com.cy.codex.CodexCatalogCard
import com.cy.codex.CodexEmptyRow
import com.cy.codex.CodexGroupTitle
import com.cy.codex.CodexPage
import com.cy.codex.CodexSection
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.AppServerClient
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One entry of `git worktree list --porcelain`. */
data class GitWorktree(
    val path: String,
    val branch: String?,
    val detached: Boolean,
    val bare: Boolean,
)

/** Parse `git worktree list --porcelain` blocks; unknown keys are ignored. */
fun parseWorktrees(output: String): List<GitWorktree> =
    output.split(Regex("\\n\\s*\\n")).mapNotNull { block ->
        val lines = block.lineSequence().map(String::trim).filter { it.isNotEmpty() }.toList()
        val path =
            lines.firstOrNull { it.startsWith("worktree ") }?.removePrefix("worktree ")?.trim()
        if (path.isNullOrEmpty()) return@mapNotNull null
        GitWorktree(
            path = path,
            branch =
                lines
                    .firstOrNull { it.startsWith("branch ") }
                    ?.removePrefix("branch ")
                    ?.trim()
                    ?.removePrefix("refs/heads/"),
            detached = lines.any { it == "detached" },
            bare = lines.any { it == "bare" },
        )
    }

/** Sibling directory `git worktree add` should create for [branch] under [repository]. */
internal fun worktreePathFor(repository: String, branch: String): String {
    val root = repository.trimEnd('/')
    val parent = root.substringBeforeLast('/', "").ifEmpty { "/" }
    val repoName = root.substringAfterLast('/').ifEmpty { "repo" }
    val leaf = branch.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').ifEmpty { "worktree" }
    val prefix = if (parent == "/") "" else parent
    return "$prefix/$repoName-$leaf"
}

/**
 * `/worktree` page: the TUI's managed pool has no protocol surface, so the GUI is the
 * VCS feature itself.
 */
@Composable
fun WorktreesScreen(
    client: AppServerClient,
    cwd: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val repository = cwd.ifBlank { "/" }
    var entries by remember(repository) { mutableStateOf<List<GitWorktree>>(emptyList()) }
    var loading by remember(repository) { mutableStateOf(true) }
    var error by remember(repository) { mutableStateOf<String?>(null) }
    var reload by remember(repository) { mutableStateOf(0) }
    var branch by remember(repository) { mutableStateOf("") }
    var creating by remember(repository) { mutableStateOf(false) }
    var createError by remember(repository) { mutableStateOf<String?>(null) }
    val listError = stringResource(R.string.worktrees_error)

    LaunchedEffect(repository, reload) {
        loading = true
        error = null
        client
            .execCommand(
                listOf("git", "worktree", "list", "--porcelain"),
                repository,
                timeoutMs = 15_000,
            )
            .onSuccess { answer -> entries = parseWorktrees(answer.stdout) }
            .onFailure { failure -> error = failure.message ?: listError }
        loading = false
    }

    CodexPage(
        title = stringResource(R.string.worktrees_title),
        description = repository,
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CodexGroupTitle(stringResource(R.string.worktrees_title))
            when {
                loading -> WorktreesNote(stringResource(R.string.worktrees_loading))
                error != null -> WorktreesNote(error!!, isError = true)
                entries.isEmpty() -> CodexEmptyRow(stringResource(R.string.worktrees_empty))
                else ->
                    CodexCardGrid(count = entries.size) { index ->
                        WorktreeCard(entries[index], repository, onOpen)
                    }
            }
        }
        CodexSection(stringResource(R.string.worktrees_create_group)) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = UiConsts.RowInset, vertical = UiConsts.Space12),
            ) {
                Text(
                    text = stringResource(R.string.worktrees_branch_label),
                    fontSize = UiType.Meta,
                    lineHeight = UiType.MetaLine,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(UiConsts.Space4))
                TextField(
                    value = branch,
                    onValueChange = { branch = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "feature/my-change",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                if (createError != null) {
                    Spacer(Modifier.height(UiConsts.Space6))
                    WorktreesNote(createError!!, isError = true)
                }
                Spacer(Modifier.height(UiConsts.Space10))
                Button(
                    onClick = {
                        val name = branch.trim()
                        if (name.isEmpty() || creating) return@Button
                        creating = true
                        createError = null
                        val path = worktreePathFor(repository, name)
                        scope.launch {
                            // git's own failure message is shown as-is: add fails clearly on an existing branch or path.
                            client
                                .execCommand(
                                    listOf("git", "worktree", "add", path, "-b", name),
                                    repository,
                                    timeoutMs = 60_000,
                                )
                                .onSuccess { answer ->
                                    creating = false
                                    branch = ""
                                    if (answer.exitCode != 0) {
                                        createError = answer.stderr.ifBlank { listError }
                                    } else {
                                        reload++
                                    }
                                }
                                .onFailure { failure ->
                                    creating = false
                                    createError = failure.message ?: listError
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = branch.isNotBlank() && !creating,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text =
                            if (creating) {
                                stringResource(R.string.worktrees_creating)
                            } else {
                                stringResource(R.string.worktrees_create)
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One worktree as a catalogue card: its path, its branch, and the marker the session runs under. */
@Composable
private fun WorktreeCard(entry: GitWorktree, current: String, onOpen: (String) -> Unit) {
    CodexCatalogCard(
        title = entry.path,
        description =
            when {
                entry.bare -> stringResource(R.string.worktrees_bare)
                entry.detached -> stringResource(R.string.worktrees_detached)
                else -> entry.branch
            },
        icon = MiuixIcons.FolderFill,
        onClick = { onOpen(entry.path) },
        trailing =
            if (entry.path == current) {
                {
                    Text(
                        text = stringResource(R.string.session_list_current),
                        fontSize = UiType.Chip,
                        lineHeight = UiType.ChipLine,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun WorktreesNote(text: String, isError: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = UiConsts.Space6),
        fontSize = UiType.Meta,
        lineHeight = UiType.MetaLine,
        color =
            if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.disabledOnSurface,
    )
}
