package com.cy.codex.history_cell

import com.cy.codex.chatwidget.hookRunRowKeys
import com.cy.codex.chatwidget.transcriptIsEmpty
import com.cy.codex.protocol.protocol.item.WebSearchResult
import com.cy.codex.protocol.protocol.v2.HookRunSummary
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Row and body projection of the web search cell; Android-only, `codex-rs/tui/src/history_cell/search.rs` never renders results. */
class SearchProjectionTest {

    private fun result(
        title: String = "A",
        url: String = "https://example.com/a",
        snippet: String? = null,
        type: String? = null,
        refId: String? = null,
    ) = WebSearchResult(title = title, url = url, snippet = snippet, type = type, refId = refId)

    @Test
    fun `blank title falls back to the url`() {
        assertEquals(
            "https://example.com/a",
            projectSearchResultRow(result(title = "")).title,
        )
        assertEquals("kept", projectSearchResultRow(result(title = "kept")).title)
    }

    @Test
    fun `a result kept on its ref id alone shows that id instead of a blank row`() {
        assertEquals(
            "turn0search0",
            projectSearchResultRow(result(title = "", url = "", refId = "turn0search0")).title,
        )
        // The ref id is the last resort only; a real title still wins.
        assertEquals("A", projectSearchResultRow(result(refId = "turn0search0")).title)
    }

    @Test
    fun `snippet rides along only when it says something`() {
        assertEquals("summary", projectSearchResultRow(result(snippet = "summary")).snippet)
        assertNull(projectSearchResultRow(result(snippet = null)).snippet)
        assertNull(projectSearchResultRow(result(snippet = "   ")).snippet)
    }

    @Test
    fun `unknown result types badge with the raw discriminator and text_result does not`() {
        assertEquals(
            "image_result",
            projectSearchResultRow(result(type = "image_result")).typeBadge,
            "the wire discriminator is shown as-is, never prettified",
        )
        assertNull(projectSearchResultRow(result(type = "text_result")).typeBadge)
        assertNull(projectSearchResultRow(result(type = null)).typeBadge)
        assertNull(projectSearchResultRow(result(type = " ")).typeBadge)
    }

    @Test
    fun `a missing results payload is not an empty search`() {
        assertEquals(WebSearchBody.NoPayload, projectWebSearchBody(null))
    }

    @Test
    fun `an explicit empty list is the no results state`() {
        assertEquals(WebSearchBody.NoResults, projectWebSearchBody(emptyList()))
    }

    @Test
    fun `results project to rows in order`() {
        val body = projectWebSearchBody(
            listOf(
                result(title = "A", url = "https://a.example", snippet = "sa", type = "text_result"),
                result(title = "", url = "https://b.example", type = "image_result"),
            ),
        )
        val rows = (body as WebSearchBody.Rows).rows
        assertEquals(2, rows.size)
        assertEquals("A", rows[0].title)
        assertEquals("sa", rows[0].snippet)
        assertNull(rows[0].typeBadge)
        assertEquals("https://b.example", rows[1].title)
        assertEquals("image_result", rows[1].typeBadge)
    }
}

/** Wiring of the transcript's hook-run rows (chatwidget/rendering.kt). */
class HookRunRowsTest {

    @Test
    fun `hook run rows cover every recorded run in order`() {
        val runs = listOf(
            HookRunSummary(id = "pre-tool-use:1:/w/hooks.json", status = "failed"),
            HookRunSummary(id = "stop:2:/w/hooks.json", status = "completed"),
        )
        assertEquals(
            listOf("hook-run:pre-tool-use:1:/w/hooks.json", "hook-run:stop:2:/w/hooks.json"),
            hookRunRowKeys(runs),
        )
    }

    @Test
    fun `no recorded runs means no hook run rows`() {
        assertEquals(emptyList<String>(), hookRunRowKeys(emptyList()))
    }
}

/** The live pane's empty check (chatwidget/rendering.kt `transcriptIsEmpty`): hook runs count as content. */
class TranscriptEmptyTest {

    @Test
    fun `hook runs alone keep the transcript from counting as empty`() {
        val run = HookRunSummary(id = "stop:1:/w/hooks.json", status = "completed")
        assertTrue(transcriptIsEmpty(emptyList(), emptyList(), emptyList()))
        assertFalse(transcriptIsEmpty(emptyList(), emptyList(), listOf(run)))
    }
}
