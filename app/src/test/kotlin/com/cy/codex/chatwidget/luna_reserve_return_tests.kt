package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.v2.ReasoningEffort
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The account-bound return cache of codex-rs/tui/src/chatwidget/luna_reserve_return.rs. */
class LunaReserveReturnTest {

    private val store = InMemoryReserveReturnStore()

    @Test
    fun `entries are stored per thread and read back with their effort`() {
        val entry = ReserveReturnModel("account-a", "gpt-5.6", ReasoningEffort.Medium)
        assertTrue(prepareReserveReturn(store, "thread-1", "account-a", entry.model, entry.effort))
        assertEquals(entry, loadReserveReturn(store, "thread-1", "account-a"))
        assertNull(loadReserveReturn(store, "thread-2", "account-a"), "threads never share a return")
    }

    @Test
    fun `a missing effort survives the round trip`() {
        prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", null)
        assertEquals(
            ReserveReturnModel("account-a", "gpt-5.6", null),
            loadReserveReturn(store, "thread-1", "account-a"),
        )
    }

    @Test
    fun `an entry from another account is refused`() {
        prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", ReasoningEffort.High)
        assertNull(loadReserveReturn(store, "thread-1", "account-b"))
        assertNull(loadReserveReturn(store, "thread-1", null))
    }

    @Test
    fun `a mismatched entry inside the store is still rejected on read`() {
        // A shared cache can hold a payload naming another account; only the payload check can refuse it.
        val store = object : ReserveReturnStore {
            var entry: ReserveReturnModel? = ReserveReturnModel("account-a", "gpt-5.6", null)
            override fun load(threadId: String, accountId: String) = entry
            override fun save(threadId: String, entry: ReserveReturnModel) {}
            override fun clearThread(threadId: String) {}
        }
        assertNull(loadReserveReturn(store, "thread-1", "account-b"))
    }

    @Test
    fun `clear removes the thread's entries and leaves other threads alone`() {
        prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", null)
        prepareReserveReturn(store, "thread-2", "account-a", "gpt-5.6", null)
        clearReserveReturn(store, "thread-1")
        assertNull(loadReserveReturn(store, "thread-1", "account-a"))
        assertEquals("gpt-5.6", loadReserveReturn(store, "thread-2", "account-a")?.model)
    }

    @Test
    fun `forks inherit the parent's return when the account matches`() {
        prepareReserveReturn(store, "parent", "account-a", "gpt-5.6", ReasoningEffort.Low)
        val inherited = inheritReserveReturn(store, "child", "parent", "account-a")
        assertEquals(ReserveReturnModel("account-a", "gpt-5.6", ReasoningEffort.Low), inherited)
        // The child owns a copy: the parent's recovery deleting the parent must not lose it.
        assertEquals(inherited, loadReserveReturn(store, "child", "account-a"))
    }

    @Test
    fun `forks never inherit across accounts`() {
        prepareReserveReturn(store, "parent", "account-a", "gpt-5.6", null)
        assertNull(inheritReserveReturn(store, "child", "parent", "account-b"))
        assertNull(loadReserveReturn(store, "child", "account-b"))
    }

    @Test
    fun `a fork without a parent entry inherits nothing`() {
        assertNull(inheritReserveReturn(store, "child", null, "account-a"))
        assertNull(inheritReserveReturn(store, "child", "parent", "account-a"))
    }

    @Test
    fun `prepare reports failure when the store cannot write`() {
        val store = object : ReserveReturnStore {
            override fun load(threadId: String, accountId: String) = null
            override fun save(threadId: String, entry: ReserveReturnModel) = error("disk full")
            override fun clearThread(threadId: String) {}
        }
        assertFalse(prepareReserveReturn(store, "thread-1", "account-a", "gpt-5.6", null))
        assertFalse(prepareReserveReturn(store, "thread-1", null, "gpt-5.6", null))
        assertFalse(prepareReserveReturn(store, "", "account-a", "gpt-5.6", null))
    }

    @Test
    fun `encoded entries decode back and reject malformed payloads`() {
        val sep = Char(31)
        val entry = ReserveReturnModel("account-a", "gpt-5.6", ReasoningEffort.XHigh)
        assertEquals(entry, decodeReserveReturn(encodeReserveReturn(entry)))
        assertNull(decodeReserveReturn(""))
        assertNull(decodeReserveReturn("account-a"))
        assertNull(decodeReserveReturn("account-a$sep"))
        assertNull(decodeReserveReturn(sep.toString().repeat(2)))
        assertNull(decodeReserveReturn("account-a${sep}gpt-5.6${sep}bogus-effort"))
        assertNull(decodeReserveReturn("x".repeat(4097)))
    }
}
