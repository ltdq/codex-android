package com.cy.codex.history_cell

import com.cy.codex.app.transcriptMarkdown
import com.cy.codex.protocol.protocol.item.HookPromptFragment
import com.cy.codex.protocol.protocol.item.HookPromptItem
import com.cy.codex.protocol.protocol.v2.HookMetadata
import com.cy.codex.protocol.protocol.v2.HookOutputEntry
import com.cy.codex.protocol.protocol.v2.HookRunSummary
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Expected lines are the upstream English wording of `output_lines` (codex-rs/tui/src/history_cell/hook_cell.rs);
 * the composable supplies the localized labels.
 */
class HookCellTest {

    private val labels = HookCellLabels(
        completed = "Hook completed",
        failed = "Hook failed",
        blocked = "Blocked by hook",
        stopped = "Hook stopped",
        running = "Hook running",
        provenanceFormat = "↳ Hook · %1\$s",
    )

    private fun run(status: String, vararg entries: HookOutputEntry) =
        HookRunSummary(id = "run", status = status, entries = entries.toList())

    private fun entry(kind: String, text: String) = HookOutputEntry(kind = kind, text = text)

    @Test
    fun `completed run with a warning rewrites the header into the provenance line`() {
        val output = hookRunOutputLines(
            run("completed", entry("warning", "first\nsecond")),
            labels,
        )
        assertEquals(listOf("↳ Hook · first", "    second"), output)
    }

    @Test
    fun `completed run without a warning shows the status header only`() {
        assertEquals(listOf("• Hook completed"), hookRunOutputLines(run("completed"), labels))
    }

    @Test
    fun `failure statuses name themselves and put entries under the header`() {
        assertEquals(
            listOf("• Hook failed", "  └ boom"),
            hookRunOutputLines(run("failed", entry("error", "boom")), labels),
        )
        assertEquals(
            listOf("• Blocked by hook"),
            hookRunOutputLines(run("blocked"), labels),
        )
        assertEquals(
            listOf("• Hook stopped", "  └ stop right there"),
            hookRunOutputLines(run("stopped", entry("stop", "stop right there")), labels),
        )
    }

    @Test
    fun `a warning under a failure header keeps its body indented`() {
        assertEquals(
            listOf("• Hook failed", "  └ head", "    body"),
            hookRunOutputLines(run("failed", entry("warning", "head\nbody")), labels),
        )
    }

    @Test
    fun `stop feedback and error entries each get their own block with blank lines kept`() {
        val output = hookRunOutputLines(
            run(
                "completed",
                entry("stop", "s1\n\ns2"),
                entry("feedback", "f"),
                entry("error", "e1\ne2"),
            ),
            labels,
        )
        assertEquals(
            listOf(
                "• Hook completed",
                "  └ s1",
                "",
                "    s2",
                "  └ f",
                "  └ e1",
                "    e2",
            ),
            output,
        )
    }

    @Test
    fun `context entries never show and only the first warning is kept`() {
        val output = hookRunOutputLines(
            run(
                "completed",
                entry("context", "model only"),
                entry("warning", "one\ntwo"),
                entry("warning", "skipped whole"),
            ),
            labels,
        )
        assertEquals(listOf("↳ Hook · one", "    two"), output)
    }

    @Test
    fun `quiet success leaves no trace while anything user facing is kept`() {
        assertTrue(hookRunIsQuietSuccess(run("completed", entry("context", "model only"))))
        assertTrue(hookRunIsQuietSuccess(run("completed")))
        assertFalse(hookRunIsQuietSuccess(run("completed", entry("warning", "w"))))
        // A failed run persists even without output: the status header alone is the record.
        assertFalse(hookRunIsQuietSuccess(run("failed")))
    }

    @Test
    fun `several runs render as blocks separated by a blank line`() {
        val output = hookOutputLines(
            listOf(
                run("completed", entry("warning", "w")),
                run("failed", entry("error", "boom")),
            ),
            labels,
        )
        assertEquals(
            listOf("↳ Hook · w", "", "• Hook failed", "  └ boom"),
            output,
        )
    }

    @Test
    fun `run id parsing splits three fields and strips known tool use suffixes`() {
        assertEquals(
            HookRunKey("preToolUse", 2, "/w/hooks.json"),
            parseHookRunId("pre-tool-use:2:/w/hooks.json:tu_1", listOf("/w/hooks.json")),
        )
        // A source path may itself contain colons; the split must keep it whole.
        assertEquals(
            HookRunKey("stop", 0, "/w/a:b.json"),
            parseHookRunId("stop:0:/w/a:b.json", listOf("/w/a:b.json")),
        )
        assertEquals(
            HookRunKey("userPromptSubmit", 7, "/w/hooks.json"),
            parseHookRunId("user-prompt-submit:7:/w/hooks.json"),
        )
    }

    @Test
    fun `unparseable run ids yield no identity`() {
        assertNull(parseHookRunId("mystery:0:/w/hooks.json"))
        assertNull(parseHookRunId("pre-tool-use:not-a-number:/w/hooks.json"))
        assertNull(parseHookRunId("pre-tool-use:2"))
    }

    @Test
    fun `identity join names the handler from its metadata`() {
        val command = HookMetadata(
            key = "k1",
            eventName = "preToolUse",
            command = "lint.sh",
            displayOrder = 2,
            sourcePath = "/w/hooks.json",
        )
        val mcpTool = HookMetadata(
            key = "k2",
            eventName = "preToolUse",
            handlerType = "mcpTool",
            server = "srv",
            tool = "tool",
            displayOrder = 3,
            sourcePath = "/w/hooks.json",
        )
        val keyed = HookMetadata(
            key = "k3",
            eventName = "stop",
            handlerType = "command",
            displayOrder = 0,
            sourcePath = "/w/hooks.json",
        )
        val hooks = listOf(command, mcpTool, keyed)
        val namedRun = HookRunSummary(
            id = "pre-tool-use:2:/w/hooks.json",
            eventName = "preToolUse",
            displayOrder = 2,
            sourcePath = "/w/hooks.json",
        )
        assertEquals("lint.sh", resolveHookLabel(namedRun, hooks))
        assertEquals(
            "srv/tool",
            resolveHookLabel(
                HookRunSummary(
                    id = "pre-tool-use:3:/w/hooks.json",
                    eventName = "preToolUse",
                    displayOrder = 3,
                    sourcePath = "/w/hooks.json",
                ),
                hooks,
            ),
        )
        assertEquals("k3", resolveHookLabel(namedRun.copy(eventName = "stop", displayOrder = 0), hooks))
    }

    @Test
    fun `unjoinable runs fall back to nothing so callers can say Hook`() {
        // Builtin hooks are filtered out of hooks/list yet still emit runs.
        val builtin = HookRunSummary(
            id = "stop:9:/internal/builtin",
            eventName = "stop",
            displayOrder = 9,
            sourcePath = "/internal/builtin",
        )
        assertNull(resolveHookLabel(builtin, emptyList()))
        assertNull(resolveHookLabel("pre-tool-use:2:/w/hooks.json:tu_1", emptyList()))
        assertNull(resolveHookLabel("", emptyList()))
    }

    @Test
    fun `hook prompt run ids join to the same identity`() {
        val hooks = listOf(
            HookMetadata(
                key = "k1",
                eventName = "preToolUse",
                command = "lint.sh",
                displayOrder = 2,
                sourcePath = "/w/hooks.json",
            ),
        )
        assertEquals(
            "lint.sh",
            resolveHookLabel("pre-tool-use:2:/w/hooks.json:tu_1", hooks),
        )
    }

    @Test
    fun `hook failure lines skip context entries`() {
        assertEquals(
            listOf("boom", "more"),
            hookFailureLines(
                run(
                    "failed",
                    entry("context", "model only"),
                    entry("error", "boom\nmore"),
                ),
            ),
        )
        assertEquals(emptyList(), hookFailureLines(run("failed", entry("context", "model only"))))
    }
}

/** Export labeling of hook prompts (app/transcript_export.kt). */
class HookExportLabelTest {

    private val hooks = listOf(
        HookMetadata(
            key = "k1",
            eventName = "preToolUse",
            command = "lint.sh",
            displayOrder = 2,
            sourcePath = "/w/hooks.json",
        ),
    )

    private fun export(hookRunId: String): String = transcriptMarkdown(
        listOf(HookPromptItem("h", listOf(HookPromptFragment("body", hookRunId)))),
        hooks,
    )!!

    @Test
    fun `a joinable run names the hook in the export header`() {
        assertTrue(
            export("pre-tool-use:2:/w/hooks.json:tu_1").contains("    hook lint.sh:\n    body\n"),
        )
    }

    @Test
    fun `an unjoinable run keeps the plain hook prefix instead of doubling the label`() {
        val markdown = export("stop:9:/internal/builtin")
        assertTrue(markdown.contains("    hook:\n    body\n"), markdown)
        assertFalse(markdown.contains("hook Hook:"), markdown)
    }

    @Test
    fun `a blank run id keeps the plain hook prefix`() {
        assertTrue(export("").contains("    hook:\n    body\n"))
    }
}
