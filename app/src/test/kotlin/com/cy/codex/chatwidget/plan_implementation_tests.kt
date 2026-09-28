package com.cy.codex.chatwidget

import com.cy.codex.protocol.protocol.v2.ThreadTokenUsage
import com.cy.codex.protocol.protocol.v2.TokenUsageBreakdown
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 112k window with the 12k baseline leaves a round 100k of user-controllable context. */
private fun usage(window: Long?, lastTokens: Long = 0, totalTokens: Long = lastTokens) =
    ThreadTokenUsage(
        total = TokenUsageBreakdown(totalTokens, 0, 0, 0, 0),
        last = TokenUsageBreakdown(lastTokens, 0, 0, 0, 0),
        modelContextWindow = window,
    )

class PlanImplementationTest {

    @Test
    fun `both implementing rows need the default mode`() {
        val options = planImplementationOptions(defaultModeAvailable = false, planMarkdown = "# plan")
        assertEquals(PlanImplementationBlock.DefaultModeUnavailable, options.implement)
        assertEquals(PlanImplementationBlock.DefaultModeUnavailable, options.clearContext)
    }

    @Test
    fun `the fresh context row needs an approved plan`() {
        val blank = planImplementationOptions(defaultModeAvailable = true, planMarkdown = "   ")
        assertNull(blank.implement)
        assertEquals(PlanImplementationBlock.NoApprovedPlan, blank.clearContext)

        val missing = planImplementationOptions(defaultModeAvailable = true, planMarkdown = null)
        assertEquals(PlanImplementationBlock.NoApprovedPlan, missing.clearContext)
    }

    @Test
    fun `an approved plan enables both rows`() {
        val options = planImplementationOptions(defaultModeAvailable = true, planMarkdown = "# plan")
        assertNull(options.implement)
        assertNull(options.clearContext)
    }

    @Test
    fun `the fresh context message carries the prefix and the plan`() {
        assertEquals(
            "$PlanImplementationClearContextPrefix\n\n# plan",
            planImplementationClearContextMessage("# plan"),
        )
    }

    @Test
    fun `remaining percent is measured against the window minus the baseline`() {
        assertEquals(100, usage(112_000, lastTokens = 12_000).contextRemainingPercent())
        assertEquals(50, usage(112_000, lastTokens = 62_000).contextRemainingPercent())
        assertEquals(0, usage(112_000, lastTokens = 112_000).contextRemainingPercent())
        // A window at or under the baseline has nothing to give back.
        assertEquals(0, usage(12_000, lastTokens = 12_000).contextRemainingPercent())
        assertNull(usage(window = null, lastTokens = 62_000).contextRemainingPercent())
    }

    @Test
    fun `context usage reports the used share of the window`() {
        assertEquals("50% used", planImplementationContextUsageLabel(usage(112_000, lastTokens = 62_000)) { it.toString() })
    }

    @Test
    fun `an unspent or unknown window yields no label`() {
        assertNull(planImplementationContextUsageLabel(usage(112_000, lastTokens = 12_000)) { it.toString() })
        assertNull(planImplementationContextUsageLabel(ThreadTokenUsage.Empty) { it.toString() })
    }

    @Test
    fun `a server that reports tokens without a window falls back to the count`() {
        val usage = usage(window = null, totalTokens = 123_000, lastTokens = 0)
        assertEquals("123k used", planImplementationContextUsageLabel(usage) { "${it / 1000}k" })
    }
}
