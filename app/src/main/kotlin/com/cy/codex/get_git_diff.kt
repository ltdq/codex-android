package com.cy.codex

import com.cy.codex.protocol.AppServerClient

/** What one `/diff` read found; mirrors `codex-rs/tui/src/get_git_diff.rs`. */
sealed interface GitDiffResult {
    data class Changes(val diff: String) : GitDiffResult

    data object Clean : GitDiffResult

    data object NotARepository : GitDiffResult

    data class Failed(val message: String) : GitDiffResult
}

/**
 * Working-tree diff via `command/exec`, the toolchain's own git. Follows
 * `codex-rs/tui/src/get_git_diff.rs`, minus `--color` (the renderer reads plain unified diff);
 * `core.quotePath=false` keeps non-ASCII untracked paths intact. Exit code 1 means "differences".
 */
object GitDiff {
    private const val CommandTimeoutMs = 30_000L

    private val NoHooks = listOf("-c", "core.hooksPath=/dev/null")

    private val DiffFlags = listOf(
        "--no-textconv",
        "--no-ext-diff",
        "--submodule=short",
        "--ignore-submodules=dirty",
    )

    internal fun trackedDiffCommand(): List<String> = listOf("git") + NoHooks + listOf("diff") + DiffFlags

    internal fun untrackedListCommand(): List<String> = listOf("git") + NoHooks +
        listOf("-c", "core.quotePath=false", "ls-files", "--others", "--exclude-standard")

    internal fun untrackedDiffCommand(path: String): List<String> =
        listOf("git") + NoHooks + listOf("diff") + DiffFlags + listOf("--no-index", "--", "/dev/null", path)

    internal fun insideRepositoryCommand(): List<String> =
        listOf("git") + NoHooks + listOf("rev-parse", "--is-inside-work-tree")

    suspend fun load(client: AppServerClient, cwd: String): GitDiffResult {
        val inside = client.execCommand(insideRepositoryCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (inside.exitCode != 0) return GitDiffResult.NotARepository

        val tracked = client.execCommand(trackedDiffCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (tracked.exitCode > 1) return GitDiffResult.Failed(tracked.stderr.ifBlank { "git diff" })

        val untracked = client.execCommand(untrackedListCommand(), cwd, CommandTimeoutMs)
            .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
        if (untracked.exitCode != 0) {
            return GitDiffResult.Failed(untracked.stderr.ifBlank { "git ls-files" })
        }

        val payload = StringBuilder(tracked.stdout)
        for (file in untracked.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
            val diff = client.execCommand(untrackedDiffCommand(file), cwd, CommandTimeoutMs)
                .getOrElse { return GitDiffResult.Failed(it.message.orEmpty()) }
            if (diff.exitCode > 1) return GitDiffResult.Failed(diff.stderr.ifBlank { "git diff --no-index" })
            payload.append(diff.stdout)
        }
        return if (payload.isBlank()) GitDiffResult.Clean else GitDiffResult.Changes(payload.toString())
    }
}
