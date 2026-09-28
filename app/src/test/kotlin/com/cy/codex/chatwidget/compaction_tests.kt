package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompactionTest {

    @Test
    fun `a compaction has a duration only when both ends are known`() {
        assertNull(compactionElapsedSeconds(ContextCompactionItem("c")))
        assertNull(compactionElapsedSeconds(ContextCompactionItem("c", startedAtMs = 1_000)))
        assertNull(compactionElapsedSeconds(ContextCompactionItem("c", completedAtMs = 4_000)))
    }

    @Test
    fun `elapsed seconds truncate the millisecond span`() {
        assertEquals(0L, compactionElapsedSeconds(ContextCompactionItem("c", startedAtMs = 1_000, completedAtMs = 1_999)))
        assertEquals(3L, compactionElapsedSeconds(ContextCompactionItem("c", startedAtMs = 1_000, completedAtMs = 4_999)))
        assertEquals(3_784L, compactionElapsedSeconds(ContextCompactionItem("c", startedAtMs = 0, completedAtMs = 3_784_500)))
    }

    @Test
    fun `a completion before its start counts as no time`() {
        assertEquals(0L, compactionElapsedSeconds(ContextCompactionItem("c", startedAtMs = 9_000, completedAtMs = 1_000)))
    }

    @Test
    fun `the completion message carries the compact elapsed form`() {
        assertEquals("Context compacted", compactionLabel("Context compacted", null))
        assertEquals("Context compacted · 0s", compactionLabel("Context compacted", 0L))
        assertEquals("Context compacted · 3s", compactionLabel("Context compacted", 3L))
        assertEquals("Context compacted · 59s", compactionLabel("Context compacted", 59L))
        assertEquals("Context compacted · 1m 00s", compactionLabel("Context compacted", 60L))
        assertEquals("Context compacted · 1h 03m 04s", compactionLabel("Context compacted", 3_784L))
    }
}
