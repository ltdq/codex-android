package com.cy.codex.diff

import com.cy.codex.FileDiff
import com.cy.codex.gitFileHeaderOffsets
import com.cy.codex.parseFileSection
import com.cy.codex.parseTurnDiff

/**
 * Owns the growing `turn/diff/updated` payload of one turn. The server re-sends the whole
 * accumulated diff per notification; a full parse per notification is quadratic, so the last
 * payload and its sections are kept, and an append costs the tail plus the one grown section.
 * Unchanged files keep the very same [FileDiff] instance, which is what the transcript's `remember`s
 * rely on. Confined to the single reducing coroutine — deliberately unsynchronized.
 */
class TurnDiffAccumulator {

    private var input: String = ""

    private var sections: List<Section> = emptyList()

    private var parsed: List<FileDiff> = emptyList()

    /** The same list instance until the payload really changes. */
    val files: List<FileDiff> get() = parsed

    fun reset() {
        input = ""
        sections = emptyList()
        parsed = emptyList()
    }

    fun apply(diff: String): List<FileDiff> {
        if (diff == input) return parsed
        if (diff.isEmpty()) {
            reset()
            return parsed
        }
        // Full parse when the payload does not extend its predecessor: no reusable prefix, a
        // single-file patch has nothing to extend, and a mid-line predecessor may hide half a
        // header.
        if (input.isEmpty() || sections.isEmpty() || !input.endsWith("\n") || !diff.startsWith(input)) {
            return replace(diff)
        }

        val tail = diff.substring(input.length)
        val headerStarts = gitFileHeaderOffsets(tail)
        val last = sections.last()
        val head = if (headerStarts.isEmpty()) tail else tail.substring(0, headerStarts.first())
        // An empty head keeps the previous section byte-identical, parsed instance included.
        val grown = if (head.isEmpty()) last else last.reparse(last.source + head)
        val added = headerStarts.mapIndexed { index, start ->
            val end = headerStarts.getOrNull(index + 1) ?: tail.length
            parseSection(tail.substring(start, end))
        }

        sections = sections.dropLast(1) + grown + added
        input = diff
        parsed = sections.map { it.file }
        return parsed
    }

    private fun replace(diff: String): List<FileDiff> {
        input = diff
        val starts = gitFileHeaderOffsets(diff)
        if (starts.isEmpty()) {
            // No header: `parseTurnDiff` guesses the path from the `---`/`+++` lines.
            sections = emptyList()
            parsed = parseTurnDiff(diff)
            return parsed
        }
        sections = starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: diff.length
            parseSection(diff.substring(start, end))
        }
        parsed = sections.map { it.file }
        return parsed
    }

    private class Section(val source: String, val file: FileDiff) {
        fun reparse(source: String): Section = Section(source, parseFileSection(source))
    }

    private companion object {
        fun parseSection(source: String): Section = Section(source, parseFileSection(source))
    }
}
