package com.cy.codex

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

class SourceCommentTest {

    @Test
    fun `no block comment is opened inside another block comment`() {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "expected to run from the module directory; looked for $root")

        val offenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file -> offenders += scan(file) }

        assertTrue(
            offenders.isEmpty(),
            buildString {
                appendLine("A `/*` inside a block comment silently swallows the rest of the file.")
                appendLine("Write the path as `family/…` instead, or close the comment first:")
                offenders.forEach { appendLine("  $it") }
            },
        )
    }

    /** One message per nested opener in [file]. */
    private fun scan(file: File): List<String> {
        val text = file.readText()
        val found = mutableListOf<String>()
        var depth = 0
        var line = 1
        var i = 0

        while (i < text.length) {
            val c = text[i]

            if (depth > 0) {
                when {
                    text.startsWith("/*", i) -> {
                        found += "${file.path}:$line opens a comment inside a comment"
                        depth++
                        i += 2
                    }

                    text.startsWith("*/", i) -> {
                        depth--
                        i += 2
                    }

                    c == '\n' -> {
                        line++
                        i++
                    }

                    else -> i++
                }
                continue
            }

            when {
                c == '\n' -> {
                    line++
                    i++
                }

                // A raw string ends only at the next triple quote; escapes do not apply inside it.
                text.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < text.length && !text.startsWith("\"\"\"", i)) {
                        if (text[i] == '\n') line++
                        i++
                    }
                    i += 3
                }

                c == '"' -> {
                    i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        if (i < text.length && text[i] == '\n') line++
                        i++
                    }
                    i++
                }

                // A character literal is short; anything further is an apostrophe in prose.
                c == '\'' -> {
                    val close = text.indexOf('\'', i + 1)
                    if (close in (i + 1)..(i + 3) && !text.substring(i + 1, close).contains('\n')) {
                        i = close + 1
                    } else {
                        i++
                    }
                }

                text.startsWith("//", i) -> {
                    while (i < text.length && text[i] != '\n') i++
                }

                text.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }

                else -> i++
            }
        }

        if (depth != 0) found += "${file.path} ends with $depth block comment(s) still open"
        return found
    }
}
