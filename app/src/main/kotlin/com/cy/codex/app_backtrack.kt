package com.cy.codex

import com.cy.codex.protocol.protocol.item.EnteredReviewModeItem
import com.cy.codex.protocol.protocol.item.ExitedReviewModeItem
import com.cy.codex.protocol.protocol.item.UserMessageItem
import com.cy.codex.protocol.protocol.v2.Turn
import com.cy.codex.protocol.protocol.v2.TurnStatus
import com.cy.codex.protocol.protocol.v2.UserInput

/** A validated prompt edit, mirroring `codex/codex-rs/tui/src/app_backtrack.rs`. */
data class BacktrackSelection(val beforeTurnId: String, val prompt: UserMessageItem)

/** Validates an initial prompt as in `codex/codex-rs/tui/src/app_backtrack.rs`; revert excludes its entire turn. */
fun backtrackSelection(turns: List<Turn>, prompt: UserMessageItem): BacktrackSelection {
    var reviewMode = false
    for ((turnIndex, turn) in turns.withIndex()) {
        val hiddenReview = turnIndex > 0 && isHiddenNestedReviewTurn(turns[turnIndex - 1], turn)
        var userMessages = 0
        for (item in turn.items) {
            when (item) {
                is EnteredReviewModeItem -> reviewMode = true
                is ExitedReviewModeItem -> reviewMode = false
                is UserMessageItem -> {
                    val isSteer = userMessages++ > 0
                    if (item.id != prompt.id) continue
                    require(!reviewMode && !hiddenReview && item.hasVisibleContent()) {
                        "The selected prompt is not visible in this thread"
                    }
                    require(!isSteer) { "A message sent during a turn cannot be edited independently" }
                    require(turn.status != TurnStatus.InProgress) { "The selected turn is still running" }
                    require(item.content == prompt.content) { "The selected prompt has changed; reload the thread" }
                    return BacktrackSelection(turn.id, item)
                }
                else -> Unit
            }
        }
    }
    error("The selected prompt is no longer in this thread")
}

/** Inline review duplicates its inputs (`codex/codex-rs/tui/src/app_backtrack.rs`). */
internal fun isHiddenNestedReviewTurn(previous: Turn, turn: Turn): Boolean {
    if (previous.status != TurnStatus.Completed || turn.status != TurnStatus.Interrupted ||
        turn.completedAt != null ||
        previous.items.none { it is EnteredReviewModeItem } ||
        previous.items.none { it is ExitedReviewModeItem }
    ) return false
    val prompts = turn.items.filterIsInstance<UserMessageItem>()
    return prompts.size == 2 && prompts[0].content == prompts[1].content
}

private fun UserMessageItem.hasVisibleContent(): Boolean = content.any { input ->
    input !is UserInput.Text || input.text.isNotBlank() || input.textElements.isNotEmpty()
}
