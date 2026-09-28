package com.cy.codex.history_cell

import com.cy.codex.chatwidget.foldTranscriptRows
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.ReasoningItem
import com.cy.codex.protocol.protocol.item.ThreadItem
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputerActivityTest {

    private fun computer(id: String, status: McpToolCallStatus = McpToolCallStatus.Completed) =
        McpToolCallItem(id = id, server = "cua_repl", tool = "js", status = status)

    private fun generic(id: String) =
        McpToolCallItem(id = id, server = "example", tool = "js")

    @Test
    fun `adjacent computer calls fold into one row`() {
        val rows =
            foldTranscriptRows(listOf<ThreadItem>(computer("1"), computer("2"), computer("3")))
        assertEquals(1, rows.size)
        assertTrue(rows[0].exposed)
        assertEquals("computer:1", rows[0].key)
        assertEquals(listOf(0, 1, 2), rows[0].indices)
    }

    @Test
    fun `a non computer call breaks the group`() {
        val rows =
            foldTranscriptRows(
                listOf<ThreadItem>(computer("1"), computer("2"), generic("mcp"), computer("3")),
            )
        assertEquals(3, rows.size)
        assertEquals("computer:1", rows[0].key)
        assertEquals(listOf(0, 1), rows[0].indices)
        assertFalse(rows[1].exposed)
        assertEquals(listOf(2), rows[1].indices)
        assertEquals("computer:3", rows[2].key)
        assertEquals(listOf(3), rows[2].indices)
    }

    @Test
    fun `a single computer call stands alone`() {
        val rows =
            foldTranscriptRows(
                listOf<ThreadItem>(generic("mcp"), computer("1"), generic("tail")),
            )
        assertEquals(3, rows.size)
        assertFalse(rows[0].exposed)
        assertTrue(rows[1].exposed)
        assertEquals("computer:1", rows[1].key)
        assertEquals(listOf(1), rows[1].indices)
        assertFalse(rows[2].exposed)
    }

    @Test
    fun `computer calls stay grouped across intervening reasoning`() {
        val rows =
            foldTranscriptRows(
                listOf<ThreadItem>(computer("1"), ReasoningItem("r"), computer("2")),
            )
        assertEquals(1, rows.size)
        assertEquals(listOf(0, 1, 2), rows[0].indices)
    }

    @Test
    fun `reasoning before the first computer call stays a separate row`() {
        val rows =
            foldTranscriptRows(
                listOf<ThreadItem>(ReasoningItem("r"), computer("1"), computer("2")),
            )
        assertEquals(2, rows.size)
        assertFalse(rows[0].exposed)
        assertEquals(listOf(0), rows[0].indices)
        assertEquals(listOf(1, 2), rows[1].indices)
    }

    @Test
    fun `a computer group is active until every call finishes`() {
        assertFalse(computerActivityActive(listOf(computer("1"), computer("2"))))
        assertTrue(
            computerActivityActive(
                listOf(computer("1"), computer("2", McpToolCallStatus.InProgress)),
            ),
        )
        assertFalse(computerActivityActive(listOf(computer("1", McpToolCallStatus.Failed))))
    }

    @Test
    fun `only cua_repl calls are computer activity`() {
        assertTrue(computer("1").isComputerActivity())
        assertFalse(generic("1").isComputerActivity())
    }

    private fun titled(id: String, title: String, status: McpToolCallStatus = McpToolCallStatus.Completed) =
        computer(id, status).copy(arguments = """{"title":"$title"}""")

    @Test
    fun `an action is named by the title in its arguments`() {
        assertEquals("Open settings", computerActionTitle(titled("1", "Open settings")))
        assertEquals(null, computerActionTitle(computer("1")))
        assertEquals(null, computerActionTitle(computer("1").copy(arguments = "not json")))
        assertEquals(null, computerActionTitle(computer("1").copy(arguments = """{"title":"  "}""")))
    }

    @Test
    fun `a running group names only the running call`() {
        val calls = listOf(titled("1", "a"), titled("2", "b", McpToolCallStatus.InProgress))
        assertEquals(listOf("2"), computerActivityVisibleCalls(calls).map { it.id })
    }

    @Test
    fun `a finished group names failures first and caps the rest`() {
        // Five actions, the oldest of which failed: the cap still keeps it, beside the newest.
        val calls =
            listOf(titled("1", "a", McpToolCallStatus.Failed)) +
                (2..5).map { titled("$it", "action $it") }
        assertEquals(listOf("1", "5"), computerActivityVisibleCalls(calls).map { it.id })

        // Three or fewer actions are named in full.
        assertEquals(3, computerActivityVisibleCalls(calls.take(3)).size)
    }
}
