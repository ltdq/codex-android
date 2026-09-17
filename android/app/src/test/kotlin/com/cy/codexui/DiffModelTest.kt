package com.cy.codexui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The diff parser is the only thing standing between the server's raw unified diff and the two
 * gutters the transcript draws, so its behaviour is pinned here — including the tolerant cases a
 * streaming turn actually produces.
 */
class DiffModelTest {

    @Test
    fun parsesHunkHeaderAndBothGutters() {
        val lines = parseUnifiedDiff(
            """
            @@ -12,4 +12,6 @@ fun main() {
             context
            -removed
            +added
            +added too
            """.trimIndent(),
        )
        assertEquals(5, lines.size)
        assertEquals(DiffLineKind.Hunk, lines[0].kind)
        assertEquals(DiffLineKind.Context, lines[1].kind)
        assertEquals(12, lines[1].oldLine)
        assertEquals(12, lines[1].newLine)
        assertEquals(DiffLineKind.Remove, lines[2].kind)
        assertEquals(13, lines[2].oldLine)
        assertEquals(null, lines[2].newLine)
        assertEquals(DiffLineKind.Add, lines[3].kind)
        assertEquals(13, lines[3].newLine)
        assertEquals("added", lines[3].text)
        assertEquals(14, lines[4].newLine)
    }

    @Test
    fun treatsFileHeadersAsHunks() {
        val lines = parseUnifiedDiff(
            """
            --- a/foo.kt
            +++ b/foo.kt
            @@ -1 +1 @@
            -a
            +b
            """.trimIndent(),
        )
        assertEquals(DiffLineKind.Hunk, lines[0].kind)
        assertEquals(DiffLineKind.Hunk, lines[1].kind)
        assertEquals(DiffLineKind.Hunk, lines[2].kind)
    }

    @Test
    fun infersFileKindFromTheBody() {
        val added = parseUnifiedDiff("@@ -0,0 +1,2 @@\n+a\n+b")
        assertEquals(DiffFileKind.Added, diffFileKind(added))
        val deleted = parseUnifiedDiff("@@ -1,2 +0,0 @@\n-a\n-b")
        assertEquals(DiffFileKind.Deleted, diffFileKind(deleted))
        val updated = parseUnifiedDiff("@@ -1 +1 @@\n-a\n+b")
        assertEquals(DiffFileKind.Modified, diffFileKind(updated))
    }

    @Test
    fun splitsATurnDiffIntoOneFilePerHeader() {
        val diff = listOf(
            "diff --git a/app/A.kt b/app/A.kt",
            "--- a/app/A.kt",
            "+++ b/app/A.kt",
            "@@ -1 +1 @@",
            "-old",
            "+new",
            "diff --git a/app/B.kt b/app/B.kt",
            "--- a/app/B.kt",
            "+++ b/app/B.kt",
            "@@ -0,0 +1,1 @@",
            "+fresh",
        ).joinToString("\n")

        val files = parseTurnDiff(diff)
        assertEquals(2, files.size)
        assertEquals("app/A.kt", files[0].path)
        assertEquals(DiffFileKind.Modified, files[0].kind)
        assertEquals(1, files[0].additions)
        assertEquals(1, files[0].removals)
        assertEquals("app/B.kt", files[1].path)
        assertEquals(DiffFileKind.Added, files[1].kind)
        assertEquals(1, files[1].additions)
        assertEquals(0, files[1].removals)
    }

    @Test
    fun singleFilePatchWithoutGitHeaderStillYieldsOneEntry() {
        val files = parseTurnDiff("--- a/only.kt\n+++ b/only.kt\n@@ -1 +1 @@\n-x\n+y")
        assertEquals(1, files.size)
        assertEquals("only.kt", files[0].path)
    }

    @Test
    fun emptyDiffYieldsNothing() {
        assertTrue(parseTurnDiff("").isEmpty())
        assertTrue(parseTurnDiff("   \n").isEmpty())
    }

    @Test
    fun fileDiffCarriesDisplayHelpers() {
        val file = fileDiffOf(
            path = "app/src/main/kotlin/com/cy/codexui/state/diff_model.kt",
            lines = parseUnifiedDiff("@@ -1 +1 @@\n-a\n+b"),
        )
        assertEquals("diff_model.kt", file.fileName)
        // parentPath shortens a deep directory from the left, keeping the last three
        // segments so the folder the file actually lives in stays readable.
        assertEquals("…/cy/codexui/state/", file.parentPath)
        assertEquals("M", file.letter)
    }

    @Test
    fun shortPathsKeepTheirDirectory() {
        val file = fileDiffOf("A.kt", emptyList())
        assertEquals("A.kt", file.fileName)
        assertEquals("", file.parentPath)
    }

    @Test
    fun unparseableLinesBecomeContextRatherThanFailing() {
        val lines = parseUnifiedDiff("this is not a diff at all")
        assertEquals(1, lines.size)
        assertEquals(DiffLineKind.Context, lines[0].kind)
    }
}
