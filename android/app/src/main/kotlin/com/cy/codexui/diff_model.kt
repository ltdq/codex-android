package com.cy.codexui

/**
 * Parsed unified diff.
 *
 * Mirrors `codex-rs/tui/src/diff_model.rs`: the server hands the client raw unified diff text
 * (`turn/diff/updated`, `FileUpdateChange.diff`), and the UI parses it once into line records that
 * carry both gutters. Rendering never re-parses.
 */
data class DiffLine(
    val kind: DiffLineKind,
    val text: String,
    val oldLine: Int? = null,
    val newLine: Int? = null,
)

enum class DiffLineKind { Hunk, Context, Add, Remove }

data class FileDiff(
    val path: String,
    val kind: DiffFileKind,
    val lines: List<DiffLine>,
    val additions: Int,
    val removals: Int,
) {
    /** Shortened path shown as the headline of a file row. */
    val fileName: String get() = path.substringAfterLast('/')

    /** Directory of the file, shortened from the left so the last folders stay readable. */
    val parentPath: String
        get() {
            val dir = path.substringBeforeLast('/', "")
            if (dir.isEmpty()) return ""
            val parts = dir.split('/')
            return if (parts.size <= 3) "$dir/" else "…/" + parts.takeLast(3).joinToString("/") + "/"
        }

    val letter: String
        get() = when (kind) {
            DiffFileKind.Added -> "A"
            DiffFileKind.Modified -> "M"
            DiffFileKind.Deleted -> "D"
        }
}

enum class DiffFileKind { Added, Modified, Deleted }

/**
 * Colours for one diff, resolved per theme so added and removed lines stay readable in both modes.
 *
 * Lives next to the model rather than in the theme layer because both the transcript cells and the
 * status card's diff pane draw from the same instance.
 */
class DiffPalette(
    val addText: androidx.compose.ui.graphics.Color,
    val addSurface: androidx.compose.ui.graphics.Color,
    val removeText: androidx.compose.ui.graphics.Color,
    val removeSurface: androidx.compose.ui.graphics.Color,
    val hunkText: androidx.compose.ui.graphics.Color,
    val hunkSurface: androidx.compose.ui.graphics.Color,
    val gutter: androidx.compose.ui.graphics.Color,
    val context: androidx.compose.ui.graphics.Color,
)

private val HunkHeader = Regex("^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@(.*)$")
private val FileHeader = Regex("^diff --git a/(.*?) b/(.*)$")
private val OldPath = Regex("^--- (?:a/)?(.*)$")
private val NewPath = Regex("^\\+\\+\\+ (?:b/)?(.*)$")

/**
 * Parse one unified diff body into [DiffLine]s.
 *
 * Tolerant by design: hunk headers reset the gutters, an unparseable line is treated as context,
 * and a body that carries no hunks at all renders as-is so a truncated stream still shows
 * something.
 */
fun parseUnifiedDiff(diff: String): List<DiffLine> {
    val lines = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0
    for (raw in diff.lines()) {
        val header = HunkHeader.find(raw)
        when {
            header != null -> {
                val numbers = Regex("-\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)?")
                    .find(raw)
                    ?.value
                    ?.split(' ')
                oldLine = numbers?.getOrNull(0)?.removePrefix("-")?.substringBefore(',')?.toIntOrNull() ?: 0
                newLine = numbers?.getOrNull(1)?.removePrefix("+")?.substringBefore(',')?.toIntOrNull() ?: 0
                lines += DiffLine(DiffLineKind.Hunk, raw, null, null)
            }

            raw.startsWith("+++") || raw.startsWith("---") || FileHeader.matches(raw) -> {
                lines += DiffLine(DiffLineKind.Hunk, raw, null, null)
            }

            raw.startsWith("+") -> {
                lines += DiffLine(DiffLineKind.Add, raw.substring(1), null, newLine)
                newLine++
            }

            raw.startsWith("-") -> {
                lines += DiffLine(DiffLineKind.Remove, raw.substring(1), oldLine, null)
                oldLine++
            }

            raw.startsWith("\\") -> lines += DiffLine(DiffLineKind.Context, raw, null, null)

            else -> {
                val text = raw.removePrefix(" ")
                lines += DiffLine(DiffLineKind.Context, text, oldLine, newLine)
                oldLine++
                newLine++
            }
        }
    }
    return lines
}

/** File kind implied by a unified diff body: everything added, everything removed, or an edit. */
fun diffFileKind(lines: List<DiffLine>): DiffFileKind {
    val additions = lines.count { it.kind == DiffLineKind.Add }
    val removals = lines.count { it.kind == DiffLineKind.Remove }
    return when {
        removals == 0 && additions > 0 -> DiffFileKind.Added
        additions == 0 && removals > 0 -> DiffFileKind.Deleted
        else -> DiffFileKind.Modified
    }
}

/** Split a whole-turn diff (`turn/diff/updated`) into one [FileDiff] per `diff --git` section. */
fun parseTurnDiff(diff: String): List<FileDiff> {
    if (diff.isBlank()) return emptyList()
    val sections = mutableListOf<Pair<String, MutableList<String>>>()
    var currentPath: String? = null
    var current = mutableListOf<String>()
    for (line in diff.lines()) {
        val header = FileHeader.find(line)
        if (header != null) {
            if (currentPath != null) sections += currentPath to current
            currentPath = header.groupValues[2]
            current = mutableListOf()
        }
        current += line
    }
    if (currentPath != null) sections += currentPath to current

    // A diff without `diff --git` headers (a single-file patch) arrives as one section.
    if (sections.isEmpty()) {
        val lines = parseUnifiedDiff(diff)
        val path = lines.firstNotNullOfOrNull { line ->
            when {
                line.text.startsWith("+++ ") -> NewPath.find(line.text)?.groupValues?.get(1)
                line.text.startsWith("--- ") -> OldPath.find(line.text)?.groupValues?.get(1)
                else -> null
            }
        } ?: "patch"
        return listOf(
            FileDiff(
                path = path,
                kind = diffFileKind(lines),
                lines = lines,
                additions = lines.count { it.kind == DiffLineKind.Add },
                removals = lines.count { it.kind == DiffLineKind.Remove },
            ),
        )
    }

    return sections.map { (path, body) ->
        val lines = parseUnifiedDiff(body.joinToString("\n"))
        FileDiff(
            path = path,
            kind = diffFileKind(lines),
            lines = lines,
            additions = lines.count { it.kind == DiffLineKind.Add },
            removals = lines.count { it.kind == DiffLineKind.Remove },
        )
    }
}

/** Build a diff from already-structured lines. */
fun fileDiffOf(path: String, lines: List<DiffLine>): FileDiff = FileDiff(
    path = path,
    kind = diffFileKind(lines),
    lines = lines,
    additions = lines.count { it.kind == DiffLineKind.Add },
    removals = lines.count { it.kind == DiffLineKind.Remove },
)
