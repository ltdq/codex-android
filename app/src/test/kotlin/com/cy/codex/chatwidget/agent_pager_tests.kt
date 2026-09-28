package com.cy.codex.chatwidget

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class AgentPagerTest {
    @Test
    fun `initial settled parent page does not clear a preselected agent`() = runTest {
        val selections = agentPageSelections(
            flowOf(0, 1, 0),
            listOf("parent", "selected_agent"),
            "parent",
        ).toList()

        assertEquals(listOf("selected_agent", null), selections)
    }

    @Test
    fun `listing change ignores the current page and follows its next settlement`() = runTest {
        val selections = agentPageSelections(
            flowOf(1, 2),
            listOf("parent", "new_older_agent", "selected_agent"),
            "parent",
        ).toList()

        assertEquals(listOf("selected_agent"), selections)
    }
}
