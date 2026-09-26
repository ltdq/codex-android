package com.cy.codex.perf

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityKeysTest {

    private data class Notice(val message: String)

    @Test
    fun equalInstancesGetDifferentKeys() {
        val keys = IdentityKeys<Notice>()
        val first = Notice("boom")
        val second = Notice("boom")
        val live = listOf(first, second)

        assertNotEquals(keys.keyOf(first, live), keys.keyOf(second, live))
    }

    @Test
    fun theSameInstanceKeepsItsKey() {
        val keys = IdentityKeys<Notice>()
        val notice = Notice("boom")
        val live = listOf(notice)

        val first = keys.keyOf(notice, live)
        val second = keys.keyOf(notice, live)
        assertEquals(first, second)
    }

    @Test
    fun keysAreUniqueAcrossManyAllocations() {
        val keys = IdentityKeys<Notice>()
        val notices = List(64) { Notice("notice-$it") }

        val allocated = notices.map { keys.keyOf(it, notices) }
        assertEquals(notices.size, allocated.toSet().size)
    }

    @Test
    fun aPrunedInstanceIsNeverHandedTheSameKeyTwice() {
        val keys = IdentityKeys<Notice>()
        val first = Notice("first")
        val firstKey = keys.keyOf(first, listOf(first))

        var churn = firstKey
        repeat(40) { index ->
            val temp = Notice("temp-$index")
            churn = keys.keyOf(temp, listOf(temp))
        }
        assertTrue(churn > firstKey)

        val again = keys.keyOf(first, listOf(first))
        assertNotEquals(firstKey, again, "an evicted instance must not resurrect its old key")
    }
}
