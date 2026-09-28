package com.cy.codex

import com.cy.codex.chatwidget.ActiveCompaction
import com.cy.codex.chatwidget.PendingCompactionId
import com.cy.codex.chatwidget.compactionElapsedSeconds
import com.cy.codex.protocol.AppServerRpcException
import com.cy.codex.protocol.protocol.item.CommandExecutionItem
import com.cy.codex.protocol.protocol.item.ContextCompactionItem
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import com.cy.codex.protocol.protocol.item.TurnSeparatorItem
import com.cy.codex.protocol.protocol.v2.ActivePermissionProfile
import com.cy.codex.protocol.protocol.v2.CommandExecutionStatus
import com.cy.codex.protocol.protocol.v2.McpToolCallStatus
import com.cy.codex.protocol.protocol.v2.PermissionProfileEntry
import com.cy.codex.protocol.protocol.v2.ThreadStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStateTest {

    @Test
    fun `an interrupted turn closes still-running tool items`() {
        val state = SessionState()
        state.upsert(CommandExecutionItem("c", "ls", "/tmp"))
        state.upsert(McpToolCallItem("m", "srv", "tool"))
        state.failInProgressItems()
        assertEquals(CommandExecutionStatus.Failed, (state.item("c") as CommandExecutionItem).status)
        assertEquals(McpToolCallStatus.Failed, (state.item("m") as McpToolCallItem).status)
        // The failed item keeps its identity, so a late completion can still replace it.
        assertEquals("c", state.item("c")?.id)
    }

    @Test
    fun `a turn separator is appended once`() {
        val state = SessionState()
        state.appendTurnSeparator(TurnSeparatorItem("turn-separator-t", "Worked for 2m"))
        state.appendTurnSeparator(TurnSeparatorItem("turn-separator-t", "Worked for 2m"))
        assertEquals(1, state.items.size)
    }

    @Test
    fun `a live compaction is status state until its completion lands`() {
        val state = SessionState()
        state.applyStatus(ThreadStatus.Active())
        state.upsert(ContextCompactionItem("compact", startedAtMs = 1_000))
        assertEquals(ActiveCompaction("compact", 1_000), state.activeCompaction)
        assertTrue(state.items.isEmpty())

        // A duplicate start must not restart the timer.
        state.upsert(ContextCompactionItem("compact", startedAtMs = 5_000))
        assertEquals(ActiveCompaction("compact", 1_000), state.activeCompaction)

        state.upsert(ContextCompactionItem("compact", completedAtMs = 4_000))
        assertNull(state.activeCompaction)
        val cell = state.items.single() as ContextCompactionItem
        assertEquals(3L, compactionElapsedSeconds(cell))
    }

    @Test
    fun `a compaction this client did not see start carries no duration`() {
        val state = SessionState()
        state.upsert(ContextCompactionItem("compact", completedAtMs = 4_000))
        assertNull(state.activeCompaction)
        val cell = state.items.single() as ContextCompactionItem
        assertNull(compactionElapsedSeconds(cell))
    }

    @Test
    fun `a restored compaction without timestamps still reaches the transcript`() {
        val state = SessionState()
        state.upsert(ContextCompactionItem("compact"))
        assertNull(state.activeCompaction)
        val cell = state.items.single() as ContextCompactionItem
        assertNull(compactionElapsedSeconds(cell))
    }

    @Test
    fun `slash compact raises the header before the server names it`() {
        val state = SessionState()
        state.beginCompaction()
        assertEquals(PendingCompactionId, state.activeCompaction?.id)
        // The server's own start replaces the placeholder and still keeps the transcript clear.
        state.upsert(ContextCompactionItem("compact", startedAtMs = 5_000))
        assertEquals(ActiveCompaction("compact", 5_000), state.activeCompaction)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `a turn that ends clears a compaction left running`() {
        val state = SessionState()
        state.applyStatus(ThreadStatus.Active())
        state.upsert(ContextCompactionItem("compact", startedAtMs = 1_000))
        state.applyStatus(ThreadStatus.Idle)
        assertNull(state.activeCompaction)
    }

    @Test
    fun `a profile allowed false is disabled by requirements`() {
        val catalog = catalogWith(
            listOf(
                PermissionProfileEntry("work", allowed = true),
                PermissionProfileEntry("locked", allowed = false),
            ),
        )
        assertNull(catalog.permissionProfileDisabledReason("work"))
        assertEquals(PermissionProfileDisabled.ByRequirements, catalog.permissionProfileDisabledReason("locked"))
    }

    @Test
    fun `a requirements map value of false disables a listed profile`() {
        val catalog = catalogWith(
            listOf(
                PermissionProfileEntry("work", allowed = true),
                PermissionProfileEntry("locked", allowed = true),
            ),
            requirements = mapOf("work" to true, "locked" to false),
        )
        assertNull(catalog.permissionProfileDisabledReason("work"))
        assertEquals(PermissionProfileDisabled.ByRequirements, catalog.permissionProfileDisabledReason("locked"))
    }

    @Test
    fun `allowed false and a refusing map together still disable the profile`() {
        val catalog = catalogWith(
            listOf(PermissionProfileEntry("locked", allowed = false)),
            requirements = mapOf("locked" to false),
        )
        assertEquals(PermissionProfileDisabled.ByRequirements, catalog.permissionProfileDisabledReason("locked"))
    }

    @Test
    fun `an absent requirements map leaves allowed as the only verdict`() {
        val catalog = catalogWith(listOf(PermissionProfileEntry("work", allowed = true)))
        assertNull(catalog.permissionProfileDisabledReason("work"))
    }

    @Test
    fun `a whitelist that omits the id keeps the profile disabled`() {
        // A present map is a whitelist: only an explicit `true` releases the profile.
        val catalog = catalogWith(
            listOf(PermissionProfileEntry("work", allowed = true)),
            requirements = mapOf("other" to true),
        )
        assertEquals(PermissionProfileDisabled.ByRequirements, catalog.permissionProfileDisabledReason("work"))
    }

    @Test
    fun `an id the catalog never listed is not available on this server`() {
        val catalog = catalogWith(listOf(PermissionProfileEntry("work", allowed = true)))
        assertEquals(PermissionProfileDisabled.NotOnServer, catalog.permissionProfileDisabledReason("ghost"))
    }

    @Test
    fun `picker rows mark the active permission profile by its id`() {
        val catalog = catalogWith(
            listOf(
                PermissionProfileEntry("work", "Work profile", allowed = true),
                PermissionProfileEntry("home", allowed = true),
            ),
        )
        val rows = catalog.permissionProfileRows(ActivePermissionProfile("home", "base"))
        assertEquals(listOf("work", "home"), rows.map { it.id })
        assertEquals(listOf(false, true), rows.map { it.selected })
        assertEquals("Work profile", rows[0].description)

        // No profile, or only a built-in `:…` mode, selects no row.
        assertEquals(listOf(false, false), catalog.permissionProfileRows(null).map { it.selected })
        val builtinActive = catalog.permissionProfileRows(ActivePermissionProfile(":workspace"))
        assertEquals(listOf("work", "home"), builtinActive.map { it.id })
        assertEquals(listOf(false, false), builtinActive.map { it.selected })
    }

    @Test
    fun `picker rows keep the built-in modes out and grey out a vanished active profile`() {
        val catalog = catalogWith(
            listOf(
                PermissionProfileEntry(":workspace", allowed = true),
                PermissionProfileEntry("work", allowed = true),
            ),
        )
        assertEquals(listOf("work"), catalog.permissionProfileRows(ActivePermissionProfile(":workspace")).map { it.id })

        val rows = catalog.permissionProfileRows(ActivePermissionProfile("gone"))
        assertEquals(listOf("work", "gone"), rows.map { it.id })
        val vanished = rows.last()
        assertTrue(vanished.selected)
        assertEquals(PermissionProfileDisabled.NotOnServer, vanished.disabledReason)
    }

    @Test
    fun `method not found marks permission discovery unsupported`() {
        assertTrue(isPermissionDiscoveryUnsupported(AppServerRpcException(-32601, "method not found")))
        assertTrue(
            isPermissionDiscoveryUnsupported(
                AppServerRpcException(-32600, "Invalid request: unknown variant `permissionProfile/list`"),
            ),
        )
        assertFalse(isPermissionDiscoveryUnsupported(AppServerRpcException(-32603, "internal")))
    }

    @Test
    fun `the experimental permissions gate asks for a newer app server`() {
        val gate = AppServerRpcException(
            -32600,
            "thread/settings/update.permissions requires experimentalApi capability",
        )
        assertTrue(isPermissionSelectionUnsupported(gate))
        assertEquals(PermissionSelectionFailure.RequiresNewerServer, permissionSelectionFailure(gate))
        assertEquals(
            PermissionSelectionFailure.RequiresNewerServer,
            permissionSelectionFailure(AppServerRpcException(-32601, "method not found")),
        )
    }

    @Test
    fun `any other selection failure keeps the transport detail`() {
        assertEquals(
            PermissionSelectionFailure.Failed("unknown profile"),
            permissionSelectionFailure(AppServerRpcException(-32602, "unknown profile")),
        )
    }
}

private fun catalogWith(
    profiles: List<PermissionProfileEntry>,
    requirements: Map<String, Boolean>? = null,
): CatalogState = CatalogState().apply {
    permissionProfiles = profiles
    allowedPermissionProfiles = requirements
}
