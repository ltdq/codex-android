package com.cy.codex.history_cell

import com.cy.codex.protocol.protocol.item.McpAppUi
import com.cy.codex.protocol.protocol.item.McpToolCallAppContext
import com.cy.codex.protocol.protocol.item.McpToolCallItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Badge projection of the MCP metadata chips (Android-only: `codex-rs/tui/src/history_cell/mcp.rs`
 * renders none of these fields). Expected values are the raw wire fields; a plain MCP server sends none of them.
 */
class McpMetaProjectionTest {

    private fun item(
        appContext: McpToolCallAppContext? = null,
        mcpAppUi: McpAppUi? = null,
        pluginId: String? = null,
        readOnlyHint: Boolean? = null,
        mcpAppResourceUri: String? = null,
    ) = McpToolCallItem(
        id = "call",
        server = "demo",
        tool = "act",
        appContext = appContext,
        mcpAppUi = mcpAppUi,
        pluginId = pluginId,
        readOnlyHint = readOnlyHint,
        mcpAppResourceUri = mcpAppResourceUri,
    )

    @Test
    fun `app name and action name become badges`() {
        val meta = projectMcpMeta(
            item(appContext = McpToolCallAppContext(connectorId = "conn-1", appName = "GitHub", actionName = "create_issue")),
        )
        assertEquals(
            listOf(McpMetaBadge.App("GitHub"), McpMetaBadge.Action("create_issue")),
            meta.badges,
        )
    }

    @Test
    fun `connector id backs the app badge when app name is missing`() {
        val missing = projectMcpMeta(
            item(appContext = McpToolCallAppContext(connectorId = "conn-1", appName = null)),
        )
        assertEquals(listOf(McpMetaBadge.App("conn-1")), missing.badges)

        val blank = projectMcpMeta(
            item(appContext = McpToolCallAppContext(connectorId = "conn-1", appName = "  ")),
        )
        assertEquals(listOf(McpMetaBadge.App("conn-1")), blank.badges)
    }

    @Test
    fun `no app badge without a nameable context`() {
        assertTrue(projectMcpMeta(item()).badges.isEmpty())
        assertTrue(
            projectMcpMeta(
                item(appContext = McpToolCallAppContext(connectorId = "  ", appName = "")),
            ).badges.isEmpty(),
        )
    }

    @Test
    fun `plugin and read only badges appear only for their fields`() {
        val both = projectMcpMeta(item(pluginId = "demo-plugin", readOnlyHint = true))
        assertEquals(
            listOf(McpMetaBadge.Plugin("demo-plugin"), McpMetaBadge.ReadOnly),
            both.badges,
        )
        // readOnlyHint is tri-state: false and null both mean "declared nothing".
        assertEquals(
            listOf(McpMetaBadge.Plugin("demo-plugin")),
            projectMcpMeta(item(pluginId = "demo-plugin", readOnlyHint = false)).badges,
        )
        assertEquals(
            listOf(McpMetaBadge.Plugin("demo-plugin")),
            projectMcpMeta(item(pluginId = "demo-plugin")).badges,
        )
        assertEquals(
            listOf(McpMetaBadge.ReadOnly),
            projectMcpMeta(item(readOnlyHint = true)).badges,
        )
    }

    @Test
    fun `badge order is app then action then plugin then read only`() {
        val meta = projectMcpMeta(
            item(
                appContext = McpToolCallAppContext(
                    connectorId = "conn-1",
                    appName = "GitHub",
                    actionName = "create_issue",
                ),
                pluginId = "demo-plugin",
                readOnlyHint = true,
            ),
        )
        assertEquals(
            listOf(
                McpMetaBadge.App("GitHub"),
                McpMetaBadge.Action("create_issue"),
                McpMetaBadge.Plugin("demo-plugin"),
                McpMetaBadge.ReadOnly,
            ),
            meta.badges,
        )
    }

    @Test
    fun `app resource uri prefers the descriptor ui and falls back to the legacy field`() {
        val preferred = projectMcpMeta(
            item(
                mcpAppUi = McpAppUi(resourceUri = "ui://a"),
                mcpAppResourceUri = "legacy://b",
            ),
        )
        assertEquals("ui://a", preferred.resourceUri)

        val legacy = projectMcpMeta(item(mcpAppResourceUri = "legacy://b"))
        assertEquals("legacy://b", legacy.resourceUri)

        // A descriptor ui without its own uri still leans on the legacy field.
        assertEquals(
            "legacy://b",
            projectMcpMeta(
                item(mcpAppUi = McpAppUi(resourceUri = null), mcpAppResourceUri = "legacy://b"),
            ).resourceUri,
        )
        assertNull(projectMcpMeta(item(mcpAppUi = McpAppUi())).resourceUri)
    }

    @Test
    fun `empty meta keeps the card payload unchanged`() {
        val meta = projectMcpMeta(item())
        assertTrue(meta.badges.isEmpty())
        assertNull(meta.resourceUri)
    }
}
