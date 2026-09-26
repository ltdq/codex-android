package com.cy.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.cy.codex.R
import com.cy.codex.UiConsts
import com.cy.codex.UiType
import com.cy.codex.protocol.protocol.item.TipItem
import com.cy.codex.raisedSurface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Mirrors `tui/src/tooltips.rs` minus terminal-only entries; random per fresh conversation. */
internal object Tooltips {
    val All: List<String> = listOf(
        "Use /compact when the conversation gets long to summarize history and free up context.",
        "Start a fresh idea with /new; the previous session stays in history.",
        "Use /feedback to send logs to the maintainers when something looks off.",
        "Switch models or reasoning effort quickly with /model.",
        "Use /permissions to control when Codex asks for confirmation.",
        "Run /review to get a code review of your current changes.",
        "Use /skills to list available skills or ask Codex to use one.",
        "Use /status to see the current model, approvals, and token usage.",
        "Use /fork to branch the current chat into a new thread.",
        "Use /side to start a side conversation in a temporary fork without polluting the main thread.",
        "Use /init to create an AGENTS.md with project-specific guidance.",
        "Use /mcp to list configured MCP tools.",
        "Use /rename to rename your threads for easier thread resuming.",
        "Use /recap to summarize the conversation when you come back to it.",
        "Use /export to save the conversation as markdown.",
        "Use /goal to keep Codex working toward an objective.",
        "Use /diff to review the working tree, not just the last turn.",
        "You can run any shell command from Codex using `!` (e.g. `!ls`)",
        "Type / to open the command popup.",
        "Press Tab to queue a message when a task is running.",
        "Use /copy to copy the latest agent response as Markdown.",
    )

    fun random(random: kotlin.random.Random = kotlin.random.Random.Default): String? = All.randomOrNull(random)
}

@Composable
internal fun TooltipCell(item: TipItem, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(UiConsts.CornerControl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(raisedSurface(), shape)
            .padding(horizontal = UiConsts.Space16, vertical = UiConsts.Space12),
    ) {
        Text(
            text = stringResource(R.string.tooltip_cell_label),
            fontSize = UiType.Footnote,
            lineHeight = UiType.FootnoteLine,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
        )
        Spacer(Modifier.height(UiConsts.Space4))
        Text(
            text = item.text,
            fontSize = UiType.Body,
            lineHeight = UiType.BodyLine,
            color = colors.onSurfaceSecondary,
        )
    }
}
