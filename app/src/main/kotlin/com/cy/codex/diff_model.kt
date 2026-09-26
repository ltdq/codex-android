package com.cy.codex

/** Parsed unified diff; the UI parses the raw text once and rendering never re-parses (codex-rs/tui/src/diff_model.rs). */
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
    /** Source path of a rename, or `null` when the file was not moved. */
    val oldPath: String? = null,
) {
    /** Shortened path shown as the headline of a file row. */
    val fileName: String get() = path.substringAfterLast('/')

    /** `old → new` for a rename, the plain file name otherwise. */
    val displayName: String
        get() = oldPath?.let { "${it.substringAfterLast('/')} → $fileName" } ?: fileName

    /** Directory of the file, shortened from the left so the last folders stay readable. */
    val parentPath: String get() = shortenedParent(path)

    val letter: String
        get() = when (kind) {
            DiffFileKind.Added -> "A"
            DiffFileKind.Modified -> "M"
            DiffFileKind.Deleted -> "D"
            DiffFileKind.Renamed -> "R"
        }
}

enum class DiffFileKind { Added, Modified, Deleted, Renamed }

/** Directory of [path], shortened from the left so the last folders stay readable. */
fun shortenedParent(path: String): String {
    val dir = path.substringBeforeLast('/', "")
    if (dir.isEmpty()) return ""
    val parts = dir.split('/')
    return if (parts.size <= 3) "$dir/" else "…/" + parts.takeLast(3).joinToString("/") + "/"
}

/** Diff colors resolved per theme; lives next to the model because transcript cells and the status pane share one instance. */
class DiffPalette(
    val addText: androidx.compose.ui.graphics.Color,
    val addSurface: androidx.compose.ui.graphics.Color,
    val removeText: androidx.compose.ui.graphics.Color,
    val removeSurface: androidx.compose.ui.graphics.Color,
    val hunkSurface: androidx.compose.ui.graphics.Color,
    val gutter: androidx.compose.ui.graphics.Color,
    val context: androidx.compose.ui.graphics.Color,
)

private val HunkHeader =
    Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$")
// MULTILINE only affects the whole-payload scan below; matching one line is unaffected.
private val FileHeader = Regex("^diff --git a/(.*?) b/(.*)$", RegexOption.MULTILINE)
private val OldPath = Regex("^--- (?:a/)?(.*)$")
private val NewPath = Regex("^\\+\\+\\+ (?:b/)?(.*)$")

/** Byte offsets where each `diff --git` line starts in [diff], in payload order. */
internal fun gitFileHeaderOffsets(diff: String): List<Int> =
    FileHeader.findAll(diff).map { it.range.first }.toList()

/** The `a/…` and `b/…` paths of one `diff --git` line; they differ for a rename. */
internal fun gitFileHeaderPaths(line: String): Pair<String, String>? =
    FileHeader.find(line)?.let { it.groupValues[1] to it.groupValues[2] }

/**
 * Parse one unified diff body into [DiffLine]s.
 *
 * Tolerant by design: hunk headers reset the gutters, unknown lines become context. Hunk line
 * counts distinguish a removed line starting `--` from a `---` file header.
 */
fun parseUnifiedDiff(diff: String): List<DiffLine> {
    val lines = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0
    var oldLeft = 0
    var newLeft = 0
    var inHunk = false
    for (raw in diff.lines()) {
        val header = HunkHeader.find(raw)
        when {
            header != null -> {
                oldLine = header.groupValues[1].toIntOrNull() ?: 0
                newLine = header.groupValues[3].toIntOrNull() ?: 0
                // A missing `,count` means one line, per the unified diff convention.
                oldLeft = header.groupValues[2].toIntOrNull() ?: 1
                newLeft = header.groupValues[4].toIntOrNull() ?: 1
                inHunk = true
                lines += DiffLine(DiffLineKind.Hunk, raw, null, null)
            }

            // Every content line carries a prefix, so a `diff --git` line is always a section header, even mid-hunk.
            FileHeader.matches(raw) -> {
                inHunk = false
                lines += DiffLine(DiffLineKind.Hunk, raw, null, null)
            }

            // Path headers are only headers outside a hunk; a removed line beginning `--` must stay a removal.
            !inHunk && (raw.startsWith("+++") || raw.startsWith("---")) -> {
                lines += DiffLine(DiffLineKind.Hunk, raw, null, null)
            }

            raw.startsWith("+") -> {
                lines += DiffLine(DiffLineKind.Add, raw.substring(1), null, newLine)
                newLine++
                if (newLeft > 0) newLeft--
            }

            raw.startsWith("-") -> {
                lines += DiffLine(DiffLineKind.Remove, raw.substring(1), oldLine, null)
                oldLine++
                if (oldLeft > 0) oldLeft--
            }

            raw.startsWith("\\") -> lines += DiffLine(DiffLineKind.Hunk, raw, null, null)

            else -> {
                val text = raw.removePrefix(" ")
                lines += DiffLine(DiffLineKind.Context, text, oldLine, newLine)
                oldLine++
                newLine++
                if (oldLeft > 0) oldLeft--
                if (newLeft > 0) newLeft--
            }
        }
        if (inHunk && oldLeft == 0 && newLeft == 0) inHunk = false
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
    val starts = gitFileHeaderOffsets(diff)

    // A diff without `diff --git` headers (a single-file patch) arrives as one section.
    if (starts.isEmpty()) {
        val lines = parseUnifiedDiff(diff)
        var oldPath: String? = null
        var newPath: String? = null
        for (line in lines) {
            when {
                line.text.startsWith("+++ ") -> newPath = NewPath.find(line.text)?.groupValues?.get(1)
                line.text.startsWith("--- ") -> oldPath = OldPath.find(line.text)?.groupValues?.get(1)
            }
        }
        val path = newPath ?: oldPath ?: "patch"
        return listOf(
            fileDiffOf(path, lines, oldPath?.takeIf { it != path && it != "/dev/null" }),
        )
    }

    return starts.mapIndexed { index, start ->
        val end = starts.getOrNull(index + 1) ?: diff.length
        parseFileSection(diff.substring(start, end))
    }
}

/** Parse one `diff --git` section — its header line plus body — into a [FileDiff]. */
internal fun parseFileSection(section: String): FileDiff {
    val paths = gitFileHeaderPaths(section.substringBefore('\n'))
    val path = paths?.second ?: "patch"
    val oldPath = paths?.first?.takeIf { it != path }
    return fileDiffOf(path, parseUnifiedDiff(section), oldPath)
}

/** Build a diff from already-structured lines. */
fun fileDiffOf(path: String, lines: List<DiffLine>, oldPath: String? = null): FileDiff = FileDiff(
    path = path,
    kind = if (oldPath != null) DiffFileKind.Renamed else diffFileKind(lines),
    lines = lines,
    additions = lines.count { it.kind == DiffLineKind.Add },
    removals = lines.count { it.kind == DiffLineKind.Remove },
    oldPath = oldPath,
)
