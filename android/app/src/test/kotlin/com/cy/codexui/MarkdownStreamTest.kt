package com.cy.codexui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The incremental parser has to be indistinguishable from a full parse of the same bytes, and it
 * has to keep earlier blocks untouched. Both properties are what makes the streaming renderer
 * linear: anything that re-writes a frozen block would recompose a transcript row.
 */
class MarkdownStreamTest {

    private fun parse(text: String): List<MarkdownBlock> =
        MarkdownStream().apply { append(text) }.allBlocks()

    @Test
    fun `chunked append matches a single parse`() {
        val doc = buildString {
            append("# Title\n\n")
            append("First line\nsecond line\n\n")
            append("- bullet one\n- bullet two\n\n")
            append("1. numbered\n\n")
            append("> quoted\n\n")
            append("```kotlin\nval a = 1\nval b = 2\n```\n\n")
            append("tail paragraph")
        }
        val expected = parse(doc)
        for (step in 1..7) {
            val stream = MarkdownStream()
            var index = 0
            while (index < doc.length) {
                val end = (index + step).coerceAtMost(doc.length)
                stream.append(doc.substring(index, end))
                index = end
            }
            assertEquals(expected, stream.allBlocks(), "chunk size $step")
        }
    }

    @Test
    fun `unterminated fence stays one mutable block`() {
        val stream = MarkdownStream()
        stream.append("```kotlin\nval a = 1\nval b =")
        val tail = stream.tail
        assertTrue(tail is MarkdownBlock.OpenCode)
        assertEquals(listOf("val a = 1"), tail.lines.toList())
        assertEquals("val b =", tail.partial)

        stream.append(" 2\n")
        assertEquals("", tail.partial)
        assertEquals(listOf("val a = 1", "val b = 2"), tail.lines.toList())

        stream.append("```\n")
        assertEquals(
            listOf(MarkdownBlock.Code("val a = 1\nval b = 2", "kotlin")),
            stream.frozen.toList(),
        )
        assertEquals(null, stream.tail)
    }

    @Test
    fun `partial line is a paragraph until it ends`() {
        val stream = MarkdownStream()
        stream.append("hello wor")
        assertEquals(MarkdownBlock.Paragraph("hello wor"), stream.tail)

        stream.append("ld\n")
        assertEquals(MarkdownBlock.Paragraph("hello world"), stream.tail)

        stream.append("\n")
        assertEquals(listOf(MarkdownBlock.Paragraph("hello world")), stream.frozen.toList())
        assertEquals(null, stream.tail)
    }

    @Test
    fun `frozen prefix is append only`() {
        val stream = MarkdownStream()
        stream.append("one\n\n")
        stream.append("two\n\n")
        val firstTwo = stream.frozen.toList()

        stream.append("three\n\n")
        assertEquals(3, stream.frozen.size)
        assertTrue(stream.frozen[0] === firstTwo[0])
        assertTrue(stream.frozen[1] === firstTwo[1])
    }

    @Test
    fun `blank line separates space joined paragraphs`() {
        val stream = MarkdownStream()
        stream.append("alpha\nbeta\n\ngamma")
        assertEquals(listOf(MarkdownBlock.Paragraph("alpha beta")), stream.frozen.toList())
        assertEquals(MarkdownBlock.Paragraph("gamma"), stream.tail)
    }

    @Test
    fun `line blocks freeze as soon as the line ends`() {
        val stream = MarkdownStream()
        stream.append("# h\n- b\n2. n\n> q\n")
        assertEquals(
            listOf(
                MarkdownBlock.Heading(1, "h"),
                MarkdownBlock.Bullet("b"),
                MarkdownBlock.Numbered(2, "n"),
                MarkdownBlock.Quote("q"),
            ),
            stream.frozen.toList(),
        )
        assertEquals(null, stream.tail)
    }

    @Test
    fun `a line is not classified until its newline arrives`() {
        val stream = MarkdownStream()
        stream.append("# heading")
        assertEquals(MarkdownBlock.Paragraph("# heading"), stream.tail)

        stream.append("\n")
        assertEquals(listOf(MarkdownBlock.Heading(1, "heading")), stream.frozen.toList())
    }

    @Test
    fun `trailing blank lines in a fence are trimmed`() {
        val stream = MarkdownStream()
        stream.append("```\ncode\n\n\n```\n")
        assertEquals(listOf(MarkdownBlock.Code("code", null)), stream.frozen.toList())
    }

    @Test
    fun `hasContent and text track the appended deltas`() {
        val stream = MarkdownStream()
        assertEquals(false, stream.hasContent)
        assertEquals(0, stream.length)

        stream.append("x")
        stream.append("y")
        assertEquals(true, stream.hasContent)
        assertEquals("xy", stream.text)
    }

    @Test
    fun `blank input produces no blocks`() {
        assertEquals(emptyList<MarkdownBlock>(), parse(""))
        assertEquals(emptyList<MarkdownBlock>(), parse("\n\n   \n"))
    }
}
