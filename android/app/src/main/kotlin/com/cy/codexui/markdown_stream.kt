package com.cy.codexui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One block of the markdown-lite document the transcript renders.
 *
 * The shapes match what `parseMarkdown` produced before streaming existed, plus [OpenCode] for a
 * fence that has not closed yet. The model is append-only so the renderer can keep the composition
 * of every finished block and rebuild only the block the latest delta landed in.
 */
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val index: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock

    /** A complete fence; [open] is true while the closing marker has not arrived. */
    data class Code(
        val code: String,
        val language: String?,
        val open: Boolean = false,
    ) : MarkdownBlock

    /**
     * A fence whose closing marker has not arrived.
     *
     * This is the one block that is mutated in place rather than replaced: [lines] takes complete
     * lines and [partial] the line still being written, so one delta costs one line instead of a
     * rebuild of the whole code text. The block becomes a [Code] the moment the fence closes.
     */
    class OpenCode(val language: String?) : MarkdownBlock {
        val lines = mutableStateListOf<String>()

        var partial by mutableStateOf("")
            internal set
    }
}

/**
 * Incremental markdown-lite parser for a streaming message.
 *
 * [append] only scans the new text: a block is frozen into [frozen] as soon as its terminator has
 * been seen, and [tail] holds the single block still being written, so scanning is O(n) over the
 * life of the message instead of the O(n) full re-parse that `remember(markdown)` forced on every
 * delta. [frozen] and [tail] are Compose state, so a delta recomposes the tail view only.
 *
 * The grammar is deliberately the same tolerant subset as before: paragraphs, bullets, numbered
 * items, headings up to level three, block quotes and fences; an unterminated fence renders as code
 * to the end of the buffer.
 */
class MarkdownStream {

    /** Blocks whose terminator has been seen. Append-only; never rewritten. */
    val frozen = mutableStateListOf<MarkdownBlock>()

    /** The block currently being written, or `null` between blocks. */
    var tail by mutableStateOf<MarkdownBlock?>(null)
        private set

    /** True once at least one delta arrived; observable so a cell can pick its body. */
    var hasContent by mutableStateOf(false)
        private set

    val length: Int get() = source.length

    private val source = StringBuilder()
    private var scanPos = 0
    private var inFence = false
    private var fenceLanguage: String? = null
    private var openCode: MarkdownBlock.OpenCode? = null
    private var paragraphStart = -1
    private var paragraphEnd = -1

    /** The whole accumulated source, materialized on demand (completion fallback, copy). */
    val text: String get() = source.toString()

    fun append(delta: String) {
        if (delta.isEmpty()) return
        source.append(delta)
        hasContent = true
        consumeCompleteLines()
        refreshTail()
    }

    /** Every block parsed so far, for callers and tests that want the whole document. */
    fun allBlocks(): List<MarkdownBlock> = if (tail == null) frozen.toList() else frozen + tail!!

    private fun consumeCompleteLines() {
        while (true) {
            val newline = source.indexOf('\n', scanPos)
            if (newline < 0) return
            val lineEnd = newline + 1
            val raw = source.substring(scanPos, newline)
            val line = raw.trimEnd()
            if (inFence) {
                if (FenceMarker.matches(line.trim())) {
                    closeFence()
                } else {
                    openCode?.lines?.add(raw)
                }
                scanPos = lineEnd
                continue
            }
            when {
                FenceMarker.find(line.trim()) != null -> {
                    freezeParagraph()
                    val language = FenceMarker.find(line.trim())!!.groupValues[1].ifBlank { null }
                    inFence = true
                    fenceLanguage = language
                    openCode = MarkdownBlock.OpenCode(language)
                    scanPos = lineEnd
                }

                line.isBlank() -> {
                    freezeParagraph()
                    scanPos = lineEnd
                }

                HeadingMarker.matches(line) -> {
                    freezeParagraph()
                    val match = HeadingMarker.find(line)!!
                    frozen += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
                    scanPos = lineEnd
                }

                BulletMarker.matches(line.trim()) -> {
                    freezeParagraph()
                    frozen += MarkdownBlock.Bullet(BulletMarker.find(line.trim())!!.groupValues[1])
                    scanPos = lineEnd
                }

                NumberedMarker.matches(line.trim()) -> {
                    freezeParagraph()
                    val match = NumberedMarker.find(line.trim())!!
                    frozen += MarkdownBlock.Numbered(
                        index = match.groupValues[1].toIntOrNull() ?: 1,
                        text = match.groupValues[2],
                    )
                    scanPos = lineEnd
                }

                line.trimStart().startsWith("> ") -> {
                    freezeParagraph()
                    frozen += MarkdownBlock.Quote(line.trimStart().removePrefix("> "))
                    scanPos = lineEnd
                }

                else -> {
                    if (paragraphStart < 0) paragraphStart = scanPos
                    paragraphEnd = lineEnd
                    scanPos = lineEnd
                }
            }
        }
    }

    private fun freezeParagraph() {
        if (paragraphStart < 0) return
        val text = joinParagraph(paragraphStart, paragraphEnd)
        if (text.isNotEmpty()) frozen += MarkdownBlock.Paragraph(text)
        paragraphStart = -1
        paragraphEnd = -1
    }

    private fun closeFence() {
        val code = openCode
        if (code != null) {
            val lines = code.lines
            var last = lines.size
            while (last > 0 && lines[last - 1].isEmpty()) last--
            frozen += MarkdownBlock.Code(
                code = if (last == 0) "" else lines.take(last).joinToString("\n"),
                language = fenceLanguage,
            )
        }
        openCode = null
        inFence = false
        fenceLanguage = null
    }

    /**
     * The block still being written.
     *
     * A paragraph is rebuilt from its own lines (a paragraph is bounded by a blank line, so the
     * tail itself is small); an open fence is handed over as the same [MarkdownBlock.OpenCode]
     * instance each time, which is what keeps a long streaming code block linear.
     */
    private fun refreshTail() {
        if (inFence) {
            val code = openCode
            if (code == null) {
                tail = null
                return
            }
            code.partial = source.substring(scanPos)
            tail = code
            return
        }
        if (paragraphStart >= 0) {
            val text = joinParagraph(paragraphStart, source.length)
            tail = if (text.isEmpty()) null else MarkdownBlock.Paragraph(text)
            return
        }
        if (scanPos >= source.length) {
            tail = null
            return
        }
        // A still-growing line is shown as a paragraph; the scanner re-reads it when it ends and
        // turns it into whatever its first character decides.
        val text = source.substring(scanPos).trim()
        tail = if (text.isEmpty()) null else MarkdownBlock.Paragraph(text)
    }

    /** Join the lines of `[from, to)` with single spaces, trimming each, as the old parser did. */
    private fun joinParagraph(from: Int, to: Int): String {
        val builder = StringBuilder()
        var cursor = from
        while (cursor < to) {
            var end = source.indexOf('\n', cursor)
            if (end < 0 || end > to) end = to
            val line = source.substring(cursor, end).trim()
            if (line.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(line)
            }
            cursor = end + 1
        }
        return builder.toString()
    }
}

private val BulletMarker = Regex("^[-*+]\\s+(.*)$")
private val NumberedMarker = Regex("^(\\d+)[.)]\\s+(.*)$")
private val HeadingMarker = Regex("^(#{1,3})\\s+(.*)$")
private val FenceMarker = Regex("^```\\s*(\\S*)\\s*$")
